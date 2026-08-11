package com.example.examplemod;

/**
 * How far up the ladder a soldier has come, expressed as the only thing the renderer cares about:
 * which set of textures to draw from.
 *
 * <p>This is deliberately coarser than {@link GuardsmanRank}. There are eight ranks and there will
 * be more; there are three <i>looks</i>, because a look has to be readable across a battlefield and
 * eight shades of "slightly more decorated" are not. Corporal and Veteran wear the same red
 * shoulder cloth; Lieutenant and Captain wear the sergeant's chevrons. The rank still governs
 * health, damage and command — only the picture collapses.</p>
 *
 * <p>Grade is derived, never stored. A soldier's rank is already saved and already survives a
 * promotion with his UUID, name and kill count intact; storing the look beside it would be a second
 * copy of the same fact, free to drift out of step the first time a rank changed somewhere that
 * forgot to update it.</p>
 */
public enum ImperialTroopGrade {
    /** The line soldier, and the fallback for every troop type that has no ladder of its own. */
    LINE(""),

    /** Blooded: red shoulder cloth, tally marks, a harder-used uniform. */
    VETERAN("veteran"),

    /** Squad leader: chevrons, a helmet band, the vox set. */
    SERGEANT("sergeant");

    private final String suffix;

    ImperialTroopGrade(String suffix) {
        this.suffix = suffix;
    }

    /** The filename infix for this grade — {@code guardsman_veteran_0.png} and so on. */
    public String suffix() {
        return this.suffix;
    }

    /**
     * The look that goes with a Guardsman rank.
     *
     * <p>The cut is at Veteran and at Sergeant because those are the two moments the fiction calls
     * a promotion: a soldier who has survived, and a soldier who now gives orders.
     */
    public static ImperialTroopGrade forRank(GuardsmanRank rank) {
        if (rank == null) {
            return LINE;
        }
        if (rank.ordinal() >= GuardsmanRank.SERGEANT.ordinal()) {
            return SERGEANT;
        }
        if (rank.ordinal() >= GuardsmanRank.VETERAN.ordinal()) {
            return VETERAN;
        }
        return LINE;
    }
}
