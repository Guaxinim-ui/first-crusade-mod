package com.example.examplemod.progression.ork;

import net.minecraft.network.chat.Component;

/**
 * What a node actually does, in a sentence, at a given rank.
 *
 * <h2>Why the screen may not write these itself</h2>
 *
 * The panel has to tell a player what he is buying <i>before</i> he buys it — "agora +8%, depois
 * +12%". The numbers behind those percentages live in {@link PlayerOrkProgressionBalance} and are
 * applied in three different places (the attribute pass, the combat events, the modifiers). If the
 * screen wrote its own arithmetic, the shop sign and the till would be two independent
 * implementations of the same price, and the day somebody rebalances a node the sign is the half
 * that does not get updated.
 *
 * <p>So every figure below is read from the same constant the effect itself reads. A rebalance moves
 * the number in one file and the panel follows.
 *
 * <h2>One key per node</h2>
 *
 * The sentence is a translation key; only the numbers are computed. That is what lets the Ork
 * dialect live entirely in the lang files — the ids stay correct English, and pt_br can misspell as
 * enthusiastically as it likes without any of it reaching the code.
 *
 * <h2>Abilities say which key</h2>
 *
 * A node that grants an active ability has no percentage to show, and telling a player "desbloqueia
 * 'EADBUTT" without telling him which button that is leaves him with an ability he cannot find. The
 * four ability nodes name their key binding instead of a number.
 */
public final class PlayerOrkNodeEffects {
    private PlayerOrkNodeEffects() {
    }

