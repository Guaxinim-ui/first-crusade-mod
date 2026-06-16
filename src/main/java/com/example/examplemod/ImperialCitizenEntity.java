package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ImperialCitizenEntity extends PathfinderMob {
    private BlockPos commandCorePos;
    private BlockPos workSitePos;

    private ImperialCitizenJob job = ImperialCitizenJob.UNEMPLOYED;

    private int citizenAgeTicks;
    private int workTicks;

    public ImperialCitizenEntity(EntityType<? extends ImperialCitizenEntity> entityType, Level level) {
        super(entityType, level);
        updateCitizenName();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        this.citizenAgeTicks++;

        if (this.tickCount % 40 == 0) {
            updateWorkRoutine();
        }

        if (this.tickCount % 100 == 0) {
            updateCitizenName();
        }
    }

    private void updateWorkRoutine() {
        if (this.commandCorePos == null) {
            return;
        }

        if (this.job == ImperialCitizenJob.UNEMPLOYED || this.workSitePos == null) {
            keepNearCommandCore();
            return;
        }

        double distanceToWork = this.distanceToSqr(
                this.workSitePos.getX() + 0.5D,
                this.workSitePos.getY() + 0.5D,
                this.workSitePos.getZ() + 0.5D
        );

        if (distanceToWork > 4.0D * 4.0D) {
            moveToWorkSite();
            return;
        }

        this.getNavigation().stop();
        this.workTicks += 40;
    }

    private void moveToWorkSite() {
        if (this.workSitePos == null) {
            return;
        }

        this.getNavigation().moveTo(
                this.workSitePos.getX() + 0.5D,
                this.workSitePos.getY(),
                this.workSitePos.getZ() + 0.5D,
                0.85D
        );
    }

    private void keepNearCommandCore() {
        if (this.commandCorePos == null) {
            return;
        }

        double distance = this.distanceToSqr(
                this.commandCorePos.getX() + 0.5D,
                this.commandCorePos.getY() + 0.5D,
                this.commandCorePos.getZ() + 0.5D
        );

        if (distance > 48.0D * 48.0D) {
            this.getNavigation().moveTo(
                    this.commandCorePos.getX() + 0.5D,
                    this.commandCorePos.getY(),
                    this.commandCorePos.getZ() + 0.5D,
                    0.9D
            );
        }
    }

    private void updateCitizenName() {
        if (this.job == ImperialCitizenJob.UNEMPLOYED) {
            this.setCustomName(Component.literal("Imperial Citizen"));
            return;
        }

        this.setCustomName(Component.literal("Imperial Citizen - " + this.job.getDisplayName()));
    }

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos.immutable();
    }

    public boolean isAssignedToCommandCore(BlockPos commandCorePos) {
        return this.commandCorePos != null && this.commandCorePos.equals(commandCorePos);
    }

    public BlockPos getCommandCorePos() {
        return this.commandCorePos;
    }

    public BlockPos getWorkSitePos() {
        return this.workSitePos;
    }

    public boolean hasWorkSite() {
        return this.workSitePos != null;
    }

    public ImperialCitizenJob getJob() {
        return this.job;
    }

    public boolean isUnemployed() {
        return this.job == ImperialCitizenJob.UNEMPLOYED;
    }

    public boolean hasJob(ImperialCitizenJob job) {
        return this.job == job;
    }

    public void assignJob(ImperialCitizenJob job, BlockPos workSitePos) {
        this.job = job;
        this.workSitePos = workSitePos == null ? null : workSitePos.immutable();
        this.workTicks = 0;
        updateCitizenName();
    }

    public void clearJob() {
        this.job = ImperialCitizenJob.UNEMPLOYED;
        this.workSitePos = null;
        this.workTicks = 0;
        updateCitizenName();
    }

    public int getCitizenAgeTicks() {
        return this.citizenAgeTicks;
    }

    public int getCitizenAgeDays() {
        return this.citizenAgeTicks / 24000;
    }

    public int getWorkTicks() {
        return this.workTicks;
    }

    public void resetWorkTicks() {
        this.workTicks = 0;
    }

    public boolean hasWorkedForTicks(int requiredTicks) {
        return this.workTicks >= requiredTicks;
    }

    public boolean isReadyForTraining() {
        return this.job == ImperialCitizenJob.UNEMPLOYED && this.citizenAgeTicks >= 200;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putInt("CitizenAgeTicks", this.citizenAgeTicks);
        tag.putInt("WorkTicks", this.workTicks);
        tag.putString("CitizenJob", this.job.name());

        if (this.commandCorePos != null) {
            tag.putBoolean("HasCommandCorePos", true);
            tag.putInt("CommandCoreX", this.commandCorePos.getX());
            tag.putInt("CommandCoreY", this.commandCorePos.getY());
            tag.putInt("CommandCoreZ", this.commandCorePos.getZ());
        } else {
            tag.putBoolean("HasCommandCorePos", false);
        }

        if (this.workSitePos != null) {
            tag.putBoolean("HasWorkSitePos", true);
            tag.putInt("WorkSiteX", this.workSitePos.getX());
            tag.putInt("WorkSiteY", this.workSitePos.getY());
            tag.putInt("WorkSiteZ", this.workSitePos.getZ());
        } else {
            tag.putBoolean("HasWorkSitePos", false);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        this.citizenAgeTicks = tag.getInt("CitizenAgeTicks");
        this.workTicks = tag.getInt("WorkTicks");
        this.job = readJob(tag.getString("CitizenJob"));

        if (tag.getBoolean("HasCommandCorePos")) {
            this.commandCorePos = new BlockPos(
                    tag.getInt("CommandCoreX"),
                    tag.getInt("CommandCoreY"),
                    tag.getInt("CommandCoreZ")
            );
        } else {
            this.commandCorePos = null;
        }

        if (tag.getBoolean("HasWorkSitePos")) {
            this.workSitePos = new BlockPos(
                    tag.getInt("WorkSiteX"),
                    tag.getInt("WorkSiteY"),
                    tag.getInt("WorkSiteZ")
            );
        } else {
            this.workSitePos = null;
        }

        updateCitizenName();
    }

    private ImperialCitizenJob readJob(String jobName) {
        if (jobName == null || jobName.isEmpty()) {
            return ImperialCitizenJob.UNEMPLOYED;
        }

        try {
            return ImperialCitizenJob.valueOf(jobName);
        } catch (IllegalArgumentException exception) {
            return ImperialCitizenJob.UNEMPLOYED;
        }
    }
}