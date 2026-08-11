package com.example.examplemod.progression.ork;

/**
 * Every number the Ork progression turns on.
 *
 * <p>Same rule as the Imperial side: one file, one owner, and nothing read on a hot path. Krump and
 * Teef move on kills, Fury moves on damage, and all three are events.
 */
public final class PlayerOrkProgressionBalance {
    private PlayerOrkProgressionBalance() {
    }

    /** Bumped when the Ork profile's stored shape changes. Independent of the Imperial version. */
    public static final int DATA_VERSION = 1;

    // ==================================================================== krumpagem
    //
    // Reputation, not experience. It is never spent and it has no levels — it is the running answer
    // to "how much has this git proved he can krump", and the evolution gates read it.

    public static final int KRUMP_HOSTILE = 1;
    public static final int KRUMP_GUARDSMAN = 3;
    public static final int KRUMP_SPECIALIST = 5;
    public static final int KRUMP_SERGEANT = 6;
    public static final int KRUMP_ELITE_TROOP = 8;
    public static final int KRUMP_SISTER = 10;
    public static final int KRUMP_SPACE_MARINE = 20;

    public static final int KRUMP_POSITION_DESTROYED = 30;
    public static final int KRUMP_CORE_DESTROYED = 75;
    public static final int KRUMP_MAJOR_VICTORY = 25;

    // ==================================================================== teef
    //
    // The personal currency, and the only thing the tree costs. Deliberately NOT
    // StrategicResourceType.TEEF, which is the Ork AI's war chest — two different things that would
    // be a nightmare to untangle if they ever shared a field.

    /** One tooth in the bag is one Teef in the pocket. */
    public static final int TEEF_PER_ORK_TOOTH = 1;

    /** Teef handed over after a win worth calling a win. */
    public static final int TEEF_MAJOR_VICTORY = 5;
    public static final int TEEF_CORE_DESTROYED = 12;

    /** Node costs. Three ranks, not five: an Ork does not study a subject to mastery. */
    public static final int[] NODE_RANK_COST = {2, 4, 7};

    /** One-shot nodes, cheapest to dearest. */
    public static final int SPECIAL_COST_SMALL = 8;
    public static final int SPECIAL_COST_MEDIUM = 12;
    public static final int SPECIAL_COST_LARGE = 15;
    public static final int SPECIAL_COST_HUGE = 20;

    // ==================================================================== waaagh fury
    //
    // Temporary. Zero to a hundred, climbs in a fight and falls out of one. It is NOT the global
    // WAAAGH (WaaaghOverlordData) and NOT a camp's local waaagh (OrkCampBlockEntity) — three values
    // with the same word on them, and the only defence against confusing them is that they never
    // share a class.

    public static final int FURY_MAX = 100;

    public static final int FURY_ON_KILL = 10;
    public static final int FURY_ON_ELITE_KILL = 20;
    public static final int FURY_ON_DAMAGE_DEALT = 2;
    public static final int FURY_ON_DAMAGE_TAKEN = 3;

    /** Floor between two Fury awards for dealt damage, so a fast weapon is not a Fury faucet. */
    public static final int FURY_DAMAGE_COOLDOWN_TICKS = 10;

    /** Quiet time before Fury starts draining. Roughly eight seconds. */
    public static final int FURY_CALM_TICKS = 160;

    /**
     * Fury lost per second once it starts draining.
     *
     * <p>Applied by timestamp arithmetic, never by decrementing on a tick: at zero there is nothing
     * to compute and no player to compute it for.
     */
    public static final int FURY_DECAY_PER_SECOND = 4;

    // ==================================================================== evolution gates

    public static final int BIG_BOY_KRUMP = 40;
    public static final int BIG_BOY_KILLS = 10;
    public static final int BIG_BOY_NODES = 3;

    public static final int NOB_KRUMP = 150;
    public static final int NOB_KILLS = 25;
    public static final int NOB_ELITES = 1;
    public static final int NOB_RANKS = 6;
    public static final int NOB_TEEF_SPENT = 25;

    public static final int BIG_NOB_KRUMP = 400;
    public static final int BIG_NOB_KILLS = 60;
    public static final int BIG_NOB_ELITES = 5;
    public static final int BIG_NOB_RANKS = 12;

