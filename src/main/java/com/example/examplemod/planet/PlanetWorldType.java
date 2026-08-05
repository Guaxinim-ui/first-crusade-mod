package com.example.examplemod.planet;

import net.minecraft.network.chat.Component;

/**
 * What kind of world a planet is — the one line under its name in the navigation terminal.
 *
 * <p>Separate from {@link PlanetFaction} on purpose: who holds a world changes with the war, what
 * the world <i>is</i> does not. A forge world overrun by Orks is still a forge world, and the
 * difference between those two facts is exactly what a player reads a destination list for.
 */
public enum PlanetWorldType {
    IMPERIAL_CAPITAL("imperial_capital"),
    FORTRESS_WORLD("fortress_world"),
    INDUSTRIAL_WAR_WORLD("industrial_war_world"),
    FORGE_WORLD("forge_world"),
    HIVE_WORLD("hive_world"),
    AGRI_WORLD("agri_world"),
    ORK_WORLD("ork_world"),
    NECRON_TOMB_WORLD("necron_tomb_world"),
    DEATH_WORLD("death_world"),
    ICE_WORLD("ice_world"),
    UNKNOWN("unknown");

    private final String key;

    PlanetWorldType(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("planet_type.firstcrusade." + this.key);
    }

    public static PlanetWorldType byName(String name) {
        for (PlanetWorldType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
