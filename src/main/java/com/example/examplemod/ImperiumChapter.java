package com.example.examplemod;

import net.minecraft.util.RandomSource;

public enum ImperiumChapter {
    IMPERIAL_STANDARD(
            "Imperial Standard",
            "IS",
            "Balanced doctrine",
            0.0D,
            0.0D,
            0.0D,
            0.0D,
            0.0D
    ),

    IRON_WARDENS(
            "Iron Wardens",
            "IW",
            "Heavy infantry and defensive warfare",
            6.0D,
            0.5D,
            3.0D,
            -0.25D,
            -0.01D
    ),

    ASHEN_MARKSMEN(
            "Ashen Marksmen",
            "AM",
            "Long range lasgun fire and precision volleys",
            0.0D,
            0.0D,
            -1.0D,
            1.75D,
            0.01D
    ),

    CRIMSON_HOUNDS(
            "Crimson Hounds",
            "CH",
            "Aggressive melee assaults and fast charges",
            2.0D,
            2.0D,
            0.0D,
            -0.5D,
            0.025D
    ),

    VOID_SENTINELS(
            "Void Sentinels",
            "VS",
            "Elite balanced troops with strong discipline",
            4.0D,
            0.75D,
            1.5D,
            0.75D,
            0.0D
    ),

    STONE_BASTIONS(
            "Stone Bastions",
            "SB",
            "Siege defense, walls, towers and garrison warfare",
            8.0D,
            0.25D,
            4.0D,
            -0.5D,
            -0.015D
    ),

    SOLAR_LANCES(
            "Solar Lances",
            "SL",
            "High energy ranged fire and anti-elite shooting",
            1.0D,
            0.25D,
            0.0D,
            2.5D,
            0.0D
    ),

    NIGHT_REAPERS(
            "Night Reapers",
            "NR",
            "Ambush, stealth patrols and fast elimination",
            0.0D,
            1.25D,
            -0.5D,
            1.0D,
            0.03D
    ),

    GILDED_LIONS(
            "Gilded Lions",
            "GL",
            "Command, morale and veteran leadership",
            3.0D,
            1.0D,
            1.0D,
            0.5D,
            0.005D
    ),

    MOURNING_STARS(
            "Mourning Stars",
            "MS",
            "Relic hunting, elite guards and sacred warfare",
            5.0D,
            1.25D,
            2.0D,
            0.75D,
            -0.005D
    ),

    RUSTWALKERS(
            "Rustwalkers",
            "RW",
            "Salvage warfare, scrap recovery and urban ruins",
            2.0D,
            0.5D,
            1.0D,
            0.25D,
            0.01D
    ),

    EMBER_FALCONS(
            "Ember Falcons",
            "EF",
            "Mobile infantry, rapid response and flanking",
            1.0D,
            0.75D,
            -0.25D,
            0.75D,
            0.035D
    ),

    GRAVE_MANTLES(
            "Grave Mantles",
            "GM",
            "Attrition warfare and survival under heavy losses",
            10.0D,
            -0.25D,
            2.5D,
            0.0D,
            -0.01D
    ),

    THUNDER_COHORTS(
            "Thunder Cohorts",
            "TC",
            "Shock troops, heavy weapons and direct assaults",
            4.0D,
            2.0D,
            1.0D,
            1.0D,
            -0.005D
    ),

    ARGENT_SPEARS(
            "Argent Spears",
            "AS",
            "Disciplined spearhead attacks and formation fighting",
            3.0D,
            1.5D,
            1.5D,
            0.25D,
            0.01D
    ),

    BLACK_AQUILAS(
            "Black Aquilas",
            "BA",
            "Elite imperial security and command protection",
            6.0D,
            1.0D,
            2.5D,
            0.5D,
            0.0D
    ),

    DAWNBRINGERS(
            "Dawnbringers",
            "DB",
            "Reclamation warfare and city liberation",
            2.0D,
            0.75D,
            1.0D,
            1.25D,
            0.015D
    ),

    RED_TEMPLARS(
            "Red Templars",
            "RT",
            "Fanatical melee combat and brutal charges",
            3.0D,
            2.75D,
            0.5D,
            -1.0D,
            0.02D
    ),

    PALE_SERAPHS(
            "Pale Seraphs",
            "PS",
            "Medical support, protection and survival doctrine",
            7.0D,
            0.0D,
            2.0D,
            0.25D,
            0.0D
    ),

    CINDER_JACKALS(
            "Cinder Jackals",
            "CJ",
            "Skirmishing, raids and fast battlefield pressure",
            1.0D,
            1.25D,
            -0.75D,
            0.75D,
            0.04D
    );

    private final String displayName;
    private final String shortName;
    private final String doctrineDescription;

    private final double maxHealthBonus;
    private final double attackDamageBonus;
    private final double armorBonus;
    private final double lasgunDamageBonus;
    private final double movementSpeedBonus;

    ImperiumChapter(
            String displayName,
            String shortName,
            String doctrineDescription,
            double maxHealthBonus,
            double attackDamageBonus,
            double armorBonus,
            double lasgunDamageBonus,
            double movementSpeedBonus
    ) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.doctrineDescription = doctrineDescription;
        this.maxHealthBonus = maxHealthBonus;
        this.attackDamageBonus = attackDamageBonus;
        this.armorBonus = armorBonus;
        this.lasgunDamageBonus = lasgunDamageBonus;
        this.movementSpeedBonus = movementSpeedBonus;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDoctrineDescription() {
        return doctrineDescription;
    }

    public double getMaxHealthBonus() {
        return maxHealthBonus;
    }

    public double getAttackDamageBonus() {
        return attackDamageBonus;
    }

    public double getArmorBonus() {
        return armorBonus;
    }

    public double getLasgunDamageBonus() {
        return lasgunDamageBonus;
    }

    public double getMovementSpeedBonus() {
        return movementSpeedBonus;
    }

    public static ImperiumChapter getRandom(RandomSource random) {
        ImperiumChapter[] chapters = ImperiumChapter.values();
        return chapters[random.nextInt(chapters.length)];
    }

    public static ImperiumChapter fromName(String name) {
        for (ImperiumChapter chapter : ImperiumChapter.values()) {
            if (chapter.name().equals(name)) {
                return chapter;
            }
        }

        return IMPERIAL_STANDARD;
    }
}