package com.example.examplemod.progression;

/**
 * Every tunable number of the Imperial Command tree, in one file.
 *
 * <h2>A separate currency on purpose</h2>
 *
 * Command Points are not Doctrine Points. If they were, every soldier a player learned to call would
 * be an implant they did not get, and "improve your command" would read as "delay your ascension" —
 * two ladders competing for one wallet, which is a choice nobody wants to be asked to make. The
 * commander earns its own experience from raids, and spends it on nothing else.
 */
public final class PlayerCommanderBalance {
    private PlayerCommanderBalance() {
    }

    // ==================================================================== experience

    public static final int XP_START_FIRST_RAID = 5;
    public static final int XP_RAID_WON = 30;
    public static final int XP_FLAWLESS_BONUS = 10;
    public static final int XP_CAMP_DESTROYED = 20;

    public static final int XP_KILL_NOB = 4;
    public static final int XP_KILL_MEGANOB = 6;
    public static final int XP_KILL_WARBOSS = 15;

    /** XP for the first commander level; each one after costs {@link #XP_PER_LEVEL_GROWTH} more. */
    public static final int XP_FIRST_LEVEL = 40;
    public static final int XP_PER_LEVEL_GROWTH = 20;
    public static final int POINTS_PER_LEVEL = 1;
    public static final int MAX_LEVEL = 30;

    /**
     * The one Command Point a player is handed on reaching Astra Veteran.
     *
     * <p>It exists so the first reinforcement node can be taken without ever touching Doctrine. A
     * commander who has never raided still gets to call their first three Guardsmen.
     */
    public static final int VETERAN_GRANT_POINTS = 1;

    // ==================================================================== calling for troops

    /** Nobody calls more than this, whatever they have unlocked. */
    public static final int MAX_REINFORCEMENTS = 10;

    /** A base always keeps at least this many soldiers at home, however many were asked for. */
    public static final int HOME_GUARD_MINIMUM = 1;

    public static final int REINFORCEMENTS_VOX = 3;
    public static final int REINFORCEMENTS_REINFORCED = 5;
    public static final int REINFORCEMENTS_SECTION = 7;
    public static final int REINFORCEMENTS_PLATOON = 10;

    /** Raids won before the deeper reinforcement nodes will open. */
    public static final int WINS_FOR_SECTION = 2;
    public static final int WINS_FOR_PLATOON = 4;

    // ==================================================================== cooldown

    /** Between one player-started raid and the next. Two minutes. */
    public static final int RAID_COOLDOWN_TICKS = 2400;

    /** Priority Vox: a quarter off the wait. */
    public static final double PRIORITY_VOX_COOLDOWN_CUT = 0.25D;

    /** After a raid that was aborted rather than won — short, so a mishap is not a punishment. */
    public static final int ABORT_COOLDOWN_TICKS = 600;

    // ==================================================================== the approach

    /** How far from the camp a called squad is set down. Far enough to be a march, not a spawn. */
    public static final int APPROACH_DISTANCE = 100;

    /** Forward Insertion brings the drop in to here. */
    public static final int APPROACH_DISTANCE_ADVANCED = 70;

    /** The band either side of the nominal distance a safe spot may be found in. */
    public static final int APPROACH_TOLERANCE = 10;

    // ==================================================================== coordinated assault

    public static final int COORDINATED_BUFF_TICKS = 400;
    public static final int COORDINATED_SPEED_AMPLIFIER = 0;
}
