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

    // Points needed to enter tiers 1..4. The Crusade advances faster than the WAAAGH! (see
    // WaaaghOverlordData) so the Imperium can research/age up ahead of the Ork tide.
    private static final int[] TIER_THRESHOLDS = {300, 1100, 3000, 7000};

    public int getTier() {
        for (int i = 0; i < TIER_THRESHOLDS.length; i++) {
            if (this.crusade < TIER_THRESHOLDS[i]) {
                return i;
            }
        }
        return TIER_THRESHOLDS.length;
    }

    // Fraction (0..1) of the way from the current tier to the next — drives the on-screen Crusade bar.
    public float getProgressToNextTier() {
        int tier = getTier();
        if (tier >= TIER_THRESHOLDS.length) {
            return 1.0F;
        }
        int lower = tier == 0 ? 0 : TIER_THRESHOLDS[tier - 1];
        int upper = TIER_THRESHOLDS[tier];
        float progress = (this.crusade - lower) / (float) (upper - lower);
        return progress < 0.0F ? 0.0F : (progress > 1.0F ? 1.0F : progress);
    }

    // Lifts the Crusade to (at least) the floor of the given tier — used by paid research to age up.
    public void ensureAtLeastTier(int tier) {
        if (tier <= 0 || tier > TIER_THRESHOLDS.length) {
            return;
        }

        int floor = TIER_THRESHOLDS[tier - 1];
        if (this.crusade < floor) {
            this.crusade = floor;
            setDirty();
        }

        if (tier > this.lastAnnouncedTier) {
            this.lastAnnouncedTier = tier;
            setDirty();
        }
    }

    public int getLastAnnouncedTier() {
        return this.lastAnnouncedTier;
    }

    public void setLastAnnouncedTier(int tier) {
        this.lastAnnouncedTier = tier;
        setDirty();
    }
}
