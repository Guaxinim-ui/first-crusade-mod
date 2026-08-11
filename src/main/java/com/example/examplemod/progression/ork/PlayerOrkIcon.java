package com.example.examplemod.progression.ork;

import com.example.examplemod.ExampleMod;

import net.minecraft.resources.ResourceLocation;

/**
 * Every picture the WAAAGH tree draws, and the only place their paths are written.
 *
 * <h2>Its own set, not the Imperial one</h2>
 *
 * {@code PlayerProgressionIcon} owns the red-and-gold family; this owns the green-and-rust one. They
 * are separate enums pointing at separate folders because they are separate visual languages — the
 * day they shared a folder would be the day somebody reused an aquila for a Nob.
 *
 * <h2>Most nodes do not have their own</h2>
 *
 * Nineteen pictures for thirty-eight nodes, on purpose. An ordinary node falls back to its
 * <b>branch</b> icon through {@link #of}, which is what lets a designer add a skill to the tree
 * without drawing anything. Only the nodes the screen shows large — the abilities, the capstones,
 * the ladder itself — earn a face of their own.
 *
 * <h2>Size travels with the icon</h2>
 *
 * {@link #width()} and {@link #height()} are the PNG's own dimensions, not the size it is drawn at.
 * {@code blit} needs both — the source size to sample and the destination size to fill — and a
 * mismatch between them is the classic "my icon is a quarter of a texture, stretched" bug.
 *
 * @see #file() — what {@code tools/generate_ork_progression_icons.py} writes
 */
public enum PlayerOrkIcon {

    // ---------------------------------------------------------------- the five branches
    BRUTAL_CHOPPA("branch_brutal_choppa"),
    TUFF_PLATE("branch_tuff_plate"),
    DAKKA_SHOOTA("branch_dakka_shoota"),
    KUNNIN_TEEF("branch_kunnin_teef"),
    WAAAGH_MOUTH("branch_waaagh_mouth"),

    // ---------------------------------------------------------------- the ladder
    STAGE_ORK_BOY("stage_ork_boy"),
    STAGE_BIG_BOY("stage_big_boy"),
    STAGE_ORK_NOB("stage_ork_nob"),
    STAGE_BIG_NOB("stage_big_nob"),
    STAGE_WARBOSS("stage_warboss"),

    // ---------------------------------------------------------------- nodes with a face
    EADBUTT("node_eadbutt"),
    KRUMP_FIRST("node_krump_first"),
    NOT_DEAD_YET("node_not_dead_yet"),
    MEGA_PLATIN("node_mega_platin"),
    BOYZ_COME_HERE("node_boyz_come_here"),
    IM_DA_BOSS("node_im_da_boss"),
    WAAAGH_ROAR("node_waaagh_roar"),
    LOOT_IT_ALL("node_loot_it_all"),
    BIG_TEEF("node_big_teef");

    /** All icons are drawn on the same grid, so one number serves for every source rectangle. */
    public static final int SIZE = 40;

    private static final String ROOT = "textures/gui/progression/ork/";

    private final String file;
    private final ResourceLocation texture;

    PlayerOrkIcon(String file) {
        this.file = file;
        this.texture = new ResourceLocation(ExampleMod.MODID, ROOT + file + ".png");
    }

    public ResourceLocation texture() {
        return this.texture;
    }

    public int width() {
        return SIZE;
    }

    public int height() {
        return SIZE;
    }

    /** The file name without extension — what the generator script writes. */
    public String file() {
        return this.file;
    }

    // ==================================================================== resolution

    /**
     * The picture for a node.
     *
     * <p>Resolved here rather than stored on the node, so the tree stays a description of what an
     * Ork can learn and does not also become a description of what it looks like. A node with no
     * entry below is not a mistake — it gets its branch's face, which is the design.
     */
    public static PlayerOrkIcon of(PlayerOrkProgressionTree.Node node) {
        if (node == null) {
            return WAAAGH_MOUTH;
        }

        if (node.isEvolution()) {
            return ofStage(node.stage());
        }

        if (node.id().equals(PlayerOrkProgressionTree.ROOT_ID)) {
            return STAGE_ORK_BOY;
        }

        return switch (node.id()) {
            case "eadbutt" -> EADBUTT;
            case "krump_first", "run_and_hit", "brutal_but_kunnin" -> KRUMP_FIRST;
            case "not_dead_yet", "da_biggest" -> NOT_DEAD_YET;
            case "mega_platin" -> MEGA_PLATIN;
            case "boyz_come_here", "boyz_listen" -> BOYZ_COME_HERE;
            case "im_da_boss" -> IM_DA_BOSS;
            case "waaagh_roar", "da_greenest" -> WAAAGH_ROAR;
            case "loot_it_all", "got_it_first" -> LOOT_IT_ALL;
            case "big_teef", "teef_is_money", "kunnin_but_brutal" -> BIG_TEEF;
            default -> of(node.branch());
        };
    }

    public static PlayerOrkIcon of(PlayerOrkSkillBranch branch) {
        return switch (branch) {
            case BRUTAL -> BRUTAL_CHOPPA;
            case TUFF -> TUFF_PLATE;
            case DAKKA -> DAKKA_SHOOTA;
            case KUNNIN -> KUNNIN_TEEF;
            case WAAAGH -> WAAAGH_MOUTH;
        };
    }

    public static PlayerOrkIcon ofStage(PlayerOrkEvolutionStage stage) {
        if (stage == null) {
            return STAGE_ORK_BOY;
        }

        return switch (stage) {
            case ORK_BOY -> STAGE_ORK_BOY;
            case BIG_BOY -> STAGE_BIG_BOY;
            case ORK_NOB -> STAGE_ORK_NOB;
            case BIG_NOB -> STAGE_BIG_NOB;
            case WARBOSS -> STAGE_WARBOSS;
        };
    }
}
