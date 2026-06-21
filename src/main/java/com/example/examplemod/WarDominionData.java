package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who is winning the war for the planet. A single global score swings toward the Imperium (positive)
 * or the WAAAGH! (negative) as battles are won and lost; when it tips far enough a global triumph is
 * declared. The war can swing back, so a triumph can be lost and re-won. See {@link WarDominionManager}.
 */
public class WarDominionData extends SavedData {
    private static final String NAME = "firstcrusade_dominion";
    private static final int LIMIT = 100;

    private int dominion = 0;
    private int announcedSide = 0; // -1 WAAAGH! triumph declared, 0 contested, +1 Imperium triumph declared

    public WarDominionData() {
    }

    public static WarDominionData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WarDominionData::load, WarDominionData::new, NAME);
    }

    public static WarDominionData load(CompoundTag tag) {
        WarDominionData data = new WarDominionData();
        data.dominion = tag.getInt("Dominion");
        data.announcedSide = tag.getInt("AnnouncedSide");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Dominion", this.dominion);
        tag.putInt("AnnouncedSide", this.announcedSide);
        return tag;
    }

    public int getDominion() {
        return this.dominion;
    }

    public void add(int amount) {
        this.dominion = Math.max(-LIMIT, Math.min(LIMIT, this.dominion + amount));
        setDirty();
    }

    public int getAnnouncedSide() {
        return this.announcedSide;
    }

    public void setAnnouncedSide(int side) {
        this.announcedSide = side;
        setDirty();
    }
}
