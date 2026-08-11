package com.example.examplemod.progression;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * The five disciplines a skill node can belong to, plus the two kinds of node that stand outside
 * them.
 *
 * <p>The colour is the node's fill in the tree and the accent on its tooltip; the glyph is drawn on
 * the node face. Both live here so that adding a discipline is one entry rather than an edit in the
 * screen, the tooltip and the legend.
 */
public enum PlayerSkillBranch {
    /** Heart, crimson. Flesh that refuses to stop. */
    VITALITY("vitality", 0xFFC0434A),

    /**
     * A breastplate, steel blue. Everything that makes a hit matter less.
     *
     * <p>A shield, not a breastplate, was the first choice, and it was wrong: nothing in this branch
     * is a thing you hold. Every node here is armour worn or flesh hardened, and the icon should say
     * which of the two the player is buying.
     */
    RESILIENCE("resilience", 0xFF6E7F92),

    /** Sword, bronze and burnt orange. Everything that makes your hit matter more. */
    DAMAGE("damage", 0xFFB4762E),

    /** Boot, military green. Ground covered, and how fast. */
    MOBILITY("mobility", 0xFF4E9E7A),

    /** Aquila, gold. The Emperor protects — and, at rank one, actually does something about it. */
    FAITH("faith", 0xFFD9B65C),

    /** The gene-seed organs: organic red under a gold border, and an icon each. */
    IMPLANT("implant", 0xFF8E3BAF),

    /** The last node on the tree: white, gold and red. */
    ASCENSION("ascension", 0xFFE8E2D0);

    private final String key;
    private final int colour;

    PlayerSkillBranch(String key, int colour) {
        this.key = key;
        this.colour = colour;
    }

    public int colour() {
        return this.colour;
    }

    /**
     * The picture a node of this discipline wears when it has not asked for one of its own.
     *
     * <p>Skills never ask: a discipline is exactly what its icon says, and thirty-six bespoke
     * drawings would say the same five things. The implants and the ascension each carry their own,
     * which is why they are the only nodes that override this.
     */
    public PlayerProgressionIcon defaultIcon() {
        return PlayerProgressionIcon.of(this);
    }

    public Component displayName() {
        return Component.translatable("branch.firstcrusade." + this.key);
    }

    public static PlayerSkillBranch byName(String name) {
        for (PlayerSkillBranch branch : values()) {
            if (branch.name().equalsIgnoreCase(name)) {
                return branch;
            }
        }
        return VITALITY;
    }

    public String key() {
        return this.key;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
