package com.example.examplemod;

import net.minecraft.network.chat.Component;

/**
 * The allegiance a player picks when they first enter the world (Origins-style): the Imperium of Man
 * or the Orks. The choice is persisted per player in {@link PlayerFactionData}; what it unlocks or
 * changes in gameplay is intentionally left open for now (the owner will define the consequences
 * later), so this enum stays a pure, saved identity with a display name.
 */
public enum PlayerFaction {
    UNCHOSEN,
    IMPERIUM,
    ORKS;

    public Component getDisplayName() {
        return Component.translatable("faction.firstcrusade." + name().toLowerCase());
    }

    public boolean isChosen() {
        return this != UNCHOSEN;
    }

    public static PlayerFaction fromName(String name) {
        if (name == null || name.isEmpty()) {
            return UNCHOSEN;
        }

        for (PlayerFaction faction : values()) {
            if (faction.name().equals(name)) {
                return faction;
            }
        }

        return UNCHOSEN;
    }
}