    /**
     * The effect at a given rank, or null when there is nothing to say.
     *
     * <p>Null at rank 0 for an ordinary node — "you do not have this" is the panel's job to render,
     * not a sentence with a zero in it.
     */
    public static Component describe(PlayerOrkProgressionTree.Node node, int rank) {
        if (node == null) {
            return null;
        }

        // An ability is the same sentence whether it is held or not: it is a button, not a curve.
        Component ability = abilityOf(node.id());
        if (ability != null) {
            return rank > 0 ? ability : null;
        }

        if (rank <= 0) {
            return null;
        }

        return switch (node.id()) {
            // ---- BRUTAL --------------------------------------------------------
            case "harder_hitting" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.HARDER_HITTING_PER_RANK));
            case "big_choppa" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.BIG_CHOPPA_PER_RANK));
            case "krump_everything" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.KRUMP_EVERYTHING_PER_RANK));
            case "brutal_but_kunnin" -> effect(node,
                    percent(PlayerOrkProgressionBalance.BRUTAL_BUT_KUNNIN_MELEE));

            // ---- TUFF ----------------------------------------------------------
            case "thick_skin" -> effect(node, number(rank * 2.0D));
            case "stay_standing" -> effect(node, number(rank * 1.0D), percent(rank * 0.03D));
            case "doesnt_hurt_much" -> effect(node, percent(Math.min(
                    PlayerOrkProgressionBalance.DOESNT_HURT_MUCH_CAP,
                    rank * PlayerOrkProgressionBalance.DOESNT_HURT_MUCH_PER_RANK)));
            case "scrap_armour" -> effect(node, number(rank * 1.0D));
            case "thick_plate" -> effect(node, number(rank * 1.0D), number(rank * 0.5D));
            case "mega_platin" -> effect(node,
                    number(PlayerOrkProgressionBalance.MEGA_PLATIN_ARMOR),
                    number(PlayerOrkProgressionBalance.MEGA_PLATIN_TOUGHNESS),
                    percent(PlayerOrkProgressionBalance.MEGA_PLATIN_KNOCKBACK));
            case "not_dead_yet" -> effect(node,
                    number(PlayerOrkProgressionBalance.NOT_DEAD_YET_HEALTH / 2.0D),
                    number(PlayerOrkProgressionBalance.NOT_DEAD_YET_COOLDOWN_TICKS / 20.0D / 60.0D));
            case "da_biggest" -> effect(node,
                    number(PlayerOrkProgressionBalance.DA_BIGGEST_HEALTH),
                    percent(PlayerOrkProgressionBalance.DA_BIGGEST_KNOCKBACK));

            // ---- DAKKA ---------------------------------------------------------
            case "basic_dakka" -> effect(node, number(rank
                    * PlayerOrkProgressionBalance.BASIC_DAKKA_DAMAGE_PER_RANK));
            case "more_dakka" -> effect(node,
                    number(rank * PlayerOrkProgressionBalance.MORE_DAKKA_COOLDOWN_PER_RANK),
                    number(Math.max(PlayerOrkProgressionBalance.SHOOTA_MIN_COOLDOWN_TICKS,
                            PlayerOrkProgressionBalance.SHOOTA_BASE_COOLDOWN_TICKS
                                    - rank * PlayerOrkProgressionBalance
                                            .MORE_DAKKA_COOLDOWN_PER_RANK)));
            case "dakka_on_the_move" -> effect(node, number(rank
                    * PlayerOrkProgressionBalance.DAKKA_ON_THE_MOVE_SPREAD_PER_RANK));
            case "dakka_dakka_dakka" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.DAKKA_DAKKA_EXTRA_SHOT_PER_RANK));
            case "neva_enuff_dakka" -> effect(node,
                    number(PlayerOrkProgressionBalance.NEVA_ENUFF_DAKKA_DAMAGE),
                    number(PlayerOrkProgressionBalance.NEVA_ENUFF_DAKKA_COOLDOWN),
                    percent(PlayerOrkProgressionBalance.NEVA_ENUFF_DAKKA_EXTRA_SHOT));

            // ---- KUNNIN --------------------------------------------------------
            case "run_to_krump" -> effect(node, percent(rank * 0.02D));
            case "teef_is_money" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.TEEF_IS_MONEY_PER_RANK));
            case "sneaky_git" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.SNEAKY_GIT_PER_RANK));
            case "loot_it_all" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.LOOT_IT_ALL_PER_RANK));
            case "big_teef" -> effect(node, number(rank
                    * PlayerOrkProgressionBalance.BIG_TEEF_PER_ELITE));
            case "got_it_first" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.GOT_IT_FIRST_PER_RANK));
            case "run_and_hit" -> effect(node,
                    percent(PlayerOrkProgressionBalance.RUN_AND_HIT_SPEED),
                    percent(PlayerOrkProgressionBalance.RUN_AND_HIT_SPRINT_DAMAGE));
            case "kunnin_but_brutal" -> effect(node,
                    percent(PlayerOrkProgressionBalance.KUNNIN_BUT_BRUTAL_TEEF),
                    percent(PlayerOrkProgressionBalance.KUNNIN_BUT_BRUTAL_REDUCTION));

            // ---- WAAAGH --------------------------------------------------------
            case "louder_waaagh" -> effect(node, percent(rank
                    * PlayerOrkProgressionBalance.LOUDER_WAAAGH_PER_RANK));
            case "boyz_listen" -> effect(node,
                    number(rank * PlayerOrkProgressionBalance.WAAAGH_RADIUS_PER_LISTEN),
                    number(rank * PlayerOrkProgressionBalance.ORDER_BOYZ_PER_LISTEN));
            case "da_greenest" -> effect(node,
                    percent(PlayerOrkProgressionBalance.DA_GREENEST_FURY),
                    number(PlayerOrkProgressionBalance.DA_GREENEST_FURY_FLOOR));

            // The root is held by everyone and does nothing; saying so is better than a blank line.
            case PlayerOrkProgressionTree.ROOT_ID -> effect(node);

            default -> null;
        };
    }

    /** The effect one rank further up, or null at the cap. */
    public static Component describeNext(PlayerOrkProgressionTree.Node node, int rank) {
        if (node == null || node.isEvolution() || rank >= node.maxRank()) {
            return null;
        }

        return describe(node, rank + 1);
    }

    /**
     * The ability a node grants and the key that fires it, or null.
     *
     * <p>Resolved off {@link PlayerOrkAbility}, so a node cannot claim an ability the manager does
     * not route.
     */
    public static Component abilityOf(String nodeId) {
        for (PlayerOrkAbility ability : PlayerOrkAbility.values()) {
            if (ability.nodeId().equals(nodeId)) {
                return Component.translatable("ork.firstcrusade.effect.ability",
                        ability.displayName(),
                        Component.translatable("key.firstcrusade.ork_" + ability.key()));
            }
        }

        // BOYZ, OVER 'ERE is not its own button — it is what BOSS ORDER does with no target — so it
        // is named here rather than in the ability enum, which only holds things a key can fire.
        if ("boyz_come_here".equals(nodeId)) {
            return Component.translatable("ork.firstcrusade.effect.boyz_come_here",
                    Component.translatable("key.firstcrusade.ork_order"));
        }

        return null;
    }

    // ==================================================================== formatting

    private static Component effect(PlayerOrkProgressionTree.Node node, Object... args) {
        return Component.translatable("ork.firstcrusade.effect." + node.id(), args);
    }

    /** A fraction as a whole-number percentage: 0.045 becomes "4.5", 0.20 becomes "20". */
    private static String percent(double fraction) {
        return number(fraction * 100.0D);
    }

    /**
     * A number without a pointless decimal.
     *
     * <p>"+2 vida" and "+2.0 vida" say the same thing, and only one of them looks like a readout
     * somebody wrote on purpose.
     */
    private static String number(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;

        if (Math.abs(rounded - Math.rint(rounded)) < 1.0E-6D) {
            return String.valueOf((long) Math.rint(rounded));
        }

        return String.valueOf(rounded);
    }
}
