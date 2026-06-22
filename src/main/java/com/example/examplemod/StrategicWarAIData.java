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

public class StrategicWarAIData extends SavedData {
    private static final String NAME = "firstcrusade_strategic_war_ai";

    private final Map<Long, StrategicSettlementRecord> imperialSettlements = new HashMap<>();
    private final Map<Long, OrkStrategicRecord> orkCamps = new HashMap<>();
    private final List<StrategicConstructionProject> projects = new ArrayList<>();

    public StrategicWarAIData() {
    }

    public static StrategicWarAIData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();

        return overworld.getDataStorage().computeIfAbsent(
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

        for (Object packedObject : map.getCities()) {
            if (!(packedObject instanceof Long packed)) {
                continue;
            }

            currentCities.add(packed);

            imperialSettlements.computeIfAbsent(
                    packed,
                    key -> new StrategicSettlementRecord(level, BlockPos.of(key))
            );
        }

        for (Object packedObject : map.getCamps()) {
            if (!(packedObject instanceof Long packed)) {
                continue;
            }

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