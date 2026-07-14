package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ImperialBarracksBlockEntity extends BlockEntity {
    private static final int TRAINING_TICKS = 1200;

    private BlockPos commandCorePos;
    private int totalTrained;

    public ImperialBarracksBlockEntity(BlockPos pos, BlockState blockState) {
        super(FCRegistry.IMPERIAL_BARRACKS_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ImperialBarracksBlockEntity barracks) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        barracks.tickTraining(serverLevel);
    }

    private void tickTraining(ServerLevel serverLevel) {
        if (this.commandCorePos == null) {
            return;
        }

        if (serverLevel.getGameTime() % 40 != 0) {
            return;
        }

        ImperialCitizenEntity recruit = findAssignedRecruit(serverLevel);

        if (recruit == null) {
            return;
        }

        if (!recruit.hasWorkedForTicks(TRAINING_TICKS)) {
            return;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(this.commandCorePos);

        if (!(blockEntity instanceof ImperialCommandCoreBlockEntity commandCore)) {
            return;
        }

        boolean trained = commandCore.completeRecruitTraining(serverLevel, recruit);

        if (!trained) {
            return;
        }

        this.totalTrained++;
        setChanged();
    }

    private ImperialCitizenEntity findAssignedRecruit(ServerLevel serverLevel) {
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
                        && citizen.hasJob(ImperialCitizenJob.RECRUIT)
                        && citizen.isAssignedToCommandCore(this.commandCorePos)
                        && this.worldPosition.equals(citizen.getWorkSitePos())
        );

        if (citizens.isEmpty()) {
            return null;
        }

        return citizens.get(0);
    }

    public boolean hasAssignedRecruit(ServerLevel serverLevel) {
        return findAssignedRecruit(serverLevel) != null;
    }

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos.immutable();
        setChanged();
    }

    public boolean isAssignedToCommandCore(BlockPos commandCorePos) {
        return this.commandCorePos != null && this.commandCorePos.equals(commandCorePos);
    }

    public int getTotalTrained() {
        return this.totalTrained;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putInt("TotalTrained", this.totalTrained);

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

        this.totalTrained = tag.getInt("TotalTrained");

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
