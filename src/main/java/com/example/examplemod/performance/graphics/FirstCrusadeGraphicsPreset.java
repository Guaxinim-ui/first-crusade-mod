package com.example.examplemod.performance.graphics;

/**
 * The four visual profiles of First Crusade, and the numbers behind each one.
 *
 * <h2>What a preset is allowed to touch</h2>
 *
 * Everything here is a <b>picture</b>: how many sparks a las-bolt leaves behind, how far away a
 * muzzle flash is still drawn, how much smoke an explosion throws. Not one of these values reaches
 * damage, accuracy, range, rate of fire or AI. A player on PERFORMANCE and a player on EXTERMINATUS
 * standing side by side in the same firefight take the same damage from the same shots; they simply
 * do not see the same amount of debris. That separation is a hard rule of the mod, not a style
 * preference, which is why these values live in a client config that the server never reads.
 *
 * <h2>Percentages, not enums-within-enums</h2>
 *
 * Every "how much" value is an integer percentage from 0 to 100, because that is the one scale that
 * reads the same in a config file, in a tooltip and in code: 40 means "four out of ten of these
 * effects are drawn". The two distances are in blocks. Mixing LOW/MEDIUM/HIGH enums into this would
 * only add a translation layer between the owner's intent and the number that gets multiplied.
 *
 * <h2>CUSTOM</h2>
 *
 * {@link #CUSTOM} carries a full copy of the GRIMDARK numbers, but nothing ever reads them: when the
 * preset is CUSTOM, {@code FirstCrusadeClientConfig} answers from the individual config fields
 * instead. The copy exists so that a mistake in the resolution logic degrades into "looks like
 * GRIMDARK" rather than "everything is zero and the screen is empty".
 */
public enum FirstCrusadeGraphicsPreset {

    /**
     * Maximum frames in a large battle. Every visual channel is thinned hard and distant combat is
     * drawn at short range. The battle still reads as a battle — tracers and flashes are reduced,
     * never switched off, because a firefight you cannot see is worse than a slow one.
     */
    PERFORMANCE(40, 40, 40, 40, 40, 40, 30, 40, 48, 24, 250),

    /**
     * The recommended default, and the look the mod is designed around: heavy enough that a Guard
     * firing line throws real light and smoke, restrained enough to survive a hundred-a-side fight
     * on ordinary hardware.
     */
    GRIMDARK(70, 75, 70, 80, 70, 70, 60, 70, 96, 48, 600),

    /** Everything, at full strength, as far as the eye reaches. For machines with room to spare. */
    EXTERMINATUS(100, 100, 100, 100, 100, 100, 100, 100, 160, 96, 1200),

    /** Hand-tuned: the values come from the individual fields of the client config. */
    CUSTOM(70, 75, 70, 80, 70, 70, 60, 70, 96, 48, 600);

    private final int particleDensity;
    private final int tracerDensity;
    private final int smokeDensity;
    private final int muzzleFlashQuality;
    private final int distantAnimationQuality;
    private final int vehicleEffects;
    private final int debrisAmount;
    private final int explosionEffects;
    private final int maxVisualCombatDistance;
    private final int corpseRenderDistance;
    private final int maxParticlesPerTick;

    FirstCrusadeGraphicsPreset(int particleDensity,
                               int tracerDensity,
                               int smokeDensity,
                               int muzzleFlashQuality,
                               int distantAnimationQuality,
                               int vehicleEffects,
                               int debrisAmount,
                               int explosionEffects,
                               int maxVisualCombatDistance,
                               int corpseRenderDistance,
                               int maxParticlesPerTick) {
        this.particleDensity = particleDensity;
        this.tracerDensity = tracerDensity;
        this.smokeDensity = smokeDensity;
        this.muzzleFlashQuality = muzzleFlashQuality;
        this.distantAnimationQuality = distantAnimationQuality;
        this.vehicleEffects = vehicleEffects;
        this.debrisAmount = debrisAmount;
        this.explosionEffects = explosionEffects;
        this.maxVisualCombatDistance = maxVisualCombatDistance;
        this.corpseRenderDistance = corpseRenderDistance;
        this.maxParticlesPerTick = maxParticlesPerTick;
    }

    /** Master multiplier applied on top of every other channel. */
    public int particleDensity() {
        return this.particleDensity;
    }

    /** Weapon tracers and projectile trails. */
    public int tracerDensity() {
        return this.tracerDensity;
    }

    /** Smoke plumes from explosions, fires and engines. */
    public int smokeDensity() {
        return this.smokeDensity;
    }

    /** Muzzle flashes at the weapon's barrel. */
    public int muzzleFlashQuality() {
        return this.muzzleFlashQuality;
    }

    /** Animation detail for units past the full-detail radius. */
    public int distantAnimationQuality() {
        return this.distantAnimationQuality;
    }

    /** Exhaust, dust and sparks thrown by vehicles. */
    public int vehicleEffects() {
        return this.vehicleEffects;
    }

    /** Fragments and dirt kicked up by impacts. */
    public int debrisAmount() {
        return this.debrisAmount;
    }

    /** Fireball, shockwave and ember count on detonation. */
    public int explosionEffects() {
        return this.explosionEffects;
    }

    /** Past this distance, in blocks, combat visuals are not drawn at all. */
    public int maxVisualCombatDistance() {
        return this.maxVisualCombatDistance;
    }

    /** Past this distance, in blocks, bodies stop being drawn. */
    public int corpseRenderDistance() {
        return this.corpseRenderDistance;
    }

    /** Ceiling on mod particles spawned in a single client tick. See FirstCrusadeParticleBudget. */
    public int maxParticlesPerTick() {
        return this.maxParticlesPerTick;
    }
}
