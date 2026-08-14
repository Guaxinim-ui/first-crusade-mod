package com.example.examplemod.performance.ai;

import java.util.EnumSet;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Wraps any goal so that a unit far from every player tries it less often.
 *
 * <h2>Why a decorator</h2>
 *
 * The combat goals in this mod are the mod's own, so level of detail went straight into their
 * countdowns. Civilians are the opposite case: they run vanilla goals — {@code
 * WaterAvoidingRandomStrollGoal}, {@code LookAtPlayerGoal}, {@code PanicGoal} — which cannot be
 * edited and should not be reimplemented. A hab-block full of citizens wandering at full rate is a
 * lot of random position sampling and pathfinding for people nobody is looking at.
 *
 * <p>Wrapping is the cheapest honest answer. This class forwards every part of the {@link Goal}
 * contract to the goal it holds and changes exactly one thing: how often {@link #canUse()} is
 * allowed to say yes.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * It never interrupts a goal that is already running. {@link #canContinueToUse()} forwards
 * untouched, so a citizen already walking somewhere finishes the walk — throttling the <i>start</i>
 * of a stroll is invisible, and cutting one short is a mob that stops dead in the street.
 *
 * <p>It also never throttles below the wrapped goal's own rate: {@link FirstCrusadeAiLod#scale}
 * returns the base interval unchanged at FULL detail, so a citizen standing next to the player
 * behaves exactly as it did before this class existed.
 *
 * <h2>Countdown, not modulo</h2>
 *
 * The same trap as everywhere else in this package: {@code Mob#serverAiStep} only evaluates
 * {@code canUse} on every other tick, so a modulo on {@code tickCount} can land permanently on the
 * ticks this is never called. A local countdown cannot care how often it is asked.
 */
public class FCLodGoal extends Goal {

    private final Mob mob;
    private final Goal delegate;
    private final int baseInterval;

    private int countdown;

    /**
     * @param mob          the unit the goal belongs to, used to work out its level of detail
     * @param delegate     the goal being throttled
     * @param baseInterval evaluations between attempts at FULL detail; 1 means "as often as vanilla"
     */
    public FCLodGoal(Mob mob, Goal delegate, int baseInterval) {
        this.mob = mob;
        this.delegate = delegate;
        this.baseInterval = Math.max(1, baseInterval);
        this.countdown = Math.floorMod(mob.getId(), this.baseInterval);

        // The flags must match the wrapped goal exactly. GoalSelector uses them to decide which
        // goals may run together; a wrapper that declared none would let a throttled stroll run
        // alongside a panic and fight it for the movement controls.
        setFlags(delegate.getFlags());
    }

    @Override
    public boolean canUse() {
        if (this.countdown > 0) {
            this.countdown--;
            return false;
        }

        this.countdown = FirstCrusadeAiLod.scale(this.mob, this.baseInterval);
        return this.delegate.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.delegate.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return this.delegate.isInterruptable();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return this.delegate.requiresUpdateEveryTick();
    }

    @Override
    public void start() {
        this.delegate.start();
    }

    @Override
    public void stop() {
        this.delegate.stop();
    }

    @Override
    public void tick() {
        this.delegate.tick();
    }

    @Override
    public String toString() {
        return "FCLodGoal[" + this.delegate.getClass().getSimpleName() + " x" + this.baseInterval + "]";
    }
}
