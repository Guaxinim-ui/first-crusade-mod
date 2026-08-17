package com.example.examplemod.unit.profile;

import com.example.examplemod.FirstCrusadeFaction;

/**
 * Which units will stand in a formation together.
 *
 * <h2>Why being allies is not enough</h2>
 *
 * Squad recruitment used to ask two questions: is this unit on my side, and will it take orders. On
 * an army of nothing but Guardsmen that is the right answer. The moment elites exist it stops being
 * one — a Guardsman Sergeant would sweep up Kasrkin, Space Marines and Custodes indiscriminately,
 * and a Space Marine falling into a formation slot behind a Guard sergeant is wrong twice over: it
 * is wrong for the fiction, and it is wrong mechanically, because a formation is built around units
 * that move and fight at the same speed.
 *
 * <p>This is deliberately coarser than {@link UnitRole}. A role says what a unit does inside a
 * squad; a family says which squad it belongs to at all. A medic and a heavy gunner have different
 * roles and the same family, which is exactly the distinction the two enums are for.
 *
 * <h2>Derived by default</h2>
 *
 * {@link #forFaction} means a unit that says nothing gets a sensible answer from its faction, so
 * adding this enum changed no existing behaviour: every Imperial Guard unit was already going to be
 * {@link #IMPERIAL_GUARD} and every Ork {@link #ORK}. A unit only overrides
 * {@link FCUnit#getSquadFamily()} when it belongs to a formation of its own.
 */
public enum FCSquadFamily {

    /** Line Astra Militarum: Guardsmen, riflemen, sergeants, and the rest of the regiment. */
    IMPERIAL_GUARD,

    /** Militarum elites who fight in their own small formations. Kasrkin and equivalents. */
    MILITARUM_ELITE,

    /** Adeptus Astartes. Space Marines form squads with Space Marines. */
    ASTARTES,

    /** The Emperor's own. Never mixed into a line formation. */
    ADEPTUS_CUSTODES,

    /** Ork mobs: Nobz and the Boyz who follow them. */
    ORK,

    /** Anything with no formation of its own. Never recruited, never recruits. */
    OTHER;

    /**
     * The family a unit belongs to when it does not declare one.
     *
     * <p>{@link #OTHER} for anything that is not clearly one side's line troops, and OTHER never
     * forms a squad — which is the safe direction to fail in. A unit wrongly placed in a formation
     * is a visible bug; a unit that simply never joins one behaves exactly as it did before squads
     * existed.
     */
    public static FCSquadFamily forFaction(FirstCrusadeFaction faction) {
        return switch (faction) {
            case IMPERIUM -> IMPERIAL_GUARD;
            case ORKS -> ORK;
            default -> OTHER;
        };
    }

    /** Whether a unit of this family may follow a leader of {@code other}. */
    public boolean canFollow(FCSquadFamily other) {
        return this == other && this != OTHER;
    }
}
