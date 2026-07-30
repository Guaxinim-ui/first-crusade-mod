package com.example.examplemod.flora.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Every small First Crusade plant: grasses, scrub, ferns, flowers, fungi, roots and the flat
 * ground details. One class, no block entity, no ticking — the whole set is a model plus a
 * ground rule.
 *
 * <p>The ground rule is a block tag rather than a hardcoded list so the same class covers a
 * fern that needs dirt and a lichen-weed that grows on rockcrete: see
 * {@code firstcrusade:flora_ground_natural} / {@code _industrial} / {@code _any}.
 *
 * <p>Inheriting {@link BushBlock} is deliberate — it already breaks the plant when its support
 * disappears, which is what keeps fungi from floating and stops plants surviving inside a
 * structure footprint after {@code WorldGenPlacement.clearVegetation} has passed.
 */
public class FloraPlantBlock extends BushBlock {
    private final TagKey<Block> groundTag;
    private final VoxelShape shape;

    public FloraPlantBlock(Properties properties, TagKey<Block> groundTag, int heightPixels) {
        super(properties);
        this.groundTag = groundTag;
        this.shape = Block.box(2.0D, 0.0D, 2.0D, 14.0D, heightPixels, 14.0D);
    }

    public TagKey<Block> groundTag() {
        return this.groundTag;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(this.groundTag);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return this.shape.move(offset.x, 0.0D, offset.z);
    }
}
