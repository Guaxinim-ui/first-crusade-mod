package com.example.examplemod.flora.fruit;

import java.util.function.Supplier;

import com.example.examplemod.flora.FloraConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A fruit hanging from the underside of a canopy: four growth stages, harvested by hand.
 *
 * <h2>Why the fruit is a node and not a property of the leaves</h2>
 *
 * The obvious design — every leaf block occasionally drops fruit — is the one the brief rules out,
 * and rightly. It makes fruit a function of canopy volume, so a big tree floods the player and a
 * small one gives nothing; it puts a random tick with a payload on <i>every</i> leaf in the world;
 * and it gives the player nothing to look at, because a fruiting tree looks exactly like any other.
 *
 * <p>A node is the opposite on all three counts. There are one to four of them per tree, placed at
 * generation time by the {@code minecraft:attached_to_leaves} decorator, so the yield is a property
 * of the <i>tree</i> and is visible from outside it.
 *
 * <h2>What this block deliberately is not</h2>
 *
 * No block entity, and no scheduled tick. Growth is a random tick — the same budget vanilla wheat
 * runs on — and it is cheap because there are so few nodes: a forest chunk holds tens of them, not
 * the thousands of leaf blocks it would take to do this the other way.
 *
 * <p>The node hangs from the leaves and checks that support the ordinary way, so felling a tree
 * pops its fruit off instead of leaving it in the air. That single rule is also what keeps the
 * generator honest: a decorator that tried to attach a node to a trunk or into open air produces a
 * block that immediately fails {@link #canSurvive} and drops.
 */
public class FruitNodeBlock extends Block {

    /** 0-2 are stages of growth; only {@link #RIPE} can be picked. */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    public static final int RIPE = 3;

    private static final VoxelShape[] SHAPES = {
            Block.box(6.0D, 11.0D, 6.0D, 10.0D, 16.0D, 10.0D),
            Block.box(5.0D, 10.0D, 5.0D, 11.0D, 16.0D, 11.0D),
            Block.box(4.0D, 9.0D, 4.0D, 12.0D, 16.0D, 12.0D),
            Block.box(4.0D, 8.0D, 4.0D, 12.0D, 16.0D, 12.0D),
    };

    private final Supplier<Item> fruit;
    private final int minYield;
    private final int maxYield;

    public FruitNodeBlock(Properties properties, Supplier<Item> fruit, int minYield, int maxYield) {
        super(properties);
        this.fruit = fruit;
        this.minYield = minYield;
        this.maxYield = maxYield;
        registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    public Supplier<Item> fruit() {
        return this.fruit;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AGE)];
    }

    // ------------------------------------------------------------------ support

    /**
     * Hangs from leaves and nothing else.
     *
     * <p>Any leaves, by tag, rather than the species that grew it. A node grafted onto another
     * canopy is a perfectly reasonable thing for a future farming phase to allow, and refusing it
     * here would be a rule this class has no business inventing.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(BlockTags.LEAVES);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                  net.minecraft.world.level.LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.UP && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        return state;
    }

    // ------------------------------------------------------------------ growth

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < RIPE;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);

        if (age >= RIPE || !FloraConfig.FRUIT_REGROWTH_ENABLED.get()) {
            return;
        }

        if (random.nextDouble() < FloraConfig.FRUIT_REGROWTH_CHANCE.get()) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ harvest

    /**
     * Picking is a right-click on a ripe node. The node stays and resets to stage zero, which is the
     * difference between an orchard and a one-off harvest — and the reason the regrowth chance is
     * the config a server operator actually wants to turn.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(AGE) < RIPE) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            int count = this.minYield + level.random.nextInt(this.maxYield - this.minYield + 1);
            popResource(level, pos, new ItemStack(this.fruit.get(), count));
            level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                    SoundSource.BLOCKS, 0.8F, 0.8F + level.random.nextFloat() * 0.4F);
            level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
