package com.example.examplemod.progression.ork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;

/**
 * Da WAAAGH! tree — thirty-eight nodes an Ork can spend his Teef on.
 *
 * <h2>Not the Astartes shape</h2>
 *
 * The Imperial tree marches: three skills, an implant, three skills, an implant, five ranks each.
 * This one does not. Ordinary nodes cap at <b>three</b> ranks rather than five, because five ranks
 * of anything reads as a syllabus; branches cross each other; and several nodes want two different
 * parents, so there is no single road up. An Ork chooses what kind of Ork he is, and two players
 * who both reached Warboss should have got there wearing different things.
 *
 * <h2>Evolution is not bought</h2>
 *
 * The five {@link #isEvolution} nodes cost no Teef at all. They are gated on what the player has
 * <i>done</i> — Krumpagem, kills, elites, ranks held — and checked by
 * {@link PlayerOrkProgressionRequirements}. Teef buys skills; only krumping makes you bigger.
 *
 * <h2>The tree is code, not data</h2>
 *
 * Same call as the Astartes tree: both sides read this class, so nothing about the tree's shape ever
 * travels on the wire, and a client cannot be told about a node the server does not have.
 */
public final class PlayerOrkProgressionTree {
    private PlayerOrkProgressionTree() {
    }

    /** The free root every Ork starts holding. */
    public static final String ROOT_ID = "ork_boy_root";

    /**
     * One node.
     *
     * @param id          stored id — always correct English, never Ork-spelled. The misspellings are
     *                    an identity for the <i>display</i>; an id with a typo in it is a bug that
     *                    survives into every save file.
     * @param branch      which of the five it belongs to
     * @param maxRank     3 for an ordinary node, 1 for a one-shot
     * @param cost        Teef per rank, indexed by the rank being bought; empty for evolutions
     * @param parents     every id that must be held at rank 1 first. More than one means the node
     *                    genuinely sits where two branches meet.
     * @param stage       the stage this node <i>grants</i>, or null for an ordinary skill
     * @param minimumStage the stage the player must already be for this node to be reachable
     */
    public record Node(String id, PlayerOrkSkillBranch branch, int maxRank, int[] cost,
                       List<String> parents, PlayerOrkEvolutionStage stage,
                       PlayerOrkEvolutionStage minimumStage) {

        public boolean isEvolution() {
            return this.stage != null;
        }

        public int costFor(int rankBeingBought) {
            if (this.cost.length == 0) {
                return 0;
            }
            int index = Math.max(0, Math.min(this.cost.length - 1, rankBeingBought - 1));
            return this.cost[index];
        }

        public Component displayName() {
            return Component.translatable("ork.firstcrusade.node." + this.id);
        }

        public Component description() {
            return Component.translatable("ork.firstcrusade.node." + this.id + ".desc");
        }
    }

    private static final Map<String, Node> NODES = new LinkedHashMap<>();

    private static final int[] NORMAL = PlayerOrkProgressionBalance.NODE_RANK_COST;
    private static final int[] NONE = new int[0];

    private static void node(String id, PlayerOrkSkillBranch branch, int maxRank, int[] cost,
                             PlayerOrkEvolutionStage minimumStage, String... parents) {
        NODES.put(id, new Node(id, branch, maxRank, cost, List.of(parents), null, minimumStage));
    }

    private static void evolution(String id, PlayerOrkEvolutionStage grants,
                                  PlayerOrkEvolutionStage from, String... parents) {
        NODES.put(id, new Node(id, PlayerOrkSkillBranch.WAAAGH, 1, NONE, List.of(parents),
                grants, from));
    }

    private static void special(String id, PlayerOrkSkillBranch branch, int cost,
                                PlayerOrkEvolutionStage minimumStage, String... parents) {
        NODES.put(id, new Node(id, branch, 1, new int[]{cost}, List.of(parents), null, minimumStage));
    }

