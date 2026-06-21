package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

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
    // Hive cities are huge, so we seed fewer of them and space them far apart.
    private static final int CITY_COUNT = 2;
    private static final int CAMP_COUNT = 3;

    // Ring around spawn (blocks). Kept well within the 5000-block world border and not on top of
    // the player so spawn stays open.
    private static final int MIN_RADIUS = 180;
    private static final int MAX_RADIUS = 520;

    // Keep the sprawling hive cities from overlapping each other.
    private static final int MIN_SEPARATION = 220;
    private static final int ATTEMPTS_PER_SETTLEMENT = 32;

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

    // Founds an autonomous Imperial city: places an unclaimed Command Core on the surface and asks
    // it to raise a full walled village around itself (Core as central keep, curtain wall, towers
    // and houses with beds). The Core then governs and grows the settlement on its own.
    private static void foundCity(ServerLevel serverLevel, BlockPos spot) {
        BlockPos surface = WorldGenPlacement.groundPlacement(serverLevel, spot.getX(), spot.getZ());
        serverLevel.setBlock(surface, ExampleMod.IMPERIAL_COMMAND_CORE.get().defaultBlockState(), 3);

        if (serverLevel.getBlockEntity(surface) instanceof ImperialCommandCoreBlockEntity core) {
            core.buildAutonomousVillage(serverLevel);
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

            BlockPos surface = WorldGenPlacement.groundPlacement(serverLevel, x, z);

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
}
