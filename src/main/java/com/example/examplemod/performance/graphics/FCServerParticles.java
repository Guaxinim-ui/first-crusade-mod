package com.example.examplemod.performance.graphics;

import java.util.List;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The one place that decides whether the server is willing to broadcast a First Crusade particle.
 *
 * <h2>Why this exists at all, next to FirstCrusadeParticleBudget</h2>
 *
 * {@link FirstCrusadeParticleBudget} governs {@code Level#addParticle} — one client drawing for
 * itself, answerable to that client's graphics preset. This class governs
 * {@code ServerLevel#sendParticles}, which is a completely different transaction: the server builds
 * a packet and pushes it to <i>every</i> player within range, and each of those clients then spawns
 * the particles whether they wanted them or not. One player's preset cannot decide that, which is
 * why {@code visuals.explosionEffects} and {@code visuals.debrisAmount} in the client config were
 * marked NOT WIRED and could never have been wired from there. This is the dial they were waiting
 * for, and it lives in the server config where a multiplayer world has exactly one answer.
 *
 * <h2>The cost being controlled</h2>
 *
 * It is not the particle. It is the packet, multiplied by the size of a battle. A firing trooper
 * sends two of these per shot — a muzzle flash and a casing eject — so two hundred troopers in a
 * firefight is several hundred packets a second per player in range, plus the client-side spawn of
 * every particle inside each one. That scales with the battle; the abilities and debug markers that
 * also call {@code sendParticles} do not, which is why they are deliberately not routed through
 * here (see the class comment on the {@code particles} section of the server config).
 *
 * <h2>The count-zero trap</h2>
 *
 * {@code sendParticles} does not mean "spawn count particles" when count is zero. Vanilla's
 * {@code ClientPacketListener#handleParticleEvent} special-cases zero to mean "spawn exactly one
 * particle whose velocity is (xDist, yDist, zDist) scaled by speed" — the mechanism behind directed
 * effects. So naively scaling a count of 5 down by 90% and sending the resulting 0 does not thin the
 * effect: it replaces it with a single fast-moving particle flying off in a direction the caller
 * never intended. {@link #scaleCount} therefore never returns zero for a non-zero request; the send
 * is dropped whole instead. A caller that genuinely wants the zero convention gets it preserved and
 * thinned by probability rather than by count.
 *
 * <h2>Three gates, cheapest first</h2>
 *
 * <ol>
 *   <li><b>Density</b> — one integer compare, and the case worth optimising (channel switched off)
 *       exits here before anything is allocated.</li>
 *   <li><b>Per-tick ceiling</b> — a safety valve for the synchronised volley, not a tuning dial.
 *       {@code /fc perf} reports whether it has ever fired, because a ceiling nobody can observe is
 *       indistinguishable from a ceiling that does nothing.</li>
 *   <li><b>Distance</b> — a loop over the level's players, so it goes last. Vanilla already refuses
 *       to send a non-forced particle past 32 blocks; this gate exists to skip the packet
 *       allocation and the player loop entirely when the effect is happening where nobody is.</li>
 * </ol>
 *
 * <h2>Master and channel combine by minimum</h2>
 *
 * Same rule as the client budget, for the same reason: multiplying double-dips, so a master of 70
 * and a channel of 70 would silently give 49%. The minimum keeps {@code masterDensity} authoritative
 * — set it to 0 and the server stops broadcasting mod particles — without thinning a channel the
 * owner deliberately turned up.
 */
public final class FCServerParticles {

    private FCServerParticles() {
    }

    /**
     * The server-broadcast effect families. Deliberately fewer than the client's channel list: these
     * are the four that actually leave a server today, and inventing channels for effects that do
     * not exist is how the client config ended up with seven dials that did nothing.
     */
    public enum Channel {
        /** The flash at a weapon's barrel. Highest frequency in the mod: one per shot fired. */
        MUZZLE_FLASH,
        /** Detonation fireballs and flame. */
        EXPLOSION,
        /** Smoke from detonations, stomps and missile trails. */
        SMOKE,
        /** Spent casings and impact fragments. One per shot fired. */
        DEBRIS,
        /**
         * Wildlife abilities: dust, sand, chitin, toxic clouds.
         *
         * <p>Separate from the three above because it scales with something else entirely. Combat
         * particles grow with the size of a battle; these grow with how close one player is standing
         * to one animal, and there is never more than a handful of them at once. Sharing a channel
         * would mean a server that turned particles down for a war also thinned the single Ambull
         * a player crossed a desert to find.
         */
        FAUNA;

        /** This channel's own percentage, before the master is applied. */
        public int density() {
            return switch (this) {
                case MUZZLE_FLASH -> FirstCrusadePerformanceConfig.muzzleFlashParticleDensity();
                case EXPLOSION -> FirstCrusadePerformanceConfig.explosionParticleDensity();
                case SMOKE -> FirstCrusadePerformanceConfig.smokeParticleDensity();
                case DEBRIS -> FirstCrusadePerformanceConfig.debrisParticleDensity();
                case FAUNA -> FirstCrusadePerformanceConfig.faunaParticleDensity();
            };
        }
    }

    // ==================================================================== counters

    /** Sends allowed in the current server tick. Server thread only; never shared. */
    private static int sentThisTick;
    /** Sends refused by the per-tick ceiling in the current server tick. */
    private static int clippedThisTick;

    private static int lastTickSent;
    private static int lastTickClipped;
    private static int peakSentInTick;
    private static long clippedTotal;

    // ==================================================================== the gate

    /**
     * Broadcasts a mod particle effect, thinned by the server's particle configuration.
     *
     * <p>Purely visual in every branch. A refused or thinned send never changes damage, radius, hit
     * registration or anything else a player could feel — the shot was still fired and the explosion
     * still hurt whatever stood in it.
     *
     * @param count vanilla's particle count. Zero keeps vanilla's directed-particle meaning and is
     *              thinned by dropping the whole send rather than by scaling.
     */
    public static void send(ServerLevel level, ParticleOptions particle, Channel channel,
                            double x, double y, double z, int count,
                            double xDist, double yDist, double zDist, double speed) {
        int density = Math.min(FirstCrusadePerformanceConfig.masterParticleDensity(), channel.density());
        if (density <= 0) {
            return;
        }

        if (sentThisTick >= FirstCrusadePerformanceConfig.maxParticleSendsPerTick()) {
            clippedThisTick++;
            clippedTotal++;
            return;
        }

        if (!anyPlayerWithin(level, x, y, z)) {
            return;
        }

        RandomSource random = level.random;

        if (count <= 0) {
            // The directed-particle convention: one particle, velocity from the dist arguments.
            // Scaling has nothing to scale, so the only honest thinning is a coin flip on the send.
            if (density < 100 && random.nextInt(100) >= density) {
                return;
            }
            level.sendParticles(particle, x, y, z, 0, xDist, yDist, zDist, speed);
            sentThisTick++;
            return;
        }

        int scaled = scaleCount(count, density, random);
        if (scaled <= 0) {
            return;
        }

        level.sendParticles(particle, x, y, z, scaled, xDist, yDist, zDist, speed);
        sentThisTick++;
    }

    /**
     * Scales a particle count by a percentage without ever landing on zero by accident.
     *
     * <p>The fractional part is resolved by a coin flip rather than by rounding, so a channel at 20%
     * on a count of 3 really does average 0.6 particles per send instead of the 1.0 that rounding up
     * would produce or the 0 that rounding down would. Over a battle that difference is the whole
     * dial: rounding up means the low settings never actually get low.
     *
     * @return the scaled count, which may be zero — meaning the caller should skip the send entirely
     *         rather than pass zero to {@code sendParticles}. See the class comment.
     */
    private static int scaleCount(int count, int density, RandomSource random) {
        if (density >= 100) {
            return count;
        }

        int product = count * density;
        int scaled = product / 100;
        int remainder = product % 100;
        if (remainder > 0 && random.nextInt(100) < remainder) {
            scaled++;
        }
        return scaled;
    }

    /**
     * Whether any player is close enough for this effect to reach somebody.
     *
     * <p>An indexed loop rather than {@code getNearestPlayer}: the list is short, this allocates no
     * iterator, and the vanilla helper's spectator filtering is wrong here — a spectator watching a
     * battle should still see it.
     *
     * <p>Measured from the centre of the player's block rather than from their exact position,
     * because that is what {@code ServerLevel#sendParticles} itself does before deciding whether to
     * deliver a packet ({@code blockPos.closerToCenterThan(pos, 32.0D)}). Matching its measure is
     * what lets {@code particles.maxSendDistance} claim that leaving it at 32 changes nothing a
     * player could see: measuring from the exact position instead would disagree with vanilla by up
     * to most of a block, and the disagreement would land exactly on the range boundary, silently
     * eating effects that would otherwise have been shown. The comparison is inclusive where
     * vanilla's is strict, so the gate errs towards letting a send through — the cost of a wrong
     * guess is one wasted packet build, never a missing effect.
     *
     * <p>{@code blockPosition()} is a cached field on Entity, not a computation, so this stays
     * allocation-free.
     */
    private static boolean anyPlayerWithin(ServerLevel level, double x, double y, double z) {
        int range = FirstCrusadePerformanceConfig.maxParticleSendDistance();
        double rangeSq = (double) range * range;

        List<ServerPlayer> players = level.players();
        for (int i = 0; i < players.size(); i++) {
            BlockPos pos = players.get(i).blockPosition();
            double dx = pos.getX() + 0.5D - x;
            double dy = pos.getY() + 0.5D - y;
            double dz = pos.getZ() + 0.5D - z;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================== readouts

    /** Sends allowed during the last completed server tick. */
    public static int lastTickSent() {
        return lastTickSent;
    }

    /** Sends refused by the ceiling during the last completed server tick. */
    public static int lastTickClipped() {
        return lastTickClipped;
    }

    /** The busiest tick since the server started, in sends. Compare against the ceiling. */
    public static int peakSentInTick() {
        return peakSentInTick;
    }

    /** Sends refused by the ceiling since the server started. Zero means the ceiling never bit. */
    public static long clippedTotal() {
        return clippedTotal;
    }

    /**
     * Refills the per-tick allowance and rolls the counters forward.
     *
     * <p>A server tick rather than a level tick: every level ticks inside one server tick, and the
     * thing being protected — the network thread and the tick budget — is shared between them.
     */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ServerTicker {
        private ServerTicker() {
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            lastTickSent = sentThisTick;
            lastTickClipped = clippedThisTick;
            if (sentThisTick > peakSentInTick) {
                peakSentInTick = sentThisTick;
            }
            sentThisTick = 0;
            clippedThisTick = 0;
        }
    }
}
