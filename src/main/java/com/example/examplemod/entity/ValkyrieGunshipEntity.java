package com.example.examplemod.entity;

import com.example.examplemod.entity.projectile.ValkyrieMultilaserBoltEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

public final class ValkyrieGunshipEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(ValkyrieGunshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.valkyrie_gunship.fly");
    private static final RawAnimation TAKEOFF = RawAnimation.begin().thenPlay("animation.valkyrie_gunship.takeoff");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlayAndHold("animation.valkyrie_gunship.land");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("animation.valkyrie_gunship.shoot");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int shotCooldown;
    private int idleAirTicks;

    public ValkyrieGunshipEntity(EntityType<? extends ValkyrieGunshipEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 18, true);
        this.setNoGravity(true);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 260.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.FLYING_SPEED, 0.42D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 72.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanFloat(true);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new GunshipCombatGoal(this));
        // Faction-aware: only real enemies (Orks/hostiles), never allied Imperial units/vehicles.
        this.targetSelector.addGoal(2, new com.example.examplemod.FirstCrusadeNearestEnemyTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LANDED, false);
    }

    public boolean isLanded() {
        return this.entityData.get(LANDED);
    }

    public void setLanded(boolean landed) {
        if (landed == isLanded()) return;
        this.entityData.set(LANDED, landed);
        this.setNoGravity(!landed);
        if (!this.level().isClientSide) {
            this.triggerAnim("action_controller", landed ? "land" : "takeoff");
            if (!landed) this.setDeltaMovement(this.getDeltaMovement().add(0, 0.32D, 0));
        }
    }

    public void fireMultilaser(LivingEntity target) {
        if (this.level().isClientSide || this.shotCooldown > 0) return;

        Vec3 look = this.getLookAngle().normalize();
        Vec3 muzzle = this.position().add(look.scale(4.3D)).add(0.0D, 1.0D, 0.0D);
        Vec3 aim = target.getEyePosition().subtract(muzzle).normalize();
        ValkyrieMultilaserBoltEntity bolt = new ValkyrieMultilaserBoltEntity(this.level(), this, aim);
        bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
        this.level().addFreshEntity(bolt);
        this.level().playSound(null, this.blockPosition(), com.example.examplemod.FCWeaponSounds.AUTOCANNON_FIRE.get(), SoundSource.HOSTILE, 1.2F, 1.45F);
        this.triggerAnim("action_controller", "shoot");
        this.shotCooldown = 4;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;

        if (this.shotCooldown > 0) this.shotCooldown--;
        if (!this.isLanded()) {
            this.setNoGravity(true);
            Vec3 velocity = this.getDeltaMovement();
            this.setDeltaMovement(velocity.x, Mth.clamp(velocity.y, -0.30D, 0.30D), velocity.z);
        }

        if (this.getTarget() == null) {
            this.idleAirTicks++;
            if (!this.isLanded() && this.idleAirTicks > 180) {
                BlockPos ground = this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.blockPosition());
                double landingY = ground.getY() + 1.6D;
                if (this.getY() > landingY + 0.7D) {
                    this.getMoveControl().setWantedPosition(this.getX(), landingY, this.getZ(), 0.45D);
                } else {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setLanded(true);
                }
            }
        } else {
            this.idleAirTicks = 0;
            if (this.isLanded()) this.setLanded(false);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    private PlayState movementPredicate(AnimationState<ValkyrieGunshipEntity> state) {
        if (!this.isLanded()) {
            state.getController().setAnimation(FLY);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement_controller", 2, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "action_controller", 0, state -> PlayState.STOP)
                .triggerableAnim("takeoff", TAKEOFF)
                .triggerableAnim("land", LAND)
                .triggerableAnim("shoot", SHOOT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private static final class GunshipCombatGoal extends Goal {
        private final ValkyrieGunshipEntity gunship;
        private int fireTimer;
        private int changeSideTimer;
        private int strafeSign = 1;

        private GunshipCombatGoal(ValkyrieGunshipEntity gunship) {
            this.gunship = gunship;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.gunship.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity target = this.gunship.getTarget();
            if (target == null) return;

            if (this.gunship.isLanded()) {
                this.gunship.setLanded(false);
                return;
            }

            if (--this.changeSideTimer <= 0) {
                this.strafeSign = -this.strafeSign;
                this.changeSideTimer = 60 + this.gunship.getRandom().nextInt(50);
            }

            Vec3 toTarget = target.position().subtract(this.gunship.position()).normalize();
            Vec3 side = new Vec3(-toTarget.z, 0, toTarget.x).scale(this.strafeSign * 10.0D);
            BlockPos ground = this.gunship.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.blockPosition());
            double desiredY = Math.max(target.getY() + 9.0D, ground.getY() + 8.0D);
            Vec3 destination = target.position().add(side).add(0, desiredY - target.getY(), 0);

            double distance = this.gunship.distanceTo(target);
            if (distance < 14.0D) destination = this.gunship.position().subtract(toTarget.scale(12.0D)).add(0, 2.5D, 0);

            this.gunship.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.1D);
            this.gunship.getLookControl().setLookAt(target, 45.0F, 45.0F);

            if (--this.fireTimer <= 0 && distance < 58.0D && this.gunship.hasLineOfSight(target)) {
                this.gunship.fireMultilaser(target);
                this.fireTimer = 7;
            }
        }
    }
}
