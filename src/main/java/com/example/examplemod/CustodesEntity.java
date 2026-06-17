package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Adeptus Custodes — the Emperor's golden guard. Not recruited: ascended from a prosperous
 * city (level 5 + surplus gene-seed + standing Space Marines). They never leave the Core's
 * inner perimeter, standing as its last and most lethal line of defence.
 */
public class CustodesEntity extends PathfinderMob {
    // How far the Custodian may stray from the Core before returning to its vigil.
    private static final double LEASH_RADIUS_SQR = 24.0D * 24.0D;

    private BlockPos commandCorePos;

    public CustodesEntity(EntityType<? extends CustodesEntity> entityType, Level level) {
        super(entityType, level);
        prepareCustodes();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new FirstCrusadeNearestEnemyTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 180.0D)
                .add(Attributes.ARMOR, 25.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 25.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
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

        if (this.tickCount % 60 == 0) {
            returnToVigilIfStrayed();
        }
    }

    // The Custodian holds the Core. If it has no foe and has drifted too far, it returns.
    private void returnToVigilIfStrayed() {
        if (this.commandCorePos == null || this.getTarget() != null) {
            return;
        }

        double distance = this.distanceToSqr(
                this.commandCorePos.getX() + 0.5D,
                this.commandCorePos.getY(),
                this.commandCorePos.getZ() + 0.5D
        );

        if (distance > LEASH_RADIUS_SQR) {
            this.getNavigation().moveTo(
                    this.commandCorePos.getX() + 0.5D,
                    this.commandCorePos.getY(),
                    this.commandCorePos.getZ() + 0.5D,
                    1.05D
            );
        }
    }

    private void prepareCustodes() {
        this.setCustomName(Component.literal("Custodian Guard"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();

        equipAsCustodes();
    }

    // Vanilla gold armour gives the Custodian his iconic golden silhouette without custom art.
    public void equipAsCustodes() {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));

        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        this.setDropChance(EquipmentSlot.CHEST, 0.0F);
        this.setDropChance(EquipmentSlot.LEGS, 0.0F);
        this.setDropChance(EquipmentSlot.FEET, 0.0F);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
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

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos;
        this.setPersistenceRequired();
    }

    public boolean isAssignedToCommandCore(BlockPos commandCorePos) {
        return this.commandCorePos != null && this.commandCorePos.equals(commandCorePos);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        if (this.level().isClientSide || this.commandCorePos == null) {
            return;
        }

        OrkRaidManager.notifyNearbyPlayers(
                (net.minecraft.server.level.ServerLevel) this.level(),
                this.commandCorePos,
                "A Custodian Guard has fallen. The Emperor's vigil is diminished."
        );
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (this.commandCorePos != null) {
            tag.putLong("CommandCorePos", this.commandCorePos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("CommandCorePos")) {
            this.commandCorePos = BlockPos.of(tag.getLong("CommandCorePos"));
        }

        prepareCustodes();
    }
}
