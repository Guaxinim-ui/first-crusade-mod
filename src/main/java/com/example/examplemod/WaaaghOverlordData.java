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

    // Points needed to enter tiers 1..4. The WAAAGH! climbs more slowly than the Imperial Crusade
    // (see ImperiumOverlordData), so the green tide builds up gradually instead of flooding early.
    private static final int[] TIER_THRESHOLDS = {900, 3000, 9000, 22000};

    public int getTier() {
        for (int i = 0; i < TIER_THRESHOLDS.length; i++) {
            if (this.waaagh < TIER_THRESHOLDS[i]) {
                return i;
            }
        }
        return TIER_THRESHOLDS.length;
    }

    // Fraction (0..1) of the way from the current tier to the next — drives the on-screen WAAAGH! bar.
    public float getProgressToNextTier() {
        int tier = getTier();
        if (tier >= TIER_THRESHOLDS.length) {
            return 1.0F;
        }
        int lower = tier == 0 ? 0 : TIER_THRESHOLDS[tier - 1];
        int upper = TIER_THRESHOLDS[tier];
        float progress = (this.waaagh - lower) / (float) (upper - lower);
        return progress < 0.0F ? 0.0F : (progress > 1.0F ? 1.0F : progress);
    }

    public int getLastAnnouncedTier() {
        return this.lastAnnouncedTier;
    }

    public void setLastAnnouncedTier(int tier) {
        this.lastAnnouncedTier = tier;
        setDirty();
    }
}
