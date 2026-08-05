package com.example.examplemod.animal;

import net.minecraft.world.entity.ai.goal.PanicGoal;

/**
 * Panic, extended to the mod's one extra reason for it: the alarm.
 *
 * <p>The vanilla goal runs when the animal is on fire, freezing, or was hit by something it can
 * name. A war zone gives an animal reasons that fit none of those — a shell landing beside it, a
 * tank going past, the animal next to it being shot. {@link FCAnimalEntity} collects all of that
 * into one flag ({@code isAlarmed}) which anything can raise, and this goal is what turns the flag
 * into running.
 *
 * <p>Everything else is inherited on purpose: fleeing toward water when on fire, the random flight
 * position, the interruption of ordinary goals. The mod's contribution is one extra condition, so
 * one extra condition is what this class contains.
 */
public class AlarmedPanicGoal extends PanicGoal {

    private final FCAnimalEntity animal;

    public AlarmedPanicGoal(FCAnimalEntity animal, double speedModifier) {
        super(animal, speedModifier);
        this.animal = animal;
    }

    @Override
    protected boolean shouldPanic() {
        return this.animal.isAlarmed() || super.shouldPanic();
    }
}
