package com.example.examplemod.planet;

import com.example.examplemod.FCRegistry;
import com.example.examplemod.WorldGenPlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Putting a traveller down somewhere they can survive.
 *
 * <h2>Why this is its own class</h2>
 *
 * The landing logic was written inside {@link com.example.examplemod.SpaceportBlock}, where it was
 * private and perfectly good. The navigation terminal needs exactly the same thing, and there are
 * only two honest options: move it here and have both callers use it, or copy it and let the two
 * copies drift until one of them drops somebody in a lava lake. The Spaceport now delegates.
 *
 * <p>The rule the whole class exists to enforce: <b>a planet is generated terrain with no promise of
 * friendly ground</b>. Nothing guarantees the coordinates a player launched from are habitable on
 * the other side, so every arrival gets a cleared, lit platform and a Spaceport to leave by.
 */
public final class PlanetLanding {
    private PlanetLanding() {
    }

    /** Half-width of the platform built under every arrival. */
    private static final int PAD_RADIUS = 3;

    /** How far the search for dry ground spirals out before giving up, in blocks. */
    private static final int SEARCH_RADIUS = 48;

    /**
     * A dry, open surface spot near (x, z).
     *
     * <p>Spirals outward in rings and takes the first spot that is air over solid ground, so an
     * arrival over an ocean or a lava sea walks out to the nearest shore instead of drowning in it.
     * Falls back to the raw surface height when nowhere nearby qualifies — a bad landing beats no
     * landing, and the platform below will still be built.
     */
    public static BlockPos findDryLanding(ServerLevel level, int x, int z) {
        for (int radius = 0; radius <= SEARCH_RADIUS; radius += 4) {
            int steps = radius == 0 ? 1 : 8;

            for (int step = 0; step < steps; step++) {
                double angle = 2.0D * Math.PI * step / steps;
                int px = x + (int) Math.round(Math.cos(angle) * radius);
                int pz = z + (int) Math.round(Math.sin(angle) * radius);
                BlockPos surface = WorldGenPlacement.groundPlacement(level, px, pz);

                if (isDryLanding(level, surface)) {
                    return surface;
                }
            }
        }

        return WorldGenPlacement.groundPlacement(level, x, z);
    }

    public static boolean isDryLanding(ServerLevel level, BlockPos surface) {
        if (!level.getFluidState(surface).isEmpty() || !level.getBlockState(surface).isAir()) {
            return false;
        }

        BlockPos ground = surface.below();
        return level.getBlockState(ground).getFluidState().isEmpty()
                && !level.getBlockState(ground).isAir();
    }

    /**
     * Builds the arrival platform: cleared airspace, a stone floor with a blackstone rim, lanterns
     * on the corners and a return Spaceport beside the centre.
     */
    public static void buildLandingPad(ServerLevel level, BlockPos landing) {
        WorldGenPlacement.clearVegetation(level, landing, PAD_RADIUS + 1, 6);

        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState edge = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

        for (int dx = -PAD_RADIUS; dx <= PAD_RADIUS; dx++) {
            for (int dz = -PAD_RADIUS; dz <= PAD_RADIUS; dz++) {
                boolean rim = Math.abs(dx) == PAD_RADIUS || Math.abs(dz) == PAD_RADIUS;
                level.setBlock(landing.offset(dx, -1, dz), rim ? edge : floor, 3);

                for (int dy = 0; dy <= 4; dy++) {
                    level.setBlock(landing.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        int[][] corners = {
                {-PAD_RADIUS, -PAD_RADIUS}, {-PAD_RADIUS, PAD_RADIUS},
                {PAD_RADIUS, -PAD_RADIUS}, {PAD_RADIUS, PAD_RADIUS},
        };
        for (int[] corner : corners) {
            level.setBlock(landing.offset(corner[0], 0, corner[1]),
                    Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(landing.offset(corner[0], 1, corner[1]),
                    Blocks.LANTERN.defaultBlockState(), 3);
        }

        BlockPos returnPort = landing.offset(2, 0, 0);
        if (!level.getBlockState(returnPort).is(FCRegistry.SPACEPORT.get())) {
            level.setBlock(returnPort, FCRegistry.SPACEPORT.get().defaultBlockState(), 3);
        }

        // And the terminal that lets the traveller choose where to go next. Without it, a planet
        // reached through the navigation screen would only be leavable by the cycling block —
        // which is the older, smaller interface, and would read as a downgrade on arrival.
        BlockPos terminal = landing.offset(-2, 0, 0);
        if (!level.getBlockState(terminal).is(FCRegistry.PLANETARY_NAVIGATION_TERMINAL.get())) {
            level.setBlock(terminal,
                    FCRegistry.PLANETARY_NAVIGATION_TERMINAL.get().defaultBlockState(), 3);
        }
    }
}
