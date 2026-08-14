package com.example.examplemod.performance.graphics;

import java.util.List;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.performance.config.FirstCrusadeClientConfig;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The single place that decides whether a First Crusade particle is worth drawing.
 *
 * <h2>Client only, visual only</h2>
 *
 * Every method here answers a question about <b>drawing</b>. Call sites are already inside a
 * {@code level.isClientSide} branch; a rejected request means one spark was not drawn, never that a
 * shot missed. Server-side {@code ServerLevel#sendParticles} calls are a different problem with a
 * different owner — those cost network bandwidth for every player in range and are governed by the
 * server config, not by one player's preset. Nothing in this class may be used to decide them.
 *
 * <h2>Three gates, cheapest first</h2>
 *
 * <ol>
 *   <li><b>Density.</b> An integer percentage per channel, from the active preset. Costs one compare
 *       when the channel is switched off entirely, which is the case worth optimising.</li>
 *   <li><b>Distance.</b> Past {@code maxVisualCombatDistance} nothing is drawn, and past half of it
 *       the density is halved again — the far half of the battlefield is where thinning is
 *       invisible, because at 60 blocks nobody counts individual sparks.</li>
 *   <li><b>Budget.</b> A hard ceiling per client tick. Density percentages scale with what is
 *       happening; a ceiling is what saves the frame when three hundred soldiers fire at once.</li>
 * </ol>
 *
 * <h2>Distance is the priority</h2>
 *
 * Rather than making every call site declare a priority it would guess at, the budget derives one:
 * effects within {@link #NEAR_RADIUS} of a player may consume the whole budget, everything further
 * out may only consume {@link #FAR_SHARE} of it. So a tick that fills up on distant smoke still has
 * room for the muzzle flash going off next to your head, which is the effect you would actually
 * miss. This is the item-36 priority rule expressed as geometry instead of as an enum every caller
 * has to get right.
 *
 * <h2>Master and channel</h2>
 *
 * The effective density is the <i>minimum</i> of the master {@code particleDensity} and the
 * channel's own value, not their product. Multiplying would double-dip: GRIMDARK asks for 70%
 * particles and tracers "on", and 0.70 x 0.75 = 52% is not what "on" means. Minimum keeps the master
 * slider authoritative — set it to 0 and the screen is clean — without quietly thinning a channel
 * the player deliberately turned up.
 */
public final class FirstCrusadeParticleBudget {

    /** Within this many blocks of a player, an effect may draw against the full budget. */
    private static final double NEAR_RADIUS = 24.0D;
    private static final double NEAR_RADIUS_SQ = NEAR_RADIUS * NEAR_RADIUS;

    /** Share of the per-tick budget that distant effects are allowed to consume. */
    private static final double FAR_SHARE = 0.6D;

    /** Particles spawned so far in the current client tick. Client thread only; never shared. */
    private static int spent;

    private FirstCrusadeParticleBudget() {
    }

    /**
     * The visual channels of the mod. Each maps to one slider the player controls.
     */
    public enum Channel {
        /** Weapon tracers and projectile trails. */
        TRACER,
        /** Muzzle flashes at a barrel. */
        MUZZLE_FLASH,
        /** Smoke from explosions, fires and engines. */
        SMOKE,
        /** Detonation fireballs and embers. */
        EXPLOSION,
        /** Impact fragments and kicked-up dirt. */
        DEBRIS,
        /** Vehicle exhaust, dust and sparks. */
        VEHICLE,
        /** Anything atmospheric with no channel of its own. */
        AMBIENT;

        /** This channel's own percentage, before the master slider is applied. */
        public int density() {
            return switch (this) {
                case TRACER -> FirstCrusadeClientConfig.tracerDensity();
                case MUZZLE_FLASH -> FirstCrusadeClientConfig.muzzleFlashQuality();
                case SMOKE -> FirstCrusadeClientConfig.smokeDensity();
                case EXPLOSION -> FirstCrusadeClientConfig.explosionEffects();
                case DEBRIS -> FirstCrusadeClientConfig.debrisAmount();
                case VEHICLE -> FirstCrusadeClientConfig.vehicleEffects();
                case AMBIENT -> FirstCrusadeClientConfig.particleDensity();
            };
        }
    }

    /**
     * Asks whether one particle of {@code channel} should be drawn at the given position.
     *
     * @return true if the caller should spawn it; false if it was thinned out. A false answer has no
     *         gameplay meaning whatsoever.
     */
    public static boolean request(Level level, double x, double y, double z,
                                  Channel channel, RandomSource random) {
        int density = Math.min(FirstCrusadeClientConfig.particleDensity(), channel.density());
        if (density <= 0) {
            return false;
        }

        int budget = FirstCrusadeClientConfig.maxParticlesPerTick();
        if (spent >= budget) {
            return false;
        }

        double distanceSq = nearestViewerDistanceSq(level, x, y, z);
        double maxVisual = FirstCrusadeClientConfig.maxVisualCombatDistance();
        if (distanceSq > maxVisual * maxVisual) {
            return false;
        }

        if (distanceSq > NEAR_RADIUS_SQ) {
            // Distant: a smaller slice of the budget, and half the density. Both are unnoticeable
            // out here and both are exactly where the savings are, because most of a large battle is
            // always far away from any one player.
            if (spent >= (int) (budget * FAR_SHARE)) {
                return false;
            }
            double half = maxVisual * 0.5D;
            if (distanceSq > half * half) {
                density /= 2;
                if (density <= 0) {
                    return false;
                }
            }
        }

        if (density < 100 && random.nextInt(100) >= density) {
            return false;
        }

        spent++;
        return true;
    }

    /** Mod particles drawn so far in the current client tick. For debug readouts. */
    public static int spentThisTick() {
        return spent;
    }

    /**
     * Squared distance from the closest player. An indexed loop over {@code level.players()} rather
     * than {@code getNearestPlayer}: the client's player list is short, this allocates no iterator,
     * and it avoids the creative/spectator filtering of the vanilla helper — a player in creative or
     * spectator mode should still see the battle.
     */
    private static double nearestViewerDistanceSq(Level level, double x, double y, double z) {
        List<? extends Player> players = level.players();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            double distanceSq = players.get(i).distanceToSqr(x, y, z);
            if (distanceSq < best) {
                best = distanceSq;
            }
        }
        return best;
    }

    /**
     * Refills the budget once per client tick.
     *
     * <p>A nested subscriber, so that the outer class stays free of client-only types. Common code
     * such as a projectile's {@code tick()} can reference {@link FirstCrusadeParticleBudget} without
     * a dedicated server ever loading anything client-side.
     */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
    public static final class ClientTicker {
        private ClientTicker() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                spent = 0;
            }
        }
    }
}
