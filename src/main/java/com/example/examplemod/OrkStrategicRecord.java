package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

public class OrkStrategicRecord {
    private final long packedPos;

    private StrategicGovernor governor;
    private StrategicResourceBank resources = StrategicResourceBank.createOrkStart();

    public OrkStrategicRecord(ServerLevel level, BlockPos pos) {
        this.packedPos = pos.asLong();
        this.governor = StrategicGovernor.randomOrk(level);
    }

    private OrkStrategicRecord(long packedPos) {
        this.packedPos = packedPos;
    }

    public long getPackedPos() {
        return packedPos;
    }

    public BlockPos getPos() {
        return BlockPos.of(packedPos);
    }

    public StrategicGovernor getGovernor() {
        return governor;
    }

    public StrategicResourceBank getResources() {
        return resources;
    }

    public void generateOrkIncome(ServerLevel level) {
        int tier = WaaaghOverlordManager.getTier(level);

        resources.add(StrategicResourceType.ORK_SCRAP, 55 + tier * 25 + governor.getEconomy() / 4);
        resources.add(StrategicResourceType.TEEF, 40 + tier * 20);
        resources.add(StrategicResourceType.WAAAGH, 35 + tier * 20 + governor.getAggression() / 5);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putLong("PackedPos", packedPos);
        tag.put("Governor", governor.save());
        tag.put("Resources", resources.save());

        return tag;
    }

    public static OrkStrategicRecord load(CompoundTag tag) {
        OrkStrategicRecord record = new OrkStrategicRecord(tag.getLong("PackedPos"));

        if (tag.contains("Governor", Tag.TAG_COMPOUND)) {
            record.governor = StrategicGovernor.load(tag.getCompound("Governor"));
        } else {
            record.governor = new StrategicGovernor("Unknown Warboss", 80, 30, 40, 20);
        }

        record.resources = StrategicResourceBank.createOrkStart();

        if (tag.contains("Resources", Tag.TAG_COMPOUND)) {
            record.resources.load(tag.getCompound("Resources"));
        }

        return record;
    }
}