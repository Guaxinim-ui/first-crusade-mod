package com.example.examplemod.progression.ork;

import com.example.examplemod.PlayerFaction;
import com.example.examplemod.PlayerFactionData;
import com.example.examplemod.WaaaghOverlordManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every reason an Ork may be told no.
 *
 * <h2>All of it on the server</h2>
 *
 * The client sends an id and nothing else — no cost, no rank, no claim about what it has. Everything
 * that decides the answer is looked up here from the tree and the profile, so a crafted packet can
 * ask for a node and still be refused for all the same reasons a legitimate click would be.
 */
public final class PlayerOrkProgressionRequirements {
    private PlayerOrkProgressionRequirements() {
    }

    /** Why not, or empty when it is allowed. Empty means yes; anything else is the message. */
    public record Result(Component reason) {
        public boolean ok() {
            return this.reason == null;
        }

        public static Result yes() {
            return new Result(null);
        }

        public static Result no(String key, Object... args) {
            return new Result(Component.translatable(key, args));
        }
    }

    /** The gate that guards the whole system: only Orks krump. */
    public static boolean isOrk(ServerPlayer player) {
        return PlayerFactionData.get(player.serverLevel()).getFaction(player.getUUID())
                == PlayerFaction.ORKS;
    }

    // ==================================================================== buying a rank

    public static Result checkBuy(ServerPlayer player, PlayerOrkProgressionProfile ork,
                                  PlayerOrkProgressionTree.Node node) {
        if (!isOrk(player)) {
            return Result.no("msg.firstcrusade.ork.not_ork");
        }

        return checkBuyRules(ork, node);
    }

    /**
     * Every reason to refuse a purchase except "you are not an Ork".
     *
     * <h2>Why it is split out</h2>
     *
     * The screen has to grey out a node it cannot buy and say why, and it runs on the client where
     * there is no {@link ServerPlayer} to ask about factions. The alternative was for the screen to
     * re-implement these six checks, which is six chances for the sign in the shop to disagree with
     * the till — a node the panel calls affordable and the server refuses is a bug report.
     *
     * <p>The faction check stays outside because it is the one thing the client genuinely cannot
     * answer for itself, and because the client asking it would be pointless: a client showing the
     * WAAAGH tree at all has already been told it is an Ork by the server.
     */
    public static Result checkBuyRules(PlayerOrkProgressionProfile ork,
                                       PlayerOrkProgressionTree.Node node) {
        if (node.isEvolution()) {
            // Evolutions are earned, never bought. Routing one through the buy verb would be a way
            // to skip the gates entirely, so it is refused here rather than priced at zero.
            return Result.no("msg.firstcrusade.ork.not_for_sale");
        }

        int held = ork.rank(node.id());
        if (held >= node.maxRank()) {
            return Result.no("msg.firstcrusade.ork.maxed");
        }

        if (!ork.stage().isAtLeast(node.minimumStage())) {
            return Result.no("msg.firstcrusade.ork.too_small", node.minimumStage().displayName());
        }

        // A Nob without a klan is a Nob who has not finished becoming one. Blocking the purchase
        // rather than the screen is what makes it actually required: the client can be modified,
        // the server cannot.
        if (ork.stage().isAtLeast(PlayerOrkEvolutionStage.ORK_NOB) && !ork.hasClan()) {
            return Result.no("msg.firstcrusade.ork.pick_klan_first");
        }

        for (String parent : node.parents()) {
            if (ork.rank(parent) < 1) {
                PlayerOrkProgressionTree.Node parentNode = PlayerOrkProgressionTree.node(parent);
                return Result.no("msg.firstcrusade.ork.needs_first",
                        parentNode == null ? parent : parentNode.displayName());
            }
        }

        int cost = node.costFor(held + 1);
        if (ork.teef() < cost) {
            return Result.no("msg.firstcrusade.ork.no_teef", cost, ork.teef());
        }

        return Result.yes();
    }

    // ==================================================================== growing

