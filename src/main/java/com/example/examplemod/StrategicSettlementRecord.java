package com.example.examplemod;

import java.util.EnumMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

public class StrategicSettlementRecord {
    private final long packedPos;

    private StrategicAge age = StrategicAge.OUTPOST;
    private StrategicPlan plan = StrategicPlan.ECONOMY;
    private StrategicGovernor governor;
    private StrategicResourceBank resources = StrategicResourceBank.createImperialStart();

    private final EnumMap<StrategicConstructionType, Integer> builtCounts =
            new EnumMap<>(StrategicConstructionType.class);

    public StrategicSettlementRecord(ServerLevel level, BlockPos pos) {
        this.packedPos = pos.asLong();
        this.governor = StrategicGovernor.randomImperial(level);

        for (StrategicConstructionType type : StrategicConstructionType.values()) {
            builtCounts.put(type, 0);
        }
    }

    private StrategicSettlementRecord(long packedPos) {
        this.packedPos = packedPos;

        for (StrategicConstructionType type : StrategicConstructionType.values()) {
            builtCounts.put(type, 0);
        }
    }

    public long getPackedPos() {
        return packedPos;
    }

    public BlockPos getPos() {
        return BlockPos.of(packedPos);
    }

    public StrategicAge getAge() {
        return age;
    }

    public StrategicPlan getPlan() {
        return plan;
    }

    public void setPlan(StrategicPlan plan) {
        this.plan = plan;
    }

    public StrategicGovernor getGovernor() {
        return governor;
    }

    public StrategicResourceBank getResources() {
        return resources;
    }

    public int getBuiltCount(StrategicConstructionType type) {
        return builtCounts.getOrDefault(type, 0);
    }

    public void addBuilt(StrategicConstructionType type) {
        builtCounts.put(type, getBuiltCount(type) + 1);
    }

    public void generateImperialIncome(ServerLevel level, ImperialCommandCoreBlockEntity core) {
        int cityLevel = Math.max(1, core.getCityLevel());
        int ageBonus = 1 + age.ordinal();

        resources.add(StrategicResourceType.FOOD, 55 + cityLevel * 18 + ageBonus * 10);
        resources.add(StrategicResourceType.IRON, 45 + cityLevel * 15 + ageBonus * 8);
        resources.add(StrategicResourceType.SCRAP, 35 + cityLevel * 13 + ageBonus * 8);
        resources.add(StrategicResourceType.COAL, 25 + cityLevel * 10 + ageBonus * 5);

        if (age.ordinal() >= StrategicAge.FORTIFIED_SETTLEMENT.ordinal()) {
            resources.add(StrategicResourceType.FERROCRETE, 50 + cityLevel * 12);
        }

        if (age.ordinal() >= StrategicAge.MANUFACTORUM_AGE.ordinal()) {
            resources.add(StrategicResourceType.PLASTEEL, 35 + cityLevel * 9);
            resources.add(StrategicResourceType.PROMETHIUM, 30 + cityLevel * 8);
        }

        if (age.ordinal() >= StrategicAge.ASTARTES_AGE.ordinal()) {
            resources.add(StrategicResourceType.CERAMITE, 26 + cityLevel * 7);
            resources.add(StrategicResourceType.CRUSADIUM, 16 + cityLevel * 5);
        }

        if (age.ordinal() >= StrategicAge.PLANETARY_WAR.ordinal()) {
            resources.add(StrategicResourceType.ADAMANTIUM, 10 + cityLevel * 3);
        }

        core.receiveProducedResource(ImperialResourceType.IRON, 4 + age.ordinal() * 2);
        core.receiveProducedResource(ImperialResourceType.SCRAP, 3 + age.ordinal() * 2);
        core.receiveProducedResource(ImperialResourceType.COAL, 2 + age.ordinal());

        if (age.ordinal() >= StrategicAge.ASTARTES_AGE.ordinal()) {
            core.receiveProducedResource(ImperialResourceType.CRUSADIUM, 1 + age.ordinal());
        }
    }

    public void choosePlan(int threat, int dominion) {
        if (threat >= 18 || dominion <= -55) {
            plan = StrategicPlan.SURVIVE;
            return;
        }

        if (threat >= 10 || dominion <= -25) {
            plan = StrategicPlan.DEFENSE;
            return;
        }

        if (canAdvanceAge() && governor.getTechnology() + governor.getEconomy() >= 105) {
            plan = StrategicPlan.TECH_UP;
            return;
        }

        if (governor.getAggression() >= 65 && dominion > -40) {
            plan = StrategicPlan.ATTACK;
            return;
        }

        if (governor.getEconomy() >= governor.getDefense()) {
            plan = StrategicPlan.ECONOMY;
            return;
        }

        plan = StrategicPlan.MILITARY;
    }

    public boolean canAdvanceAge() {
        if (!age.hasNext()) {
            return false;
        }

        return age.next().canPay(resources);
    }

    public boolean advanceAge() {
        if (!canAdvanceAge()) {
            return false;
        }

        StrategicAge next = age.next();
        next.spend(resources);
        age = next;

        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putLong("PackedPos", packedPos);
        tag.putString("Age", age.name());
        tag.putString("Plan", plan.name());
        tag.put("Governor", governor.save());
        tag.put("Resources", resources.save());

        CompoundTag builtTag = new CompoundTag();

        for (StrategicConstructionType type : StrategicConstructionType.values()) {
            builtTag.putInt(type.name(), getBuiltCount(type));
        }

        tag.put("BuiltCounts", builtTag);

        return tag;
    }

    public static StrategicSettlementRecord load(CompoundTag tag) {
        StrategicSettlementRecord record = new StrategicSettlementRecord(tag.getLong("PackedPos"));

        try {
            record.age = StrategicAge.valueOf(tag.getString("Age"));
        } catch (IllegalArgumentException exception) {
            record.age = StrategicAge.OUTPOST;
        }

        try {
            record.plan = StrategicPlan.valueOf(tag.getString("Plan"));
        } catch (IllegalArgumentException exception) {
            record.plan = StrategicPlan.ECONOMY;
        }

        if (tag.contains("Governor", Tag.TAG_COMPOUND)) {
            record.governor = StrategicGovernor.load(tag.getCompound("Governor"));
        } else {
            record.governor = new StrategicGovernor("Unknown Governor", 50, 50, 50, 50);
        }

        record.resources = StrategicResourceBank.createImperialStart();

        if (tag.contains("Resources", Tag.TAG_COMPOUND)) {
            record.resources.load(tag.getCompound("Resources"));
        }

        CompoundTag builtTag = tag.getCompound("BuiltCounts");

        for (StrategicConstructionType type : StrategicConstructionType.values()) {
            record.builtCounts.put(type, builtTag.getInt(type.name()));
        }

        return record;
    }
}