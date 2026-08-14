package com.example.examplemod.ai.formation;

import java.util.EnumSet;
import java.util.List;

import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.performance.ai.FirstCrusadeAiLod;
import com.example.examplemod.performance.ai.FirstCrusadeCombatantIndex;
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
 * <p>Recruitment scans an area, which is expensive, so it runs every {@code RECRUIT_INTERVAL} ticks
 * and is staggered by entity id — the same trick {@link com.example.examplemod.ai.combat.FCTargetPriority}
 * uses — so several sergeants spawned together never scan on the same tick.</p>
 */
public class FCLeaderGoal extends Goal {

    /** Ticks between recruitment sweeps. */
    private static final int RECRUIT_INTERVAL = 40;

    /** How far a leader looks for new followers. */
    private static final double RECRUIT_RADIUS = 12.0D;

    /**
     * Evaluations between enemy head-counts. Five is roughly half a second at this goal's cadence:
     * fast enough that a squad caught by a charge re-forms while the charge is still arriving, slow
     * enough that it is no longer a per-tick cost.
     */
    private static final int ENEMY_COUNT_INTERVAL = 5;

    /** How far around the leader an enemy still counts towards "are we outnumbered". */
    private static final double ENEMY_COUNT_RADIUS = 14.0D;

    /**
     * How far a follower may get before it is dropped from the squad. Larger than the recruit
     * radius on purpose: a squad should not disband the moment someone chases a target, only when
     * they are genuinely gone.
     */
    private static final double COHESION_RADIUS = 28.0D;

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

        // Offset the first sweep by entity id so a squad of sergeants spawned on one tick spreads
        // its scans across the interval instead of spiking together. Unlike a modulo on the world
        // tick, this only shifts the phase — every leader still sweeps on schedule.
        this.recruitCountdown = Math.floorMod(leader.getId(), RECRUIT_INTERVAL);

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
        this.squad.prune(COHESION_RADIUS);

        LivingEntity target = this.leader.getTarget();
        boolean inCombat = target != null && target.isAlive();

        if (this.enemyCountCountdown > 0) {
            this.enemyCountCountdown--;
        } else {
            this.enemyCountCountdown = FirstCrusadeAiLod.scale(this.leader, ENEMY_COUNT_INTERVAL);
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

        this.recruitCountdown = FirstCrusadeAiLod.scale(this.leader, RECRUIT_INTERVAL);
        recruit();
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

        AABB box = this.leader.getBoundingBox().inflate(RECRUIT_RADIUS, 6.0D, RECRUIT_RADIUS);

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
