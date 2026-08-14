package com.example.examplemod.ai.formation;

import java.util.EnumSet;
import java.util.List;

import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.performance.ai.FirstCrusadeCombatantIndex;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;
import com.example.examplemod.unit.profile.FCUnit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * Runs on a squad leader. Recruits nearby friendly units, keeps the roster clean, and picks the
 * formation the squad should be holding.
 *
 * <p>This goal never moves the leader. It sets {@link Goal.Flag}s to nothing and returns quickly,
 * so it layers under the leader's combat and movement goals rather than competing with them — a
 * sergeant fights normally and the squad organises itself around wherever it ends up. Followers do
 * all the positioning work in {@link FCFormationGoal}.</p>
 *
 * <h2>Cost</h2>
 * <p>Recruitment scans an area, which is expensive, so it runs on the configured interval and is
 * staggered by squad identity, so several sergeants spawned together never scan on the same tick.
 * Every interval here is stretched further by {@link FCSquad#intervalFor}, which folds in both the
 * distance to the nearest player and what the squad is currently doing.</p>
 */
public class FCLeaderGoal extends Goal {

    /**
     * How far around the leader an enemy still counts towards "are we outnumbered".
     *
     * <p>Left as a constant while the intervals moved to config, because it is not a performance
     * dial: it goes through the shared combatant index rather than a world query, and it feeds a
     * three-way formation choice where a couple of blocks either way cannot change the outcome.
     */
    private static final double ENEMY_COUNT_RADIUS = 14.0D;

    /** How far a follower may be from its slot before the squad counts as scattered. */
    private static final double SCATTERED_DISTANCE = 12.0D;

    private final PathfinderMob leader;
    private final FCSquad squad;
    private final int maxFollowers;

    /** Ticks until the next recruitment sweep. See the note in {@link #tick()}. */
    private int recruitCountdown;

    /**
     * Evaluations until the next enemy head-count. The formation itself is re-decided every tick
     * from the cached number, so a squad still reacts instantly to its leader acquiring a target;
     * only the counting — the part that walks a list of enemies — is throttled. Item 29 of the
     * performance brief: a firing line does not need its shape recomputed ten times a second.
     */
    private int enemyCountCountdown;

    /** Last enemy head-count, reused between counts. */
    private int cachedEnemyCount;

    public FCLeaderGoal(PathfinderMob leader, FCSquad squad, int maxFollowers) {
        this.leader = leader;
        this.squad = squad;
        this.maxFollowers = maxFollowers;

        // Offset the first sweep by the SQUAD's identity so a dozen sergeants spawned on one tick
        // spread their scans across the interval instead of spiking together. Unlike a modulo on
        // the world tick, this only shifts the phase — every leader still sweeps on schedule.
        //
        // Keyed on the squad UUID rather than the entity id: entity ids handed out in one batch are
        // consecutive, which spreads fine, but ids are also recycled, so a squad could inherit the
        // phase of a dead one and land back on top of a neighbour it was meant to be offset from.
        this.recruitCountdown = Math.floorMod(squad.getId().hashCode(),
                Math.max(1, FirstCrusadePerformanceConfig.squadRecruitInterval()));

        // No MOVE or LOOK flags: this goal only bookkeeps, it must not block combat goals.
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return this.maxFollowers > 0 && this.leader.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void stop() {
        // disband(), not a roster clear: every follower has to be told it no longer has a leader,
        // or it becomes a ghost that no other sergeant will ever recruit. See FCSquad.
        this.squad.disband();
    }

    @Override
    public void tick() {
        this.squad.prune(FirstCrusadePerformanceConfig.squadCohesionRadius());

        LivingEntity target = this.leader.getTarget();
        boolean inCombat = target != null && target.isAlive();

        // State first: every interval below is scaled by it, so reading it stale would throttle a
        // squad that has just been charged at the rate it used while it was standing around.
        this.squad.updateState(inCombat, isScattered());

        if (this.enemyCountCountdown > 0) {
            this.enemyCountCountdown--;
        } else {
            this.enemyCountCountdown = this.squad.intervalFor(
                    FirstCrusadePerformanceConfig.squadEnemyCountInterval());
            this.cachedEnemyCount = countEnemies(target);
        }

        this.squad.setFormation(this.squad.chooseFormation(inCombat, false, this.cachedEnemyCount));

        // A countdown rather than a modulo of the world tick. The obvious
        // `(tickCount + getId()) % INTERVAL == 0` form is broken here: because this goal declares
        // requiresUpdateEveryTick() == false, vanilla only calls tick() on every other server tick,
        // so for any leader whose id gives the wrong parity the condition is never true on a tick
        // this method actually runs — meaning roughly half of all sergeants would silently never
        // recruit anybody. Counting down locally is immune to how often we are called.
        if (this.recruitCountdown > 0) {
            this.recruitCountdown--;
            return;
        }

        this.recruitCountdown = this.squad.intervalFor(
                FirstCrusadePerformanceConfig.squadRecruitInterval());
        recruit();
    }

    /**
     * Whether anybody is far enough from the squad to be worth reforming for.
     *
     * <p>Measured against the leader rather than against each follower's exact slot: the slot is
     * derived from the leader's position anyway, and this runs on the leader's tick, so asking the
     * cheap question keeps a whole-squad geometry pass out of a routine bookkeeping step.
     */
    private boolean isScattered() {
        double limitSq = SCATTERED_DISTANCE * SCATTERED_DISTANCE;

        for (Mob member : this.squad.getMembers()) {
            if (member.distanceToSqr(this.leader) > limitSq) {
                return true;
            }
        }

        return false;
    }

    /**
     * Brings nearby eligible units into the squad.
     *
     * <p>Eligibility is entirely delegated to {@link FCUnit#acceptsOrders()} and the faction
     * manager, so a sergeant will lead <em>any</em> Imperial unit that implements {@link FCUnit} —
     * riflemen, heavy gunners, medics, and anything added later — without this class knowing what
     * those units are. Other leaders are excluded because a sergeant does not command a sergeant.
     */
    private void recruit() {
        if (this.squad.isFull(this.maxFollowers)) {
            return;
        }

        AABB box = this.leader.getBoundingBox().inflate(FirstCrusadePerformanceConfig.squadRecruitRadius(), 6.0D,
                FirstCrusadePerformanceConfig.squadRecruitRadius());

        List<Mob> candidates = this.leader.level().getEntitiesOfClass(
                Mob.class,
                box,
                candidate -> candidate != this.leader
                        && candidate.isAlive()
                        && candidate instanceof FCUnit
                        && FirstCrusadeFactionManager.areAllies(this.leader, candidate));

        for (Mob candidate : candidates) {
            if (this.squad.isFull(this.maxFollowers)) {
                return;
            }

            // Do not poach: a unit already following someone else keeps its squad.
            if (candidate instanceof FCSquadMember member && member.hasSquad()
                    && member.getSquadLeader() != this.leader) {
                continue;
            }

            // add() attaches the follower to this leader itself. Doing it here as well was how the
            // roster and the soldiers' own leader references came to be maintained in two places.
            this.squad.add(candidate, this.maxFollowers);
        }
    }

    /**
     * Rough count of hostiles near the leader, used only to decide when to disperse.
     *
     * <p>Goes through the shared combatant index instead of its own box query. The number feeds a
     * three-way formation choice, so the index's own vertical band (a little wider than the 6 blocks
     * this used to ask for) cannot change the outcome in any way a player could notice.
     */
    private int countEnemies(LivingEntity target) {
        if (target == null) {
            return 0;
        }

        return FirstCrusadeCombatantIndex.countEnemiesNear(this.leader, ENEMY_COUNT_RADIUS);
    }
}
