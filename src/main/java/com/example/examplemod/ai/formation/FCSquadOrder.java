package com.example.examplemod.ai.formation;

/**
 * What a squad has been told to do, as opposed to {@link FCSquadState}, which is what it is
 * currently doing about it.
 *
 * <h2>Order and state are not the same thing</h2>
 *
 * An order is an intent that persists until something changes it; a state is a reading of the
 * present moment. A squad under {@link #ATTACK} may be {@link FCSquadState#MOVING} while it closes
 * and {@link FCSquadState#ENGAGING} once it arrives, without the order changing at all. Collapsing
 * the two into one enum is tempting and wrong: the squad would forget what it was sent to do every
 * time the situation moved.
 *
 * <h2>Deliberately without a GUI</h2>
 *
 * Nothing issues these to a player's specification yet. The architecture is here so that a future
 * command interface, a Lord Commander, or the strategic layer has a single place to write an
 * intention into, rather than each of those growing its own private way of nudging troops around.
 * {@link #FOLLOW} is the default and is exactly what squads did before this existed, so adding the
 * enum changed no behaviour.
 */
public enum FCSquadOrder {

    /** Stay where you are. Formation holds, nobody advances on a target of opportunity. */
    HOLD_POSITION,

    /** Go to the squad's destination. */
    MOVE,

    /** Advance on the enemy and engage. */
    ATTACK,

    /** Hold the destination against attack — like HOLD_POSITION, but expecting contact. */
    DEFEND,

    /** Break contact and fall back to the destination. */
    RETREAT,

    /** Stay with the leader and do what it does. The default, and the pre-existing behaviour. */
    FOLLOW;

    /** Whether this order names a place the squad is supposed to be. */
    public boolean needsDestination() {
        return this == MOVE || this == DEFEND || this == RETREAT;
    }
}
