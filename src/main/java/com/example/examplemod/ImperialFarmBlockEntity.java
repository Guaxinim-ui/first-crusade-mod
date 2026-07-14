package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

// An Imperial Farm employs a Farmer who produces Food into the city's stockpile each work cycle.
// A staffed farm also counts toward morale and sustains population growth.
public class ImperialFarmBlockEntity extends BlockEntity {
    private static final int REQUIRED_WORK_TICKS = 700;

    private BlockPos commandCorePos;
    private int totalFoodProduced;

    public ImperialFarmBlockEntity(BlockPos pos, BlockState blockState) {
        super(FCRegistry.IMPERIAL_FARM_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ImperialFarmBlockEntity farm) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        farm.tickProduction(serverLevel);
    }

    private void tickProduction(ServerLevel serverLevel) {
        if (this.commandCorePos == null) {
            return;
        }

        if (serverLevel.getGameTime() % 40 != 0) {
            return;
        }

        ImperialCitizenEntity farmer = findAssignedFarmer(serverLevel);

        if (farmer == null) {
            return;
        }

        if (!farmer.hasWorkedForTicks(REQUIRED_WORK_TICKS)) {
            return;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(this.commandCorePos);

        if (!(blockEntity instanceof ImperialCommandCoreBlockEntity commandCore)) {
            return;
        }

        int accepted = commandCore.receiveProducedFood(commandCore.getFarmFoodYield());

        if (accepted <= 0) {
            return;
        }

        farmer.resetWorkTicks();
        this.totalFoodProduced += accepted;
        setChanged();
    }

    public int getTotalFoodProduced() {
        return this.totalFoodProduced;
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

    public boolean hasActiveWorker(ServerLevel serverLevel) {
        return findAssignedFarmer(serverLevel) != null;
    }

    private ImperialCitizenEntity findAssignedFarmer(ServerLevel serverLevel) {
        if (this.commandCorePos == null) {
            return null;
        }

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
                        && citizen.hasJob(ImperialCitizenJob.FARMER)
                        && citizen.isAssignedToCommandCore(this.commandCorePos)
                        && this.worldPosition.equals(citizen.getWorkSitePos())
        );

        if (citizens.isEmpty()) {
            return null;
        }

        return citizens.get(0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

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
