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
    // Villages are light now, so seed a few of them (plus the Ork camps) for testing the systems.
    private static final int CITY_COUNT = 3;
    private static final int CAMP_COUNT = 3;

    // Ring around spawn (blocks). Kept well within the 5000-block world border and not on top of
    // the player so spawn stays open.
    private static final int MIN_RADIUS = 120;
    private static final int MAX_RADIUS = 360;

    // Keep the villages from overlapping each other.
    private static final int MIN_SEPARATION = 110;
    private static final int ATTEMPTS_PER_SETTLEMENT = 32;

    private WorldSettlementSeeder() {
    }

    // Plants the starting settlements around spawn the first time anyone joins the world.
    public static void seedAroundSpawn(ServerLevel overworld) {
        WorldSettlementData data = WorldSettlementData.get(overworld);
        if (data.isSeeded()) {
            return;
        }
        // Mark first so a mid-way failure never re-runs (and double-populates) on the next login.
        data.markSeeded();

        if (ExampleMod.TEST_FIXED_WORLD) {
            seedTestLayout(overworld, overworld.getSharedSpawnPos());
        } else {
            seedRing(overworld, overworld.getSharedSpawnPos());
        }
    }

    // Test layout (owner request, 2026-07-02): a WAR PAIR — one Imperial walled village to the
    // south and one Ork city to the north, facing each other across spawn, so the commander/squad
    // systems (march, assault, defense) can be watched from the middle. ~280 blocks apart keeps the
    // city's threat low enough that its strategic AI still attempts offensives by chance (a camp
    // closer than 120 blocks pushes threat above the attack gate for non-aggressive governors).
    // More settlements can be planted at will with /fcstrategy seedcity | seedcamp.
    private static final int TEST_BAND_DISTANCE = 140;
    // Level 4 = the same scale as a seeded Imperial city (radius 30), so the Ork city is a peer.
    private static final int TEST_ORK_START_LEVEL = 4;

    private static void seedTestLayout(ServerLevel level, BlockPos center) {
        BlockPos citySpot = new BlockPos(center.getX(), center.getY(), center.getZ() + TEST_BAND_DISTANCE);
        // Fixed TOWN scale so the war-pair stays a balanced peer of the Ork city (radius ~30).
        BlockPos city = foundCity(level, citySpot, SettlementScale.TOWN);

        BlockPos campSpot = new BlockPos(center.getX(), center.getY(), center.getZ() - TEST_BAND_DISTANCE);
        BlockPos camp = OrkCampManager.seedWorldCamp(level, campSpot, city);

        if (camp != null && level.getBlockEntity(camp) instanceof OrkCampBlockEntity orkCity) {
            orkCity.seedAsCity(level, TEST_ORK_START_LEVEL);
        }
    }

    // Populates a planet dimension the first time a traveller lands there (around their landing).
    //
    // The seeded flag is per dimension. It used to be one boolean for the whole save, so whichever
    // planet was visited first was also the only one that ever got settlements — every later world
    // was landed on, marked as "the planet is seeded", and left empty.
    public static void seedPlanet(ServerLevel planet, BlockPos center) {
        WorldSettlementData data = WorldSettlementData.get(planet);
        if (data.isPlanetSeeded(planet.dimension())) {
            return;
        }
        // Mark first so a mid-way failure never re-runs (and double-populates) on the next arrival.
        data.markPlanetSeeded(planet.dimension());

        seedRing(planet, center);

        com.example.examplemod.campaign.CampaignLog.war("{} seeded with starting settlements around {}",
                planet.dimension().location(), center);
    }

    // Scatters CITY_COUNT autonomous cities and CAMP_COUNT Ork camps in a ring around a centre.
    private static void seedRing(ServerLevel level, BlockPos center) {
        RandomSource rng = level.random;

        List<BlockPos> placed = new ArrayList<>();
        List<BlockPos> cities = new ArrayList<>();

        for (int i = 0; i < CITY_COUNT; i++) {
            BlockPos spot = findSurfaceSpot(level, center, placed, rng);
            if (spot == null) {
                continue;
            }
            foundCity(level, spot);
            placed.add(spot);
            cities.add(spot);
        }

        for (int i = 0; i < CAMP_COUNT; i++) {
            BlockPos spot = findSurfaceSpot(level, center, placed, rng);
            if (spot == null) {
                continue;
            }
            BlockPos target = nearestCity(cities, spot, center);
            BlockPos camp = OrkCampManager.seedWorldCamp(level, spot, target);
            placed.add(camp != null ? camp : spot);
        }
    }

    // Founds an autonomous Imperial city: places an unclaimed Command Core on the surface and asks
    // it to raise a full walled village around itself (Core as central keep, curtain wall, towers
    // and houses with beds). The Core then governs and grows the settlement on its own. Public so
    // the /fcstrategy seedcity command can plant villages on demand in an already-seeded world.
    public static BlockPos foundCity(ServerLevel serverLevel, BlockPos spot) {
        return foundCity(serverLevel, spot, SettlementScale.randomSeedable(serverLevel.random));
    }

    // Founds an autonomous city at the given settlement scale. The scale is a normally-seedable size
    // (Village/Town/City); HIVE_CAPITAL is deliberately unreachable here — it is raised only by its
    // own dedicated founding path, never by this world seeder.
    public static BlockPos foundCity(ServerLevel serverLevel, BlockPos spot, SettlementScale scale) {
        SettlementScale safeScale = (scale == null || !scale.isSeededNormally()) ? SettlementScale.TOWN : scale;

        BlockPos surface = WorldGenPlacement.groundPlacement(serverLevel, spot.getX(), spot.getZ());
        serverLevel.setBlock(surface, FCRegistry.IMPERIAL_COMMAND_CORE.get().defaultBlockState(), 3);

        if (serverLevel.getBlockEntity(surface) instanceof ImperialCommandCoreBlockEntity core) {
            core.setSettlementScale(safeScale);
            core.buildAutonomousVillage(serverLevel);
        }

        return surface;
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
