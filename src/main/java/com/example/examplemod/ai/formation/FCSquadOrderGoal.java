package com.example.examplemod.ai.formation;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Makes a squad's order mean something.
 *
 * <h2>The enum existed; nothing obeyed it</h2>
 *
 * {@link FCSquadOrder} and {@link FCSquad#updateState} have been in the mod for a while, and
 * {@code updateState} genuinely reads the order — a squad told to RETREAT does report
 * {@link FCSquadState#RETREATING}. But the state only throttled how often the squad thought. No goal
 * ever moved anybody because of an order, so a squad ordered to fall back reported that it was
 * retreating while standing exactly where it was and shooting.
 *
 * <p>This goal closes that. It runs on the <b>leader</b> only, and it walks the leader to wherever
 * the order points. Followers need no changes at all: {@link FCFormationGoal} already keeps them in
 * slots derived from the leader's position, so moving the leader moves the squad in formation. One
 * goal on one mob steers the whole unit.
 *
 * <h2>Above the combat goals, and why that is safe</h2>
 *
 * Registered at a priority over the attack goal, because an order the enemy can veto is not an
 * order — a squad told to fall back has to be able to break contact. What stops that from turning
 * every squad into a pacifist is {@link #canUse}: it is false unless the order actually points
 * somewhere the leader is not already standing. A squad on ATTACK or FOLLOW never enters this goal,
 * and a squad on DEFEND leaves it the moment it arrives, handing movement straight back to combat.
 */
public class FCSquadOrderGoal extends Goal {

    /** Close enough to the destination to count as there. */
    private static final double ARRIVAL_DISTANCE = 3.0D;

    /** Ticks between repaths while marching. Pathing every tick over a long march is the cost. */
    private static final int REPATH_INTERVAL = 20;

    private final PathfinderMob leader;
    private final FCSquad squad;

    private int repathCooldown;

    public FCSquadOrderGoal(PathfinderMob leader, FCSquad squad) {
        this.leader = leader;
        this.squad = squad;

        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos destination = this.squad.getDestination();

        if (destination == null || !movesToDestination() || yieldsToCombat()) {
            return false;
        }

        return this.leader.distanceToSqr(centre(destination)) > ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos destination = this.squad.getDestination();

        if (destination == null || !movesToDestination() || yieldsToCombat()) {
            return false;
        }

        // Hysteresis, like the formation goal: keep going until comfortably inside the arrival
        // radius, or the goal switches off on the boundary and immediately back on.
        double tolerance = ARRIVAL_DISTANCE * 0.6D;

        return this.leader.distanceToSqr(centre(destination)) > tolerance * tolerance;
    }

    /**
     * Whether an enemy in front of the squad should stop the march.
     *
     * <p>Yes for every order except RETREAT. Sitting above the combat goals is what lets a squad
     * break contact, but applying that to MOVE and DEFEND as well produced a squad that walked
     * straight past — and straight through — an enemy standing between it and its destination,
     * without ever firing a shot. A squad ordered to hold a bunker fights whatever it meets on the
     * way to the bunker; only a squad ordered to run keeps running.
     *
     * <p>The same rule {@link FCFormationGoal} already uses for dressing ranks, and for the same
     * reason: fighting outranks marching.
     */
    private boolean yieldsToCombat() {
        if (this.squad.getOrder() == FCSquadOrder.RETREAT) {
            return false;
        }

        LivingEntity target = this.leader.getTarget();
        return target != null && target.isAlive();
    }

    /**
     * Whether the current order is one that should move the squad.
     *
     * <p>HOLD_POSITION is in here alongside the marching orders on purpose: holding a position means
     * going back to it if you drifted, which is the same movement with a different reason. The
     * difference between hold and defend is what the squad does on arrival, and that is the combat
     * goals' business, not this one's.
     */
    private boolean movesToDestination() {
        FCSquadOrder order = this.squad.getOrder();

        return order == FCSquadOrder.MOVE
                || order == FCSquadOrder.DEFEND
                || order == FCSquadOrder.RETREAT
                || order == FCSquadOrder.HOLD_POSITION;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;

        // Breaking contact is the whole point of a retreat. Without dropping the target the leader
        // keeps its combat goals live, and they will fight this one for the navigation the entire
        // way back.
        if (this.squad.getOrder() == FCSquadOrder.RETREAT) {
            this.leader.setTarget(null);
        }
    }

    @Override
    public void stop() {
        this.leader.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void tick() {
        BlockPos destination = this.squad.getDestination();

        if (destination == null) {
            return;
        }

        // A retreating squad keeps shedding its target: an enemy that hurts it mid-withdrawal would
        // otherwise re-acquire through the hurt-by goal and pin the squad in place.
        if (this.squad.getOrder() == FCSquadOrder.RETREAT) {
            LivingEntity target = this.leader.getTarget();

            if (target != null) {
                this.leader.setTarget(null);
            }
        }

        if (this.repathCooldown > 0) {
            this.repathCooldown--;
            return;
        }

        this.repathCooldown = this.squad.intervalFor(REPATH_INTERVAL);

        double speed = this.squad.getOrder() == FCSquadOrder.RETREAT ? 1.25D : 1.0D;

        this.leader.getNavigation().moveTo(
                destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, speed);
    }

    private static net.minecraft.world.phys.Vec3 centre(BlockPos pos) {
        return new net.minecraft.world.phys.Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }
}
