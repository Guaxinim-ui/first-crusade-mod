package com.example.examplemod;

/**
 * The standing order a squad is executing. Orders are issued (and re-issued) periodically by the
 * {@link CityMilitaryManager}; individual troops keep their normal combat goals, so an order is
 * about WHERE the squad should be, not micro-managing each swing.
 */
public enum CitySquadOrder {
    /** Hold position around the Command Core (defensive ring). */
    HOLD_CORE("Defender o Núcleo"),

    /** Circulate the settlement perimeter (delegated to the patrol manager today). */
    PATROL_CITY("Patrulhar a Cidade"),

    /** March in formation behind the squad's commander. */
    FOLLOW_LEADER("Seguir o Comandante"),

    /** Assault a position (enemy camp / structure). */
    ATTACK_POSITION("Atacar Posição"),

    /** Guard a specific friendly structure. */
    DEFEND_POSITION("Defender Posição"),

    /** March back to the Command Core and stand down. */
    RETURN_TO_BASE("Voltar à Base"),

    /** Squad got too dispersed — collapse back onto the commander before resuming. */
    REGROUP("Reagrupar");

    private final String displayName;

    CitySquadOrder(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
