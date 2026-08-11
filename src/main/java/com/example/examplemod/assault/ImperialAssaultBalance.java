package com.example.examplemod.assault;

/**
 * Every number of the player-started raid, in one file.
 *
 * <p>The cadences at the top are the ones that decide what this system costs. A raid updates five
 * times a second at most, retargets half as often, and does nothing whatsoever when no raid is
 * running — {@link ImperialAssaultManager} returns on an {@code isEmpty()} before it looks at
 * anything.
 */
public final class ImperialAssaultBalance {
    private ImperialAssaultBalance() {
    }

    // ==================================================================== cadence

    /** How often an active raid is looked at. */
    public static final int UPDATE_INTERVAL_TICKS = 20;

    /** How often a soldier is given a fresh target/destination. Never every tick. */
    public static final int RETARGET_INTERVAL_TICKS = 40;

    // ==================================================================== starting

    /**
     * How close a player must stand to the camp to declare a raid on it.
     *
     * <p>Was 8 while the only way in was right-clicking the camp block, where the player is
     * standing on top of it anyway. The keybind changed what the number means: it is now "am I at
     * this camp", asked by somebody who is fighting rather than reaching for a block, and 8 blocks
     * of a fortified Ork camp is inside the palisade. Both paths use this one value — the button
     * that names a camp and the key that looks one up — so there is no way for them to disagree.
     */
    public static final double START_RANGE = 24.0D;

    /** How far a soldier may be from its Core and still count as part of the garrison. */
    public static final int GARRISON_PICK_RADIUS = 32;

    /** How far the search for an eligible base will reach, in blocks. */
    public static final int BASE_SEARCH_RADIUS = 4000;

    // ==================================================================== the march

    /** How near the camp a marching soldier is considered to have arrived. */
    public static final double ARRIVAL_RANGE = 24.0D;

    /** How far from a soldier it will look for something to shoot. */
    public static final double TARGET_SEARCH_RANGE = 20.0D;

    public static final double MARCH_SPEED = 1.05D;

    // ==================================================================== abort

    /** Beyond this from the camp, the initiating player is counted as having walked away. */
    public static final double ABANDON_RADIUS = 256.0D;

    /** How long the player may be away, dead or logged out before the raid is called off. */
    public static final int ABANDON_GRACE_TICKS = 600;

    /** A hard ceiling so a forgotten raid cannot hold troops out forever. */
    public static final int MAX_RAID_TICKS = 24000;

    // ==================================================================== coming home

    /** Ring around the Core survivors are placed in when their exact origin is no longer safe. */
    public static final int RETURN_RING_MIN = 4;
    public static final int RETURN_RING_MAX = 12;

    // ==================================================================== safety of a drop point

    /** How many candidate spots the approach search tries before giving up on a direction. */
    public static final int APPROACH_ATTEMPTS = 48;

    /** How many soldiers share one drop spot before the next one is picked. */
    public static final int SQUAD_CLUSTER_SIZE = 3;

    /** How far apart the clusters are set down. */
    public static final int CLUSTER_SPREAD = 6;

    /** No drop point may be nearer the camp heart than this, whatever the maths says. */
    public static final int MIN_DISTANCE_FROM_CAMP = 40;
}
