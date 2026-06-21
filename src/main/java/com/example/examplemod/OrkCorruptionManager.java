package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Ork corruption — the visible scab the green tide leaves on the world. As the WAAAGH! grows
 * and battles are fought, sculk creeps across the land: every camp radiates a corruption halo whose
 * reach grows with the global WAAAGH! tier, and every death in the Orks' shadow seeds a fresh patch
 * where it falls. Purely a code mechanic over natural surface blocks — reversible, no worldgen edits.
 */
public final class OrkCorruptionManager {
    private static final int BASE_RADIUS = 4;
    private static final int RADIUS_PER_TIER = 5;
    private static final int RADIUS_GROWTH_PER_CYCLE = 1;
    private static final int BLOCKS_PER_CYCLE = 8;   // surface blocks scabbed over each camp cycle

    private OrkCorruptionManager() {
    }

    // Grows a camp's corruption halo one step and scabs over a few random surface blocks within it.
    // Returns the (possibly increased) corruption radius to store back on the camp.
    public static int spreadFromCamp(ServerLevel level, BlockPos campPos, int tier, int currentRadius) {
        int maxRadius = BASE_RADIUS + tier * RADIUS_PER_TIER;
        int radius = Math.min(maxRadius, Math.max(BASE_RADIUS, currentRadius + RADIUS_GROWTH_PER_CYCLE));

        RandomSource rng = level.random;
        for (int i = 0; i < BLOCKS_PER_CYCLE; i++) {
            int dx = rng.nextInt(radius * 2 + 1) - radius;
            int dz = rng.nextInt(radius * 2 + 1) - radius;

            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }

            corruptColumn(level, campPos.getX() + dx, campPos.getZ() + dz);
        }

        return radius;
    }

    // The Imperium pushes back: an active city cleanses sculk back into living ground within its
    // territory, so the frontier between corruption and the Imperium shifts with the war.
    public static void purifyAround(ServerLevel level, BlockPos center, int radius, int count) {
        RandomSource rng = level.random;

        for (int i = 0; i < count; i++) {
            int dx = rng.nextInt(radius * 2 + 1) - radius;
            int dz = rng.nextInt(radius * 2 + 1) - radius;

            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }

            BlockPos air = WorldGenPlacement.groundPlacement(level, center.getX() + dx, center.getZ() + dz);
            BlockPos ground = air.below();

            if (level.getBlockState(ground).is(Blocks.SCULK)) {
                level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                if (level.getBlockState(air).is(Blocks.SCULK_VEIN)) {
                    level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    // Seeds a small corruption patch where something died in the Orks' shadow.
    public static void corruptDeathSite(ServerLevel level, BlockPos pos, int count) {
        RandomSource rng = level.random;

        for (int i = 0; i < count; i++) {
            int dx = rng.nextInt(5) - 2;
            int dz = rng.nextInt(5) - 2;
            corruptColumn(level, pos.getX() + dx, pos.getZ() + dz);
        }
    }

    // Turns the natural ground block at (x,z) into sculk, with the odd sculk vein creeping over it.
    private static void corruptColumn(ServerLevel level, int x, int z) {
        BlockPos air = WorldGenPlacement.groundPlacement(level, x, z);
        BlockPos ground = air.below();
        BlockState groundState = level.getBlockState(ground);

        if (!isCorruptible(groundState)) {
            return;
        }

        level.setBlock(ground, Blocks.SCULK.defaultBlockState(), 3);

        // Occasionally let a vein crawl over the scab.
        if (level.random.nextInt(4) == 0 && level.getBlockState(air).isAir()) {
            level.setBlock(air, Blocks.SCULK_VEIN.defaultBlockState()
                    .setValue(BlockStateProperties.DOWN, true), 3);
        }
    }

    // Natural ground only — corruption eats earth and stone, never structures or trees.
    private static boolean isCorruptible(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(Blocks.SCULK) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return false;
        }
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.STONE)
                || state.is(Blocks.SNOW_BLOCK);
    }
}
