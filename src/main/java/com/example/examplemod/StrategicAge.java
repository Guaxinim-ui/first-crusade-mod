package com.example.examplemod;

public enum StrategicAge {
    OUTPOST(
            "Posto Avançado",
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
    ),

    FORTIFIED_SETTLEMENT(
            "Assentamento Fortificado",
            500,
            380,
            240,
            160,
            0,
            0,
            0,
            0,
            0,
            0
    ),

    MANUFACTORUM_AGE(
            "Era Manufactorum",
            900,
            650,
            480,
            320,
            260,
            120,
            80,
            0,
            0,
            0
    ),

    ASTARTES_AGE(
            "Era Astartes",
            1400,
            1100,
            800,
            520,
            500,
            360,
            260,
            180,
            120,
            0
    ),

    PLANETARY_WAR(
            "Guerra Planetária",
            2200,
            1800,
            1400,
            950,
            900,
            800,
            600,
            480,
            300,
            180
    );

    private final String displayName;

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

    StrategicAge(
            String displayName,
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

    public boolean hasNext() {
        return ordinal() < values().length - 1;
    }

    public StrategicAge next() {
        if (!hasNext()) {
            return this;
        }

        return values()[ordinal() + 1];
    }

    public boolean canPay(StrategicResourceBank bank) {
        return bank.canAfford(
                foodCost,
                ironCost,
                scrapCost,
                coalCost,
                ferrocreteCost,
                plasteelCost,
                promethiumCost,
                ceramiteCost,
                crusadiumCost,
                adamantiumCost
        );
    }

    public void spend(StrategicResourceBank bank) {
        bank.spend(
                foodCost,
                ironCost,
                scrapCost,
                coalCost,
                ferrocreteCost,
                plasteelCost,
                promethiumCost,
                ceramiteCost,
                crusadiumCost,
                adamantiumCost
        );
    }
}