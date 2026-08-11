package com.example.examplemod.progression;

import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * One node on the tree, as data.
 *
 * <p>Everything the server needs to gate it, the client needs to draw it and the tooltip needs to
 * describe it is in this record. Nothing here executes: see {@link PlayerProgressionEffect}.
 *
 * @param id            stable string id — it is what goes into NBT, so it must never be renamed
 * @param cycle         which of the twelve cycles this belongs to (0 for the root and the ascension)
 * @param branch        discipline, which decides colour and glyph
 * @param maxRanks      5 for a skill, 1 for the root, an implant and the ascension
 * @param prerequisites ids that must be at rank 1 before this one may be bought
 * @param effect        what buying it does
 * @param valuePerRank  the effect's magnitude for one rank; multiply by rank for the total
 * @param x             column in tree space (see {@link PlayerProgressionTree})
 * @param y             row in tree space, increasing downward
 * @param implant       true when this is one of the twelve surgeries
 * @param ascension     true for the single final node
 */
public record PlayerSkillNodeDefinition(
        String id,
        int cycle,
        PlayerSkillBranch branch,
        int maxRanks,
        List<String> prerequisites,
        PlayerProgressionEffect effect,
        double valuePerRank,
        int x,
        int y,
        boolean implant,
        boolean ascension,
        PlayerProgressionIcon icon) {

    /**
     * The picture this node wears, with the discipline's own as the floor.
     *
     * <p>Carried as a field rather than resolved by a {@code switch} somewhere in the renderer: the
     * tree is the thing that knows a Multi-lung looks like lungs, and the screen should only have to
     * ask. A node built without one still draws — it falls back to its branch — so adding a skill
     * never means drawing anything.
     */
    public PlayerProgressionIcon icon() {
        return this.icon == null ? this.branch.defaultIcon() : this.icon;
    }

    /** Doctrine cost of the given rank (1-based), per the progression's fixed price ladder. */
    public int costOfRank(int rank) {
        if (this.implant || this.ascension) {
            return PlayerProgressionBalance.IMPLANT_POINT_COST;
        }

        return switch (rank) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            default -> 3;
        };
    }

    /** Total doctrine spent to take this node from nothing to {@code rank}. */
    public int cumulativeCost(int rank) {
        int total = 0;
        for (int r = 1; r <= rank; r++) {
            total += costOfRank(r);
        }
        return total;
    }

    public Component displayName() {
        return Component.translatable("node.firstcrusade." + this.id);
    }

    public Component description() {
        return Component.translatable("node.firstcrusade." + this.id + ".desc");
    }

    /** True for the free starting node, which nobody buys. */
    public boolean isRoot() {
        return PlayerProgressionTree.ROOT_ID.equals(this.id);
    }

    /** A node bigger than a skill: implants and the ascension draw larger and gold-bordered. */
    public boolean isMajor() {
        return this.implant || this.ascension;
    }
}
