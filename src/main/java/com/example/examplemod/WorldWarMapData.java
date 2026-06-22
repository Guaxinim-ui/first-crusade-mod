package com.example.examplemod;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide register of every settlement in the war: the positions of all Imperial Command Cores
 * (cities) and all Ork Camps. It lets the Core's War Table draw the whole planet's strategic picture
 * — every city and camp, wherever it is — instead of only what is within a small radius. Stored on
 * the overworld's data storage (same pattern as {@link WaaaghOverlordData}).
 *
 * Entries are self-registered: a city/camp adds itself while it ticks (so existing and new ones are
 * covered, even after their chunk reloads), and removes itself when its block is destroyed. Positions
 * for settlements in unloaded chunks stay on the map until they are actually torn down.
 */
public class WorldWarMapData extends SavedData {
    private static final String NAME = "firstcrusade_warmap";

    private final Set<Long> cities = new HashSet<>();
    private final Set<Long> camps = new HashSet<>();

    public WorldWarMapData() {
    }

    public static WorldWarMapData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldWarMapData::load, WorldWarMapData::new, NAME);
    }

    public static WorldWarMapData load(CompoundTag tag) {
        WorldWarMapData data = new WorldWarMapData();

        for (long packed : tag.getLongArray("Cities")) {
            data.cities.add(packed);
        }

        for (long packed : tag.getLongArray("Camps")) {
            data.camps.add(packed);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("Cities", this.cities.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("Camps", this.camps.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public void recordCity(BlockPos pos) {
        if (this.cities.add(pos.asLong())) {
            setDirty();
        }
    }

    public void recordCamp(BlockPos pos) {
        if (this.camps.add(pos.asLong())) {
            setDirty();
        }
    }

    public void removeCity(BlockPos pos) {
        if (this.cities.remove(pos.asLong())) {
            setDirty();
        }
    }

    public void removeCamp(BlockPos pos) {
        if (this.camps.remove(pos.asLong())) {
            setDirty();
        }
    }

    public Set<Long> getCities() {
        return this.cities;
    }

    public Set<Long> getCamps() {
        return this.camps;
    }
}
