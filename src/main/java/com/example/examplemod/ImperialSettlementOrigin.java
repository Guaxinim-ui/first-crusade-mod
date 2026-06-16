package com.example.examplemod;

public enum ImperialSettlementOrigin {
    PLAYER_PLACED("Player Placed"),
    WORLD_GENERATED("World Generated"),
    VILLAGE_CONVERTED("Village Converted"),
    CAPITAL_GENERATED("Capital Generated");

    private final String displayName;

    ImperialSettlementOrigin(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public boolean isGeneratedByWorld() {
        return this == WORLD_GENERATED || this == VILLAGE_CONVERTED || this == CAPITAL_GENERATED;
    }

    public boolean isVillageConverted() {
        return this == VILLAGE_CONVERTED;
    }

    public boolean isCapitalGenerated() {
        return this == CAPITAL_GENERATED;
    }

    public static ImperialSettlementOrigin fromName(String name) {
        if (name == null || name.isEmpty()) {
            return PLAYER_PLACED;
        }

        for (ImperialSettlementOrigin origin : values()) {
            if (origin.name().equalsIgnoreCase(name)) {
                return origin;
            }
        }

        return PLAYER_PLACED;
    }
}