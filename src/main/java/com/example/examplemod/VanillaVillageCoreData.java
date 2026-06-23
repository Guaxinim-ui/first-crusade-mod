package com.example.examplemod;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class VanillaVillageCoreData extends SavedData {
    private static final String NAME = "firstcrusade_vanilla_village_core_data";

    private final Set<Long> processedCells = new HashSet<>();

    public VanillaVillageCoreData() {
    }

    public static VanillaVillageCoreData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();

        return overworld.getDataStorage().computeIfAbsent(
                VanillaVillageCoreData::load,
                VanillaVillageCoreData::new,
                NAME
        );
    }

    public static VanillaVillageCoreData load(CompoundTag tag) {
        VanillaVillageCoreData data = new VanillaVillageCoreData();

        long[] savedCells = tag.getLongArray("ProcessedCells");

        for (long cell : savedCells) {
            data.processedCells.add(cell);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        long[] cells = processedCells.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("ProcessedCells", cells);

        return tag;
    }

    public boolean hasProcessed(int cellX, int cellZ) {
        return processedCells.contains(pack(cellX, cellZ));
    }

    public void markProcessed(int cellX, int cellZ) {
        processedCells.add(pack(cellX, cellZ));
        setDirty();
    }

    private static long pack(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }
}