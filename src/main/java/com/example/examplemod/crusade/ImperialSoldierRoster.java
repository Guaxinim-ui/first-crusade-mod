package com.example.examplemod.crusade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One base's people: who serves there, who died there, and what the base is called.
 *
 * <h2>Two lists, and the second one only grows</h2>
 *
 * The serving roster is keyed by UUID so a soldier finds his own record in one lookup — no scan, at
 * any garrison size. The fallen are a list in the order they died, because that is how a memorial
 * reads and because nothing ever looks one up by name.
 *
 * <p>The fallen list is capped ({@link ImperialSoldierBalance#MAX_REMEMBERED_FALLEN}); a long
 * campaign would otherwise turn a base's saved data into a graveyard. The <i>counters</i> are not
 * capped — {@link #totalFallen()} keeps counting after the names stop being kept, so the Crusade
 * record stays truthful even when the memorial has scrolled.
 */
public final class ImperialSoldierRoster {

    private ImperialRegimentType regiment = ImperialRegimentType.CRUSADE_GENERIC;

    /** Insertion-ordered so the roster reads in the order soldiers joined, which is a career order. */
    private final Map<UUID, ImperialSoldierRecord> serving = new LinkedHashMap<>();

    private final List<ImperialSoldierRecord> fallen = new ArrayList<>();

    /** Every death this base has ever taken, including names no longer kept. */
    private int totalFallen;

    // ==================================================================== regiment

    public ImperialRegimentType regiment() {
        return this.regiment;
    }

    public void setRegiment(ImperialRegimentType regiment) {
        this.regiment = regiment == null ? ImperialRegimentType.CRUSADE_GENERIC : regiment;
    }

    // ==================================================================== serving

    public Collection<ImperialSoldierRecord> serving() {
        return this.serving.values();
    }

    public ImperialSoldierRecord record(UUID id) {
        return this.serving.get(id);
    }

    /**
     * Enlists a soldier, or hands back the record he already has.
     *
     * <p>Idempotent on purpose: a soldier is bound to his base every time he is spawned, every time
     * he comes home from a raid and every time an old save is migrated. If this created a second
     * record on the second call, a returning veteran would come back a recruit.
     */
    public ImperialSoldierRecord enlist(UUID id, long gameTime) {
        ImperialSoldierRecord existing = this.serving.get(id);
        if (existing != null) {
            return existing;
        }

        ImperialSoldierRecord record = new ImperialSoldierRecord(
                id, ImperialSoldierNames.forUuid(id), this.regiment, gameTime);

        this.serving.put(id, record);
        return record;
    }

    /**
     * Moves a soldier onto the roll of the dead.
     *
     * <p>Returns the record so the caller can announce it. Returns {@code null} when the soldier was
     * not on this roster — something else's casualty, and not this base's to mourn.
     */
    public ImperialSoldierRecord fall(UUID id, long gameTime, String fate) {
        ImperialSoldierRecord record = this.serving.remove(id);
        if (record == null) {
            return null;
        }

        record.markFallen(gameTime, fate);
        this.fallen.add(record);
        this.totalFallen++;

        while (this.fallen.size() > ImperialSoldierBalance.MAX_REMEMBERED_FALLEN) {
            this.fallen.remove(0);
        }

        return record;
    }

    /** Drops a soldier without killing him — used when a base is abandoned, not when one dies. */
    public void discharge(UUID id) {
        this.serving.remove(id);
    }

    // ==================================================================== counts

    public int servingCount() {
        return this.serving.size();
    }

    public List<ImperialSoldierRecord> fallen() {
        return this.fallen;
    }

    public int totalFallen() {
        return this.totalFallen;
    }

    /** How many currently serving soldiers hold this grade. Drives the sergeant cap. */
    public int countAtGrade(com.example.examplemod.ImperialTroopGrade grade) {
        int count = 0;

        for (ImperialSoldierRecord record : this.serving.values()) {
            if (record.grade() == grade) {
                count++;
            }
        }

        return count;
    }

    // ==================================================================== persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Regiment", this.regiment.id());
        tag.putInt("TotalFallen", this.totalFallen);

        ListTag servingList = new ListTag();
        for (ImperialSoldierRecord record : this.serving.values()) {
            servingList.add(record.save());
        }
        tag.put("Serving", servingList);

        ListTag fallenList = new ListTag();
        for (ImperialSoldierRecord record : this.fallen) {
            fallenList.add(record.save());
        }
        tag.put("Fallen", fallenList);

        return tag;
    }

    public static ImperialSoldierRoster load(CompoundTag tag) {
        ImperialSoldierRoster roster = new ImperialSoldierRoster();
        roster.regiment = ImperialRegimentType.byId(tag.getString("Regiment"));
        roster.totalFallen = tag.getInt("TotalFallen");

        ListTag servingList = tag.getList("Serving", Tag.TAG_COMPOUND);
        for (int i = 0; i < servingList.size(); i++) {
            ImperialSoldierRecord record = ImperialSoldierRecord.load(servingList.getCompound(i));
            roster.serving.put(record.id(), record);
        }

        ListTag fallenList = tag.getList("Fallen", Tag.TAG_COMPOUND);
        for (int i = 0; i < fallenList.size(); i++) {
            roster.fallen.add(ImperialSoldierRecord.load(fallenList.getCompound(i)));
        }

        // A save written before the counter existed still knows how many names it kept.
        if (roster.totalFallen < roster.fallen.size()) {
            roster.totalFallen = roster.fallen.size();
        }

        return roster;
    }
}
