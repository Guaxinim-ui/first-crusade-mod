package com.example.examplemod.performance.ai;

import java.util.List;

import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * How hard a unit is allowed to think, based on how far it is from the nearest player.
 *
 * <h2>A multiplier, not a switch</h2>
 *
 * The obvious way to build level-of-detail AI is to stop ticking distant mobs — cancel
 * {@code LivingTickEvent}, or flip {@code setNoAi}. Both produce exactly the thing the brief rules
 * out: soldiers standing frozen mid-stride, sliding when knocked, dropping through the world when
 * the ground changes under them. And {@code Mob#serverAiStep} is {@code final}, so throttling it
 * without a mixin is not on the table anyway.
 *
 * <p>So this does something duller and safer. Every unit keeps ticking, keeps moving, keeps falling,
 * keeps shooting. What changes is the <b>cadence of the expensive decisions</b>: how often it looks
 * for a new target, how often it recomputes a path, how often a sergeant recounts the enemy. Those
 * are already countdowns after the earlier work in this package, so LOD is one multiplication
 * applied where each countdown is reset.
 *
 * <p>That also makes transitions free. A unit crossing the 32-block line does not change state; its
 * next target scan is simply forty ticks away instead of twenty. There is no boundary to see, no
 * hysteresis to tune, and nothing to unfreeze.
 *
 * <h2>Nothing here touches balance</h2>
 *
 * A unit at STRATEGIC does the same damage with the same weapon at the same range as one at FULL. It
 * reacts to a new enemy later — up to a second later at the far end, when no player is within a
 * hundred and twenty-eight blocks to notice the difference.
 *
 * <h2>Finding the nearest player</h2>
 *
 * An indexed loop over {@code level.players()}: no iterator, no predicate machinery, no allocation,
 * and no {@code getNearestPlayer} call with its creative/spectator filtering. Callers ask only when
 * a countdown expires rather than every tick, so the real cost is a handful of subtractions per unit
 * per interval. If a server ever carried enough players for that to matter, the fix would be a
 * per-tick snapshot of player positions — worth knowing, not worth building yet.
 */
public enum FirstCrusadeAiLod {

    /** Close enough to be watched: full cadence, exactly as the goal author wrote it. */
    FULL,

    /** Half rate by default. About ten decisions a second. */
    MEDIUM,

    /** A fifth by default. About four decisions a second — still fluid from that far. */
    LOW,

    /** A twentieth by default. About one decision a second, for war happening out of sight. */
    STRATEGIC;

    /**
     * What an interval at this level of detail is multiplied by.
     *
     * <p>Read from the config rather than baked into the enum, because the right number is a taste
     * decision the owner has to make with a running world. In particular STRATEGIC at 20 makes a
     * battle nobody is watching crawl — measured at eight casualties in thirty seconds where a
     * full-rate fight produced about a hundred — which is either exactly right or exactly wrong
     * depending on whether off-screen battles are supposed to decide anything.
     */
    public int intervalMultiplier() {
        return switch (this) {
            case FULL -> 1;
            case MEDIUM -> FirstCrusadePerformanceConfig.mediumAiMultiplier();
            case LOW -> FirstCrusadePerformanceConfig.lowAiMultiplier();
            case STRATEGIC -> FirstCrusadePerformanceConfig.strategicAiMultiplier();
        };
    }

    /**
     * The level of detail for one entity right now.
     *
     * <p>Returns {@link #FULL} whenever LOD is switched off in the config, and on the client, where
     * none of this applies. An entity in a level with no players at all is STRATEGIC: there is
     * nobody it could possibly be doing detail work for.
     */
    public static FirstCrusadeAiLod forEntity(Entity entity) {
        if (!FirstCrusadePerformanceConfig.aiLodEnabled()) {
            return FULL;
        }

        Level level = entity.level();
        if (level.isClientSide) {
            return FULL;
        }

        double nearestSq = nearestPlayerDistanceSq(level, entity);

        double full = FirstCrusadePerformanceConfig.fullAiDistance();
        if (nearestSq <= full * full) {
            return FULL;
        }

        double medium = FirstCrusadePerformanceConfig.mediumAiDistance();
        if (nearestSq <= medium * medium) {
            return MEDIUM;
        }

        double low = FirstCrusadePerformanceConfig.lowAiDistance();
        if (nearestSq <= low * low) {
            return LOW;
        }

        return STRATEGIC;
    }

    /**
     * Stretches an interval to suit how far the entity is from anyone who could notice.
     *
     * <p>The one call every throttled goal makes. Reset your countdown to
     * {@code FirstCrusadeAiLod.scale(mob, BASE_INTERVAL)} instead of {@code BASE_INTERVAL} and the
     * goal is LOD-aware, with its close-up behaviour completely unchanged.
     *
     * @return the interval in ticks, never below 1
     */
    public static int scale(Entity entity, int baseIntervalTicks) {
        int scaled = baseIntervalTicks * forEntity(entity).intervalMultiplier();
        return Math.max(1, scaled);
    }

    private static double nearestPlayerDistanceSq(Level level, Entity entity) {
        List<? extends Player> players = level.players();
        double best = Double.MAX_VALUE;

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);

            // A spectator is still a pair of eyes: someone recording a battle should not be shown
            // the low-detail version of it.
            if (player.isRemoved()) {
                continue;
            }

            double distanceSq = player.distanceToSqr(entity.getX(), entity.getY(), entity.getZ());
            if (distanceSq < best) {
                best = distanceSq;
            }
        }

        return best;
    }
}
