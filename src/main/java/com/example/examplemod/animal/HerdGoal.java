package com.example.examplemod.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeping to the herd: an animal that has drifted away from its own kind walks back toward them.
 *
 * <h2>Why not a follow-the-leader goal</h2>
 *
 * The vanilla flocking goal ({@code FollowFlockLeaderGoal}) picks a leader and has everyone else
 * path to it every tick. On land that produces a queue rather than a herd, and it means one animal's
 * pathfinding is running inside twelve others' ticks.
 *
 * <p>This is the cheap version of the same idea and it looks better: every few seconds an animal
 * asks once whether its nearest neighbour is too far away, and if so walks toward it. Nobody leads.
 * The group stays loose, drifts, and re-forms — which is what a herd on open ground actually does.
 *
 * <h2>Cost</h2>
 *
 * One AABB query per animal per {@link #CHECK_INTERVAL} ticks, over a box the animal could walk
 * across anyway, and a path no longer than {@link #RADIUS}. Nothing here searches the world.
 */
public class HerdGoal extends Goal {

    /** How far the animal looks for company, in blocks. */
    private static final double RADIUS = 16.0D;

    /** Beyond this distance from the nearest neighbour, the animal starts closing the gap. */
    private static final double TOO_FAR = 10.0D;

    /** Close enough to count as "with the herd" again — hysteresis, so it does not oscillate. */
    private static final double CLOSE_ENOUGH = 6.0D;

    /** Ticks between checks. Four seconds: slow enough to be free, quick enough to look alive. */
    private static final int CHECK_INTERVAL = 80;

    /**
     * How long one attempt to rejoin lasts before the animal gives up and goes back to grazing.
     *
     * <p>A clock rather than "until the navigation finishes": a neighbour on the far side of a
     * ravine makes the navigator return no path at all, and ending the goal on that would put the
     * animal straight back into {@link #canUse} — which would run the neighbour query on the very
     * next tick instead of on the interval.
     */
    private static final int REJOIN_LIMIT = 300;

    /** Re-aims at the neighbour this often, because the neighbour is also walking. */
    private static final int REPATH_INTERVAL = 40;

    private final FCAnimalEntity animal;
    private final double speedModifier;

    private int cooldown;
    private int rejoinTicks;

    @Nullable
    private FCAnimalEntity neighbour;

    public HerdGoal(FCAnimalEntity animal, double speedModifier) {
        this.animal = animal;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));

        this.cooldown = animal.getRandom().nextInt(CHECK_INTERVAL);
    }

    @Override
    public boolean canUse() {
        if (!FCAnimalConfig.ANIMAL_ECOLOGY_ENABLED.get() || this.animal.isAlarmed()
                || this.animal.isVehicle() || this.animal.isLeashed()) {
            return false;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        this.cooldown = CHECK_INTERVAL;
        this.neighbour = nearestOfKind();

        return this.neighbour != null && this.animal.distanceTo(this.neighbour) > TOO_FAR;
    }

    @Override
    public boolean canContinueToUse() {
        return this.neighbour != null && this.neighbour.isAlive() && !this.animal.isAlarmed()
                && this.animal.distanceTo(this.neighbour) > CLOSE_ENOUGH
                && this.rejoinTicks < REJOIN_LIMIT;
    }

    @Override
    public void start() {
        this.rejoinTicks = 0;
        if (this.neighbour != null) {
            this.animal.getNavigation().moveTo(this.neighbour, this.speedModifier);
        }
    }

    @Override
    public void tick() {
        this.rejoinTicks++;

        if (this.neighbour != null && this.rejoinTicks % REPATH_INTERVAL == 0) {
            this.animal.getNavigation().moveTo(this.neighbour, this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.neighbour = null;
        this.rejoinTicks = 0;
        this.animal.getNavigation().stop();
    }

    /**
     * The closest animal of exactly this species.
     *
     * <p>Exact type, not "any animal": a Grox drifting toward a Sump Rat is not a herd, and the
     * population cap counts by species for the same reason.
     */
    @Nullable
    private FCAnimalEntity nearestOfKind() {
        List<? extends FCAnimalEntity> others = this.animal.level().getEntitiesOfClass(
                this.animal.getClass(), this.animal.getBoundingBox().inflate(RADIUS),
                other -> other != this.animal && other.isAlive() && !other.isBaby());

        FCAnimalEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (FCAnimalEntity other : others) {
            double distance = this.animal.distanceToSqr(other);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = other;
            }
        }

        return closest;
    }
}
