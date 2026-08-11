package com.example.examplemod.crusade;

/**
 * Every number the soldier career turns on, in one file.
 *
 * <p>The project's rule: a tunable lives in exactly one place, and the place is named after what it
 * tunes. Nothing here is read on a hot path — promotions happen on kills and on raid results, both
 * of which are events.
 */
public final class ImperialSoldierBalance {
    private ImperialSoldierBalance() {
    }

    // ==================================================================== career gates

    /**
     * Merit needed to be blooded, and to be given a squad.
     *
     * <p>These mirror {@code GuardsmanRank.VETERAN} and {@code GuardsmanRank.SERGEANT}, which own the
     * stats. Kept here as well because the career manager decides <i>whether</i> a promotion may
     * happen and the rank table decides <i>what it is worth</i> — two questions, and mixing them is
     * how a cap ends up being enforced in three places.
     */
    public static final int VETERAN_MERIT = 10;
    public static final int SERGEANT_MERIT = 40;

    /**
     * One sergeant per this many soldiers in the garrison, rounded down, minimum one once the
     * garrison is big enough to have any.
     *
     * <p>Without a cap every survivor eventually becomes a sergeant and the rank stops meaning
     * anything — which is the failure mode the brief calls out by name.
     */
    public static final int SOLDIERS_PER_SERGEANT = 5;

    /** A base below this many soldiers fields no sergeant at all. */
    public static final int MIN_GARRISON_FOR_SERGEANT = 3;

    // ==================================================================== merit awards

    /** Merit for a plain greenskin. Deliberately small: a career is built out of many fights. */
    public static final int MERIT_ORK_BOY = 1;
    public static final int MERIT_GRETCHIN = 0;
    public static final int MERIT_ORK_NOB = 4;
    public static final int MERIT_MEGANOB = 6;
    public static final int MERIT_KILLA_KAN = 6;
    public static final int MERIT_WARBOSS = 15;

    /** Surviving a raid at all, and surviving one that was won. */
    public static final int MERIT_RAID_SURVIVED = 3;
    public static final int MERIT_RAID_WON = 5;

    // ==================================================================== veteran and sergeant bonuses

    /** Fraction added to max health at each grade. */
    public static final double VETERAN_HEALTH_BONUS = 0.10D;
    public static final double SERGEANT_HEALTH_BONUS = 0.15D;

    /**
     * Fraction taken off a shot's inaccuracy. "Accuracy" in this mod is spread, so the bonus is a
     * reduction, and a smaller number is a better shot.
     */
    public static final double VETERAN_ACCURACY_BONUS = 0.05D;
    public static final double SERGEANT_ACCURACY_BONUS = 0.08D;

    // ==================================================================== the memorial

    /**
     * How many fallen soldiers a base remembers.
     *
     * <p>The roll of the dead is stored in the base's own saved data, so it has to be bounded or a
     * long campaign turns the save file into a graveyard. Oldest entries fall off the end; the
     * counters that feed the Crusade record are totals and are never trimmed.
     */
    public static final int MAX_REMEMBERED_FALLEN = 64;
}
