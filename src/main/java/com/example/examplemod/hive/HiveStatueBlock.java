package com.example.examplemod.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;

/**
 * Base for tall statues that occupy a vertical column of blocks (2..4 tall). Only the bottom
 * block is "master" and carries the model + collision; the blocks above are lightweight
 * upper parts (air-like collision, no model of their own — the master's model is tall).
 *
 * This is the cheap multiblock pattern used by vanilla doors/tall flowers: one HALF/PART
 * property, no BlockEntity. Breaking any part removes the whole column and drops one item.
 * Rotatable via FACING. Height is fixed per registered block.
 */
public class HiveStatueBlock extends HorizontalDirectionalBlock {

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    private final int height;

    public HiveStatueBlock(int height, Properties properties) {
        super(properties);
        this.height = Math.max(2, Math.min(4, height));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        // need `height` free blocks upward
        for (int i = 1; i < height; i++) {
            if (!level.getBlockState(pos.above(i)).canBeReplaced(context)) {
                return null;
            }
        }
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        for (int i = 1; i < height; i++) {
            level.setBlock(pos.above(i), state.setValue(PART, i), 3);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        int part = state.getValue(PART);
        // if the block that should be adjacent in the column is gone, break
        if (dir == Direction.DOWN && part > 0) {
            BlockState below = level.getBlockState(pos.below());
            if (!(below.getBlock() instanceof HiveStatueBlock) || below.getValue(PART) != part - 1) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }
        if (dir == Direction.UP && part < height - 1) {
            BlockState above = level.getBlockState(pos.above());
            if (!(above.getBlock() instanceof HiveStatueBlock) || above.getValue(PART) != part + 1) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(PART) == 0) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof HiveStatueBlock;
    }

    @Override
    @SuppressWarnings("deprecation")
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    public int getHeight() {
        return height;
    }
}
