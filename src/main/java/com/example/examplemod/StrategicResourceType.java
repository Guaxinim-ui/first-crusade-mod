package com.example.examplemod;

public enum StrategicResourceType {
    FOOD("Food"),
    IRON("Iron"),
    SCRAP("Scrap"),
    COAL("Coal"),

    FERROCRETE("Ferrocrete"),
    PLASTEEL("Plasteel"),
    PROMETHIUM("Promethium"),
    CERAMITE("Ceramite"),
    CRUSADIUM("Crusadium"),
    ADAMANTIUM("Adamantium"),

    ORK_SCRAP("Ork Scrap"),
    TEEF("Teef"),
    WAAAGH("WAAAGH!");

    private final String displayName;

    StrategicResourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}