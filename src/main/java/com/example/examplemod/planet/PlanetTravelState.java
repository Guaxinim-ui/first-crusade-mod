package com.example.examplemod.planet;

import net.minecraft.network.chat.Component;

/**
 * What a given player may currently do with a given destination.
 *
 * <p>The state that earns its place here is {@link #DISCOVERED_NOT_DEPLOYABLE}: a planet whose entry
 * exists but whose dimension does not. Without it the terminal would have to choose between hiding
 * such a world (and losing the roadmap the list is meant to show) or offering a launch that cannot
 * happen. It shows the destination, describes it, and keeps the button dark.
 */
public enum PlanetTravelState {
    /** Requirements not met. Shown dimmed, with a padlock and the requirement list in the tooltip. */
    LOCKED("locked"),

    /** Unlocked, but the destination dimension is not registered in this installation. */
    DISCOVERED_NOT_DEPLOYABLE("not_deployable"),

    /** Unlocked and reachable. */
    AVAILABLE("available"),

    /** Where the player is standing right now. */
    CURRENT("current");

    private final String key;

    PlanetTravelState(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("planet_state.firstcrusade." + this.key);
    }

    public boolean canTravel() {
        return this == AVAILABLE;
    }
}
