package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Helpers so world-generated structures (cities, Ork camps) sit on the actual ground and aren't
 * wrecked by ambient blocks. The vanilla heightmaps count tree trunks as "surface", which made
 * settlements spawn on top of forest canopies; here we scan past trees/plants down to real terrain
 * and can clear the leftover vegetation out of a build footprint first.
 */
public final class WorldGenPlacement {
    private WorldGenPlacement() {
    }

    // The air position directly above the true ground (terrain) surface at x,z — skipping trees
    // (logs/leaves), plants and snow so structures rest on the ground, not on a treetop.
    public static BlockPos groundPlacement(ServerLevel level, int x, int z) {
        // Force the chunk to finish generating first, so its heightmap is valid. Without this, a
        // settlement seeded into a not-yet-generated (e.g. distant) chunk reads a bogus low surface
        // and is built underground — very visible on a superflat world.
        level.getChunk(x >> 4, z >> 4);

        BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, top.getY(), z);

        while (cursor.getY() > level.getMinBuildHeight() + 1) {
            if (isGround(level.getBlockState(cursor))) {
                break;
            }
            cursor.move(Direction.DOWN);
        }

        return new BlockPos(x, cursor.getY() + 1, z);
    }

    // True only for solid terrain (grass/dirt/sand/stone/...), not air, fluids, trees or plants.
    private static boolean isGround(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return false;
        }
        // Tall grass, flowers, snow layers, etc. are replaceable and never count as ground.
        return !state.canBeReplaced();
    }

    // Clears trees, leaves, plants and snow from a square footprint up to the given height, so the
    // ambient terrain doesn't poke through (or block) a structure. Solid ground is left untouched.
    public static void clearVegetation(ServerLevel level, BlockPos center, int radius, int height) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= height; y++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
