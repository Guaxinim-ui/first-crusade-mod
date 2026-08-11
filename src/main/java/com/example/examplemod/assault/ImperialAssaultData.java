package com.example.examplemod.assault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every live player-started raid, saved on the overworld.
 *
 * <h2>Why it is persisted at all</h2>
 *
 * A raid is holding real soldiers away from a real base. If the record vanished on shutdown, those
 * soldiers would be left standing in a field with an expedition tag and nothing to bring them back —
 * so the record outlives the session, and a restart resumes or unwinds the raid rather than
 * abandoning it. The same reasoning is why the reward flag lives on the record: a raid that was
 * already paid out cannot pay again after a reload.
 *
 * <h2>Two indexes, one truth</h2>
 *
 * Raids are keyed by id; the camp index is derived and rebuilt on load. Storing the camp lookup as a
 * second persisted map would be a second thing that can be wrong.
 */
public class ImperialAssaultData extends SavedData {
    private static final String NAME = "firstcrusade_imperial_assaults";

    private final Map<UUID, ImperialAssaultRecord> raids = new HashMap<>();

    /** camp position (packed) -> raid id. Derived; never saved. */
    private final Map<Long, UUID> byCamp = new HashMap<>();

    public ImperialAssaultData() {
    }

    public static ImperialAssaultData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage()
                .computeIfAbsent(ImperialAssaultData::load, ImperialAssaultData::new, NAME);
    }

    // ==================================================================== access

    public Map<UUID, ImperialAssaultRecord> raids() {
        return this.raids;
    }

    public boolean isEmpty() {
        return this.raids.isEmpty();
    }

    @Nullable
    public ImperialAssaultRecord byId(UUID raidId) {
        return this.raids.get(raidId);
    }

    @Nullable
    public ImperialAssaultRecord atCamp(BlockPos campPos) {
        UUID id = this.byCamp.get(campPos.asLong());
        return id == null ? null : this.raids.get(id);
    }

    /** The live raid this player started, if they have one. */
    @Nullable
    public ImperialAssaultRecord byInitiator(UUID playerId) {
        for (ImperialAssaultRecord record : this.raids.values()) {
            if (record.initiator().equals(playerId) && !record.phase().isOver()) {
                return record;
            }
        }
        return null;
    }

    public void add(ImperialAssaultRecord record) {
        this.raids.put(record.raidId(), record);
        this.byCamp.put(record.campPos().asLong(), record.raidId());
        setDirty();
    }

    public void remove(UUID raidId) {
        ImperialAssaultRecord removed = this.raids.remove(raidId);

        if (removed != null) {
            this.byCamp.remove(removed.campPos().asLong());
            setDirty();
        }
    }

    /** A snapshot, so the manager may finish and remove raids while walking them. */
    public List<ImperialAssaultRecord> snapshot() {
        return new ArrayList<>(this.raids.values());
    }

    public void markChanged() {
        setDirty();
    }

    // ==================================================================== persistence

    public static ImperialAssaultData load(CompoundTag tag) {
        ImperialAssaultData data = new ImperialAssaultData();

        ListTag list = tag.getList("Raids", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            ImperialAssaultRecord record = ImperialAssaultRecord.load(list.getCompound(i));

            if (record == null) {
                // One unreadable raid must not cost the server every other raid's troops.
                continue;
            }

            data.raids.put(record.raidId(), record);
            data.byCamp.put(record.campPos().asLong(), record.raidId());
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();

        for (ImperialAssaultRecord record : this.raids.values()) {
            list.add(record.save());
        }

        tag.put("Raids", list);
        return tag;
    }
}
