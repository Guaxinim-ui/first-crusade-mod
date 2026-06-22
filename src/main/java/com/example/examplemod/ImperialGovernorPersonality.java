package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * The bias of a city's Governor — the persona that runs the settlement's politics, economy and
 * building. It is data only (no entity): a personality shapes how an autonomous (or delegated) city
 * weighs its decisions, grants a small passive perk, and is shown in the Core's GUI next to the
 * Governor's name (see {@link ImperialGovernorManager}). Chosen when the city is founded, biased by
 * the city type, and saved in the Core's NBT.
 */
public enum ImperialGovernorPersonality {
    //            recruitGateBias  upgradeGateBias  borderBonus
    WARMONGER(-1, 0, 0),      // drives the garrison hard
    ADMINISTRATOR(0, -1, 0),  // grows the city/economy faster
    ARCHITECT(0, 0, 8),       // claims more building space
    ZEALOT(0, 0, 0);          // steady, faithful (morale flavour)

    public static final ImperialGovernorPersonality DEFAULT = ADMINISTRATOR;

    private final int recruitGateBias;
    private final int upgradeGateBias;
    private final int borderBonus;

    ImperialGovernorPersonality(int recruitGateBias, int upgradeGateBias, int borderBonus) {
        this.recruitGateBias = recruitGateBias;
        this.upgradeGateBias = upgradeGateBias;
        this.borderBonus = borderBonus;
    }

    // Lower = recruits more often (applied to the random gate in autonomousRecruit).
    public int getRecruitGateBias() {
        return this.recruitGateBias;
    }

    // Lower = upgrades the city more eagerly (applied to the random gate in autonomousUpgrade).
    public int getUpgradeGateBias() {
        return this.upgradeGateBias;
    }

    // Extra build-border radius this Governor claims for the city.
    public int getBorderBonus() {
        return this.borderBonus;
    }

    public Component getDisplayName() {
        return Component.translatable("governor.firstcrusade.personality." + name().toLowerCase());
    }

    public static ImperialGovernorPersonality fromName(String name) {
        if (name == null || name.isEmpty()) {
            return DEFAULT;
        }

        for (ImperialGovernorPersonality personality : values()) {
            if (personality.name().equals(name)) {
                return personality;
            }
        }

        return DEFAULT;
    }

    public static ImperialGovernorPersonality fromOrdinal(int ordinal) {
        ImperialGovernorPersonality[] values = values();

        if (ordinal < 0 || ordinal >= values.length) {
            return DEFAULT;
        }

        return values[ordinal];
    }

    // A city's character leans on its type: a Fortress breeds a Warmonger, a Forge/Hive an
    // Administrator or Architect, a Shrine a Zealot. Everything else keeps some variety.
    public static ImperialGovernorPersonality pickForCityType(ImperialCityType cityType, RandomSource random) {
        return switch (cityType) {
            case FORTRESS, DEATH_WORLD -> WARMONGER;
            case SHRINE, PENAL -> ZEALOT;
            case HIVE, FORGE -> random.nextBoolean() ? ADMINISTRATOR : ARCHITECT;
            case MINING, AGRI -> ADMINISTRATOR;
            default -> values()[random.nextInt(values().length)];
        };
    }
}
