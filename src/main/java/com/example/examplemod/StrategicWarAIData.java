package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The strategic bookkeeping of one world: a record per Imperial settlement, a record per Ork camp,
 * and the construction projects queued on that world.
 *
 * <h2>Stored per level, not on the overworld</h2>
 *
 * Records are keyed by packed {@link BlockPos}, which is unique inside one world and meaningless
 * across worlds. This used to resolve to the overworld's data storage no matter which level it was
 * handed, so a city on Macragge and an Ork camp at the same coordinates on Armageddon shared one
 * key — and {@link #syncWithWorldMap} then pruned every record whose settlement was not on whatever
 * planet happened to be ticking, so each planet deleted the others' records in turn.
 *
 * <p>The fix is to store it where the settlements are. Unlike {@link WorldWarMapData}, nothing here
 * has to be readable from another planet: the War Table draws positions and ownership (which the war
 * map holds globally), never a governor's current plan.
 *
 * <p><b>Old saves:</b> the pre-campaign file sat on the overworld and every planet now reads a fresh
 * one. Nothing is lost that does not come back: records are recreated by {@link #syncWithWorldMap}
 * from the war map, and a settlement's Age is rewritten by its Core on the next tick
 * ({@code applyStrategicAgeForLevel}). What does reset is the accumulated strategic resource bank of
 * each camp — half a minute of income.
 */
public class StrategicWarAIData extends SavedData {
    private static final String NAME = "firstcrusade_strategic_war_ai";

    private final Map<Long, StrategicSettlementRecord> imperialSettlements = new HashMap<>();
    private final Map<Long, OrkStrategicRecord> orkCamps = new HashMap<>();
    private final List<StrategicConstructionProject> projects = new ArrayList<>();

    public StrategicWarAIData() {
    }

    public static StrategicWarAIData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StrategicWarAIData::load,
                StrategicWarAIData::new,
                NAME
        );
    }

    public static StrategicWarAIData load(CompoundTag tag) {
        StrategicWarAIData data = new StrategicWarAIData();

        ListTag imperialList = tag.getList("Imperials", Tag.TAG_COMPOUND);

        for (int i = 0; i < imperialList.size(); i++) {
            StrategicSettlementRecord record = StrategicSettlementRecord.load(imperialList.getCompound(i));
            data.imperialSettlements.put(record.getPackedPos(), record);
        }

        ListTag orkList = tag.getList("Orks", Tag.TAG_COMPOUND);

        for (int i = 0; i < orkList.size(); i++) {
            OrkStrategicRecord record = OrkStrategicRecord.load(orkList.getCompound(i));
            data.orkCamps.put(record.getPackedPos(), record);
        }

        ListTag projectList = tag.getList("Projects", Tag.TAG_COMPOUND);

        for (int i = 0; i < projectList.size(); i++) {
            data.projects.add(StrategicConstructionProject.load(projectList.getCompound(i)));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag imperialList = new ListTag();

        for (StrategicSettlementRecord record : imperialSettlements.values()) {
            imperialList.add(record.save());
        }

        tag.put("Imperials", imperialList);

        ListTag orkList = new ListTag();

        for (OrkStrategicRecord record : orkCamps.values()) {
            orkList.add(record.save());
        }

        tag.put("Orks", orkList);

        ListTag projectList = new ListTag();

        for (StrategicConstructionProject project : projects) {
            projectList.add(project.save());
        }

        tag.put("Projects", projectList);

        return tag;
    }

    public void syncWithWorldMap(ServerLevel level, WorldWarMapData map) {
        HashSet<Long> currentCities = new HashSet<>();
        HashSet<Long> currentCamps = new HashSet<>();

        // This level's settlements only. The war map is global; these records are not.
        for (long packed : map.getCities(level)) {
            currentCities.add(packed);

            imperialSettlements.computeIfAbsent(
                    packed,
                    key -> new StrategicSettlementRecord(level, BlockPos.of(key))
            );
        }

        for (long packed : map.getCamps(level)) {
            currentCamps.add(packed);

            orkCamps.computeIfAbsent(
                    packed,
                    key -> new OrkStrategicRecord(level, BlockPos.of(key))
            );
        }

        imperialSettlements.keySet().removeIf(key -> !currentCities.contains(key));
        orkCamps.keySet().removeIf(key -> !currentCamps.contains(key));

        setDirty();
    }

    public StrategicSettlementRecord getImperial(BlockPos pos) {
        return imperialSettlements.get(pos.asLong());
    }

    public StrategicSettlementRecord getOrCreateImperial(ServerLevel level, BlockPos pos) {
        StrategicSettlementRecord record = imperialSettlements.get(pos.asLong());

        if (record == null) {
            record = new StrategicSettlementRecord(level, pos);
            imperialSettlements.put(pos.asLong(), record);
            setDirty();
        }

        return record;
    }

    public OrkStrategicRecord getOrkCamp(BlockPos pos) {
        return orkCamps.get(pos.asLong());
    }

    public OrkStrategicRecord getOrCreateOrkCamp(ServerLevel level, BlockPos pos) {
        OrkStrategicRecord record = orkCamps.get(pos.asLong());

        if (record == null) {
            record = new OrkStrategicRecord(level, pos);
            orkCamps.put(pos.asLong(), record);
            setDirty();
        }

        return record;
    }

    public void removeImperial(BlockPos pos) {
        if (imperialSettlements.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public void removeOrkCamp(BlockPos pos) {
        if (orkCamps.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public Iterable<StrategicSettlementRecord> getImperialSettlements() {
        return imperialSettlements.values();
    }

    public Iterable<OrkStrategicRecord> getOrkCamps() {
        return orkCamps.values();
    }

    public List<StrategicConstructionProject> getProjects() {
        return projects;
    }

    public void addProject(StrategicConstructionProject project) {
        projects.add(project);
        setDirty();
    }

    public void removeProject(StrategicConstructionProject project) {
        if (projects.remove(project)) {
            setDirty();
        }
    }

    public int countActiveProjectsForCity(BlockPos corePos) {
        int count = 0;

        for (StrategicConstructionProject project : projects) {
            if (project.getCorePos().equals(corePos)) {
                count++;
            }
        }

        return count;
    }

    public boolean hasActiveProjectAt(BlockPos sitePos) {
        for (StrategicConstructionProject project : projects) {
            if (project.getSitePos().equals(sitePos)) {
                return true;
            }
        }

        return false;
    }

    public int getImperialSettlementCount() {
        return imperialSettlements.size();
    }

    public int getOrkCampCount() {
        return orkCamps.size();
    }

    public void reset() {
        imperialSettlements.clear();
        orkCamps.clear();
        projects.clear();
        setDirty();
    }
}