    static {
        PlayerOrkEvolutionStage boy = PlayerOrkEvolutionStage.ORK_BOY;
        PlayerOrkEvolutionStage bigBoy = PlayerOrkEvolutionStage.BIG_BOY;
        PlayerOrkEvolutionStage nob = PlayerOrkEvolutionStage.ORK_NOB;
        PlayerOrkEvolutionStage bigNob = PlayerOrkEvolutionStage.BIG_NOB;
        PlayerOrkEvolutionStage warboss = PlayerOrkEvolutionStage.WARBOSS;

        // ---- the root ------------------------------------------------------------
        NODES.put(ROOT_ID, new Node(ROOT_ID, PlayerOrkSkillBranch.WAAAGH, 1, NONE,
                List.of(), null, boy));

        // ---- Boy -----------------------------------------------------------------
        node("harder_hitting", PlayerOrkSkillBranch.BRUTAL, 3, NORMAL, boy, ROOT_ID);
        node("thick_skin", PlayerOrkSkillBranch.TUFF, 3, NORMAL, boy, ROOT_ID);
        node("basic_dakka", PlayerOrkSkillBranch.DAKKA, 3, NORMAL, boy, ROOT_ID);
        node("run_to_krump", PlayerOrkSkillBranch.KUNNIN, 3, NORMAL, boy, ROOT_ID);

        evolution("become_big_boy", bigBoy, boy, ROOT_ID);

        // ---- Big Boy -------------------------------------------------------------
        node("doesnt_hurt_much", PlayerOrkSkillBranch.TUFF, 3, NORMAL, bigBoy, "thick_skin");
        special("eadbutt", PlayerOrkSkillBranch.BRUTAL,
                PlayerOrkProgressionBalance.SPECIAL_COST_SMALL, bigBoy, "harder_hitting");
        node("more_dakka", PlayerOrkSkillBranch.DAKKA, 3, NORMAL, bigBoy, "basic_dakka");
        node("teef_is_money", PlayerOrkSkillBranch.KUNNIN, 3, NORMAL, bigBoy, "run_to_krump");
        node("louder_waaagh", PlayerOrkSkillBranch.WAAAGH, 3, NORMAL, bigBoy, ROOT_ID);

        evolution("become_nob", nob, bigBoy, "become_big_boy");

        // ---- Nob -----------------------------------------------------------------
        node("big_choppa", PlayerOrkSkillBranch.BRUTAL, 3, NORMAL, nob, "harder_hitting");
        node("scrap_armour", PlayerOrkSkillBranch.TUFF, 3, NORMAL, nob, "doesnt_hurt_much");
        node("dakka_on_the_move", PlayerOrkSkillBranch.DAKKA, 3, NORMAL, nob, "more_dakka");
        node("sneaky_git", PlayerOrkSkillBranch.KUNNIN, 3, NORMAL, nob, "run_to_krump");
        node("stay_standing", PlayerOrkSkillBranch.TUFF, 3, NORMAL, nob, "thick_skin");

        // Where two branches genuinely meet: a charge is being fast and being brutal at once.
        special("krump_first", PlayerOrkSkillBranch.BRUTAL,
                PlayerOrkProgressionBalance.SPECIAL_COST_MEDIUM, nob, "run_to_krump", "harder_hitting");

        special("im_da_boss", PlayerOrkSkillBranch.WAAAGH,
                PlayerOrkProgressionBalance.SPECIAL_COST_MEDIUM, nob, "louder_waaagh");
        special("waaagh_roar", PlayerOrkSkillBranch.WAAAGH,
                PlayerOrkProgressionBalance.SPECIAL_COST_LARGE, nob, "louder_waaagh");

        node("loot_it_all", PlayerOrkSkillBranch.KUNNIN, 3, NORMAL, nob, "teef_is_money");
        node("big_teef", PlayerOrkSkillBranch.KUNNIN, 3, NORMAL, nob, "teef_is_money");
        special("boyz_come_here", PlayerOrkSkillBranch.WAAAGH,
                PlayerOrkProgressionBalance.SPECIAL_COST_SMALL, nob, "im_da_boss");

        evolution("become_big_nob", bigNob, nob, "become_nob");

        // ---- Big Nob -------------------------------------------------------------
        node("thick_plate", PlayerOrkSkillBranch.TUFF, 3, NORMAL, bigNob, "scrap_armour");
        node("krump_everything", PlayerOrkSkillBranch.BRUTAL, 3, NORMAL, bigNob, "big_choppa");
        node("dakka_dakka_dakka", PlayerOrkSkillBranch.DAKKA, 3, NORMAL, bigNob, "dakka_on_the_move");
        special("run_and_hit", PlayerOrkSkillBranch.BRUTAL,
                PlayerOrkProgressionBalance.SPECIAL_COST_MEDIUM, bigNob, "krump_first", "sneaky_git");
        special("not_dead_yet", PlayerOrkSkillBranch.TUFF,
                PlayerOrkProgressionBalance.SPECIAL_COST_LARGE, bigNob, "stay_standing");
        node("got_it_first", PlayerOrkSkillBranch.KUNNIN, 3, NORMAL, bigNob, "loot_it_all");
        node("boyz_listen", PlayerOrkSkillBranch.WAAAGH, 3, NORMAL, bigNob, "im_da_boss");

        // Reserved for real Mega Armour. It gates on nothing that does not exist, and grants a
        // handling bonus rather than an item, so the tree does not ship a broken set to fill a slot.
        special("mega_platin", PlayerOrkSkillBranch.TUFF,
                PlayerOrkProgressionBalance.SPECIAL_COST_HUGE, bigNob, "thick_plate");

        evolution("become_warboss", warboss, bigNob, "become_big_nob");

        // ---- endgame -------------------------------------------------------------
        special("da_biggest", PlayerOrkSkillBranch.TUFF,
                PlayerOrkProgressionBalance.SPECIAL_COST_HUGE, warboss, "thick_plate");
        special("da_greenest", PlayerOrkSkillBranch.WAAAGH,
                PlayerOrkProgressionBalance.SPECIAL_COST_HUGE, warboss, "waaagh_roar");
        special("brutal_but_kunnin", PlayerOrkSkillBranch.BRUTAL,
                PlayerOrkProgressionBalance.SPECIAL_COST_HUGE, warboss, "run_and_hit");
        special("kunnin_but_brutal", PlayerOrkSkillBranch.KUNNIN,
                PlayerOrkProgressionBalance.SPECIAL_COST_HUGE, warboss, "im_da_boss", "got_it_first");
        special("neva_enuff_dakka", PlayerOrkSkillBranch.DAKKA,
                PlayerOrkProgressionBalance.SPECIAL_COST_HUGE, warboss, "dakka_dakka_dakka");
    }

