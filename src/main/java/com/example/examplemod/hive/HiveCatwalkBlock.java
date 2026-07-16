package com.example.examplemod.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Industrial catwalk floor plate.
 *
 * The walking surface sits at the TOP of the block space (a 3px plate from y=13 to y=16), so a
 * catwalk placed next to a full block is perfectly level with it — NPCs and players walk across
 * without jumps, which the module navigation rules (spec §13) require. The empty 13px underneath
 * is intentional: it is where pipes, cables and lumen strips run beneath the walkways, exactly
 * like the reference art.
 */
public class HiveCatwalkBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0.0D, 13.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public HiveCatwalkBlock(BlockBehaviour.Properties properties) {
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
