package com.example.examplemod.campaign.war;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * How hard a front is being fought over right now — the "threat" line on the War Table.
 *
 * <h2>Derived, never stored as an opinion</h2>
 *
 * Intensity is computed from what the front actually contains: how many sectors are disputed and how
 * evenly the two sides are matched. A planet where one side holds everything is quiet however many
 * armies are standing on it; a planet split down the middle with half its sectors changing hands is
 * TOTAL_WAR even if the body count is low. That is the distinction a stored "difficulty" number
 * could never make.
 *
 * <p>It is read by the raid layer to decide how often an offensive can be prepared, so it has to
 * mean something mechanically rather than only colouring a label.
 */
public enum WarIntensity {
    /** Nothing is contested. Garrison duty. */
    DORMANT("dormant", ChatFormatting.DARK_GRAY, 0.4D),

    /** Occasional pressure on the edges. */
    SKIRMISH("skirmish", ChatFormatting.GREEN, 0.7D),

    /** A real front line, moving slowly. */
    CONTESTED("contested", ChatFormatting.YELLOW, 1.0D),

    /** Several sectors changing hands; both sides committed. */
    HEAVY_FIGHTING("heavy_fighting", ChatFormatting.GOLD, 1.4D),

    /** The whole planet is a battlefield. */
    TOTAL_WAR("total_war", ChatFormatting.RED, 2.0D);

    private final String key;
    private final ChatFormatting colour;
    private final double raidRate;

    WarIntensity(String key, ChatFormatting colour, double raidRate) {
        this.key = key;
        this.colour = colour;
        this.raidRate = raidRate;
    }

    public String key() {
        return this.key;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    /** Multiplier on how quickly enemy offensives build here. 1.0 is the configured baseline. */
    public double raidRate() {
        return this.raidRate;
    }

    public Component displayName() {
        return Component.translatable("war.firstcrusade.intensity." + this.key).withStyle(this.colour);
    }

    /**
     * Reads the front's own numbers.
     *
     * @param disputedFraction how many of the front's sectors have both sides pressing on them, 0..1
     * @param balance          how evenly the front is split, 0 (one side holds all) to 1 (dead even)
     */
    public static WarIntensity of(double disputedFraction, double balance) {
        double heat = disputedFraction * 0.65D + balance * 0.35D;

        if (heat >= 0.60D) {
            return TOTAL_WAR;
        }
        if (heat >= 0.40D) {
            return HEAVY_FIGHTING;
        }
        if (heat >= 0.22D) {
            return CONTESTED;
        }
        if (heat >= 0.08D) {
            return SKIRMISH;
        }

        return DORMANT;
    }

    public static WarIntensity fromName(String name) {
        if (name != null) {
            for (WarIntensity intensity : values()) {
                if (intensity.name().equalsIgnoreCase(name)) {
                    return intensity;
                }
            }
        }

        return DORMANT;
    }
}
