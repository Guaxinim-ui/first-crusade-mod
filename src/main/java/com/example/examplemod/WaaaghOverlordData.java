package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Global, persistent state of the WAAAGH! — the world-spanning Ork tide. It grows as the
 * Imperium prospers and, the bigger it gets, the more aggressive every Ork Camp becomes. Stored
 * on the overworld's data storage so it is truly one mind for the whole world.
 */
public class WaaaghOverlordData extends SavedData {
    private static final String NAME = "firstcrusade_waaagh";

    private int waaagh = 0;
    private int lastAnnouncedTier = 0;

    public WaaaghOverlordData() {
    }

    public static WaaaghOverlordData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WaaaghOverlordData::load, WaaaghOverlordData::new, NAME);
    }

    public static WaaaghOverlordData load(CompoundTag tag) {
        WaaaghOverlordData data = new WaaaghOverlordData();
        data.waaagh = tag.getInt("Waaagh");
        data.lastAnnouncedTier = tag.getInt("LastAnnouncedTier");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Waaagh", this.waaagh);
        tag.putInt("LastAnnouncedTier", this.lastAnnouncedTier);
        return tag;
    }

    public int getWaaagh() {
        return this.waaagh;
    }

    public void add(int amount) {
        if (amount <= 0) {
            return;
        }

        this.waaagh += amount;
        setDirty();
    }

    public int getTier() {
        if (this.waaagh < 500) {
            return 0;
        }
        if (this.waaagh < 2000) {
            return 1;
        }
        if (this.waaagh < 6000) {
            return 2;
        }
        if (this.waaagh < 15000) {
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
