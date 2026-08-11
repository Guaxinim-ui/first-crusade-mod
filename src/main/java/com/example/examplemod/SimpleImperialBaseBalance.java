package com.example.examplemod;

/**
 * Every number of the simplified Imperial base, in one file.
 *
 * <h2>Why the city builder went away</h2>
 *
 * An Imperial settlement used to be a city builder: construction projects ticking every 20 ticks,
 * a military manager every 60, a strategic AI every 100, citizens looking for work, governors
 * queueing houses. That is a lot of CPU spent on something the player never commands. The base is
 * now the Imperial mirror of an Ork camp — a Core, a small pad of ground and a handful of soldiers
 * standing around it — and everything below is what "a handful" means.
 *
 * <h2>One table, not five</h2>
 *
 * The garrison size per Core level lived in {@link ImperialCityLevelStats} beside the old city's
 * storage and production tables, where it read as one of twenty numbers. It is the only military
 * number the simple base has, so it lives here, alone, where changing it is obviously a balance
 * decision and not a typo in a spreadsheet.
 */
public final class SimpleImperialBaseBalance {
    private SimpleImperialBaseBalance() {
    }

    // ==================================================================== garrison

    /** What a brand-new base is founded with, whatever its Core level says it could hold. */
    public static final int FOUNDING_GARRISON = 4;

    /** How many soldiers the base may hold, by Core level (1-5). */
    public static int garrisonCapacity(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 6;
            case 3 -> 8;
            case 4 -> 10;
            case 5 -> 12;
            default -> 4;
        };
    }

    // ==================================================================== replenishment

    /**
     * How often the base looks at its garrison at all. One minute: a base that lost soldiers rebuilds
     * over minutes, not seconds, and a base at strength does nothing measurable.
     */
    public static final int REPLENISH_INTERVAL_TICKS = 1200;

    /** At most one soldier per check — a base never refills itself in a single cycle. */
    public static final int REPLENISH_PER_CHECK = 1;

    /**
     * How far from the Core the reconciliation sweep looks, and only when the counter and reality
     * might have drifted. Deliberately small: the old code swept 96 blocks on every level-up.
     */
    public static final int RECONCILE_SCAN_RADIUS = 32;

    // ==================================================================== how soldiers stand around

    /** The Core is the soldiers' home; they wander inside this radius and no further. */
    public static final int HOME_RADIUS = 24;

    /** Beyond this from home, an idle soldier walks back. Below it, it is left alone. */
    public static final int RETURN_TRIGGER_DISTANCE = 32;

    /** Ticks between stroll attempts. High on purpose: standing still is the default, not marching. */
    public static final int STROLL_INTERVAL_TICKS = 140;

    // ==================================================================== the founding pad

    /** Half-width of the founding foundation, so 4 gives the 9x9 pad. */
    public static final int FOUNDATION_HALF = 4;

    /** How far above/below the Core the pad may reach when levelling the ground it sits on. */
    public static final int FOUNDATION_CLEAR_HEIGHT = 4;

    // ==================================================================== the abstract level

    /**
     * The strategic Age a Core level stands for.
     *
     * <p>The Age used to be bought with strategic resources by an AI nobody could see. It is now a
     * plain function of the Core's level, so "upgrade the Core" and "the Astartes path opens" are
     * the same visible act.
     */
    public static StrategicAge ageForCoreLevel(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> StrategicAge.FORTIFIED_SETTLEMENT;
            case 3 -> StrategicAge.MANUFACTORUM_AGE;
            case 4 -> StrategicAge.ASTARTES_AGE;
            case 5 -> StrategicAge.PLANETARY_WAR;
            default -> StrategicAge.OUTPOST;
        };
    }
}
