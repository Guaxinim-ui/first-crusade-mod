package com.example.examplemod.hive;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sittable seat (chair / bench / cogitator stool). Right-click to sit: we spawn a tiny
 * invisible "seat" marker entity ({@link HiveSeatEntity}) at the block and mount the player
 * on it; dismounting returns the player to the block top. Uses a lightweight marker rather
 * than a BlockEntity so idle chairs cost nothing (spec §15).
 *
 * FACING points the backrest away from the player (chair model faces the sitter).
 */
public class HiveChairBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 10.0D, 15.0D);
    private final double seatHeight;

    public HiveChairBlock(double seatHeight, BlockBehaviour.Properties properties) {
        super(properties);
        this.seatHeight = seatHeight;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                               BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // already occupied?
        AABB box = new AABB(pos).inflate(0.1D);
        List<HiveSeatEntity> seats = level.getEntitiesOfClass(HiveSeatEntity.class, box);
        if (!seats.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!player.getPassengers().isEmpty() || player.isPassenger()) {
            return InteractionResult.PASS;
        }
        HiveSeatEntity seat = new HiveSeatEntity(HiveEntities.SEAT.get(), level);
        seat.setPos(pos.getX() + 0.5D, pos.getY() + seatHeight, pos.getZ() + 0.5D);
        level.addFreshEntity(seat);
        player.startRiding(seat);
        return InteractionResult.CONSUME;
    }
}
