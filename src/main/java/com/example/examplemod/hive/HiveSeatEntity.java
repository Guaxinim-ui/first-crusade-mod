package com.example.examplemod.hive;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible, weightless marker that a player rides while "sitting" on a {@link HiveChairBlock}.
 * It removes itself the instant nobody is riding and the moment the underlying chair block is
 * gone, so no orphan entities accumulate. Not saved to disk (chairs are re-sat on demand).
 */
public class HiveSeatEntity extends Entity {

    public HiveSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        boolean chairGone = !(this.level().getBlockState(this.blockPosition()).getBlock() instanceof HiveChairBlock);
        if (!this.isVehicle() || chairGone) {
            this.ejectPassengers();
            this.discard();
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // nudge the dismounting entity to the side of the chair so it doesn't clip in
        Vec3 exit = this.position().add(0.0D, 0.35D, 0.0D);
        passenger.setPos(exit.x, exit.y, exit.z);
        this.discard();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
