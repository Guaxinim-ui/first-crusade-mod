package com.example.examplemod.campaign.wartable;

import com.example.examplemod.FCRegistry;
import com.example.examplemod.FirstCrusadeNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Strategium's War Table: the map of the whole Crusade.
 *
 * <h2>Beside the Research bench, not instead of it</h2>
 *
 * The {@link com.example.examplemod.StrategiumBlock} still does exactly what it did — it is where
 * research is funded and started, and none of that moved. This is the second console in the same
 * room: research is what the Imperium is <i>building</i>, and this is what it is <i>fighting</i>.
 * Folding the two into one screen would have meant rewriting a system that works to make room for
 * one that did not exist yet.
 *
 * <h2>A read-only picture, on purpose, for now</h2>
 *
 * Right-clicking sends the player a {@link WarTableSnapshot} and opens a screen over it. The screen
 * can ask for a fresh snapshot and nothing else. Commanding troops from the table — the brief's
 * defend/attack/reinforce orders — is the next slice; adding a half-validated version of it now
 * would mean shipping a button that changes the war without the server checking whether the player
 * has anything to send.
 */
public class WarTableBlock extends HorizontalDirectionalBlock {

    /** How close the player must stay to keep using the table. */
    public static final double USE_RANGE = 8.0D;

    /** A table: knee-to-waist high, so it reads as something you lean over rather than a machine. */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    public WarTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        serverPlayer.playNotifySound(SoundEvents.LODESTONE_COMPASS_LOCK,
                SoundSource.BLOCKS, 0.7F, 1.2F);

        FirstCrusadeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> serverPlayer),
                new WarTableSnapshotPacket(WarTableSnapshot.capture(serverPlayer, pos)));

        return InteractionResult.CONSUME;
    }

    /**
     * Whether this player may still read the table at this position.
     *
     * <p>Re-checked on every refresh request rather than trusted from when the screen opened. The
     * two moments are different: a player can open the table, walk out of the room, and press
     * refresh. Without this, the position in the packet would be enough to read the strategic
     * picture from anywhere on the server.
     */
    public static boolean canUse(ServerPlayer player, BlockPos tablePos) {
        if (player.blockPosition().distSqr(tablePos) > USE_RANGE * USE_RANGE) {
            return false;
        }

        return player.level().getBlockState(tablePos).is(FCRegistry.WAR_TABLE.get());
    }
}
