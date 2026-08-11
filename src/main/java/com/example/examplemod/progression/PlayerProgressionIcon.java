package com.example.examplemod.progression;

import com.example.examplemod.ExampleMod;

import net.minecraft.resources.ResourceLocation;

/**
 * Every picture the progression tree draws, and the only place their paths are written.
 *
 * <h2>Why an enum and not a path in the screen</h2>
 *
 * The screen used to draw its marks from two or three rectangles each, which meant the visual
 * language lived inside a {@code switch} in the renderer: a new icon was a new branch of drawing
 * code, and twelve implants shared one shape because writing twelve more branches was not worth it.
 * A texture per icon moves that decision out of the renderer entirely — the screen asks a node for
 * its icon and blits it, and adding one is an entry here plus a PNG.
 *
 * <h2>Size travels with the icon</h2>
 *
 * {@link #width()} and {@link #height()} are the PNG's own dimensions, not the size it is drawn at.
 * {@code blit} needs both — the source size to sample and the destination size to fill — and a
 * mismatch between them is the classic "my icon is a quarter of a texture, stretched" bug. Keeping
 * the true size beside the path makes the two impossible to disagree.
 */
public enum PlayerProgressionIcon {

    // ---------------------------------------------------------------- the five disciplines
    VITALITY_HEART("branch_vitality_heart", Kind.CATEGORY),
    RESISTANCE_ARMOUR("branch_resilience_armour", Kind.CATEGORY),
    DAMAGE_SWORD("branch_damage_sword", Kind.CATEGORY),
    MOBILITY_BOOT("branch_mobility_boot", Kind.CATEGORY),
    FAITH_AQUILA("branch_faith_aquila", Kind.CATEGORY),

    // ---------------------------------------------------------------- the two ends of the road
    GUARDSMAN_HELMET("root_guardsman_helmet", Kind.SPECIAL),
    SPACE_MARINE_HELMET("ascension_space_marine_helmet", Kind.SPECIAL),

    // ---------------------------------------------------------------- the twelve organs
    SECONDARY_HEART("implant_secondary_heart", Kind.IMPLANT),
    OSSMODULA_BONE("implant_ossmodula_bone", Kind.IMPLANT),
    BISCOPEA_MUSCLE("implant_biscopea_muscle", Kind.IMPLANT),
    HAEMASTAMEN_BLOOD("implant_haemastamen_blood", Kind.IMPLANT),
    LARRAMAN_CLOT("implant_larraman_clot", Kind.IMPLANT),
    CATALEPSEAN_BRAIN("implant_catalepsean_brain", Kind.IMPLANT),
    PREOMNOR_STOMACH("implant_preomnor_omophagea_stomach", Kind.IMPLANT),
    MULTI_LUNG("implant_multi_lung", Kind.IMPLANT),
    OCCULOBE_LYMAN("implant_occulobe_lyman_eye_ear", Kind.IMPLANT),
    SUSAN_MELANOCHROME_OOLITIC("implant_susan_melanochrome_oolitic", Kind.IMPLANT),
    CHEMICAL_MATURITY("implant_chemical_maturity_glands", Kind.IMPLANT),
    BLACK_CARAPACE("implant_black_carapace", Kind.IMPLANT);

    /** What an icon is for. Drives nothing but the fallback, and reads well in a debugger. */
    public enum Kind {
        CATEGORY, IMPLANT, SPECIAL
    }

    /** All icons are drawn on the same grid, so one number serves for every source rectangle. */
    public static final int SIZE = 40;

    private static final String ROOT = "textures/gui/progression/icons/";

    private final String file;
    private final Kind kind;
    private final ResourceLocation texture;

    PlayerProgressionIcon(String file, Kind kind) {
        this.file = file;
        this.kind = kind;
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

    public Kind kind() {
        return this.kind;
    }

    /** The file name without extension — what {@code generate_progression_icons.py} writes. */
    public String file() {
        return this.file;
    }

    /**
     * The default icon for a discipline.
     *
     * <p>This is what a skill node gets when nothing more specific was asked for, and it is the
     * reason a designer can add a skill without drawing anything: the category already has a face.
     */
    public static PlayerProgressionIcon of(PlayerSkillBranch branch) {
        return switch (branch) {
            case VITALITY -> VITALITY_HEART;
            case RESILIENCE -> RESISTANCE_ARMOUR;
            case DAMAGE -> DAMAGE_SWORD;
            case MOBILITY -> MOBILITY_BOOT;
            case FAITH -> FAITH_AQUILA;
            // An implant or the ascension that reached here was built without its own icon, which
            // is a mistake in the tree rather than a state to render — the helmet is a loud default.
            case IMPLANT, ASCENSION -> SPACE_MARINE_HELMET;
        };
    }
}
