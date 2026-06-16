package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ImperialForgeBlockEntity extends BlockEntity {
    private static final int REQUIRED_WORK_TICKS = 900;

    private BlockPos commandCorePos;
    private int totalPlatesProduced;

    public ImperialForgeBlockEntity(BlockPos pos, BlockState blockState) {
        super(ExampleMod.IMPERIAL_FORGE_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ImperialForgeBlockEntity forge) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        forge.tickProduction(serverLevel);
    }

    private void tickProduction(ServerLevel serverLevel) {
        if (this.commandCorePos == null) {
            return;
        }

        if (serverLevel.getGameTime() % 40 != 0) {
            return;
        }

        ImperialCitizenEntity smith = findAssignedSmith(serverLevel);

        if (smith == null) {
            return;
        }

        if (!smith.hasWorkedForTicks(REQUIRED_WORK_TICKS)) {
            return;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(this.commandCorePos);

        if (!(blockEntity instanceof ImperialCommandCoreBlockEntity commandCore)) {
            return;
        }

        boolean consumedResources = commandCore.consumeResourcesForCrusadiumPlateProduction(4, 3, 2);

        if (!consumedResources) {
            return;
        }

        smith.resetWorkTicks();
        this.totalPlatesProduced++;
        spawnCrusadiumPlate(serverLevel);
        setChanged();
    }

    private ImperialCitizenEntity findAssignedSmith(ServerLevel serverLevel) {
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
                        && citizen.hasJob(ImperialCitizenJob.SMITH)
                        && citizen.isAssignedToCommandCore(this.commandCorePos)
                        && this.worldPosition.equals(citizen.getWorkSitePos())
        );

        if (citizens.isEmpty()) {
            return null;
        }

        return citizens.get(0);
    }

    private void spawnCrusadiumPlate(ServerLevel serverLevel) {
        ItemEntity itemEntity = new ItemEntity(
                serverLevel,
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 1.2D,
                this.worldPosition.getZ() + 0.5D,
                new ItemStack(ExampleMod.CRUSADIUM_PLATE.get())
        );

        itemEntity.setDefaultPickUpDelay();
        serverLevel.addFreshEntity(itemEntity);
    }

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos.immutable();
        setChanged();
    }

    public boolean isAssignedToCommandCore(BlockPos commandCorePos) {
        return this.commandCorePos != null && this.commandCorePos.equals(commandCorePos);
    }

    public int getTotalPlatesProduced() {
        return this.totalPlatesProduced;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putInt("TotalPlatesProduced", this.totalPlatesProduced);

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

        this.totalPlatesProduced = tag.getInt("TotalPlatesProduced");

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