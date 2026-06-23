package com.example.examplemod;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class VanillaVillageImperializationData extends SavedData {
    private static final String NAME = "firstcrusade_vanilla_village_imperialization";

    private final Set<Long> processedVillageCells = new HashSet<>();

    public VanillaVillageImperializationData() {
    }

    public static VanillaVillageImperializationData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();

        return overworld.getDataStorage().computeIfAbsent(
                VanillaVillageImperializationData::load,
                VanillaVillageImperializationData::new,
                NAME
        );
    }

    public static VanillaVillageImperializationData load(CompoundTag tag) {
        VanillaVillageImperializationData data = new VanillaVillageImperializationData();

        for (long key : tag.getLongArray("ProcessedVillageCells")) {
            data.processedVillageCells.add(key);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(
                "ProcessedVillageCells",
                processedVillageCells.stream().mapToLong(Long::longValue).toArray()
        );

        return tag;
    }

    public boolean hasProcessed(int cellX, int cellZ) {
        return processedVillageCells.contains(pack(cellX, cellZ));
    }

    public void markProcessed(int cellX, int cellZ) {
        if (processedVillageCells.add(pack(cellX, cellZ))) {
            setDirty();
        }
    }

    private static long pack(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }
}