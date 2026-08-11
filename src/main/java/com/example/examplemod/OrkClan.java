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
    //         name            hp     dmg    spd   +boyz nobz +grots  tactics
    GOFFS("Goffs", 1.35D, 1.25D, 1.0D, 2, 1, 0, "a brutal melee horde"),
    BAD_MOONS("Bad Moons", 1.1D, 1.45D, 1.0D, 0, 2, 0, "well-armed Nobz with more dakka"),
    DEATHSKULLS("Deathskulls", 1.0D, 1.0D, 1.0D, 0, 0, 3, "looters with grot mobs"),
    EVIL_SUNZ("Evil Sunz", 0.9D, 1.1D, 1.35D, 1, 0, 0, "a fast-moving speed raid"),
    SNAKEBITES("Snakebites", 1.45D, 1.0D, 0.95D, 1, 1, -2, "tough, primitive savages");

    private final String displayName;
    private final double healthFactor;
    private final double damageFactor;
    private final double speedFactor;
    private final int bonusBoyz;
    private final int nobz;
    private final int bonusGretchin;
    private final String tactics;

    OrkClan(String displayName, double healthFactor, double damageFactor, double speedFactor,
            int bonusBoyz, int nobz, int bonusGretchin, String tactics) {
        this.displayName = displayName;
        this.healthFactor = healthFactor;
        this.damageFactor = damageFactor;
        this.speedFactor = speedFactor;
        this.bonusBoyz = bonusBoyz;
        this.nobz = nobz;
        this.bonusGretchin = bonusGretchin;
        this.tactics = tactics;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getBonusBoyz() {
        return this.bonusBoyz;
    }

    public int getNobz() {
        return this.nobz;
    }

    public int getBonusGretchin() {
        return this.bonusGretchin;
    }

    public String getTactics() {
        return this.tactics;
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

    /**
      * Strict lookup: nothing matched means nothing chosen.
      *
      * <p>{@link #fromName} answers GOFFS for anything it does not recognise, which is fine for a
      * camp reading its own old save and completely wrong for a player's permanent, one-time choice
      * arriving over the network. A malformed packet must pick no klan at all, not the first one in
      * the enum.
      */
     public static OrkClan fromNameStrict(String name) {
         if (name == null || name.isEmpty()) {
             return null;
         }

         for (OrkClan clan : values()) {
             if (clan.name().equals(name)) {
                 return clan;
             }
         }

         return null;
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
