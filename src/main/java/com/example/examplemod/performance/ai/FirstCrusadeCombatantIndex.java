package com.example.examplemod.performance.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;
import com.example.examplemod.unit.profile.FCUnit;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * One roster of everyone who can be shot at, per level, so that a hundred soldiers do not each ask
 * the world the same question.
 *
 * <h2>The problem this replaces</h2>
 *
 * Vanilla's {@code NearestAttackableTargetGoal#findTarget} does two expensive things per scanning
 * unit. It calls {@code getEntitiesOfClass} over a box inflated by FOLLOW_RANGE, which walks the
 * entity sections and allocates a list of <i>everything</i> alive in that volume. It then hands that
 * list to {@code getNearestEntity}, which runs the full {@link TargetingConditions} test on
 * <b>every</b> candidate before comparing distances — and the last gate in that test is
 * {@code Sensing#hasLineOfSight}, a raycast. In a hundred-a-side battle that is a hundred raycasts
 * per scanning unit, and the scans of three hundred units land on whatever ticks their RNG picks.
 *
 * <h2>What this does instead</h2>
 *
 * <ol>
 *   <li>Keeps a live roster per level, maintained by join/leave events rather than by sweeping the
 *       world. Membership costs nothing per tick.</li>
 *   <li>Sorts the roster into faction buckets every {@code enemyIndexRefreshTicks}, so a query only
 *       walks the sides it is actually at war with.</li>
 *   <li>Answers a query by filtering on <b>live</b> positions (cheap arithmetic, no allocation,
 *       never stale) and then testing candidates in ascending distance order, stopping at the first
 *       one that passes.</li>
 * </ol>
 *
 * <h2>Why the answer is the same</h2>
 *
 * Vanilla returns the nearest candidate that passes the test. Walking candidates nearest-first and
 * returning the first that passes returns that same entity — but it runs the raycast one to three
 * times instead of once per enemy on the field. The cap in
 * {@code FirstCrusadePerformanceConfig#maxTargetCandidateChecks} bounds the pathological case (a
 * unit walled off from everything): after that many blocked candidates it reports no target, which
 * is the honest answer for a soldier who cannot see anyone, and it retries on its next scan.
 *
 * <h2>Freshness</h2>
 *
 * Buckets are rebuilt on a cadence, so a unit that spawned in the last few ticks may be missed for
 * that long — the same order of delay as the scan interval itself. Positions are never cached, so a
 * unit that <i>moved</i> is always evaluated where it actually is. Dead and removed entities are
 * dropped both by the leave event and by every rebuild, so the index self-heals if an event is ever
 * missed.
 *
 * <h2>Threading</h2>
 *
 * Server thread only, like everything it is called from. The scratch buffers are reused across
 * queries, which is safe precisely because nothing here is concurrent and no query can re-enter
 * another (the conditions test only reads state; it never scans).
 */
public final class FirstCrusadeCombatantIndex {

    private static final Map<ResourceKey<Level>, FirstCrusadeCombatantIndex> BY_LEVEL = new HashMap<>();

    private static final FirstCrusadeFaction[] FACTIONS = FirstCrusadeFaction.values();

    /** Everyone in this level who could ever be a target or a shooter. */
    private final Set<LivingEntity> roster = new HashSet<>();

    /** Roster split by faction, indexed by {@link FirstCrusadeFaction#ordinal()}. */
    private final List<List<LivingEntity>> buckets = new ArrayList<>(FACTIONS.length);

    private long refreshedAtTick = Long.MIN_VALUE;

    // Reused query scratch. Parallel arrays rather than a list of pairs: a target scan runs tens of
    // times per tick and must not allocate.
    private LivingEntity[] candidates = new LivingEntity[64];
    private double[] candidateDistances = new double[64];
    private int candidateCount;

    private FirstCrusadeCombatantIndex() {
        for (int i = 0; i < FACTIONS.length; i++) {
            this.buckets.add(new ArrayList<>());
        }
    }

    // ==================================================================== queries

    /**
     * The nearest enemy of {@code hunter} that satisfies {@code conditions}, or null.
     *
     * @param range      horizontal search distance in blocks, normally the unit's FOLLOW_RANGE
     * @param conditions the goal's own targeting conditions, applied unchanged — this method decides
     *                   the <i>order</i> candidates are tested in, never whether one is valid
     */
    public static LivingEntity nearestTarget(Mob hunter, double range, TargetingConditions conditions) {
        Level level = hunter.level();
        if (level.isClientSide) {
            return null;
        }

        FirstCrusadeFaction own = FirstCrusadeFactionManager.getFaction(hunter);
        if (own == FirstCrusadeFaction.NEUTRAL) {
            return null;
        }

        FirstCrusadeCombatantIndex index = of(level);
        index.refreshIfStale(level);
        return index.findNearest(hunter, own, range, conditions);
    }

    /**
     * How many enemies of {@code observer} are within {@code radius}. No line-of-sight test: this
     * answers "how outnumbered am I", where a foe behind a wall still counts.
     */
    public static int countEnemiesNear(Mob observer, double radius) {
        Level level = observer.level();
        if (level.isClientSide) {
            return 0;
        }

        FirstCrusadeFaction own = FirstCrusadeFactionManager.getFaction(observer);
        if (own == FirstCrusadeFaction.NEUTRAL) {
            return 0;
        }

        FirstCrusadeCombatantIndex index = of(level);
        index.refreshIfStale(level);
        return index.countEnemies(observer, own, radius);
    }

    /**
     * Appends every enemy of {@code hunter} within {@code radius} to {@code out}, in no particular
     * order, and returns it. For callers that score candidates themselves rather than taking the
     * nearest.
     */
    public static List<LivingEntity> collectEnemies(Mob hunter, double radius, List<LivingEntity> out) {
        Level level = hunter.level();
        if (level.isClientSide) {
            return out;
        }

        FirstCrusadeFaction own = FirstCrusadeFactionManager.getFaction(hunter);
        if (own == FirstCrusadeFaction.NEUTRAL) {
            return out;
        }

        FirstCrusadeCombatantIndex index = of(level);
        index.refreshIfStale(level);
        index.appendEnemies(hunter, own, radius, out);
        return out;
    }

    // ==================================================================== implementation

    private LivingEntity findNearest(Mob hunter, FirstCrusadeFaction own, double range,
                                     TargetingConditions conditions) {
        gather(hunter, own, range);

        if (this.candidateCount == 0) {
            return null;
        }

        int attempts = Math.min(FirstCrusadePerformanceConfig.maxTargetCandidateChecks(), this.candidateCount);
        LivingEntity found = null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            int best = -1;
            double bestDistance = Double.MAX_VALUE;

            for (int i = 0; i < this.candidateCount; i++) {
                if (this.candidateDistances[i] < bestDistance) {
                    bestDistance = this.candidateDistances[i];
                    best = i;
                }
            }

            if (best < 0) {
                break;
            }

            // Consume it, so the next pass looks at the next-nearest.
            this.candidateDistances[best] = Double.MAX_VALUE;

            if (conditions.test(hunter, this.candidates[best])) {
                found = this.candidates[best];
                break;
            }
        }

        releaseScratch();
        return found;
    }

    private int countEnemies(Mob observer, FirstCrusadeFaction own, double radius) {
        gather(observer, own, radius);

        int count = 0;
        for (int i = 0; i < this.candidateCount; i++) {
            if (FirstCrusadeFactionManager.canAttack(observer, this.candidates[i])) {
                count++;
            }
        }

        releaseScratch();
        return count;
    }

    private void appendEnemies(Mob hunter, FirstCrusadeFaction own, double radius, List<LivingEntity> out) {
        gather(hunter, own, radius);

        for (int i = 0; i < this.candidateCount; i++) {
            if (FirstCrusadeFactionManager.canAttack(hunter, this.candidates[i])) {
                out.add(this.candidates[i]);
            }
        }

        releaseScratch();
    }

    /**
     * Fills the scratch buffers with everything in range that is not on the asking unit's own side.
     *
     * <p>Deliberately does not call {@code canAttack} here: the caller either applies its own
     * conditions (which include that same predicate) or filters afterwards. Doing it twice would
     * only pay for the instanceof cascade twice.
     */
    private void gather(Mob asker, FirstCrusadeFaction own, double range) {
        this.candidateCount = 0;

        Level level = asker.level();
        double rangeSq = range * range;
        double verticalRange = FirstCrusadePerformanceConfig.verticalTargetRange();
        double x = asker.getX();
        double eyeY = asker.getEyeY();
        double z = asker.getZ();

        for (int factionIndex = 0; factionIndex < this.buckets.size(); factionIndex++) {
            if (FACTIONS[factionIndex] == own) {
                continue;
            }

            List<LivingEntity> bucket = this.buckets.get(factionIndex);

            for (int i = 0; i < bucket.size(); i++) {
                LivingEntity candidate = bucket.get(i);

                if (candidate == asker || !candidate.isAlive() || candidate.isRemoved()
                        || candidate.level() != level) {
                    continue;
                }

                double dx = candidate.getX() - x;
                double dz = candidate.getZ() - z;
                double horizontalSq = dx * dx + dz * dz;
                if (horizontalSq > rangeSq) {
                    continue;
                }

                double dy = candidate.getY() - eyeY;
                if (dy > verticalRange || dy < -verticalRange) {
                    continue;
                }

                addCandidate(candidate, horizontalSq + dy * dy);
            }
        }
    }

    private void addCandidate(LivingEntity candidate, double distanceSq) {
        if (this.candidateCount == this.candidates.length) {
            int grown = this.candidates.length * 2;
            LivingEntity[] biggerEntities = new LivingEntity[grown];
            double[] biggerDistances = new double[grown];
            System.arraycopy(this.candidates, 0, biggerEntities, 0, this.candidateCount);
            System.arraycopy(this.candidateDistances, 0, biggerDistances, 0, this.candidateCount);
            this.candidates = biggerEntities;
            this.candidateDistances = biggerDistances;
        }

        this.candidates[this.candidateCount] = candidate;
        this.candidateDistances[this.candidateCount] = distanceSq;
        this.candidateCount++;
    }

    /** Drops the references so a finished query cannot keep dead entities out of the collector's reach. */
    private void releaseScratch() {
        for (int i = 0; i < this.candidateCount; i++) {
            this.candidates[i] = null;
        }
        this.candidateCount = 0;
    }

    private void refreshIfStale(Level level) {
        long now = level.getGameTime();
        long elapsed = now - this.refreshedAtTick;

        // The elapsed >= 0 test is not paranoia. This map is static, so it outlives a world: quit to
        // the title screen, open a different save, and the new world's game time starts below the
        // old one's. A plain "elapsed < interval" check would then be true forever, the buckets
        // would never rebuild, and not one unit in that world would ever find an enemy.
        if (this.refreshedAtTick != Long.MIN_VALUE && elapsed >= 0
                && elapsed < FirstCrusadePerformanceConfig.enemyIndexRefreshTicks()) {
            return;
        }

        this.refreshedAtTick = now;
        rebuildBuckets();
    }

    /**
     * Re-sorts the roster into faction buckets and drops anything no longer alive.
     *
     * <p>Faction is resolved here rather than at join time because a player's allegiance is a
     * runtime decision — they pick a side on a screen, and from that moment the Orks must start
     * hunting them.
     */
    private void rebuildBuckets() {
        for (int i = 0; i < this.buckets.size(); i++) {
            this.buckets.get(i).clear();
        }

        Iterator<LivingEntity> iterator = this.roster.iterator();

        while (iterator.hasNext()) {
            LivingEntity entity = iterator.next();

            if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                iterator.remove();
                continue;
            }

            FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(entity);
            if (faction == FirstCrusadeFaction.NEUTRAL) {
                // Kept on the roster (a player's faction can change) but not a valid target today.
                continue;
            }

            this.buckets.get(faction.ordinal()).add(entity);
        }
    }

    private static FirstCrusadeCombatantIndex of(Level level) {
        return BY_LEVEL.computeIfAbsent(level.dimension(), key -> new FirstCrusadeCombatantIndex());
    }

    /**
     * Whether an entity is worth tracking at all.
     *
     * <p>Players always are: their faction is chosen at runtime and can change. {@link FCUnit}s
     * always are, so a unit that ever changes side stays visible to the index. Everything else joins
     * only if it already belongs to a fighting faction, which is what pulls in vanilla monsters
     * (HOSTILE) while leaving animals and other mods' livestock out of the war entirely.
     */
    private static boolean tracks(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }

        if (entity instanceof Player || entity instanceof FCUnit) {
            return true;
        }

        return FirstCrusadeFactionManager.getFaction(entity) != FirstCrusadeFaction.NEUTRAL;
    }

    // ==================================================================== roster maintenance

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Lifecycle {
        private Lifecycle() {
        }

        @SubscribeEvent
        public static void onEntityJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide || !tracks(event.getEntity())) {
                return;
            }

            of(event.getLevel()).roster.add((LivingEntity) event.getEntity());
        }

        @SubscribeEvent
        public static void onEntityLeave(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity living)) {
                return;
            }

            FirstCrusadeCombatantIndex index = BY_LEVEL.get(event.getLevel().dimension());
            if (index != null) {
                index.roster.remove(living);
            }
        }

        /**
         * A level going away takes its roster with it. Without this, a server that unloads a planet
         * would hold every soldier on it alive in a map forever.
         */
        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel() instanceof Level level && !level.isClientSide) {
                BY_LEVEL.remove(level.dimension());
            }
        }
    }
}
