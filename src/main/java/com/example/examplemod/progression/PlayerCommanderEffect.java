package com.example.examplemod.progression;

/**
 * What a command node actually does.
 *
 * <p>Kept as an enum rather than a lambda per node because the assault manager reads these by
 * asking "what is the best REINFORCEMENT_LIMIT this player owns" — a question about the whole
 * profile, not about one node. A node that carried its own behaviour could not answer it.
 */
public enum PlayerCommanderEffect {
    /** Opens the tree. Calls nobody by itself. */
    AUTHORITY,

    /** Raises the ceiling on how many soldiers may be called. The value is the ceiling. */
    REINFORCEMENT_LIMIT,

    /** A Sergeant is preferred in the squad when one exists. Never creates one. */
    SERGEANT_PREFERENCE,

    /** Cuts the wait between raids. */
    COOLDOWN_CUT,

    /** Brings the approach drop closer to the camp. */
    APPROACH_CUT,

    /** A short Speed and knockback-resistance blessing when the squad arrives. */
    ARRIVAL_BUFF
}