    public static final int WARBOSS_KRUMP = 900;
    public static final int WARBOSS_KILLS = 120;
    public static final int WARBOSS_ELITES = 10;
    public static final int WARBOSS_RANKS = 18;
    public static final int WARBOSS_MAJOR_VICTORIES = 2;

    /**
     * The global WAAAGH tier that must be running before anyone can crown himself.
     *
     * <p>Read from the existing {@code WaaaghOverlordManager}, never from a second copy. It is a
     * confirmation that a real WAAAGH is under way — the personal requirements above still have to
     * be met on their own, so one player maxing the global tier cannot hand the title to another.
     */
    public static final int WARBOSS_GLOBAL_TIER = 2;

    // ==================================================================== stage bonuses
    //
    // Small on purpose. The tree already hands out most of the power; these are what growing itself
    // is worth.

    public static final double BIG_BOY_HEALTH = 2.0D;

    public static final double NOB_HEALTH = 4.0D;
    public static final double NOB_MELEE = 1.0D;
    public static final double NOB_KNOCKBACK_RESIST = 0.05D;

    public static final double BIG_NOB_HEALTH = 5.0D;
    public static final double BIG_NOB_ARMOR = 2.0D;
    public static final double BIG_NOB_MELEE = 1.0D;
    public static final double BIG_NOB_KNOCKBACK_RESIST = 0.08D;

    public static final double WARBOSS_HEALTH = 8.0D;
    public static final double WARBOSS_MELEE = 2.0D;
    public static final double WARBOSS_ARMOR = 2.0D;
    public static final double WARBOSS_TOUGHNESS = 2.0D;
    public static final double WARBOSS_KNOCKBACK_RESIST = 0.10D;

    // ==================================================================== dakka
    //
    // The Shoota's three hardcoded numbers used to live inside ShootaItem, where the tree could not
    // reach them and a designer could not find them. The item now asks PlayerOrkCombatModifiers, and
    // the modifiers read this block — so what a Dakka rank is worth is one edit in one file.

    public static final int SHOOTA_BASE_COOLDOWN_TICKS = 8;

    /**
     * The floor no amount of MOAR DAKKA may go under.
     *
     * <p>Five ticks is four shots a second. Below that the client's cooldown animation and the
     * server's are visibly out of step on any real latency, and the gun stops reading as a gun.
     */
    public static final int SHOOTA_MIN_COOLDOWN_TICKS = 5;

    public static final double SHOOTA_BASE_DAMAGE = 4.0D;
    public static final float SHOOTA_BASE_INACCURACY = 6.0F;

    /** A Shoota is never a sniper rifle, however much KUNNIN is poured into it. */
    public static final float SHOOTA_MIN_INACCURACY = 1.5F;

    /** DAKKA IZ LOUD — a flat bite added to every bolt. */
    public static final double BASIC_DAKKA_DAMAGE_PER_RANK = 0.5D;

    /** MOAR DAKKA — one tick off the cooldown per rank, floored above. */
    public static final int MORE_DAKKA_COOLDOWN_PER_RANK = 1;

    /** DAKKA ON DA MOVE — spread comes off, and the sprinting penalty with it. */
    public static final float DAKKA_ON_THE_MOVE_SPREAD_PER_RANK = 1.2F;

    /** Extra spread while sprinting, which DAKKA ON DA MOVE cancels outright at rank 3. */
    public static final float SHOOTA_SPRINT_SPREAD = 3.0F;

    /** DAKKA DAKKA DAKKA — the chance the trigger produces a second bolt. */
    public static final double DAKKA_DAKKA_EXTRA_SHOT_PER_RANK = 0.12D;

    /** NEVA ENUFF DAKKA — the Warboss capstone, on top of everything else. */
    public static final double NEVA_ENUFF_DAKKA_DAMAGE = 2.0D;
    public static final int NEVA_ENUFF_DAKKA_COOLDOWN = 1;
    public static final double NEVA_ENUFF_DAKKA_EXTRA_SHOT = 0.25D;

    /** However the chances stack, one squeeze is never more than three bolts. */
    public static final int SHOOTA_MAX_EXTRA_SHOTS = 2;

