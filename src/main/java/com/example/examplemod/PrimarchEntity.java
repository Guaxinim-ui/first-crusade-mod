package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

import javax.annotation.Nullable;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Primarch — the unique, demigod son of the Emperor. Towering far above a normal man, he
 * is the apex of a settlement: he inspires the city merely by his presence, and he personally
 * leads the city's troops into battle, rallying every nearby warrior to his target.
 *
 * Only one may live per settlement. If he falls, the city enters mourning (see the manager).
 */
public class PrimarchEntity extends PathfinderMob
        implements GeoEntity, com.example.examplemod.performance.strategic.FCStrategicExempt {

    @Override
    public String strategicExemptReason() {
        return "Primarca";
    }

    // The Primarch ranges far, since he marches out to lead sorties against Ork camps.
    private static final double LEASH_RADIUS_SQR = 128.0D * 128.0D;
    private static final double LEADERSHIP_RADIUS = 24.0D;

    // Names must match the keys in assets/firstcrusade/animations/primarch.animation.json.
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.primarch.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.primarch.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.primarch.attack");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos commandCorePos;

    public PrimarchEntity(EntityType<? extends PrimarchEntity> entityType, Level level) {
        super(entityType, level);
        preparePrimarch();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new FirstCrusadeNearestEnemyTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.ARMOR, 30.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 15.0D)
                .add(Attributes.ATTACK_DAMAGE, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
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

        if (this.tickCount % 40 == 0) {
            leadNearbyTroops();
        }

        if (this.tickCount % 80 == 0) {
            returnToCityIfStrayed();
        }
    }

    // Leadership: inspire nearby Imperial warriors, and when the Primarch is fighting, rally
    // every leaderless soldier around him onto the same foe — leading the charge in person.
    private void leadNearbyTroops() {
        LivingEntity primarchTarget = this.getTarget();

        AABB area = this.getBoundingBox().inflate(LEADERSHIP_RADIUS);

        List<PathfinderMob> troops = this.level().getEntitiesOfClass(
                PathfinderMob.class,
                area,
                mob -> mob != this
                        && mob.isAlive()
                        && FirstCrusadeFactionManager.areAllies(this, mob)
        );

        for (PathfinderMob troop : troops) {
            troop.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, false, false));
            troop.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0, false, false));

            if (primarchTarget != null
                    && troop.getTarget() == null
                    && FirstCrusadeFactionManager.canAttack(troop, primarchTarget)) {
                troop.setTarget(primarchTarget);
            }
        }
    }

    private void returnToCityIfStrayed() {
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
                    1.0D
            );
        }
    }

    private void preparePrimarch() {
        this.setCustomName(Component.literal("Primarch"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();

        equipAsPrimarch();
    }

    public void equipAsPrimarch() {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.POWER_SWORD.get()));

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

        if (this.level().getBlockEntity(this.commandCorePos) instanceof ImperialCommandCoreBlockEntity commandCore) {
            commandCore.onPrimarchDeath();
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    this.commandCorePos,
                    Component.translatable("msg.firstcrusade.bcast.primarch_fallen")
            );
        }
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

        preparePrimarch();
    }

    // =========================
    // GeckoLib
    // =========================

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide) {
            this.triggerAnim("attack", "attack");
        }
        return hit;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));

        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
