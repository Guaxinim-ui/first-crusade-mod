package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * A group of city troops acting under one standing order. The squad only stores entity UUIDs —
 * members are resolved back to live entities each military tick by the {@link CityMilitaryManager},
 * so dead/despawned troops silently drop out. Attack squads are persisted inside the settlement's
 * {@link StrategicSettlementRecord} so a marching army survives a save/load.
 */
public class CitySquad {
    private final CitySquadType type;

    private CitySquadOrder order;

    @Nullable
    private BlockPos targetPos;

    private final List<UUID> members = new ArrayList<>();

    // Size at formation time; used to detect that the squad was mauled (defeat threshold).
    private int initialSize;

    public CitySquad(CitySquadType type, CitySquadOrder order, @Nullable BlockPos targetPos) {
        this.type = type;
        this.order = order;
        this.targetPos = targetPos;
    }

    public CitySquadType getType() {
        return type;
    }

    public CitySquadOrder getOrder() {
        return order;
    }

    public void setOrder(CitySquadOrder order) {
        this.order = order;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    public void setTargetPos(@Nullable BlockPos targetPos) {
        this.targetPos = targetPos;
    }

    public List<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID memberId) {
        if (!members.contains(memberId)) {
            members.add(memberId);
        }
    }

    public void sealInitialSize() {
        this.initialSize = members.size();
    }

    public int getInitialSize() {
        return initialSize;
    }

    public boolean isMauled() {
        return initialSize > 0 && members.size() * 4 < initialSize;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putString("Type", type.name());
        tag.putString("Order", order.name());
        tag.putInt("InitialSize", initialSize);

        if (targetPos != null) {
            tag.put("TargetPos", NbtUtils.writeBlockPos(targetPos));
        }

        ListTag memberList = new ListTag();

        for (UUID memberId : members) {
            memberList.add(NbtUtils.createUUID(memberId));
        }

        tag.put("Members", memberList);

        return tag;
    }

    public static CitySquad load(CompoundTag tag) {
        CitySquadType type;

        try {
            type = CitySquadType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException exception) {
            type = CitySquadType.DEFENSE;
        }

        CitySquadOrder order;

        try {
            order = CitySquadOrder.valueOf(tag.getString("Order"));
        } catch (IllegalArgumentException exception) {
            order = CitySquadOrder.HOLD_CORE;
        }

        BlockPos targetPos = null;

        if (tag.contains("TargetPos", Tag.TAG_COMPOUND)) {
            targetPos = NbtUtils.readBlockPos(tag.getCompound("TargetPos"));
        }

        CitySquad squad = new CitySquad(type, order, targetPos);
        squad.initialSize = tag.getInt("InitialSize");

        ListTag memberList = tag.getList("Members", Tag.TAG_INT_ARRAY);

        for (Tag memberTag : memberList) {
            squad.members.add(NbtUtils.loadUUID(memberTag));
        }

        return squad;
    }
}
