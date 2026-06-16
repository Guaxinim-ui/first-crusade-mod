package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

public class SpaceMarineEntity extends PathfinderMob {
    private BlockPos commandCorePos;
    private BlockPos guardPostPos;

    public SpaceMarineEntity(EntityType<? extends SpaceMarineEntity> entityType, Level level) {
        super(entityType, level);
        prepareSpaceMarine();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
this.targetSelector.addGoal(2, new FirstCrusadeNearestEnemyTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 90.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 72.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35D);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);

        prepareSpaceMarine();
        this.setHealth(this.getMaxHealth());

        return data;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.level().isClientSide) {
            return;
        }

        if (this.getTarget() != null && !FirstCrusadeFactionManager.canAttack(this, this.getTarget())) {
            this.setTarget(null);
        }

        if (this.tickCount % 100 == 0) {
            prepareSpaceMarine();
            moveToGuardPostIfNeeded();
        }
    }

    private void prepareSpaceMarine() {
        this.setCustomName(Component.literal("Space Marine Initiate"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();

        equipAsSpaceMarine();
    }

    private void moveToGuardPostIfNeeded() {
        if (this.guardPostPos == null) {
            return;
        }

        if (this.getTarget() != null) {
            return;
        }

        double distance = this.distanceToSqr(
                this.guardPostPos.getX() + 0.5D,
                this.guardPostPos.getY(),
                this.guardPostPos.getZ() + 0.5D
        );

        if (distance > 36.0D) {
            this.getNavigation().moveTo(
                    this.guardPostPos.getX() + 0.5D,
                    this.guardPostPos.getY(),
                    this.guardPostPos.getZ() + 0.5D,
                    1.05D
            );
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (!FirstCrusadeFactionManager.canAttack(this, target)) {
            return false;
        }

        return super.canAttack(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !FirstCrusadeFactionManager.canAttack(this, target)) {
            super.setTarget(null);
            return;
        }

        super.setTarget(target);
    }

    public void equipAsSpaceMarine() {
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));

        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        this.setDropChance(EquipmentSlot.CHEST, 0.0F);
        this.setDropChance(EquipmentSlot.LEGS, 0.0F);
        this.setDropChance(EquipmentSlot.FEET, 0.0F);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos;
    }

    public boolean isAssignedToCommandCore(BlockPos commandCorePos) {
        return this.commandCorePos != null && this.commandCorePos.equals(commandCorePos);
    }

    public BlockPos getCommandCorePos() {
        return this.commandCorePos;
    }

    public void assignGuardPost(BlockPos guardPostPos) {
        this.guardPostPos = guardPostPos;
    }

    public BlockPos getGuardPostPos() {
        return this.guardPostPos;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        if (this.level().isClientSide) {
            return;
        }

        if (this.commandCorePos == null) {
            return;
        }

        if (this.level().getBlockEntity(this.commandCorePos) instanceof ImperialCommandCoreBlockEntity commandCore) {
            commandCore.onAssignedGuardsmanDeath();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (this.commandCorePos != null) {
            tag.putLong("CommandCorePos", this.commandCorePos.asLong());
        }

        if (this.guardPostPos != null) {
            tag.putLong("GuardPostPos", this.guardPostPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("CommandCorePos")) {
            this.commandCorePos = BlockPos.of(tag.getLong("CommandCorePos"));
        }

        if (tag.contains("GuardPostPos")) {
            this.guardPostPos = BlockPos.of(tag.getLong("GuardPostPos"));
        }

        prepareSpaceMarine();
    }
}