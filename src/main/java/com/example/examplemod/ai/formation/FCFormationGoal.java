package com.example.examplemod.ai.formation;

import java.util.EnumSet;

import com.example.examplemod.performance.ai.FirstCrusadeAiLod;
import com.example.examplemod.unit.profile.FCUnit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a follower standing in its assigned formation slot.
 *
 * <p>Priority matters more than the code here: this goal is registered <em>below</em> the ranged
 * attack goal, so a trooper that has an enemy in range fights instead of dressing ranks. Formation
 * is what a squad does when it is <em>not</em> shooting at something — which is most of the time,
 * and is exactly when a disorganised mob looks worst.</p>
 *
 * <h2>Why followers move themselves</h2>
 * <p>The leader could push every follower's navigation each tick, but that centralises N pathfinding
 * calls onto one entity's tick and makes the leader's cost grow with squad size. Having each
 * follower run its own cheap goal spreads that naturally and means a follower that gets stuck only
 * affects itself.</p>
 *
 * <h2>Deadband</h2>
 * <p>A unit only repaths when it is further from its slot than {@code formationTolerance}. Without
 * that deadband, troopers jitter permanently: they arrive, drift a few centimetres as the leader
 * breathes, repath, overshoot, repeat. The tolerance comes from the unit's own
 * {@link com.example.examplemod.unit.profile.FCCombatProfile}.</p>
 */
public class FCFormationGoal extends Goal {

    /** Ticks between repath attempts while out of position. */
    private static final int REPATH_INTERVAL = 10;

    /**
     * Beyond this distance from its slot a unit sprints to catch up rather than walking. Stops a
     * squad from stringing out into a queue after the leader rounds a corner.
     */
    private static final double CATCHUP_DISTANCE = 8.0D;

    private final PathfinderMob follower;

    private int repathCooldown;

    public FCFormationGoal(PathfinderMob follower) {
        this.follower = follower;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(this.follower instanceof FCSquadMember member) || !member.hasSquad()) {
            return false;
        }

        // Fighting outranks marching. Returning false here lets the combat goal own movement
        // instead of the two goals fighting over the navigation every tick.
        LivingEntity target = this.follower.getTarget();

        if (target != null && target.isAlive()) {
            return false;
        }

        return slotDistance() > tolerance();
    }

    @Override
    public boolean canContinueToUse() {
        if (!(this.follower instanceof FCSquadMember member) || !member.hasSquad()) {
            return false;
        }

        LivingEntity target = this.follower.getTarget();

        if (target != null && target.isAlive()) {
            return false;
        }

        // Hysteresis: keep going until comfortably inside the tolerance, so the goal does not
        // switch off the instant it touches the boundary and immediately switch back on.
        return slotDistance() > tolerance() * 0.5D;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.follower.getNavigation().stop();
    }

    @Override
    public void tick() {
        Vec3 slot = slotPosition();

        if (slot == null) {
            return;
        }

        Float facing = desiredFacing();

        if (facing != null) {
            // Face the direction the formation wants (only the defensive circle sets this).
            this.follower.setYRot(facing);
            this.follower.yBodyRot = facing;
        }

        if (this.repathCooldown > 0) {
            this.repathCooldown--;
            return;
        }

        double distance = slotDistance();
        double speed = distance > CATCHUP_DISTANCE ? 1.25D : 0.95D;

        this.follower.getNavigation().moveTo(slot.x, slot.y, slot.z, speed);
        this.repathCooldown = FirstCrusadeAiLod.scale(this.follower, REPATH_INTERVAL);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private FCSquad squad() {
        if (!(this.follower instanceof FCSquadMember member)) {
            return null;
        }

        Mob leader = member.getSquadLeader();

        return leader instanceof FCSquadLeader squadLeader ? squadLeader.getSquad() : null;
    }

    private Vec3 slotPosition() {
        FCSquad squad = squad();

        if (squad == null) {
            return null;
        }

        Mob leader = squad.getLeader();

        return squad.getFormation().slotPosition(
                leader.position(),
                leader.yBodyRot,
                squad.slotOf(this.follower),
                FCSquad.rankOf(this.follower));
    }

    private Float desiredFacing() {
        FCSquad squad = squad();

        if (squad == null) {
            return null;
        }

        return squad.getFormation().facingFor(
                squad.getLeader().yBodyRot,
                squad.slotOf(this.follower));
    }

    private double slotDistance() {
        Vec3 slot = slotPosition();
        return slot == null ? 0.0D : this.follower.position().distanceTo(slot);
    }

    private double tolerance() {
        if (this.follower instanceof FCUnit unit) {
            return unit.getCombatProfile().formationTolerance();
        }

        return 2.0D;
    }
}
