package com.example.examplemod;

public enum StrategicConstructionType {
    HABITATION(
            "Habitação Imperial",
            StrategicAge.OUTPOST,
            3,
            false,
            true,
            90,
            50,
            35,
            0,
            30,
            0,
            0,
            0,
            0,
            0
    ),

    FARM(
            "Fazenda Imperial",
            StrategicAge.OUTPOST,
            4,
            false,
            true,
            80,
            35,
            20,
            0,
            10,
            0,
            0,
            0,
            0,
            0
    ),

    MINE(
            "Mina Imperial",
            StrategicAge.OUTPOST,
            3,
            false,
            true,
            70,
            70,
            40,
            20,
            25,
            0,
            0,
            0,
            0,
            0
    ),

    SCRAP_YARD(
            "Pátio de Sucata",
            StrategicAge.OUTPOST,
            3,
            false,
            true,
            70,
            50,
            40,
            20,
            20,
            0,
            0,
            0,
            0,
            0
    ),

    REFINERY(
            "Refinaria de Promethium",
            StrategicAge.FORTIFIED_SETTLEMENT,
            3,
            false,
            true,
            120,
            100,
            80,
            70,
            80,
            40,
            0,
            0,
            0,
            0
    ),

    FORGE(
            "Forja Manufactorum",
            StrategicAge.MANUFACTORUM_AGE,
            4,
            false,
            true,
            160,
            150,
            120,
            110,
            120,
            90,
            60,
            0,
            0,
            0
    ),

    BARRACKS(
            "Quartel Imperial",
            StrategicAge.OUTPOST,
            4,
            true,
            false,
            140,
            130,
            80,
            40,
            80,
            0,
            0,
            0,
            0,
            0
    ),

    WALL_BASTION(
            "Bastião de Ferrocrete",
            StrategicAge.FORTIFIED_SETTLEMENT,
            5,
            true,
            false,
            100,
            120,
            90,
            60,
            180,
            60,
            0,
            0,
            0,
            0
    ),

    GOLD_MINE(
            "Mina de Ouro Estratégica",
            StrategicAge.MANUFACTORUM_AGE,
            3,
            false,
            true,
            150,
            180,
            120,
            90,
            90,
            60,
            40,
            0,
            0,
            0
    ),

    TRADE_DEPOT(
            "Entreposto Imperial",
            StrategicAge.MANUFACTORUM_AGE,
            4,
            false,
            true,
            160,
            120,
            120,
            80,
            120,
            90,
            40,
            0,
            0,
            0
    ),

    COMMAND_BASTION(
            "Bastião de Comando Astartes",
            StrategicAge.ASTARTES_AGE,
            5,
            true,
            false,
            260,
            260,
            220,
            160,
            220,
            180,
            120,
            160,
            120,
            0
    );

    private final String displayName;
    private final StrategicAge requiredAge;
    private final int footprintRadius;
    private final boolean military;
    private final boolean economy;

    private final int foodCost;
    private final int ironCost;
    private final int scrapCost;
    private final int coalCost;
    private final int ferrocreteCost;
    private final int plasteelCost;
    private final int promethiumCost;
    private final int ceramiteCost;
    private final int crusadiumCost;
    private final int adamantiumCost;

    StrategicConstructionType(
            String displayName,
            StrategicAge requiredAge,
            int footprintRadius,
            boolean military,
            boolean economy,
            int foodCost,
            int ironCost,
            int scrapCost,
            int coalCost,
            int ferrocreteCost,
            int plasteelCost,
            int promethiumCost,
            int ceramiteCost,
            int crusadiumCost,
            int adamantiumCost
    ) {
        this.displayName = displayName;
        this.requiredAge = requiredAge;
        this.footprintRadius = footprintRadius;
        this.military = military;
        this.economy = economy;
        this.foodCost = foodCost;
        this.ironCost = ironCost;
        this.scrapCost = scrapCost;
        this.coalCost = coalCost;
        this.ferrocreteCost = ferrocreteCost;
        this.plasteelCost = plasteelCost;
        this.promethiumCost = promethiumCost;
        this.ceramiteCost = ceramiteCost;
        this.crusadiumCost = crusadiumCost;
        this.adamantiumCost = adamantiumCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getFootprintRadius() {
        return footprintRadius;
    }

    public boolean isMilitary() {
        return military;
    }

    public boolean isEconomy() {
        return economy;
    }

    public boolean isUnlocked(StrategicAge age) {
        return age.ordinal() >= requiredAge.ordinal();
    }

    public int foodCost() {
        return foodCost;
    }

    public int ironCost() {
        return ironCost;
    }

    public int scrapCost() {
        return scrapCost;
    }

    public int coalCost() {
        return coalCost;
    }

    public int ferrocreteCost() {
        return ferrocreteCost;
    }

    public int plasteelCost() {
        return plasteelCost;
    }

    public int promethiumCost() {
        return promethiumCost;
    }

    public int ceramiteCost() {
        return ceramiteCost;
    }

    public int crusadiumCost() {
        return crusadiumCost;
    }

    public int adamantiumCost() {
        return adamantiumCost;
    }
}