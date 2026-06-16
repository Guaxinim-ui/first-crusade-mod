package com.example.examplemod;

public enum ImperialResourceType {
    IRON("Iron"),
    COAL("Coal"),
    SCRAP("Scrap Metal"),
    GOLD("Gold"),
    EMERALD("Emerald"),
    CRUSADIUM("Crusadium");

    private final String displayName;

    ImperialResourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}