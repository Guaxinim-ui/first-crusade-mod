package com.example.examplemod.planet;

import net.minecraft.network.chat.Component;

/**
 * Who holds a world, and the colour the terminal paints it in.
 *
 * <p>The marker texture is part of the enum rather than of the screen because "which side is this"
 * is a fact about the planet, and a new faction should not mean editing the renderer.
 */
public enum PlanetFaction {
    IMPERIUM("imperium", 0xFFD9B65C),
    ORKS("orks", 0xFF5BA832),
    NECRONS("necrons", 0xFF3FE07A),
    CONTESTED("contested", 0xFFD06030),
    UNKNOWN("unknown", 0xFF8A8A8A);

    private final String key;
    private final int colour;

    PlanetFaction(String key, int colour) {
        this.key = key;
        this.colour = colour;
    }

    public String key() {
        return this.key;
    }

    /** ARGB used for the faction's text and its marker tint. */
    public int colour() {
        return this.colour;
    }

    public Component displayName() {
        return Component.translatable("planet_faction.firstcrusade." + this.key);
    }

    public static PlanetFaction byName(String name) {
        for (PlanetFaction faction : values()) {
            if (faction.name().equalsIgnoreCase(name)) {
                return faction;
            }
        }
        return UNKNOWN;
    }
}
