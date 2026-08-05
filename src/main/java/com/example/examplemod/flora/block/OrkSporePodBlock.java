package com.example.examplemod.flora.block;

import com.example.examplemod.FCRegistry;
import com.example.examplemod.flora.FloraTags;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Orkoid life cycle, as a block.
 *
 * <p>Orks are a fungus. A spore takes root, swells through three visible stages and finally becomes
 * a cocoon that splits open and puts a living greenskin on the ground. This block is that cycle:
 * four stages, one per quarter of a Minecraft day, and at the end of the fourth an Ork crawls out.
 *
 * <h2>Timing without a block entity</h2>
 *
 * The rule the rest of the vegetation system lives by is that plants get no block entities and no
 * individual tickers — a field of thousands of these would otherwise be a tick sink. So the pod
 * carries two small properties instead: its {@link #AGE}, and {@link #LAST_QUARTER}, the quarter of
 * the day in which it last advanced.
 *
 * <p>On a random tick it compares the world's current quarter against the one it remembers. If they
 * differ, it advances once and records the new quarter; if they match, it does nothing. Random
 * ticks come far more often than once per quarter-day, so every pod reliably advances exactly one
 * stage per quarter — no matter how many are loaded, how long the chunk was away, or whether the
 * server was down. Two properties and a comparison, in place of a ticking block entity per plant.
 *
 * <h2>What hatches</h2>
 *
 * A Gretchin — the weakest greenskin there is, at 8 health against an Ork Boy's 30. A spore pod is
 * how the tide replenishes its labourers, not how it fields an army; Boyz are still raised by a
 * camp spending its loot.
 */
public class OrkSporePodBlock extends BushBlock {

    /** Visible stage: 0 sprout, 1 fungus, 2 swollen fungus, 3 cocoon about to split. */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    /** The quarter-day this pod last grew in. Purely bookkeeping — see the class javadoc. */
    public static final IntegerProperty LAST_QUARTER = IntegerProperty.create("last_quarter", 0, 3);

    public static final int MAX_AGE = 3;

    /** A Minecraft day is 24000 ticks; a quarter of one is a stage. */
    private static final int QUARTER_TICKS = 6000;

    private static final VoxelShape[] SHAPES = {
            Block.box(5.0D, 0.0D, 5.0D, 11.0D, 4.0D, 11.0D),
            Block.box(4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D),
            Block.box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D),
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 15.0D, 14.0D),
    };

    public OrkSporePodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(LAST_QUARTER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, LAST_QUARTER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AGE)];
    }

    /** Spores take in anything: dirt, rock, rubble. That is rather the point of them. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(FloraTags.GROUND_ANY);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int quarter = currentQuarter(level);

        // Already grown in this quarter — wait for the next one.
        if (quarter == state.getValue(LAST_QUARTER)) {
            return;
        }

        int age = state.getValue(AGE);

        if (age < MAX_AGE) {
            level.setBlock(pos, state.setValue(AGE, age + 1).setValue(LAST_QUARTER, quarter),
                    Block.UPDATE_CLIENTS);
            return;
        }

        hatch(level, pos);
    }

    /** Which quarter of the day it is, 0..3. */
    private static int currentQuarter(Level level) {
        return (int) ((level.getDayTime() / QUARTER_TICKS) % 4L);
    }

    /** How many greenskins may already stand within {@link #CROWD_RADIUS} before a pod gives up. */
    private static final int CROWD_LIMIT = 6;
    private static final double CROWD_RADIUS = 24.0D;

    /**
     * The cocoon splits — unless the ground around it is already full of greenskins.
     *
     * <p><b>Why the cap exists.</b> Without it this block is an unbounded mob generator: the chunk
     * decorator plants pods across a camp's whole halo, every pod becomes a Gretchin, and nothing
     * anywhere counts them. In play that ended with the Imperium losing the planet to a tide that
     * grew out of the scenery rather than out of any decision. A spore pod should dress an Ork camp
     * and occasionally reinforce it, not out-produce the war.
     *
     * <p>The count is local and cheap: one AABB query, and only at the moment a pod is ripe, which
     * is at most once per pod per quarter-day. A crowded pod keeps its ripe stage and simply tries
     * again later, so a camp that gets cleared out will repopulate on its own.
     *
     * <p>The pod is consumed on a real hatch either way — a hatch that finds nowhere to put the
     * Gretchin still ends the pod's life, so a blocked-in cocoon cannot sit there retrying forever.
     */
    private void hatch(ServerLevel level, BlockPos pos) {
        if (crowded(level, pos)) {
            return;
        }

        level.removeBlock(pos, false);

        Mob ork = FCRegistry.GRETCHIN.get().create(level);

        if (ork == null) {
            return;
        }

        ork.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);

        ork.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);

        if (!level.addFreshEntity(ork)) {
            return;
        }

        level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.9F, 0.7F);
        level.levelEvent(2001, pos, Block.getId(Blocks.SLIME_BLOCK.defaultBlockState()));
    }

    /**
     * True when there are already {@link #CROWD_LIMIT} greenskins nearby.
     *
     * <p>Counts any mob of the mod's Ork family rather than Gretchins alone: a pod that hatches
     * into a warband of Boyz has not found empty ground just because none of them is a Gretchin.
     */
    private static boolean crowded(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.AABB box =
                new net.minecraft.world.phys.AABB(pos).inflate(CROWD_RADIUS);

        return level.getEntitiesOfClass(Mob.class, box, OrkSporePodBlock::isGreenskin).size()
                >= CROWD_LIMIT;
    }

    private static boolean isGreenskin(Mob mob) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());

        return id != null
                && id.getNamespace().equals(com.example.examplemod.ExampleMod.MODID)
                && (id.getPath().contains("ork") || id.getPath().contains("gretchin")
                    || id.getPath().contains("nob") || id.getPath().contains("squig"));
    }
}
