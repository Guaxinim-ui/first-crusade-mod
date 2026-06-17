package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

// A Trade Depot trades the city's Gold for Emeralds with the capital. A Trader staffs it and,
// each work cycle, the Core converts stored Gold into Emerald (rate defined on the Core).
public class ImperialEmeraldTradeDepotBlockEntity extends BlockEntity {
    private static final int REQUIRED_WORK_TICKS = 800;

    private BlockPos commandCorePos;
    private int totalEmeraldTraded;

    public ImperialEmeraldTradeDepotBlockEntity(BlockPos pos, BlockState blockState) {
        super(ExampleMod.IMPERIAL_EMERALD_TRADE_DEPOT_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ImperialEmeraldTradeDepotBlockEntity depot) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        depot.tickTrade(serverLevel);
    }

    private void tickTrade(ServerLevel serverLevel) {
        if (this.commandCorePos == null) {
            return;
        }

        if (serverLevel.getGameTime() % 40 != 0) {
            return;
        }

        ImperialCitizenEntity trader = findAssignedTrader(serverLevel);

        if (trader == null) {
            return;
        }

        if (!trader.hasWorkedForTicks(REQUIRED_WORK_TICKS)) {
            return;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(this.commandCorePos);

        if (!(blockEntity instanceof ImperialCommandCoreBlockEntity commandCore)) {
            return;
        }

        int produced = commandCore.tradeGoldForEmeraldAtDepot();

        if (produced <= 0) {
            return;
        }

        trader.resetWorkTicks();
        this.totalEmeraldTraded += produced;
        setChanged();
    }

    private ImperialCitizenEntity findAssignedTrader(ServerLevel serverLevel) {
        AABB searchBox = new AABB(
                this.worldPosition.getX() - 8,
                this.worldPosition.getY() - 4,
                this.worldPosition.getZ() - 8,
                this.worldPosition.getX() + 8,
                this.worldPosition.getY() + 6,
                this.worldPosition.getZ() + 8
        );

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                searchBox,
                citizen -> citizen.isAlive()
                        && citizen.hasJob(ImperialCitizenJob.TRADER)
                        && citizen.isAssignedToCommandCore(this.commandCorePos)
                        && this.worldPosition.equals(citizen.getWorkSitePos())
        );

        if (citizens.isEmpty()) {
            return null;
        }

        return citizens.get(0);
    }

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos.immutable();
        setChanged();
    }

    public boolean isAssignedToCommandCore(BlockPos commandCorePos) {
        return this.commandCorePos != null && this.commandCorePos.equals(commandCorePos);
    }

    public BlockPos getCommandCorePos() {
        return this.commandCorePos;
    }

    public int getTotalEmeraldTraded() {
        return this.totalEmeraldTraded;
    }

    public boolean hasActiveWorker(ServerLevel serverLevel) {
        return findAssignedTrader(serverLevel) != null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putInt("TotalEmeraldTraded", this.totalEmeraldTraded);

        if (this.commandCorePos != null) {
            tag.putBoolean("HasCommandCorePos", true);
            tag.putInt("CommandCoreX", this.commandCorePos.getX());
            tag.putInt("CommandCoreY", this.commandCorePos.getY());
            tag.putInt("CommandCoreZ", this.commandCorePos.getZ());
        } else {
            tag.putBoolean("HasCommandCorePos", false);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        this.totalEmeraldTraded = tag.getInt("TotalEmeraldTraded");

        if (tag.getBoolean("HasCommandCorePos")) {
            this.commandCorePos = new BlockPos(
                    tag.getInt("CommandCoreX"),
                    tag.getInt("CommandCoreY"),
                    tag.getInt("CommandCoreZ")
            );
        } else {
            this.commandCorePos = null;
        }
    }
}