    // ==================================================================== readers

    public static Node node(String id) {
        return id == null ? null : NODES.get(id);
    }

    public static List<Node> all() {
        return new ArrayList<>(NODES.values());
    }

    /** Every node of one branch, in declaration order. Used by the screen's column layout. */
    public static List<Node> ofBranch(PlayerOrkSkillBranch branch) {
        List<Node> out = new ArrayList<>();
        for (Node node : NODES.values()) {
            if (node.branch() == branch) {
                out.add(node);
            }
        }
        return out;
    }

    /** The node that grants a stage, or null. */
    public static Node evolutionTo(PlayerOrkEvolutionStage stage) {
        for (Node node : NODES.values()) {
            if (node.stage() == stage) {
                return node;
            }
        }
        return null;
    }

    public static int size() {
        return NODES.size();
    }

    /**
     * Fails loudly at load if the tree is malformed.
     *
     * <p>Called from the same place the Astartes tree's validator is. A parent that does not exist
     * or a node that grants a stage nothing leads to would otherwise be a dead branch nobody notices
     * until a player reports that he cannot buy anything.
     */
    public static void validate() {
        for (Node node : NODES.values()) {
            for (String parent : node.parents()) {
                if (!NODES.containsKey(parent)) {
                    throw new IllegalStateException(
                            "Ork tree: node '" + node.id() + "' names a parent that does not exist: " + parent);
                }
            }

            if (node.maxRank() < 1) {
                throw new IllegalStateException("Ork tree: node '" + node.id() + "' has no ranks");
            }

            if (!node.isEvolution() && !node.id().equals(ROOT_ID) && node.cost().length == 0) {
                throw new IllegalStateException(
                        "Ork tree: node '" + node.id() + "' is buyable but free");
            }
        }

        for (PlayerOrkEvolutionStage stage : PlayerOrkEvolutionStage.values()) {
            if (stage != PlayerOrkEvolutionStage.ORK_BOY && evolutionTo(stage) == null) {
                throw new IllegalStateException("Ork tree: nothing grants " + stage.name());
            }
        }
    }
}
