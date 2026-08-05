package com.example.examplemod.planet;

import net.minecraft.network.chat.Component;

/**
 * The war situation on a world, shown in the information panel and in the launch confirmation.
 *
 * <p>Today every planet declares this statically. The strategic layer is meant to override it later
 * — which is why the screen never hardcodes a status and always asks the definition, and why
 * {@link PlanetDefinition#militaryStatus()} is a single call the war AI can one day feed.
 */
public enum PlanetMilitaryStatus {
    PEACEFUL("peaceful"),
    GARRISONED("garrisoned"),
    CONTESTED("contested"),
    ACTIVE_WARZONE("active_warzone"),
    OVERRUN("overrun"),
    UNKNOWN("unknown");

    private final String key;

    PlanetMilitaryStatus(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("planet_military.firstcrusade." + this.key);
    }

    public static PlanetMilitaryStatus byName(String name) {
        for (PlanetMilitaryStatus status : values()) {
            if (status.name().equalsIgnoreCase(name)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
