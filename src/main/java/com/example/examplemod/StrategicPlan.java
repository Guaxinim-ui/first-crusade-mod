package com.example.examplemod;

public enum StrategicPlan {
    ECONOMY("Economia"),
    DEFENSE("Defesa"),
    MILITARY("Militarização"),
    ATTACK("Ataque"),
    TECH_UP("Evoluir idade"),
    SURVIVE("Sobrevivência");

    private final String displayName;

    StrategicPlan(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}