    // ==================================================================== melee
    //
    // Percentages of a blow, so they live here and are applied in PlayerOrkCombatEvents. An attribute
    // cannot express "+5% against elites" or "+10% from behind" — those only exist at the swing.

    public static final double HARDER_HITTING_PER_RANK = 0.04D;
    public static final double BIG_CHOPPA_PER_RANK = 0.05D;
    public static final double KRUMP_EVERYTHING_PER_RANK = 0.05D;

    /** SNEAKY GIT — for hitting something that is not looking at you. */
    public static final double SNEAKY_GIT_PER_RANK = 0.10D;

    /** The dot product above which the victim counts as facing away. Roughly a 90-degree rear arc. */
    public static final double SNEAKY_GIT_REAR_ARC = 0.35D;

    /** KRUMP FIRST — the opening blow on something still untouched. */
    public static final double KRUMP_FIRST_OPENER = 0.25D;

    /** RUN AN 'IT — only while actually sprinting into the blow. */
    public static final double RUN_AND_HIT_SPRINT_DAMAGE = 0.15D;
    public static final double RUN_AND_HIT_SPEED = 0.06D;

    /** BRUTAL BUT KUNNIN — flat on every swing, which is what a Warboss capstone should be. */
    public static final double BRUTAL_BUT_KUNNIN_MELEE = 0.15D;

    public static final double GOFFS_MELEE = 0.05D;

    // ==================================================================== soaking it
    //
    // Two nodes reduce incoming damage, and both are capped — separately and then together — so no
    // rebalance of either can quietly add up to immunity.

    public static final double DOESNT_HURT_MUCH_PER_RANK = 0.015D;
    public static final double DOESNT_HURT_MUCH_CAP = 0.045D;
    public static final double KUNNIN_BUT_BRUTAL_REDUCTION = 0.06D;

    /** The hard ceiling on everything this system takes off a blow. */
    public static final double DAMAGE_REDUCTION_CEILING = 0.12D;

    /** NOT DEAD YET — a lethal blow leaves him standing, once in a while. */
    public static final int NOT_DEAD_YET_COOLDOWN_TICKS = 6000;
    public static final float NOT_DEAD_YET_HEALTH = 4.0F;

    // ==================================================================== fury nodes

    /** SHOUT LOUDA — every source of Fury pays more. */
    public static final double LOUDER_WAAAGH_PER_RANK = 0.20D;

    /** DA GREENEST — Fury climbs half again as fast and never drains all the way out. */
    public static final double DA_GREENEST_FURY = 0.50D;
    public static final int DA_GREENEST_FURY_FLOOR = 30;

    // ==================================================================== teef and loot
    //
    // The KUNNIN branch pays in currency rather than in damage, which is the whole reason it is a
    // branch and not four more damage nodes.

    /** TEEF IZ MONEY — a better rate when the teeth in the pack are stashed. */
    public static final double TEEF_IS_MONEY_PER_RANK = 0.10D;

    /** KUNNIN BUT BRUTAL — the Warboss takes a cut of everything. */
    public static final double KUNNIN_BUT_BRUTAL_TEEF = 0.20D;

    public static final double BAD_MOONS_TEEF = 0.05D;
    public static final double BAD_MOONS_RANGED = 0.05D;

    /** BIG TEEF — big gitz have big teeth, and they go straight in the bag. */
    public static final int BIG_TEEF_PER_ELITE = 1;

    /** LOOT IT ALL — the chance a corpse gives up its drops a second time. */
    public static final double LOOT_IT_ALL_PER_RANK = 0.08D;
    public static final double DEATHSKULLS_SALVAGE = 0.10D;

    /** However it stacks, salvage never becomes the rule. */
    public static final double SALVAGE_CHANCE_CAP = 0.60D;

    /** GOT IT FIRST — teeth pulled off a krumped git before anyone else gets there. */
    public static final double GOT_IT_FIRST_PER_RANK = 0.15D;
    public static final int GOT_IT_FIRST_MAX_TEETH = 2;

    // ==================================================================== late armour

    /** MEGA PLATIN — the handling of Mega Armour without the item, until the item exists. */
    public static final double MEGA_PLATIN_ARMOR = 3.0D;
    public static final double MEGA_PLATIN_TOUGHNESS = 2.0D;
    public static final double MEGA_PLATIN_KNOCKBACK = 0.15D;

