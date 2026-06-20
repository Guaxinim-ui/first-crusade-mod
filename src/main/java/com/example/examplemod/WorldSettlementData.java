package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers whether the world has already been seeded with its starting settlements (Imperial
 * cities + Ork camps). Stored on the overworld's data storage so the planet is only populated
 * once, the first time someone joins. See {@link WorldSettlementSeeder}.
 */
public class WorldSettlementData extends SavedData {
    private static final String NAME = "firstcrusade_settlements";

    private boolean seeded = false;

    public WorldSettlementData() {
    }

    public static WorldSettlementData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldSettlementData::load, WorldSettlementData::new, NAME);
    }

    public static WorldSettlementData load(CompoundTag tag) {
        WorldSettlementData data = new WorldSettlementData();
        data.seeded = tag.getBoolean("Seeded");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Seeded", this.seeded);
        return tag;
    }

    public boolean isSeeded() {
        return this.seeded;
    }

    public void markSeeded() {
        this.seeded = true;
        setDirty();
    }
}
