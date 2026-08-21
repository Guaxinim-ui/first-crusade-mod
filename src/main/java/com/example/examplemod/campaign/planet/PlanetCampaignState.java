package com.example.examplemod.campaign.planet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Where a front stands in the campaign.
 *
 * <h2>Two axes, not one</h2>
 *
 * The first three values answer "has the Crusade engaged here?" and the last five answer "who is
 * winning?". They are one enum because a front is only ever in one of them, and because the
 * progression between them is the campaign: LOCKED to AVAILABLE is an unlock, AVAILABLE to ACTIVE is
 * a landing, and everything after that is the war.
 *
 * <p>{@link #CONQUERED} and {@link #LOST} are terminal in the sense that they change what the front
 * is <i>for</i> — a conquered world supplies the Crusade instead of consuming it — but they are not
 * permanent. A conquered planet whose enemy strength climbs back drops to IMPERIAL_CONTROL, then to
 * CONTESTED, and can be lost again. The war is meant to swing.
 */
public enum PlanetCampaignState {
    /** Known to exist; the Crusade cannot reach it yet. */
    LOCKED("locked", ChatFormatting.DARK_GRAY),

    /** Reachable, but no one has landed and the front has never been laid out. */
    AVAILABLE("available", ChatFormatting.GRAY),

    /** Laid out and being fought over, with neither side yet dominant. */
    ACTIVE("active", ChatFormatting.WHITE),

    /** Both sides hold substantial ground and the line is moving. */
    CONTESTED("contested", ChatFormatting.YELLOW),

    /** The Imperium holds the majority of the front's weight. */
    IMPERIAL_CONTROL("imperial_control", ChatFormatting.GOLD),

    /** An enemy power holds the majority. */
    ENEMY_CONTROL("enemy_control", ChatFormatting.RED),

    /** Taken: the Imperium holds nearly everything and the enemy's seat of power is gone. */
    CONQUERED("conquered", ChatFormatting.AQUA),

    /** Fallen: the enemy holds nearly everything the Crusade had here. */
    LOST("lost", ChatFormatting.DARK_RED);

    private final String key;
    private final ChatFormatting colour;

    PlanetCampaignState(String key, ChatFormatting colour) {
        this.key = key;
        this.colour = colour;
    }

    public String key() {
        return this.key;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    public Component displayName() {
        return Component.translatable("war.firstcrusade.state." + this.key).withStyle(this.colour);
    }

    /** True once the front has been laid out and is being simulated. */
    public boolean isEngaged() {
        return this != LOCKED && this != AVAILABLE;
    }

    /** True for a front that is currently supplying the Crusade rather than draining it. */
    public boolean isImperialHeld() {
        return this == IMPERIAL_CONTROL || this == CONQUERED;
    }

    public static PlanetCampaignState fromName(String name) {
        if (name != null) {
            for (PlanetCampaignState state : values()) {
                if (state.name().equalsIgnoreCase(name)) {
                    return state;
                }
            }
        }

        return AVAILABLE;
    }
}
