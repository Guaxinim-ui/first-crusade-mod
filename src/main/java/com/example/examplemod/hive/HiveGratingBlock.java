package com.example.examplemod.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * See-through industrial grating (full cube collision, cutout texture with real holes).
 *
 * Behaves like glass for rendering purposes: light passes through, faces between adjacent
 * gratings are culled (so large grated floors stay cheap to render), and it never darkens the
 * level below — important for the multi-level Hive floors where players must glimpse the
 * machinery underneath.
 */
public class HiveGratingBlock extends Block {

    public HiveGratingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, direction);
    }
}
