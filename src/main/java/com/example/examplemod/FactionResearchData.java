package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Global, persistent state of the Imperium's <b>paid research</b> (the MineColonies/Age-of-Empires
 * style age-up the player drives from a {@link StrategiumBlock} bench). At most one Crusade research
 * runs at a time; while it runs, {@link FactionResearchManager} counts it down and, on completion,
 * advances the Crusade Age ({@link ImperiumOverlordData}). Stored on the overworld so it is one
 * research effort for the whole Imperium, no matter which bench started it.
 */
public class FactionResearchData extends SavedData {
    private static final String NAME = "firstcrusade_research";

    private boolean active = false;
    private int ticksRemaining = 0;
    private int totalTicks = 0;
    private int targetTier = 0;

    public FactionResearchData() {
    }

    public static FactionResearchData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(FactionResearchData::load, FactionResearchData::new, NAME);
    }

    public static FactionResearchData load(CompoundTag tag) {
        FactionResearchData data = new FactionResearchData();
        data.active = tag.getBoolean("Active");
        data.ticksRemaining = tag.getInt("TicksRemaining");
        data.totalTicks = tag.getInt("TotalTicks");
        data.targetTier = tag.getInt("TargetTier");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Active", this.active);
        tag.putInt("TicksRemaining", this.ticksRemaining);
        tag.putInt("TotalTicks", this.totalTicks);
        tag.putInt("TargetTier", this.targetTier);
        return tag;
    }

    public boolean isActive() {
        return this.active;
    }

    public int getTicksRemaining() {
        return this.ticksRemaining;
    }

    public int getTotalTicks() {
        return this.totalTicks;
    }

    public int getTargetTier() {
        return this.targetTier;
    }

    public void start(int targetTier, int totalTicks) {
        this.active = true;
        this.targetTier = targetTier;
        this.totalTicks = totalTicks;
        this.ticksRemaining = totalTicks;
        setDirty();
    }

    // Counts down one cycle; returns true exactly when the research has just completed.
    public boolean countDown(int amount) {
        if (!this.active) {
            return false;
        }

        this.ticksRemaining -= amount;
        setDirty();

        if (this.ticksRemaining <= 0) {
            this.active = false;
            this.ticksRemaining = 0;
            setDirty();
            return true;
        }

        return false;
    }
}
