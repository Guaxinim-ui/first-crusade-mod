package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class StrategicConstructionProject {
    private final long corePackedPos;
    private final long sitePackedPos;
    private final StrategicConstructionType type;

    private int progress;
    private int totalBlocks;

    public StrategicConstructionProject(BlockPos corePos, BlockPos sitePos, StrategicConstructionType type, int totalBlocks) {
        this.corePackedPos = corePos.asLong();
        this.sitePackedPos = sitePos.asLong();
        this.type = type;
        this.progress = 0;
        this.totalBlocks = Math.max(1, totalBlocks);
    }

    private StrategicConstructionProject(
            long corePackedPos,
            long sitePackedPos,
            StrategicConstructionType type,
            int progress,
            int totalBlocks
    ) {
        this.corePackedPos = corePackedPos;
        this.sitePackedPos = sitePackedPos;
        this.type = type;
        this.progress = progress;
        this.totalBlocks = Math.max(1, totalBlocks);
    }

    public BlockPos getCorePos() {
        return BlockPos.of(corePackedPos);
    }

    public BlockPos getSitePos() {
        return BlockPos.of(sitePackedPos);
    }

    public StrategicConstructionType getType() {
        return type;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = Math.max(1, totalBlocks);
    }

    public void addProgress(int amount) {
        if (amount <= 0) {
            return;
        }

        this.progress += amount;

        if (this.progress > this.totalBlocks) {
            this.progress = this.totalBlocks;
        }
    }

    public boolean isFinished() {
        return progress >= totalBlocks;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putLong("CorePos", corePackedPos);
        tag.putLong("SitePos", sitePackedPos);
        tag.putString("Type", type.name());
        tag.putInt("Progress", progress);
        tag.putInt("TotalBlocks", totalBlocks);

        return tag;
    }

    public static StrategicConstructionProject load(CompoundTag tag) {
        StrategicConstructionType type;

        try {
            type = StrategicConstructionType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException exception) {
            type = StrategicConstructionType.HABITATION;
        }

        int totalBlocks = tag.contains("TotalBlocks") ? tag.getInt("TotalBlocks") : 1;

        return new StrategicConstructionProject(
                tag.getLong("CorePos"),
                tag.getLong("SitePos"),
                type,
                tag.getInt("Progress"),
                totalBlocks
        );
    }
}