package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ImperialMineBlockEntity extends BlockEntity {
    private static final int REQUIRED_WORK_TICKS = 600;

    private BlockPos commandCorePos;
    private int totalIronProduced;

    public ImperialMineBlockEntity(BlockPos pos, BlockState blockState) {
        super(ExampleMod.IMPERIAL_MINE_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ImperialMineBlockEntity mine) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        mine.tickProduction(serverLevel);
    }

    private void tickProduction(ServerLevel serverLevel) {
        if (this.commandCorePos == null) {
            return;
        }

        if (serverLevel.getGameTime() % 40 != 0) {
            return;
        }

        ImperialCitizenEntity miner = findAssignedMiner(serverLevel);

        if (miner == null) {
            return;
        }

        if (!miner.hasWorkedForTicks(REQUIRED_WORK_TICKS)) {
            return;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(this.commandCorePos);

        if (!(blockEntity instanceof ImperialCommandCoreBlockEntity commandCore)) {
            return;
        }

        int accepted = commandCore.receiveProducedResource(ImperialResourceType.IRON, 1);

        if (accepted <= 0) {
            return;
        }

        miner.resetWorkTicks();
        this.totalIronProduced += accepted;
        setChanged();
    }

    private ImperialCitizenEntity findAssignedMiner(ServerLevel serverLevel) {
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
                        && citizen.hasJob(ImperialCitizenJob.MINER)
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

    public int getTotalIronProduced() {
        return this.totalIronProduced;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putInt("TotalIronProduced", this.totalIronProduced);

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

        this.totalIronProduced = tag.getInt("TotalIronProduced");

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