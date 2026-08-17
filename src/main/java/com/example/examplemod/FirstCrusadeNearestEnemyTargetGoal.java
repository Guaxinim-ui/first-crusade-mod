package com.example.examplemod;

import com.example.examplemod.ai.formation.FCSquadMember;
import com.example.examplemod.performance.ai.FirstCrusadeAiLod;
import com.example.examplemod.performance.ai.FirstCrusadeCombatantIndex;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * How every First Crusade unit finds someone to shoot.
 *
 * <p>Thirteen entity classes register this one goal, so the scan they all share is the single most
 * repeated piece of work in a battle. Two things changed here, and neither changes who a unit
 * decides to attack.
 *
 * <h2>1. The search goes through the shared index</h2>
 *
 * {@link FirstCrusadeCombatantIndex} replaces vanilla's per-unit {@code getEntitiesOfClass} box
 * query, and tests candidates nearest-first so the line-of-sight raycast runs a couple of times
 * instead of once per enemy on the field. The unit it returns is the same one vanilla's
 * {@code getNearestEntity} would have returned: the nearest candidate that passes this goal's own
 * {@link #targetConditions}, which are handed to the index untouched.
 *
 * <h2>2. The scan cadence is a countdown, not a dice roll</h2>
 *
 * Vanilla rolls {@code random.nextInt(10) != 0} on each evaluation. That has a long tail — a unit
 * can rescan twice running or go three seconds without looking — and, worse, three hundred units
 * rolling independently produce spikes. A countdown gives every unit exactly one scan per interval,
 * phase-offset by entity id so units spawned together spread across the window instead of all
 * scanning on the same tick.
 *
 * <p><b>Why a countdown and not {@code (tickCount + id) % interval == 0}.</b> That form looks
 * equivalent and is quietly broken here. {@code Mob#serverAiStep} only evaluates {@code canUse} when
 * {@code (serverTickCount + entityId) % 2 == 0}; on the other ticks it calls
 * {@code tickRunningGoals(false)}, which never touches a goal that is not already running. Since a
 * unit's {@code tickCount} is offset from the server's by whatever tick it spawned on, a modulo
 * condition can land permanently on the ticks this method is never called — and that unit would
 * never acquire a target for its entire life. A local countdown cannot care how often it is called.
 * {@link com.example.examplemod.ai.formation.FCLeaderGoal} hit the same trap; this is the same fix.
 *
 * <p>Because {@code canUse} is only reachable every other tick, the countdown is measured in
 * evaluations and the configured tick interval is halved to convert.
 */
public class FirstCrusadeNearestEnemyTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {

    /** Evaluations remaining before this unit is allowed to scan again. */
    private int scanCountdown;

    public FirstCrusadeNearestEnemyTargetGoal(Mob mob) {
        super(
                mob,
                LivingEntity.class,
                // Vanilla's random interval. Unused: canUse() is overridden and gates on the
                // countdown below. Left at the original value so nothing else that reads
                // randomInterval sees a surprise.
                10,
                true,
                false,
                target -> FirstCrusadeFactionManager.canAttack(mob, target)
        );

        // Phase, not period: every unit still scans once per interval, they just start at different
        // points in it. Deliberately the un-scaled interval — this runs inside the entity's own
        // constructor, before it has been added to the level, and asking where the players are at
        // that moment would be both meaningless and fragile. The first real scan re-reads the LOD.
        this.scanCountdown = Math.floorMod(mob.getId(),
                Math.max(1, FirstCrusadePerformanceConfig.targetScanInterval() / 2));
    }

    /**
     * The configured interval in ticks, stretched by the unit's level of detail, then converted to
     * {@code canUse} evaluations — which happen on every other tick.
     *
     * <p>The LOD term is what makes a battle a hundred and fifty blocks away cost a twentieth of one
     * happening at the player's feet, without either of them behaving differently in any way the
     * player could see.
     */
    private int evaluationsPerScan() {
        int ticks = FirstCrusadeAiLod.scale(this.mob, FirstCrusadePerformanceConfig.targetScanInterval());
        return Math.max(1, ticks / 2);
    }

    @Override
    public boolean canUse() {
        if (this.scanCountdown > 0) {
            this.scanCountdown--;
            return false;
        }

        this.scanCountdown = evaluationsPerScan();
        findTarget();
        return this.target != null;
    }

    @Override
    protected void findTarget() {
        double range = getFollowDistance();

        // Keep the conditions' range in step with the live attribute. Vanilla bakes this in the
        // constructor, which is before FirstCrusadeForgeEvents clamps FOLLOW_RANGE on join, so the
        // range the conditions enforced and the range actually searched could disagree.
        this.targetConditions.range(range);

        LivingEntity shared = squadTarget();
        if (shared != null) {
            this.target = shared;
            return;
        }

        this.target = FirstCrusadeCombatantIndex.nearestTarget(this.mob, range, this.targetConditions);
    }

    /**
     * The enemy this unit's squad has already settled on, if it is one this unit could attack.
     *
     * <h2>This is where a squad stops costing what it holds</h2>
     *
     * <p>A mob of twenty Boyz used to run twenty scans to arrive at roughly the same answer. The Nob
     * has already made the expensive, line-of-sight-tested choice, so every follower that inherits it
     * skips its scan entirely and the mob costs one scan instead of twenty. Putting it here rather
     * than in each unit means all thirteen classes that register this goal get it, not just the ones
     * somebody remembered to edit — the Guardsman Rifleman had this and the Orks did not, which is
     * exactly the kind of gap a shared goal is supposed to close.
     *
     * <p>The inherited enemy is put through {@link #targetConditions} rather than trusted: it was
     * chosen from where the leader stands, and a follower can be far enough away, walled off, or
     * simply out of range. Failing that test falls through to a normal scan, so a straggler still
     * fights rather than standing idle because its sergeant is shooting at something it cannot see.
     *
     * <h2>Individuality</h2>
     *
     * <p>A unit that is actually being shot at still answers for itself: {@code
     * FirstCrusadeHurtByTargetGoal} sits above this in the target selector, so whoever hurts you takes
     * precedence over whatever the leader is pointing at.
     */
    private LivingEntity squadTarget() {
        if (!(this.mob instanceof FCSquadMember member)) {
            return null;
        }

        LivingEntity shared = member.getSquadTarget();

        return shared != null && this.targetConditions.test(this.mob, shared) ? shared : null;
    }
}
