package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Global, persistent state of the Imperial Crusade — the Imperium's world-spanning war effort, the
 * counterpart to {@link WaaaghOverlordData}. It grows as Imperial cities prosper; the higher its
 * tier, the heavier the reinforcements the Crusade can dispatch to any settlement under threat.
 * Stored on the overworld's data storage so it is one strategic mind for the whole world.
 */
public class ImperiumOverlordData extends SavedData {
    private static final String NAME = "firstcrusade_imperium";

    private int crusade = 0;
    private int lastAnnouncedTier = 0;

    public ImperiumOverlordData() {
    }

    public static ImperiumOverlordData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(ImperiumOverlordData::load, ImperiumOverlordData::new, NAME);
    }

    public static ImperiumOverlordData load(CompoundTag tag) {
        ImperiumOverlordData data = new ImperiumOverlordData();
        data.crusade = tag.getInt("Crusade");
        data.lastAnnouncedTier = tag.getInt("LastAnnouncedTier");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Crusade", this.crusade);
        tag.putInt("LastAnnouncedTier", this.lastAnnouncedTier);
        return tag;
    }

    public int getCrusade() {
        return this.crusade;
    }

    public void add(int amount) {
        if (amount <= 0) {
            return;
        }

        this.crusade += amount;
        setDirty();
    }

    public int getTier() {
        if (this.crusade < 500) {
            return 0;
        }
        if (this.crusade < 2000) {
            return 1;
        }
        if (this.crusade < 6000) {
            return 2;
        }
        if (this.crusade < 15000) {
            return 3;
        }
        return 4;
    }

    public int getLastAnnouncedTier() {
        return this.lastAnnouncedTier;
    }

    public void setLastAnnouncedTier(int tier) {
        this.lastAnnouncedTier = tier;
        setDirty();
    }
}
