package com.example.examplemod;

/**
 * A unit whose lasgun pose the client renderer needs to know about.
 *
 * <h2>The pose counter is derived, not synchronised</h2>
 *
 * <p>Implementors store the game time at which the current pose <i>began</i> and subtract it on
 * read, rather than synchronising the counter itself. The combat goals call
 * {@link #setLasgunCombatPose} every tick with a counter that has advanced by one, so storing the
 * counter meant a changed value — and therefore an entity-metadata packet to every tracking client —
 * twenty times a second per soldier, for a number the client can derive on its own. Storing the
 * start makes that written value identical on every one of those calls, and vanilla only marks a
 * synched entry dirty when the value actually differs.</p>
 */
public interface LasgunAimingEntity {

    /**
     * The stored start value meaning "this unit has never struck a pose".
     *
     * <p>Needed because the synchronised default is a number like any other, and without a sentinel
     * a soldier that has never raised its weapon would compute its pose age as the entire age of the
     * world. Shared here so the four implementors cannot drift on what "unset" looks like.</p>
     */
    int POSE_NEVER_STARTED = Integer.MIN_VALUE;

    LasgunCombatPose getLasgunCombatPose();

    /** How far into the current pose this unit is, in ticks. Zero when it has never posed. */
    int getLasgunCombatTicks();

    void setLasgunCombatPose(LasgunCombatPose pose, int poseTicks);

    default void clearLasgunCombatPose() {
        setLasgunCombatPose(LasgunCombatPose.IDLE, 0);
    }
}
