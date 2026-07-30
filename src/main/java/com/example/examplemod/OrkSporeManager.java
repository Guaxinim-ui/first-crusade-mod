package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * How far the Ork infestation reaches, and what it costs the Imperium.
 *
 * <p>This replaces the old sculk-based corruption. That system painted vanilla sculk over the
 * ground to show the green tide spreading, which had two problems: it looked like the Deep Dark
 * rather than like Orks, and it worked by sampling and rewriting terrain block by block — including
 * through {@code WorldGenPlacement.groundPlacement}, which forces chunks to generate.
 *
 * <p>The infestation is now vegetation: Ork fungus, squig grass and spore pods, placed by the
 * per-chunk flora decorator inside the radius this class tracks. Nothing here touches a block. The
 * radius is a number that grows with the WAAAGH!, and the decorator turns it into ground cover
 * wherever chunks happen to be loaded — which is both cheaper and the thing the corruption was
 * always trying to depict.
 */
public final class OrkSporeManager {
    private static final int BASE_RADIUS = 12;
    private static final int RADIUS_PER_TIER = 10;
    private static final int RADIUS_GROWTH_PER_CYCLE = 2;

    /** Beyond this an Ork camp stops projecting extra pressure on a city's production. */
    private static final int PRESSURE_RANGE = 220;

    private OrkSporeManager() {
    }

    /**
     * Grows a camp's spore radius one step, capped by the global WAAAGH! tier.
     *
     * <p>Returns the new radius; the camp stores it and publishes it to
     * {@link WorldWarMapData}, where the flora decorator reads it to decide how far Ork vegetation
     * reaches. No blocks are written here at all.
     */
    public static int grow(int tier, int currentRadius) {
        int maxRadius = BASE_RADIUS + tier * RADIUS_PER_TIER;

        return Math.min(maxRadius, Math.max(BASE_RADIUS, currentRadius + RADIUS_GROWTH_PER_CYCLE));
    }

    /**
     * How much the Ork presence drags down a city's output: 1.0 when the region is clear, down to
     * 0.4 when it is overrun.
     *
     * <p>The old version sampled a dozen ground blocks looking for sculk, which meant loading
     * terrain to answer an economic question. This reads the war map instead — camp positions and
     * the radius each one projects — so it costs a walk over a few dozen longs and works even when
     * every camp involved is in an unloaded chunk.
     */
    public static double productionMultiplier(ServerLevel level, BlockPos centre, int territoryRadius) {
        WorldWarMapData warMap = WorldWarMapData.get(level);

        double pressure = 0.0D;

        for (long packed : warMap.getCamps()) {
            BlockPos camp = BlockPos.of(packed);

            double distance = Math.sqrt(camp.distSqr(centre));

            if (distance > PRESSURE_RANGE) {
                continue;
            }

            WorldWarMapData.CampInfo info = warMap.getCampInfo(packed);
            int reach = info == null ? BASE_RADIUS : Math.max(BASE_RADIUS, info.corruptionRadius());

            // A camp presses hardest when its spore field actually overlaps the city's ground.
            double overlap = (territoryRadius + reach) - distance;

            if (overlap <= 0.0D) {
                // Still nearby, still a problem, just not yet growing into the fields.
                pressure += 0.06D * (1.0D - distance / PRESSURE_RANGE);
                continue;
            }

            pressure += 0.10D + 0.20D * Math.min(1.0D, overlap / Math.max(1, territoryRadius));
        }

        return Math.max(0.4D, 1.0D - Math.min(0.6D, pressure));
    }
}
