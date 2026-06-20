package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Populates a fresh planet once: scatters a few autonomous Imperial cities and Ork camps in a ring
 * around world spawn so both factions are already present when the player arrives. Runs a single
 * time per world (guarded by {@link WorldSettlementData}); placement is reversible (it only sets
 * blocks, never touches chunk generation). Imperial cities are founded by simply placing an
 * (unclaimed) {@link ImperialCommandCoreBlock} — the Core then governs itself like any city.
 */
public final class WorldSettlementSeeder {
    private static final int CITY_COUNT = 3;
    private static final int CAMP_COUNT = 3;

    // Ring around spawn (blocks). Kept well within the 5000-block world border and not on top of
    // the player so spawn stays open.
    private static final int MIN_RADIUS = 140;
    private static final int MAX_RADIUS = 360;

    // Keep settlements from piling up on each other.
    private static final int MIN_SEPARATION = 80;
    private static final int ATTEMPTS_PER_SETTLEMENT = 24;

    private static final int PLAZA_RADIUS = 4;

    private WorldSettlementSeeder() {
    }

    // Plants the starting settlements around spawn the first time it is called for a world.
    public static void seedAroundSpawn(ServerLevel overworld) {
        WorldSettlementData data = WorldSettlementData.get(overworld);
        if (data.isSeeded()) {
            return;
        }
        // Mark first so a mid-way failure never re-runs (and double-populates) on the next login.
        data.markSeeded();

        RandomSource rng = overworld.random;
        BlockPos spawn = overworld.getSharedSpawnPos();

        List<BlockPos> placed = new ArrayList<>();
        List<BlockPos> cities = new ArrayList<>();

        for (int i = 0; i < CITY_COUNT; i++) {
            BlockPos spot = findSurfaceSpot(overworld, spawn, placed, rng);
            if (spot == null) {
                continue;
            }
            foundCity(overworld, spot);
            placed.add(spot);
            cities.add(spot);
        }

        for (int i = 0; i < CAMP_COUNT; i++) {
            BlockPos spot = findSurfaceSpot(overworld, spawn, placed, rng);
            if (spot == null) {
                continue;
            }
            BlockPos target = nearestCity(cities, spot, spawn);
            BlockPos camp = OrkCampManager.seedWorldCamp(overworld, spot, target);
            placed.add(camp != null ? camp : spot);
        }
    }

    // Founds an autonomous Imperial city: a small stone foundation crowned by an unclaimed Command
    // Core. The Core's serverTick assigns the (biome-biased) city type and grows it on its own.
    private static void foundCity(ServerLevel serverLevel, BlockPos spot) {
        BlockPos surface = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
        buildCityFoundation(serverLevel, surface);
        serverLevel.setBlock(surface, ExampleMod.IMPERIAL_COMMAND_CORE.get().defaultBlockState(), 3);
    }

    // A plain Imperial plaza: a stone-brick platform, corner pillars with lanterns and a banner,
    // so a world-generated city reads as a settlement and not a lone floating block.
    private static void buildCityFoundation(ServerLevel serverLevel, BlockPos center) {
        int r = PLAZA_RADIUS;
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState pillar = Blocks.POLISHED_ANDESITE.defaultBlockState();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                // Clear a little headroom above the plaza.
                for (int y = 0; y <= 4; y++) {
                    BlockPos clear = center.offset(x, y, z);
                    if (!clear.equals(center)) {
                        serverLevel.setBlock(clear, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // Lay the floor one below the surface so the Core sits on the plaza.
                serverLevel.setBlock(center.offset(x, -1, z), floor, 3);
            }
        }

        // Pedestal directly under the Core.
        serverLevel.setBlock(center.below(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);

        // Corner pillars with a lantern on top and a banner partway up.
        int[][] corners = {{-r, -r}, {-r, r}, {r, -r}, {r, r}};
        for (int[] c : corners) {
            BlockPos base = center.offset(c[0], 0, c[1]);
            for (int y = 0; y < 4; y++) {
                serverLevel.setBlock(base.offset(0, y, 0), pillar, 3);
            }
            place(serverLevel, base.offset(0, 4, 0), Blocks.LANTERN.defaultBlockState());
            place(serverLevel, base.offset(0, 2, 0), Blocks.BLUE_BANNER.defaultBlockState());
        }
    }

    // Picks the city the camp will march on: the nearest founded city, or spawn if none exist.
    private static BlockPos nearestCity(List<BlockPos> cities, BlockPos from, BlockPos fallback) {
        BlockPos best = fallback;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos city : cities) {
            double d = city.distSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = city;
            }
        }
        return best;
    }

    // Searches the ring around spawn for a flat, dry, well-separated surface spot. Returns null if
    // none was found within the attempt budget (that settlement is simply skipped).
    private static BlockPos findSurfaceSpot(ServerLevel serverLevel, BlockPos spawn, List<BlockPos> placed, RandomSource rng) {
        for (int attempt = 0; attempt < ATTEMPTS_PER_SETTLEMENT; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0D;
            int distance = MIN_RADIUS + rng.nextInt(MAX_RADIUS - MIN_RADIUS + 1);

            int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);

            BlockPos surface = serverLevel.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, spawn.getY(), z)
            );

            if (!isDryGround(serverLevel, surface)) {
                continue;
            }

            if (tooClose(placed, surface)) {
                continue;
            }

            return surface.immutable();
        }

        return null;
    }

    // The ground just below the surface must be solid and not water/lava, so settlements don't drop
    // into oceans or rivers (the planet has both since the A2 terrain pass).
    private static boolean isDryGround(ServerLevel serverLevel, BlockPos surface) {
        // No fluid at the surface or just under it -> not in an ocean, river or lava lake.
        if (!serverLevel.getFluidState(surface).isEmpty()) {
            return false;
        }
        BlockState below = serverLevel.getBlockState(surface.below());
        if (!below.getFluidState().isEmpty()) {
            return false;
        }
        // And there must be actual ground to build on.
        return !below.isAir();
    }

    private static boolean tooClose(List<BlockPos> placed, BlockPos candidate) {
        for (BlockPos other : placed) {
            int dx = other.getX() - candidate.getX();
            int dz = other.getZ() - candidate.getZ();
            if (dx * dx + dz * dz < MIN_SEPARATION * MIN_SEPARATION) {
                return true;
            }
        }
        return false;
    }

    // Places a block only into empty space, never overwriting the central Core.
    private static void place(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (serverLevel.isEmptyBlock(pos)) {
            serverLevel.setBlock(pos, state, 3);
        }
    }
}
