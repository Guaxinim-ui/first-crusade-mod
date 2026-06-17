package com.example.examplemod;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Flavour of an Ork Camp. Each camp belongs to a clan, which shapes its Boyz: how tough, how
 * hard-hitting and how fast they are. Data-driven seed for the full clan roster in
 * {@code docs/DESIGN_WORLD_CITIES_FACTIONS.md}; more clans and unique units come later.
 */
public enum OrkClan {
    GOFFS("Goffs", 1.35D, 1.25D, 1.0D),        // brutal melee, tough
    BAD_MOONS("Bad Moons", 1.1D, 1.45D, 1.0D), // rich, more dakka/damage
    DEATHSKULLS("Deathskulls", 1.0D, 1.0D, 1.0D), // looters (scrap theme)
    EVIL_SUNZ("Evil Sunz", 0.9D, 1.1D, 1.35D),  // speed freaks
    SNAKEBITES("Snakebites", 1.45D, 1.0D, 0.95D); // primitive, very tough

    private final String displayName;
    private final double healthFactor;
    private final double damageFactor;
    private final double speedFactor;

    OrkClan(String displayName, double healthFactor, double damageFactor, double speedFactor) {
        this.displayName = displayName;
        this.healthFactor = healthFactor;
        this.damageFactor = damageFactor;
        this.speedFactor = speedFactor;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    // Applies this clan's stat profile to a freshly created Ork.
    public void applyTo(LivingEntity ork) {
        scaleAttribute(ork, Attributes.MAX_HEALTH, this.healthFactor);
        scaleAttribute(ork, Attributes.ATTACK_DAMAGE, this.damageFactor);
        scaleAttribute(ork, Attributes.MOVEMENT_SPEED, this.speedFactor);
        ork.setHealth(ork.getMaxHealth());
    }

    private void scaleAttribute(LivingEntity ork, Attribute attribute, double factor) {
        AttributeInstance instance = ork.getAttribute(attribute);

        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    public static OrkClan random(RandomSource random) {
        OrkClan[] values = values();
        return values[random.nextInt(values.length)];
    }

    public static OrkClan fromName(String name) {
        if (name == null || name.isEmpty()) {
            return GOFFS;
        }

        for (OrkClan clan : values()) {
            if (clan.name().equals(name)) {
                return clan;
            }
        }

        return GOFFS;
    }
}
