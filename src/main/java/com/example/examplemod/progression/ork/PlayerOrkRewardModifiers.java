package com.example.examplemod.progression.ork;

import com.example.examplemod.OrkClan;

/**
 * What the KUNNIN branch is actually worth: Teef and loot.
 *
 * <h2>Why not in the combat modifiers</h2>
 *
 * {@link PlayerOrkCombatModifiers} answers questions asked at the moment of a blow, and is read by
 * the client so a gun's cooldown matches. Nothing here is ever asked on the client — a reward is
 * decided by the server, applied by the server, and told to the player afterwards. Keeping the two
 * apart means nothing that touches currency is even reachable from client code.
 *
 * <h2>Every multiplier lands once</h2>
 *
 * Teef enters the game in three places — stashing teeth, a major victory, a destroyed Core — and all
 * three call {@link #scaleTeef}. A bonus written at one of those call sites would have been a bonus
 * missing from the other two, which is exactly the bug that makes a node feel broken to the player
 * who bought it.
 */
public final class PlayerOrkRewardModifiers {
    private PlayerOrkRewardModifiers() {
    }

    /**
     * Scales a Teef payment by everything that makes an Ork better at getting paid.
     *
     * <p>Rounded down, with a floor of one on any positive award: a node that turns 1 Teef into 1
     * Teef reads as broken even when the arithmetic is right, and a node that turns it into 0 is
     * worse than broken.
     */
    public static int scaleTeef(PlayerOrkProgressionProfile ork, int amount) {
        if (amount <= 0) {
            return 0;
        }

        double multiplier = 1.0D
                + ork.rank("teef_is_money") * PlayerOrkProgressionBalance.TEEF_IS_MONEY_PER_RANK;

        if (ork.rank("kunnin_but_brutal") > 0) {
            multiplier += PlayerOrkProgressionBalance.KUNNIN_BUT_BRUTAL_TEEF;
        }

        if (ork.clan() == OrkClan.BAD_MOONS) {
            multiplier += PlayerOrkProgressionBalance.BAD_MOONS_TEEF;
        }

        return (int) Math.max(1L, (long) Math.floor(amount * multiplier));
    }

    /**
     * BIG TEEF — teeth pulled straight off something worth killing.
     *
     * <p>Paid on elites only, and deliberately flat rather than a percentage: it is a reward for
     * picking a hard fight, so it should not also scale with how good the player already is at being
     * paid. It goes through {@link #scaleTeef} at the call site like every other award.
     */
    public static int bigTeefBonus(PlayerOrkProgressionProfile ork, boolean elite) {
        if (!elite) {
            return 0;
        }

        return ork.rank("big_teef") * PlayerOrkProgressionBalance.BIG_TEEF_PER_ELITE;
    }

    /**
     * The chance a corpse gives up its drops a second time.
     *
     * <p>Capped well below certainty. Salvage that always fires is not salvage, it is a doubled loot
     * table, and a doubled loot table is an economy change nobody asked for.
     */
    public static double salvageChance(PlayerOrkProgressionProfile ork) {
        double chance = ork.rank("loot_it_all") * PlayerOrkProgressionBalance.LOOT_IT_ALL_PER_RANK;

        if (ork.clan() == OrkClan.DEATHSKULLS) {
            chance += PlayerOrkProgressionBalance.DEATHSKULLS_SALVAGE;
        }

        return Math.min(PlayerOrkProgressionBalance.SALVAGE_CHANCE_CAP, chance);
    }

    /** GOT IT FIRST — the chance of prising teeth off the body before anyone else arrives. */
    public static double toothPickingChance(PlayerOrkProgressionProfile ork) {
        return Math.min(PlayerOrkProgressionBalance.SALVAGE_CHANCE_CAP,
                ork.rank("got_it_first") * PlayerOrkProgressionBalance.GOT_IT_FIRST_PER_RANK);
    }
}
