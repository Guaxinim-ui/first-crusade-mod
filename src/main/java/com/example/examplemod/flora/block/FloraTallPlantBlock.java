package com.example.examplemod.flora.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Two-block-tall vegetation (tall grasses, reeds). {@link DoublePlantBlock} already handles the
 * upper/lower halves, breaking both together, and — importantly for worldgen — refusing to place
 * when the block above is not free, so tall plants never punch through a floor or a roof.
 */
public class FloraTallPlantBlock extends DoublePlantBlock {
    private final TagKey<Block> groundTag;

    public FloraTallPlantBlock(Properties properties, TagKey<Block> groundTag) {
        super(properties);
        this.groundTag = groundTag;
    }

    public TagKey<Block> groundTag() {
        return this.groundTag;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(this.groundTag);
    }
}