    /**
     * Everything one rung of the ladder asks for.
     *
     * <h2>Why this is a record and not six arguments</h2>
     *
     * The screen has to draw this gate as a checklist — "krumpagem 312/400, gitz 45/60" — long
     * before the player can pass it, and the screen runs on the client where there is no
     * {@link ServerPlayer} to ask. Before this existed the numbers lived only inside a private
     * method here, so the only way to show them was to write them out a second time in the screen.
     * Two copies of an evolution gate is two copies that drift, and the one the player reads would
     * have been the one nobody enforces.
     *
     * <p>So the table is public data, both sides read it, and the <b>server is still the only thing
     * that decides</b>: the client draws this, and {@link #checkEvolve} refuses with it.
     *
     * @param distinctBoyNodes how many <i>different</i> first-tier skills are needed — zero for
     *                         every rung but the first
     * @param globalTier       the global WAAAGH tier required, zero when the rung does not care
     */
    public record Gate(int krump, int kills, int elites, int ranks, int teefSpent, int victories,
                       int distinctBoyNodes, int globalTier) {
    }

    /** What the given rung costs, or null for a stage nothing leads to. */
    public static Gate gateFor(PlayerOrkEvolutionStage target) {
        if (target == null) {
            return null;
        }

        return switch (target) {
            case BIG_BOY -> new Gate(
                    PlayerOrkProgressionBalance.BIG_BOY_KRUMP,
                    PlayerOrkProgressionBalance.BIG_BOY_KILLS,
                    0, 0, 0, 0,
                    PlayerOrkProgressionBalance.BIG_BOY_NODES, 0);
            case ORK_NOB -> new Gate(
                    PlayerOrkProgressionBalance.NOB_KRUMP,
                    PlayerOrkProgressionBalance.NOB_KILLS,
                    PlayerOrkProgressionBalance.NOB_ELITES,
                    PlayerOrkProgressionBalance.NOB_RANKS,
                    PlayerOrkProgressionBalance.NOB_TEEF_SPENT, 0, 0, 0);
            case BIG_NOB -> new Gate(
                    PlayerOrkProgressionBalance.BIG_NOB_KRUMP,
                    PlayerOrkProgressionBalance.BIG_NOB_KILLS,
                    PlayerOrkProgressionBalance.BIG_NOB_ELITES,
                    PlayerOrkProgressionBalance.BIG_NOB_RANKS,
                    0, 0, 0, 0);
            case WARBOSS -> new Gate(
                    PlayerOrkProgressionBalance.WARBOSS_KRUMP,
                    PlayerOrkProgressionBalance.WARBOSS_KILLS,
                    PlayerOrkProgressionBalance.WARBOSS_ELITES,
                    PlayerOrkProgressionBalance.WARBOSS_RANKS,
                    0,
                    PlayerOrkProgressionBalance.WARBOSS_MAJOR_VICTORIES,
                    0,
                    PlayerOrkProgressionBalance.WARBOSS_GLOBAL_TIER);
            // Nothing grants ORK_BOY: it is where everybody starts.
            default -> null;
        };
    }

    /**
     * Whether this player has done enough to become the next thing.
     *
     * <p>Each gate reads what he has <i>done</i> and never what he has spent, except for the one
     * place the design asks for it (Teef spent, at Nob) — an Ork who hoarded every tooth he ever
     * found has not proved anything by hoarding.
     */
    public static Result checkEvolve(ServerPlayer player, PlayerOrkProgressionProfile ork,
                                     PlayerOrkEvolutionStage target) {
        if (!isOrk(player)) {
            return Result.no("msg.firstcrusade.ork.not_ork");
        }

        PlayerOrkEvolutionStage next = ork.stage().next();
        if (next != target) {
            // No skipping a rung, and no going back down one.
            return Result.no("msg.firstcrusade.ork.wrong_step");
        }

        Gate gate = gateFor(target);
        if (gate == null) {
            return Result.no("msg.firstcrusade.ork.wrong_step");
        }

        Result numbers = gate(ork, gate);
        if (!numbers.ok()) {
            return numbers;
        }

        // The one gate that counts distinct NODES rather than ranks. The design asks for three
        // different first-tier skills; running that number through the ordinary rank check — which
        // is what shipped — let three ranks of one skill satisfy it, so a player could reach Big Boy
        // having learned exactly one thing. The requirement asks him to be broad, not deep.
        if (gate.distinctBoyNodes() > 0) {
            int distinct = countDistinctBought(ork, PlayerOrkEvolutionStage.ORK_BOY);
            if (distinct < gate.distinctBoyNodes()) {
                return Result.no("msg.firstcrusade.ork.need_nodes",
                        gate.distinctBoyNodes(), distinct);
            }
        }

        // The only requirement that looks outside the player. It reads the existing global WAAAGH
        // rather than a second copy of it, and it is a confirmation and not a substitute: every
        // personal number above still had to be met, so one player driving the tide cannot crown
        // another.
        if (gate.globalTier() > 0) {
            int tier = WaaaghOverlordManager.getTier(player.serverLevel());
            if (tier < gate.globalTier()) {
                return Result.no("msg.firstcrusade.ork.need_waaagh", gate.globalTier(), tier);
            }
        }

        return Result.yes();
    }

