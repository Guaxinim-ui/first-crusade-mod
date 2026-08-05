package com.example.examplemod.planet;

import net.minecraft.network.chat.Component;

/**
 * How likely a destination is to kill the traveller, from "garrison duty" to "active war zone".
 *
 * <p>{@link #EXTREME} is not just a colour: it is the level that makes the terminal ask twice
 * before launching, which is why the ordinal order matters and must stay ascending.
 */
public enum PlanetDangerLevel {
    LOW("low", 0xFF6FBF6F),
    MODERATE("moderate", 0xFFD9C05C),
    HIGH("high", 0xFFE08A3C),
    EXTREME("extreme", 0xFFD24A3C),
    UNKNOWN("unknown", 0xFF8A8A8A);

    private final String key;
    private final int colour;

    PlanetDangerLevel(String key, int colour) {
        this.key = key;
        this.colour = colour;
    }

    public String key() {
        return this.key;
    }

    public int colour() {
        return this.colour;
    }

    public Component displayName() {
        return Component.translatable("planet_danger.firstcrusade." + this.key);
    }

    /** Extreme destinations demand a second, explicit confirmation before launch. */
    public boolean requiresDoubleConfirmation() {
        return this == EXTREME;
    }

    public static PlanetDangerLevel byName(String name) {
        for (PlanetDangerLevel level : values()) {
            if (level.name().equalsIgnoreCase(name)) {
                return level;
            }
        }
        return UNKNOWN;
    }
}
