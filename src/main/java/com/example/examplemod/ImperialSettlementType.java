package com.example.examplemod;

public enum ImperialSettlementType {
    OUTPOST("Imperial Outpost", 1, 3, 16, false),
    FORTRESS("Imperial Fortress", 2, 4, 32, false),
    CITY("Imperial City", 3, 5, 64, false),
    CAPITAL("Imperial Capital", 5, 5, 128, true);

    private final String displayName;
    private final int startingLevel;
    private final int maxLevel;
    private final int baseMilitaryCapacityBonus;
    private final boolean protectedFromPlayerOwnership;

    ImperialSettlementType(String displayName, int startingLevel, int maxLevel, int baseMilitaryCapacityBonus, boolean protectedFromPlayerOwnership) {
        this.displayName = displayName;
        this.startingLevel = startingLevel;
        this.maxLevel = maxLevel;
        this.baseMilitaryCapacityBonus = baseMilitaryCapacityBonus;
        this.protectedFromPlayerOwnership = protectedFromPlayerOwnership;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getStartingLevel() {
        return this.startingLevel;
    }

    public int getMaxLevel() {
        return this.maxLevel;
    }

    public int getBaseMilitaryCapacityBonus() {
        return this.baseMilitaryCapacityBonus;
    }

    public boolean isProtectedFromPlayerOwnership() {
        return this.protectedFromPlayerOwnership;
    }

    public boolean isCapital() {
        return this == CAPITAL;
    }

    public static ImperialSettlementType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return OUTPOST;
        }

        for (ImperialSettlementType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }

        return OUTPOST;
    }
}