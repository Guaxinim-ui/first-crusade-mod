package com.example.examplemod.crusade;

import java.util.UUID;

import com.example.examplemod.ImperialTroopGrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * One soldier's service history — the thing that makes him a person rather than a spawn.
 *
 * <h2>Why the record outlives the entity</h2>
 *
 * The entity carries what the entity needs to fight: rank, merit, Ork tally. The record carries what
 * the <i>campaign</i> needs to remember, and it has to survive the soldier. When Marius Holt dies on
 * a raid his entity is gone that tick, and the only reason the Crusade can still name him, count his
 * raids and put him on the roll of the dead is that this object was never stored on him.
 *
 * <h2>Mutable, and edited only on events</h2>
 *
 * Nothing here is recalculated. Fields move when a kill lands, a raid resolves or a promotion
 * happens — that is the whole update schedule. A garrison standing idle does not touch this class.
 */
public final class ImperialSoldierRecord {

    /** The soldier's entity UUID, and the key he is filed under. */
    private final UUID id;

    /** Cached from {@link ImperialSoldierNames}, because a dead man has no UUID to ask about. */
    private final String name;

    private ImperialRegimentType regiment;
    private ImperialTroopGrade grade = ImperialTroopGrade.LINE;

    private int orkKills;
    private int eliteKills;
    private int warbossAssists;

    private int raidsJoined;
    private int raidsWon;

    /** Game time when he was enlisted, and when he fell. {@code 0} means "still serving". */
    private long enlistedAt;
    private long fellAt;

    /** Free-form, short, and only set on death: "assalto ao Camp Ork" and so on. */
    private String fate = "";

    public ImperialSoldierRecord(UUID id, String name, ImperialRegimentType regiment, long enlistedAt) {
        this.id = id;
        this.name = name;
        this.regiment = regiment == null ? ImperialRegimentType.CRUSADE_GENERIC : regiment;
        this.enlistedAt = enlistedAt;
    }

    // ==================================================================== identity

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public ImperialRegimentType regiment() {
        return this.regiment;
    }

    public void setRegiment(ImperialRegimentType regiment) {
        this.regiment = regiment == null ? ImperialRegimentType.CRUSADE_GENERIC : regiment;
    }

    public ImperialTroopGrade grade() {
        return this.grade;
    }

    public void setGrade(ImperialTroopGrade grade) {
        this.grade = grade == null ? ImperialTroopGrade.LINE : grade;
    }

    /** "Trooper", "Veteran", "Sergeant" — the title that goes in front of the name. */
    public Component title() {
        return Component.translatable("soldier.firstcrusade.title." + this.grade.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The full "Sergeant Marius Holt" line, for a nameplate or a roster row. */
    public Component displayName() {
        return Component.translatable("soldier.firstcrusade.full_name", this.title(), this.name);
    }

    // ==================================================================== tallies

    public int orkKills() {
        return this.orkKills;
    }

    public int eliteKills() {
        return this.eliteKills;
    }

    public int warbossAssists() {
        return this.warbossAssists;
    }

    public int raidsJoined() {
        return this.raidsJoined;
    }

    public int raidsWon() {
        return this.raidsWon;
    }

    public void addOrkKill() {
        this.orkKills++;
    }

    public void addEliteKill() {
        this.orkKills++;
        this.eliteKills++;
    }

    public void addWarbossAssist() {
        this.warbossAssists++;
    }

    public void addRaid(boolean won) {
        this.raidsJoined++;
        if (won) {
            this.raidsWon++;
        }
    }

    // ==================================================================== service

    public long enlistedAt() {
        return this.enlistedAt;
    }

    public boolean isFallen() {
        return this.fellAt > 0L;
    }

    public long fellAt() {
        return this.fellAt;
    }

    public String fate() {
        return this.fate;
    }

    /**
     * Marks the soldier dead, once.
     *
     * <p>Guarded because permanence is the point: a record that could be re-killed is a record whose
     * death toll drifts, and the roll of the dead is one of the few things in this system a player
     * is meant to trust.
     */
    public void markFallen(long gameTime, String fate) {
        if (this.isFallen()) {
            return;
        }

        this.fellAt = Math.max(1L, gameTime);
        this.fate = fate == null ? "" : fate;
    }

    /** Ticks served. For a fallen soldier this stops at the moment he fell. */
    public long serviceTicks(long now) {
        long end = this.isFallen() ? this.fellAt : now;
        return Math.max(0L, end - this.enlistedAt);
    }

    // ==================================================================== persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("Id", this.id);
        tag.putString("Name", this.name);
        tag.putString("Regiment", this.regiment.id());
        tag.putString("Grade", this.grade.name());

        tag.putInt("OrkKills", this.orkKills);
        tag.putInt("EliteKills", this.eliteKills);
        tag.putInt("WarbossAssists", this.warbossAssists);
        tag.putInt("RaidsJoined", this.raidsJoined);
        tag.putInt("RaidsWon", this.raidsWon);

        tag.putLong("EnlistedAt", this.enlistedAt);
        tag.putLong("FellAt", this.fellAt);
        tag.putString("Fate", this.fate);

        return tag;
    }

    public static ImperialSoldierRecord load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        String name = tag.getString("Name");

        ImperialSoldierRecord record = new ImperialSoldierRecord(
                id,
                name.isEmpty() ? ImperialSoldierNames.forUuid(id) : name,
                ImperialRegimentType.byId(tag.getString("Regiment")),
                tag.getLong("EnlistedAt"));

        record.grade = gradeByName(tag.getString("Grade"));
        record.orkKills = tag.getInt("OrkKills");
        record.eliteKills = tag.getInt("EliteKills");
        record.warbossAssists = tag.getInt("WarbossAssists");
        record.raidsJoined = tag.getInt("RaidsJoined");
        record.raidsWon = tag.getInt("RaidsWon");
        record.fellAt = tag.getLong("FellAt");
        record.fate = tag.getString("Fate");

        return record;
    }

    private static ImperialTroopGrade gradeByName(String name) {
        for (ImperialTroopGrade grade : ImperialTroopGrade.values()) {
            if (grade.name().equals(name)) {
                return grade;
            }
        }

        return ImperialTroopGrade.LINE;
    }
}
