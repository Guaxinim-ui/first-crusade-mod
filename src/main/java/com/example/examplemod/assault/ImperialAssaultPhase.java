package com.example.examplemod.assault;

/**
 * Where a player-started raid has got to.
 *
 * <p>The phases exist so the manager's per-tick work is a switch and not a pile of booleans: a raid
 * in {@link #RETURNING} is not asked whether the camp is dead, and a raid in {@link #COMPLETE} is
 * not asked anything at all — it is swept.
 */
public enum ImperialAssaultPhase {
    /** Validated and registered; the camp has been marked, nothing has moved yet. */
    STARTING,

    /** Troops have been chosen, marked and set down on the approach ring. */
    REINFORCEMENTS_DEPLOYED,

    /** The fight. The only phase that checks for victory. */
    ACTIVE,

    /** Over, one way or another; survivors are being sent home. */
    RETURNING,

    /** The camp fell. Rewards are paid once, on the way into this. */
    VICTORY,

    /** Aborted or lost. No reward, troops still come home. */
    FAILED,

    /** Nothing left to do. Swept from the save on the next pass. */
    COMPLETE;

    public boolean isOver() {
        return this == VICTORY || this == FAILED || this == COMPLETE;
    }

    public boolean isFighting() {
        return this == STARTING || this == REINFORCEMENTS_DEPLOYED || this == ACTIVE;
    }
}
