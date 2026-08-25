package com.example.examplemod.hive.city;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

/**
 * The Hive's levels, as the city generator actually builds them (spec §18).
 *
 * <h2>Why this exists as a type</h2>
 *
 * {@link HiveCityLayout} already stacks the districts — Underhive at {@code -64}, Manufactorum at
 * {@code 0}, Hab at {@code +64}, Administratum at {@code +128}, Spire at {@code +192} — but it does
 * so with three local {@code int} variables inside one method. Nothing else in the mod could answer
 * "which level is this Y on?" or "what is above the Hab?", so nothing else could reason about the
 * Hive vertically. The lift needed both questions answered, and a second copy of the arithmetic in
 * the block would be a copy that drifts the first time {@code LEVEL_HEIGHT} changes.
 *
 * <h2>Five levels, four zone names</h2>
 *
 * The spec names four zones — UPPER, MID, UNDERHIVE, DEEP — and the generator builds five levels. The
 * mapping is not one-to-one and this enum does not pretend otherwise: the levels are what exists and
 * {@link #zoneKey()} says which zone each belongs to. MID covers two of them, because a hive's
 * habitation and its industry are the same zone read from outside and different floors read from
 * inside.
 *
 * <p><b>DEEP HIVE is deliberately absent.</b> The spec asks for it, and no district generates below
 * the Underhive — inventing a constant for a level nothing builds would make {@link #below} hand out
 * a destination that is solid stone. When a deep district exists, it is one entry here and the lift
 * reaches it with no other change.
 */
public enum HiveTier {

    /** Gangs, sludge and what the city above throws away. */
    UNDERHIVE("underhive", HiveWorld.UNDERHIVE_Y, "underhive"),

    /** Industry, and the ground the gates open onto. */
    MANUFACTORUM("manufactorum", HiveWorld.GROUND_Y, "mid"),

    /** Where the population actually lives. */
    HAB("hab", HiveWorld.GROUND_Y + HiveWorld.LEVEL_HEIGHT, "mid"),

    /** Administration, records, and the people who sign for the war. */
    ADMINISTRATUM("administratum", HiveWorld.GROUND_Y + 2 * HiveWorld.LEVEL_HEIGHT, "upper"),

    /** The spire. Whoever is up here has never been down there. */
    SPIRE("spire", HiveWorld.GROUND_Y + 3 * HiveWorld.LEVEL_HEIGHT, "upper");

    private final String key;
    private final int baseY;
    private final String zoneKey;

    HiveTier(String key, int baseY, String zoneKey) {
        this.key = key;
        this.baseY = baseY;
        this.zoneKey = zoneKey;
    }

    public String key() {
        return this.key;
    }

    /** The Y the district's own floor is placed at. */
    public int baseY() {
        return this.baseY;
    }

    /** {@code upper} / {@code mid} / {@code underhive} — the spec's zone this level belongs to. */
    public String zoneKey() {
        return this.zoneKey;
    }

    public Component displayName() {
        return Component.translatable("hive.firstcrusade.tier." + this.key);
    }

    @Nullable
    public HiveTier above() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    @Nullable
    public HiveTier below() {
        return ordinal() > 0 ? values()[ordinal() - 1] : null;
    }

    /**
     * The level a given Y belongs to.
     *
     * <p>Answers with the highest level whose floor is at or below {@code y}, rather than the nearest
     * one. Standing on the Hab floor and standing in the roof space thirty blocks above it are both
     * "on the Hab" — nearest-match would put the second one on the Administratum and send a lift
     * rider straight past the level they were trying to leave.
     *
     * <p>Below the Underhive floor there is no level, and the answer is the Underhive anyway: a
     * player in the sludge at the bottom of the world is on the lowest level there is.
     */
    public static HiveTier of(int y) {
        HiveTier found = UNDERHIVE;

        for (HiveTier tier : values()) {
            if (y >= tier.baseY) {
                found = tier;
            }
        }

        return found;
    }
}
