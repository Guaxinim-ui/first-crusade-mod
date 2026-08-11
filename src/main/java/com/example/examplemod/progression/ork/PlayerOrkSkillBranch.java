package com.example.examplemod.progression.ork;

import net.minecraft.network.chat.Component;

/**
 * The five things an Ork can be good at.
 *
 * <p>Named the way an Ork would name them, and deliberately not "Vitality / Resistance / Damage /
 * Mobility" — those are the Imperium's words for the same ideas, and using them here would make the
 * WAAAGH tree read as the Astartes tree with the serial numbers filed off.
 */
public enum PlayerOrkSkillBranch {

    /** Choppa work. Melee, knockback, charging, hitting things until they stop. */
    BRUTAL("brutal", 0xFFB03028),

    /** Being hard to kill. Health, armour, not falling over. */
    TUFF("tuff", 0xFF5F7A4A),

    /** Guns, and the noise they make. */
    DAKKA("dakka", 0xFFD8A020),

    /** Being sneaky about it. Speed, loot, Teef, getting there first. */
    KUNNIN("kunnin", 0xFF4A82C8),

    /** Shouting, and what shouting does to Boyz. */
    WAAAGH("waaagh", 0xFF52D82E);

    private final String id;
    private final int colour;

    PlayerOrkSkillBranch(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    public String id() {
        return this.id;
    }

    public int colour() {
        return this.colour;
    }

    public Component displayName() {
        return Component.translatable("ork.firstcrusade.branch." + this.id);
    }
}
