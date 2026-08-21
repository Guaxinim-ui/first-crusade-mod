package com.example.examplemod.campaign.supply;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * How well a supply route is running.
 *
 * <p>The four states are a scale of interference, and each carries the throughput multiplier that
 * <i>is</i> its meaning — a state whose only effect was its colour would belong in a tooltip, not in
 * an enum the logistics pass reads.
 */
public enum SupplyState {
    /** Everything arrives. */
    ACTIVE("active", ChatFormatting.GREEN, 1.0D),

    /** The line is under pressure: it still runs, at a fraction. */
    DISRUPTED("disrupted", ChatFormatting.YELLOW, 0.5D),

    /** A sector the route depends on is in enemy hands. Nothing gets through. */
    BLOCKED("blocked", ChatFormatting.RED, 0.0D),

    /** The route's origin or destination no longer produces or receives at all. */
    DESTROYED("destroyed", ChatFormatting.DARK_RED, 0.0D);

    private final String key;
    private final ChatFormatting colour;
    private final double throughput;

    SupplyState(String key, ChatFormatting colour, double throughput) {
        this.key = key;
        this.colour = colour;
        this.throughput = throughput;
    }

    public String key() {
        return this.key;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    /** The fraction of the route's nominal amount that actually arrives. */
    public double throughput() {
        return this.throughput;
    }

    public boolean carries() {
        return this.throughput > 0.0D;
    }

    public Component displayName() {
        return Component.translatable("supply.firstcrusade.state." + this.key).withStyle(this.colour);
    }

    public static SupplyState fromName(String name) {
        if (name != null) {
            for (SupplyState state : values()) {
                if (state.name().equalsIgnoreCase(name)) {
                    return state;
                }
            }
        }

        return ACTIVE;
    }
}
