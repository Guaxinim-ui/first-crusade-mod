package com.example.examplemod.progression;

/**
 * Every tunable number of the Imperial progression, in one file.
 *
 * <p>The point is not tidiness. A balance value that lives at its use site gets copied to the
 * second use site, and the two drift; the prayer's cooldown floor in particular is read by the
 * ability, by the node that shortens it and by the tooltip that promises it, and all three have to
 * agree or the screen lies to the player.
 */
public final class PlayerProgressionBalance {
    private PlayerProgressionBalance() {
    }

    // ==================================================================== shape of the tree
    public static final int IMPLANT_COUNT = 12;
    public static final int CYCLE_COUNT = 12;
    public static final int SKILL_RANKS = 5;

    /** Implants and the ascension are paid for in gene-seed and blood, not doctrine. */
    public static final int IMPLANT_POINT_COST = 0;

    // ==================================================================== experience
    public static final int XP_GRETCHIN = 2;
    public static final int XP_ORK_BOY = 4;
    public static final int XP_ORK_NOB = 12;
    public static final int XP_MEGANOB = 20;
    public static final int XP_KILLA_KAN = 20;
    public static final int XP_WARBOSS = 50;
    public static final int XP_RAID_VICTORY = 30;

    /** XP for the first level; each level after costs {@link #XP_PER_LEVEL_GROWTH} more. */
    public static final int XP_FIRST_LEVEL = 60;
    public static final int XP_PER_LEVEL_GROWTH = 25;
    public static final int POINTS_PER_LEVEL = 1;
    public static final int MAX_LEVEL = 60;

    /**
     * Anti-farm: a kill only pays if the victim had a fight in it.
     *
     * <p>A mob that never saw the player, was summoned by the player, or is the ninth identical
     * corpse at the same spot inside the window pays nothing.
     */
    public static final int FARM_WINDOW_TICKS = 1200;
    public static final int FARM_SAME_TYPE_LIMIT = 12;
    public static final double FARM_RADIUS = 12.0D;

    // ==================================================================== combat memory
    public static final int MEMORY_WINDOW_TICKS = 300;
    public static final int MEMORY_MAX_STACKS = 5;

    // ==================================================================== prayer
    public static final int PRAYER_CHANNEL_TICKS = 40;
    public static final int PRAYER_BASE_DURATION_TICKS = 400;
    public static final int PRAYER_BASE_COOLDOWN_TICKS = 3600;
    public static final int PRAYER_MIN_COOLDOWN_TICKS = 1200;
    public static final int PRAYER_MAX_BONUS_DURATION_TICKS = 300;
    public static final int PRAYER_MAX_COOLDOWN_CUT_TICKS = 800;
    public static final double PRAYER_MOVE_LIMIT = 0.02D;
    public static final float PRAYER_MAX_ABSORPTION = 12.0F;
    public static final double PRAYER_CHOIR_RADIUS = 12.0D;

    // ==================================================================== tactical roll
    public static final int ROLL_BASE_COOLDOWN_TICKS = 200;
    public static final int ROLL_COOLDOWN_PER_RANK = 15;
    public static final int ROLL_MIN_COOLDOWN_TICKS = 100;
    public static final double ROLL_BASE_POWER = 0.85D;
    public static final double ROLL_POWER_PER_RANK = 0.06D;
    public static final int ROLL_GRACE_TICKS = 30;

    // ==================================================================== acid spit
    public static final int ACID_BASE_COOLDOWN_TICKS = 120;
    public static final double ACID_BASE_RANGE = 4.0D;
    public static final double ACID_RANGE_PER_RANK = 0.5D;
    public static final float ACID_BASE_DAMAGE = 4.0F;
    public static final float ACID_DAMAGE_PER_RANK = 1.0F;

    // ==================================================================== Sus-an stasis
    public static final int SUSAN_COOLDOWN_TICKS = 6000;
    public static final int SUSAN_BASE_DURATION_TICKS = 100;
    public static final int SUSAN_DURATION_PER_RANK = 20;
    public static final float SUSAN_HEALTH_THRESHOLD = 0.25F;

    // ==================================================================== out-of-combat healing
    public static final int COMBAT_TAG_TICKS = 200;
    public static final int REGEN_INTERVAL_TICKS = 40;
    public static final int MIN_FOOD_FOR_REGEN = 6;

    /** Larraman's emergency clot: one burst, then a long wait. */
    public static final int LARRAMAN_COOLDOWN_TICKS = 2400;
    public static final float LARRAMAN_THRESHOLD = 0.25F;
    public static final float LARRAMAN_HEAL = 6.0F;

    // ==================================================================== safety rails
    /** However many nodes stack, incoming damage is never cut by more than this. */
    public static final double MAX_DAMAGE_REDUCTION = 0.60D;
    public static final double MAX_ENVIRONMENTAL_REDUCTION = 0.60D;
    public static final double MAX_WEAPON_COOLDOWN_REDUCTION = 0.50D;

    // ==================================================================== surgery
    public static final int SURGERY_TICKS = 1200;
    public static final double SURGERY_CORE_RANGE = 12.0D;
    public static final int SURGERY_GENE_SEED_COST = 1;

    /** Head-room the surgery needs above the player before it will let them grow. */
    public static final double SURGERY_CLEARANCE = 2.6D;

    // ==================================================================== blood trial
    public static final int TRIAL_ORK_KILLS = 50;
    public static final int TRIAL_ELITE_KILLS = 5;
    public static final int TRIAL_WARBOSS_KILLS = 1;
    public static final int TRIAL_RAIDS_ALTERNATIVE = 2;

    // ==================================================================== data
    /**
     * Bumped whenever the saved shape changes, so an old profile can be migrated on read.
     *
     * <p>2 — the Imperial Command career joined the profile. A version-1 record simply has no
     * {@code Commander} tag, and an absent tag reads as a commander who has done nothing, so the
     * migration needs no code: an old save starts its command career from zero rather than being
     * handed one.
     */
    public static final int DATA_VERSION = 2;
}
