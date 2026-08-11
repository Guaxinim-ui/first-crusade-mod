package com.example.examplemod.progression.ork;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * An evolution gate, as a list of lines a player can read.
 *
 * <h2>Why a checklist and not a price</h2>
 *
 * Every other node in the tree answers "what does it cost" with a number of Teef. An evolution
 * answers with a list of things the player has to have <i>done</i>, most of which he is part of the
 * way through. Showing that as a cost would be a lie — there is nothing to save up — and showing
 * only the first unmet requirement, which is all the server's refusal message can say, tells a
 * player he needs 400 Krumpagem without mentioning the sixty kills waiting behind it.
 *
 * <h2>It does not decide anything</h2>
 *
 * This is a renderer's input. The gate is enforced by {@link PlayerOrkProgressionRequirements}, on
 * the server, and both read the same {@link PlayerOrkProgressionRequirements.Gate} table — so the
 * list a player ticks off is the list he is actually being measured against, rather than a second
 * description of it that drifts on the next rebalance.
 */
public final class PlayerOrkEvolutionChecklist {
    private PlayerOrkEvolutionChecklist() {
    }

    /**
     * One requirement.
     *
     * @param label what it is, already translated
     * @param have  where the player is
     * @param need  where he has to get to
     */
    public record Line(Component label, int have, int need) {
        public boolean met() {
            return this.have >= this.need;
        }
    }

    /**
     * Builds the list for a rung.
     *
     * <p>Requirements the rung does not have are left out rather than shown as "0/0": a line that is
     * always ticked teaches the reader to stop reading lines.
     *
     * @param globalTier the global WAAAGH tier as the client was last told
     */
    public static List<Line> of(PlayerOrkProgressionProfile ork, PlayerOrkEvolutionStage target,
                                int globalTier) {
        List<Line> lines = new ArrayList<>();

        PlayerOrkProgressionRequirements.Gate gate =
                PlayerOrkProgressionRequirements.gateFor(target);

        if (ork == null || gate == null) {
            return lines;
        }

        add(lines, "krump", ork.krumpScore(), gate.krump());
        add(lines, "kills", ork.validKills(), gate.kills());
        add(lines, "elites", ork.eliteKills(), gate.elites());
        add(lines, "ranks", ork.totalPurchasedRanks(), gate.ranks());
        add(lines, "spent", ork.totalTeefSpent(), gate.teefSpent());
        add(lines, "victories", ork.majorVictories(), gate.victories());

        if (gate.distinctBoyNodes() > 0) {
            add(lines, "nodes",
                    PlayerOrkProgressionRequirements.countDistinctBought(
                            ork, PlayerOrkEvolutionStage.ORK_BOY),
                    gate.distinctBoyNodes());
        }

        add(lines, "waaagh", globalTier, gate.globalTier());

        return lines;
    }

    /** True when every line is ticked. The server still has the last word. */
    public static boolean complete(List<Line> lines) {
        for (Line line : lines) {
            if (!line.met()) {
                return false;
            }
        }

        return true;
    }

    private static void add(List<Line> lines, String key, int have, int need) {
        if (need <= 0) {
            return;
        }

        lines.add(new Line(Component.translatable("ork.firstcrusade.gate." + key), have, need));
    }
}
