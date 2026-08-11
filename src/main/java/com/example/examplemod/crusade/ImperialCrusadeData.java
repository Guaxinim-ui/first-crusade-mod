package com.example.examplemod.crusade;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where the Crusade's people are kept: one {@link ImperialSoldierRoster} per Command Core.
 *
 * <h2>Why saved data and not the Core block entity</h2>
 *
 * The obvious home is the Core itself, and the design says as much. Two things argue against it. A
 * block entity's data lives in its chunk, so a roster is only readable while that chunk is loaded —
 * and the Crusade record, the memorial and a future Spaceport screen all want to read a base the
 * player is nowhere near. And the Core is already a three-thousand-line class the project has a
 * standing rule against inflating.
 *
 * <p>So the roster is keyed by the Core's position, which is the Core's identity anyway. A soldier
 * knows his Core position, so finding his record is two map lookups from anywhere, loaded or not,
 * with no scan.
 *
 * <h2>Cost</h2>
 *
 * Nothing here ticks. Every method is called from an event — a soldier binding to a base, a kill, a
 * death, a raid resolving. A world full of idle garrisons never touches this class.
 */
public class ImperialCrusadeData extends SavedData {
    private static final String NAME = "firstcrusade_crusade";

    /** Core position (packed long) -> that base's roster. */
    private final Map<Long, ImperialSoldierRoster> rosters = new HashMap<>();

    /** The player's name for this Crusade. Empty until they choose one. */
    private String crusadeName = "";

    public ImperialCrusadeData() {
    }

    /**
     * Always the overworld's storage, whatever level asks.
     *
     * <p>A Crusade spans planets — a base on Armageddon and a base on Cadia belong to the same war.
     * Storing per-dimension would give the player two unrelated Crusades and no way to see both.
     */
    public static ImperialCrusadeData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                ImperialCrusadeData::load, ImperialCrusadeData::new, NAME);
    }

    // ==================================================================== rosters

    /** The roster for this base, created on first use. */
    public ImperialSoldierRoster roster(BlockPos corePos) {
        return this.rosters.computeIfAbsent(corePos.asLong(), key -> new ImperialSoldierRoster());
    }

    /** The roster for this base, or {@code null} — for readers that must not create one. */
    public ImperialSoldierRoster peek(BlockPos corePos) {
        return corePos == null ? null : this.rosters.get(corePos.asLong());
    }

    /** Forgets a base entirely. Called when a Core is destroyed, not when its garrison dies. */
    public void removeBase(BlockPos corePos) {
        if (this.rosters.remove(corePos.asLong()) != null) {
            this.setDirty();
        }
    }

    public int baseCount() {
        return this.rosters.size();
    }

    // ==================================================================== crusade identity

    public String crusadeName() {
        return this.crusadeName;
    }

    /**
     * Names the Crusade.
     *
     * <p>Trimmed and length-capped here rather than at the call site, because this is the only place
     * that can promise the stored value is sane no matter who sets it.
     */
    public void setCrusadeName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.length() > MAX_CRUSADE_NAME) {
            clean = clean.substring(0, MAX_CRUSADE_NAME);
        }

        this.crusadeName = clean;
        this.setDirty();
    }

    public static final int MAX_CRUSADE_NAME = 32;

    // ==================================================================== persistence

    public static ImperialCrusadeData load(CompoundTag tag) {
        ImperialCrusadeData data = new ImperialCrusadeData();
        data.crusadeName = tag.getString("CrusadeName");

        ListTag list = tag.getList("Rosters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.rosters.put(entry.getLong("Core"),
                    ImperialSoldierRoster.load(entry.getCompound("Roster")));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("CrusadeName", this.crusadeName);

        ListTag list = new ListTag();
        for (Map.Entry<Long, ImperialSoldierRoster> entry : this.rosters.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putLong("Core", entry.getKey());
            row.put("Roster", entry.getValue().save());
            list.add(row);
        }
        tag.put("Rosters", list);

        return tag;
    }
}
