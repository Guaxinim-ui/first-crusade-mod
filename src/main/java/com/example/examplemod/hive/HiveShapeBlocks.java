package com.example.examplemod.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Small shape-only blocks for the detailing pack:
 *  - {@link Table}: full-width top on four legs (a real table you can put things under).
 *  - {@link Rug}: 1px carpet-like mat that light passes through.
 *
 * All are dumb decoration: no BlockEntity, no ticking (spec §15).
 */
public final class HiveShapeBlocks {

    private HiveShapeBlocks() {
    }

    public static class Table extends Block {
        private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
                Block.box(0.0D, 13.0D, 0.0D, 16.0D, 16.0D, 16.0D),   // table top
                Block.box(1.0D, 0.0D, 1.0D, 4.0D, 13.0D, 4.0D),      // legs
                Block.box(12.0D, 0.0D, 1.0D, 15.0D, 13.0D, 4.0D),
                Block.box(1.0D, 0.0D, 12.0D, 4.0D, 13.0D, 15.0D),
                Block.box(12.0D, 0.0D, 12.0D, 15.0D, 13.0D, 15.0D));

        public Table(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return true;
        }
    }

    public static class Rug extends Block {
        private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

        public Rug(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return true;
        }

        @Override
        public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
            return 1.0F;
        }
    }
}
