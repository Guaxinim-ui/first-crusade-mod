package com.example.examplemod.progression;

/**
 * What a node actually does, as data.
 *
 * <h2>Why an enum and not a lambda per node</h2>
 *
 * A node definition has to survive two trips it cannot take with behaviour attached to it: into NBT,
 * and across the network to a client that must draw "+3% ranged damage" in a tooltip without owning
 * any of the server's logic. So a node carries an <i>effect kind</i> and a <i>value per rank</i>, and
 * exactly one place in the mod turns those into gameplay
 * ({@link PlayerProgressionManager#aggregate}). The screen turns the same two fields into a sentence.
 *
 * <p>{@link Format} is what lets one tooltip routine render every node: the number is the same, only
 * the unit differs.
 */
public enum PlayerProgressionEffect {

    // ---------------------------------------------------------------- attribute-shaped effects
    MAX_HEALTH(Format.FLAT),
    ARMOR(Format.FLAT),
    ARMOR_TOUGHNESS(Format.FLAT),
    MELEE_DAMAGE(Format.FLAT),
    KNOCKBACK_RESISTANCE(Format.FLAT),
    MOVEMENT_SPEED(Format.PERCENT),

    // ---------------------------------------------------------------- combat multipliers
    RANGED_DAMAGE(Format.PERCENT),
    ANTI_ORK_DAMAGE(Format.PERCENT),
    ANTI_ELITE_DAMAGE(Format.PERCENT),
    DAMAGE_REDUCTION(Format.PERCENT),
    ENVIRONMENTAL_RESISTANCE(Format.PERCENT),
    WEAPON_COOLDOWN(Format.PERCENT),
    WEAPON_ACCURACY(Format.PERCENT),
    MOVING_FIRE(Format.PERCENT),
    AIM_STABILITY(Format.PERCENT),

    // ---------------------------------------------------------------- sustain
    MEDKIT_POWER(Format.PERCENT),
    HEALING_RECEIVED(Format.PERCENT),
    OUT_OF_COMBAT_REGEN(Format.PERCENT),
    FOOD_EFFICIENCY(Format.PERCENT),
    SPRINT_HUNGER(Format.PERCENT),
    BREATH_CONTROL(Format.PERCENT),
    DEBUFF_DURATION(Format.PERCENT),

    // ---------------------------------------------------------------- movement
    FALL_DAMAGE(Format.PERCENT),
    STEP_HEIGHT(Format.FLAT),
    HEAVY_ARMOUR_MOVEMENT(Format.PERCENT),
    POWER_ARMOUR_MARCH(Format.PERCENT),
    NEURAL_INTERFACE(Format.PERCENT),

    // ---------------------------------------------------------------- active abilities
    /** Unlocks the prayer, and sets how many absorption hearts it grants. */
    PRAYER(Format.HEARTS),
    /** Longer absorption, shorter cooldown. */
    PRAYER_LITANY(Format.SECONDS),
    /** Resistance after a completed prayer. */
    PRAYER_RESISTANCE(Format.SECONDS),
    /** Fire resistance after a completed prayer. */
    PRAYER_FIRE_WARD(Format.SECONDS),
    /** Nearby enemies glow after a completed prayer. */
    PRAYER_VIGIL(Format.SECONDS),
    /** Imperial allies in range share a smaller absorption. */
    PRAYER_CHOIR(Format.HEARTS),

    /** Unlocks the combat roll. */
    TACTICAL_ROLL(Format.PERCENT),
    /** Unlocks the Betcher acid spit. */
    ACID_SPIT(Format.FLAT),
    /** Unlocks the Sus-an emergency stasis. */
    SUS_AN(Format.SECONDS),

    // ---------------------------------------------------------------- passive senses
    COMBAT_MEMORY(Format.FLAT),
    TOXIN_SENSE(Format.NONE),

    /** The organ nodes and the ascension: their effects are written into the definition's text. */
    IMPLANT(Format.NONE);

    public enum Format {
        FLAT, PERCENT, SECONDS, HEARTS, NONE
    }

    private final Format format;

    PlayerProgressionEffect(Format format) {
        this.format = format;
    }

    public Format format() {
        return this.format;
    }
}
