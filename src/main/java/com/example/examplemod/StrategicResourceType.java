package com.example.examplemod;

public enum StrategicResourceType {
    FOOD("Food"),
    IRON("Iron"),
    SCRAP("Scrap"),
    COAL("Coal"),

    // Bodies, not materials. The Crusade's supply lines carry replacements the same way they carry
    // ammunition, and a Hive World's whole strategic purpose is producing them — which is why this
    // is a supply resource rather than a number on a city.
    MANPOWER("Manpower"),

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