package com.example.examplemod;

/**
 * The role a city squad was formed for. DEFENSE and ATTACK are actively managed by the
 * {@link CityMilitaryManager} today; PATROL duty is currently fulfilled by the
 * {@link ImperialPatrolManager} for every troop that is NOT in a squad, and RESERVE is
 * reserved for future reinforcement waves.
 */
public enum CitySquadType {
    DEFENSE("Esquadrão de Defesa"),
    PATROL("Esquadrão de Patrulha"),
    ATTACK("Esquadrão de Ataque"),
    RESERVE("Esquadrão de Reserva");

    private final String displayName;

    CitySquadType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