    /** DA BIGGEST — the last word in being hard to move. */
    public static final double DA_BIGGEST_HEALTH = 8.0D;
    public static final double DA_BIGGEST_KNOCKBACK = 0.20D;

    // ==================================================================== the global tide
    //
    // What a player's own war contributes to WaaaghOverlordData. Deliberately only for events big
    // enough to be news: a kill or a swing never touches the world's WAAAGH, or the tide would be a
    // readout of how long somebody has been grinding rather than of how the war is going.

    public static final int GLOBAL_WAAAGH_CORE_DESTROYED = 120;

    // ==================================================================== abilities
    //
    // Four buttons, four cooldowns, and not one of them a thing that lingers. An Ork ability is an
    // event: it happens, it lands, it is over. Nothing here starts a channel, an aura or a timer that
    // has to be watched every tick — the one exception is the rally, whose whole point is that the
    // Boyz walk somewhere, and even that re-paths on a timer rather than steering per tick.

    /** 'EADBUTT — short, brutal, and it costs him a little skin. */
    public static final int HEADBUTT_COOLDOWN_TICKS = 200;
    public static final double HEADBUTT_RANGE = 3.0D;
    public static final float HEADBUTT_DAMAGE = 6.0F;

    /** A bigger git headbutts harder. One step of the ladder, one more point. */
    public static final float HEADBUTT_DAMAGE_PER_STAGE = 2.0F;
    public static final int HEADBUTT_STUN_TICKS = 60;
    public static final double HEADBUTT_KNOCKBACK = 0.9D;

    /** KRUMP FIRST as a button: a run at something, not a flight over it. */
    public static final int CHARGE_COOLDOWN_TICKS = 300;
    public static final double CHARGE_POWER = 1.35D;

    /** How far ahead the destination is tested. The charge is refused if that box is not empty. */
    public static final double CHARGE_TEST_DISTANCE = 3.0D;
    public static final double CHARGE_HIT_RANGE = 4.0D;
    public static final float CHARGE_DAMAGE = 5.0F;

    /** WAAAAAAAAAGH! — the whole bar, spent at once, on everyone who can hear it. */
    public static final int WAAAGH_COOLDOWN_TICKS = 1200;
    public static final double WAAAGH_RADIUS = 12.0D;
    public static final double WAAAGH_RADIUS_PER_LISTEN = 3.0D;
    public static final int WAAAGH_DURATION_TICKS = 300;
    public static final int WAAAGH_DURATION_PER_LISTEN = 60;

    /** A hard ceiling on how many things one shout may touch, whatever the radius says. */
    public static final int WAAAGH_MAX_TARGETS = 40;

    /** I'Z DA BOSS — point at a git, and the Boyz go for it. */
    public static final int ORDER_COOLDOWN_TICKS = 200;
    public static final double ORDER_TARGET_RANGE = 32.0D;
    public static final double ORDER_RADIUS = 20.0D;
    public static final double ORDER_RADIUS_PER_LISTEN = 4.0D;
    public static final int ORDER_MAX_BOYZ = 16;
    public static final int ORDER_BOYZ_PER_LISTEN = 4;

    /** BOYZ, OVER 'ERE — roughly thirty seconds of them coming to him. */
    public static final int RALLY_DURATION_TICKS = 600;

    /**
     * How rarely a rallied Boy is given a fresh path.
     *
     * <p>Two seconds. Steering a mob every tick is what turns a nice order into a server that spends
     * its budget on pathfinding, and a Boy who re-paths twice a second walks no faster than one who
     * re-paths every forty ticks.
     */
    public static final int RALLY_REPATH_TICKS = 40;

    public static final double RALLY_SPEED = 1.15D;

    /** Close enough that a Boy stops being told to come here. */
    public static final double RALLY_ARRIVED_DISTANCE = 4.0D;

    // ==================================================================== fury on the wire
    //
    // Fury moves on every blow in either direction, and it used to send the entire profile — ranks,
    // tallies, klan, body — down the wire each time. This is the small packet's throttle.

    /** Minimum ticks between two Fury packets to the same player. */
    public static final int FURY_SYNC_INTERVAL_TICKS = 10;
}
