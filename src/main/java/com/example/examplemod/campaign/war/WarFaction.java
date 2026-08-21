package com.example.examplemod.campaign.war;

import com.example.examplemod.FirstCrusadeFaction;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Who can own strategic ground.
 *
 * <h2>Why not {@link FirstCrusadeFaction}</h2>
 *
 * That enum answers a different question: "may this entity attack that entity?". It has PLAYER and
 * HOSTILE in it, which are combat relationships, not war powers, and it has no Necrons because no
 * Necron entity exists yet. Widening it would mean touching {@code FirstCrusadeFactionManager} and
 * every targeting goal that reads it, to add a value none of them can act on.
 *
 * <p>So the campaign keeps its own, smaller enum: the powers that can hold a sector. The two map
 * onto each other through {@link #fromEntityFaction}/{@link #toEntityFaction}, which is the only
 * place the translation lives.
 *
 * <p>{@link #CONTESTED} is not a power. It is a sector's answer to "who holds this?" while it is
 * being fought over, and it is why {@link #holdsGround()} exists — a contested sector counts toward
 * nobody's control percentage.
 */
public enum WarFaction {
    IMPERIUM("imperium", ChatFormatting.GOLD),
    ORKS("orks", ChatFormatting.GREEN),
    NECRONS("necrons", ChatFormatting.AQUA),
    NEUTRAL("neutral", ChatFormatting.GRAY),
    CONTESTED("contested", ChatFormatting.YELLOW);

    private final String key;
    private final ChatFormatting colour;

    WarFaction(String key, ChatFormatting colour) {
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
        return Component.translatable("war.firstcrusade.faction." + this.key).withStyle(this.colour);
    }

    /** True for a power that can own a sector and have it counted toward planetary control. */
    public boolean holdsGround() {
        return this == IMPERIUM || this == ORKS || this == NECRONS;
    }

    /** True for a power the Imperium is at war with. Drives "enemy control" in the planet state. */
    public boolean isEnemyOfImperium() {
        return this == ORKS || this == NECRONS;
    }

    public static WarFaction fromEntityFaction(FirstCrusadeFaction faction) {
        if (faction == null) {
            return NEUTRAL;
        }

        return switch (faction) {
            case IMPERIUM, PLAYER -> IMPERIUM;
            case ORKS -> ORKS;
            default -> NEUTRAL;
        };
    }

    /**
     * The entity-level faction this power fights as. {@link #NECRONS} has no entities yet, so it
     * answers HOSTILE — which is already the relationship every existing unit has toward things it
     * is not allied with, so nothing has to know about Necrons before they exist.
     */
    public FirstCrusadeFaction toEntityFaction() {
        return switch (this) {
            case IMPERIUM -> FirstCrusadeFaction.IMPERIUM;
            case ORKS -> FirstCrusadeFaction.ORKS;
            case NECRONS -> FirstCrusadeFaction.HOSTILE;
            default -> FirstCrusadeFaction.NEUTRAL;
        };
    }

    /** Reads a persisted name, degrading an unknown one to {@link #NEUTRAL} instead of throwing. */
    public static WarFaction fromName(String name) {
        if (name == null || name.isEmpty()) {
            return NEUTRAL;
        }

        for (WarFaction faction : values()) {
            if (faction.name().equalsIgnoreCase(name) || faction.key.equalsIgnoreCase(name)) {
                return faction;
            }
        }

        return NEUTRAL;
    }
}