    /**
     * How many different buyable nodes of one tier the player holds at rank 1 or better.
     *
     * <p>The root and the evolutions are excluded: neither is bought, and counting them would let a
     * player who has learned nothing still score.
     */
    public static int countDistinctBought(PlayerOrkProgressionProfile ork,
                                          PlayerOrkEvolutionStage tier) {
        int count = 0;

        for (PlayerOrkProgressionTree.Node node : PlayerOrkProgressionTree.all()) {
            if (node.isEvolution() || node.id().equals(PlayerOrkProgressionTree.ROOT_ID)) {
                continue;
            }
            if (node.minimumStage() == tier && ork.rank(node.id()) >= 1) {
                count++;
            }
        }

        return count;
    }

    /**
     * The numeric half of a rung, checked in the order the player is most likely to be short.
     *
     * <p>Destroying Imperial positions is folded into the personal counts rather than made a
     * separate hard requirement — a world with no Imperial Core in reach would otherwise be a world
     * where nobody can ever be Warboss, and that is a design that breaks on somebody's save.
     */
    private static Result gate(PlayerOrkProgressionProfile ork, Gate gate) {
        if (ork.krumpScore() < gate.krump()) {
            return Result.no("msg.firstcrusade.ork.need_krump", gate.krump(), ork.krumpScore());
        }
        if (ork.validKills() < gate.kills()) {
            return Result.no("msg.firstcrusade.ork.need_kills", gate.kills(), ork.validKills());
        }
        if (ork.eliteKills() < gate.elites()) {
            return Result.no("msg.firstcrusade.ork.need_elites", gate.elites(), ork.eliteKills());
        }
        if (ork.totalPurchasedRanks() < gate.ranks()) {
            // Purchased, not total: the root and the evolutions are free and must not count toward
            // a requirement that is asking how much the player has trained.
            return Result.no("msg.firstcrusade.ork.need_ranks", gate.ranks(),
                    ork.totalPurchasedRanks());
        }
        if (ork.totalTeefSpent() < gate.teefSpent()) {
            return Result.no("msg.firstcrusade.ork.need_spent", gate.teefSpent(),
                    ork.totalTeefSpent());
        }
        if (ork.majorVictories() < gate.victories()) {
            return Result.no("msg.firstcrusade.ork.need_victories", gate.victories(),
                    ork.majorVictories());
        }

        return Result.yes();
    }

    // ==================================================================== clan

    /** A clan is picked once, at Nob, and never traded. */
    public static Result checkClan(ServerPlayer player, PlayerOrkProgressionProfile ork) {
        if (!isOrk(player)) {
            return Result.no("msg.firstcrusade.ork.not_ork");
        }
        if (!ork.stage().isAtLeast(PlayerOrkEvolutionStage.ORK_NOB)) {
            return Result.no("msg.firstcrusade.ork.clan_too_small");
        }
        if (ork.hasClan()) {
            return Result.no("msg.firstcrusade.ork.clan_already");
        }

        return Result.yes();
    }
}
