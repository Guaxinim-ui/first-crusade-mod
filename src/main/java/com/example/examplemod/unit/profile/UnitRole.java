package com.example.examplemod.unit.profile;

/**
 * The job a unit does on the battlefield. This is deliberately about FUNCTION, not about faction or
 * model: an Ork Shoota Boy and a Guardsman Rifleman are both {@link #LINE_INFANTRY} even though they
 * fight nothing alike, because they occupy the same slot in a battle line and should be targeted
 * with the same priority.
 *
 * <p>Two things read this enum:</p>
 * <ul>
 *   <li>{@link com.example.examplemod.ai.combat.FCTargetPriority} — a medic is worth shooting before a
 *       rifleman, an anti-tank gunner before a medic.</li>
 *   <li>Formation code — only {@link #isLeader()} roles may anchor a formation, and
 *       {@link #preferredFormationSlot()} decides whether a unit walks in the front rank, the
 *       support rank or trails behind.</li>
 * </ul>
 *
 * <p>Roles do NOT dictate stats. Two LINE_INFANTRY units can have wildly different
 * {@link FCCombatProfile}s; the role only says what the unit is FOR.</p>
 */
public enum UnitRole {
    /** Basic rifle-armed trooper. The backbone of any formation, walks in the front rank. */
    LINE_INFANTRY(2, FormationSlot.FRONT, false),

    /** Close-combat specialist with no meaningful ranged option. Front rank, closes distance. */
    ASSAULT_INFANTRY(2, FormationSlot.FRONT, false),

    /**
     * Slow, heavy weapon. Must stop (or nearly stop) to fire, so it wants to stand behind the front
     * rank rather than in it — hence {@link FormationSlot#SUPPORT}.
     */
    HEAVY_WEAPON(4, FormationSlot.SUPPORT, false),

    /** Long-range precision shooter. Hangs back, picks targets, avoids the melee. */
    MARKSMAN(4, FormationSlot.SUPPORT, false),

    /** Healer. High-value target: killing the medic makes the whole squad brittle. */
    MEDIC(6, FormationSlot.REAR, false),

    /**
     * Repairs vehicles and structures. Same high-value logic as a medic but for machines, and
     * cowardly — a Grot Mek runs rather than fights.
     */
    TECH_SUPPORT(6, FormationSlot.REAR, false),

    /** Squad leader. Anchors a formation and issues orders. Highest-priority target. */
    SQUAD_LEADER(8, FormationSlot.FRONT, true),

    /** Commands multiple squads. Also a formation anchor, but a rarer and tougher one. */
    COMMANDER(9, FormationSlot.SUPPORT, true),

    /** Elite heavy infantry — Meganobz, Custodes. Front rank, absorbs damage. */
    ELITE(3, FormationSlot.FRONT, false),

    /** Walker or vehicle. Front rank, does not take orders from infantry leaders. */
    WALKER(5, FormationSlot.FRONT, false),

    /** Non-combatant. Never targeted by faction logic, never joins a formation. */
    CIVILIAN(0, FormationSlot.REAR, false);

    /** Where a unit stands relative to its formation leader. */
    public enum FormationSlot {
        /** Front rank — closest to the enemy. */
        FRONT,
        /** Second rank — firing over or between the front rank. */
        SUPPORT,
        /** Behind everyone, out of the fire lanes. */
        REAR
    }

    private final int targetPriority;
    private final FormationSlot formationSlot;
    private final boolean leader;

    UnitRole(int targetPriority, FormationSlot formationSlot, boolean leader) {
        this.targetPriority = targetPriority;
        this.formationSlot = formationSlot;
        this.leader = leader;
    }

    /**
     * How badly an enemy wants this unit dead, 0 (ignore) to 9 (kill first). Used as a weight, not
     * an absolute ordering — {@link com.example.examplemod.ai.combat.FCTargetPriority} still folds in
     * distance and line of sight, so a distant commander loses to an adjacent rifleman.
     */
    public int targetPriority() {
        return this.targetPriority;
    }

    /** Which rank of a formation this role belongs in. */
    public FormationSlot preferredFormationSlot() {
        return this.formationSlot;
    }

    /** Whether units of this role can anchor a formation and give orders. */
    public boolean isLeader() {
        return this.leader;
    }

    /** Whether this role should be excluded from all combat targeting. */
    public boolean isNonCombatant() {
        return this == CIVILIAN;
    }
}
