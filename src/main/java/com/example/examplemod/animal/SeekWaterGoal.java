package com.example.examplemod.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * Thirst: every few minutes the animal walks to the nearest water it can actually see from where it
 * stands, drinks, and forgets about water again.
 *
 * <h2>The search is the whole design problem</h2>
 *
 * "Animals seek water" is one line in a brief and an unbounded scan in code, which is exactly what
 * the performance rules forbid — a herd of twelve each sweeping hundreds of blocks every tick would
 * cost more than the war does. Three things keep it bounded, and all three matter:
 *
 * <ul>
 *   <li><b>Radius 16, height ±3.</b> Water further than that is not this animal's problem. A Grox
 *       that cannot find a drink simply carries on grazing; nothing is owed to it.</li>
 *   <li><b>The scan stops at the first hit.</b> {@link BlockPos#withinManhattan} walks outwards in
 *       order of distance, so the nearest water is also the first one found. When water is close
 *       the loop ends after a few dozen lookups; the full sweep only happens where there is no
 *       water at all, and there it is followed by the longest cooldown.</li>
 *   <li><b>It only runs when thirsty.</b> Not once a tick — once per drink interval, and again on
 *       the retry interval if the sweep came up dry.</li>
 * </ul>
 *
 * <p>So the worst case is one bounded sweep per animal per minute, and the common case is a short
 * one every several minutes. That is the budget this behaviour is worth.
 */
public class SeekWaterGoal extends Goal {

    /** How far the animal will look, in blocks. Deliberately small: see the class note. */
    private static final int SEARCH_RADIUS = 16;

    /** Vertical reach of the search. Water up a cliff is not water you can drink. */
    private static final int SEARCH_HEIGHT = 3;

    /** Ticks between drinks once one has been had. Five minutes. */
    private static final int DRINK_INTERVAL = 6000;

    /** Ticks before looking again when the last sweep found nothing. One minute. */
    private static final int DRY_RETRY = 1200;

    /** Close enough to drink from, squared. */
    private static final double REACH_SQR = 4.0D;

    /** Ticks spent with the head down once the water is reached. */
    private static final int DRINK_TICKS = 60;

    /**
     * How long the animal will keep walking toward the water before giving up.
     *
     * <p>A time limit rather than "until the navigation says it is done": an unreachable target
     * makes the navigator finish instantly with no path, and a goal that ends on that would be
     * re-proposed on the next tick. Thirty seconds is long enough to cross the search radius twice.
     */
    private static final int WALK_LIMIT = 600;

    /** Re-issues the path this often, so a herd that drifts does not walk to a stale point. */
    private static final int REPATH_INTERVAL = 40;

    private final FCAnimalEntity animal;
    private final Level level;
    private final double speedModifier;

    private int thirstTicks;
    private int drinkTicks;
    private int walkTicks;

    @Nullable
    private BlockPos water;

    public SeekWaterGoal(FCAnimalEntity animal, double speedModifier) {
        this.animal = animal;
        this.level = animal.level();
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));

        // Spread the herd's first drink out. Without this every animal in a freshly loaded chunk
        // would head for the same pond on the same tick, which reads as a scripted march.
        this.thirstTicks = animal.getRandom().nextInt(DRINK_INTERVAL);
    }

    @Override
    public boolean canUse() {
        if (!FCAnimalConfig.ANIMAL_ECOLOGY_ENABLED.get() || this.animal.isAlarmed()
                || this.animal.isBaby()) {
            return false;
        }

        if (this.thirstTicks > 0) {
            this.thirstTicks--;
            return false;
        }

        // The cooldown is armed BEFORE the sweep, and that ordering is the whole safety of this
        // goal. Arming it only on failure looks equivalent and is not: an animal that finds water
        // it cannot reach — across a ravine, behind a wall — has its path fail, the goal end, and
        // {@code canUse} run again on the very next tick with the counter still at zero. That is a
        // 7.600-position sweep every tick, forever, on an animal that looks like it is standing
        // still. Armed first, the worst case is one sweep per retry interval no matter what
        // happens afterwards.
        this.thirstTicks = DRY_RETRY;
        this.water = findWater();

        return this.water != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.water != null && !this.animal.isAlarmed() && this.walkTicks < WALK_LIMIT;
    }

    @Override
    public void start() {
        this.drinkTicks = 0;
        this.walkTicks = 0;
        path();
    }

    @Override
    public void stop() {
        this.water = null;
        this.drinkTicks = 0;
        this.walkTicks = 0;
        this.animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.water == null) {
            return;
        }

        this.animal.getLookControl().setLookAt(this.water.getX() + 0.5D, this.water.getY(),
                this.water.getZ() + 0.5D);

        if (this.drinkTicks > 0) {
            this.drinkTicks--;
            if (this.drinkTicks == 0) {
                // Thirst is satisfied here rather than on arrival, so a drink interrupted halfway
                // does not count.
                this.thirstTicks = DRINK_INTERVAL;
                this.animal.ate();
                this.water = null;
            }
            return;
        }

        this.walkTicks++;

        if (this.animal.distanceToSqr(this.water.getX() + 0.5D, this.water.getY(),
                this.water.getZ() + 0.5D) <= REACH_SQR) {
            this.animal.getNavigation().stop();
            this.drinkTicks = adjustedTickDelay(DRINK_TICKS);
            return;
        }

        if (this.walkTicks % REPATH_INTERVAL == 0) {
            path();
        }
    }

    private void path() {
        if (this.water != null) {
            this.animal.getNavigation().moveTo(this.water.getX() + 0.5D, this.water.getY(),
                    this.water.getZ() + 0.5D, this.speedModifier);
        }
    }

    /**
     * The nearest drinkable water within reach, or null.
     *
     * <p>Only the water's own block matters — not what is above it. An animal standing at the edge
     * of a pond can drink from it whether or not the surface has a lily pad on it.
     */
    @Nullable
    private BlockPos findWater() {
        for (BlockPos pos : BlockPos.withinManhattan(this.animal.blockPosition(),
                SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS)) {
            if (this.level.getFluidState(pos).is(FluidTags.WATER)) {
                return pos.immutable();
            }
        }

        return null;
    }
}
