package com.example.examplemod.entity;

import com.example.examplemod.entity.projectile.SentinelCannonBoltEntity;
import com.example.examplemod.entity.projectile.SentinelMissileEntity;
import com.example.examplemod.performance.graphics.FCServerParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
import java.util.List;

public final class SentinelWalkerEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> RUNNING = SynchedEntityData.defineId(SentinelWalkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STOMPING = SynchedEntityData.defineId(SentinelWalkerEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.sentinel_walker.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.sentinel_walker.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.sentinel_walker.run");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("animation.sentinel_walker.shoot");
    private static final RawAnimation MISSILE_SHOOT = RawAnimation.begin().thenPlay("animation.sentinel_walker.missile_shoot");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.sentinel_walker.stomp");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int normalShotCooldown;
    private int missileCooldown;
    private int stompCooldown;
    private int stompTicks;
    private boolean stompImpactDone;

    public SentinelWalkerEntity(EntityType<? extends SentinelWalkerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 26;
        this.setMaxUpStep(1.5F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 220.0D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.18D)
                .add(Attributes.FOLLOW_RANGE, 56.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new SentinelCombatGoal(this));
        // Faction-aware: only real enemies, never allied Imperial units/vehicles.
        this.targetSelector.addGoal(2, new com.example.examplemod.FirstCrusadeNearestEnemyTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(RUNNING, false);
        this.entityData.define(STOMPING, false);
    }

    public boolean isRunningAnimation() {
        return this.entityData.get(RUNNING);
    }

    public void setRunningAnimation(boolean running) {
        this.entityData.set(RUNNING, running);
    }

    public boolean isStomping() {
        return this.entityData.get(STOMPING);
    }

    public void fireCannons(LivingEntity target) {
        if (this.level().isClientSide || this.normalShotCooldown > 0) return;

        Vec3 look = this.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 base = this.position().add(0, 3.1D, 0).add(look.scale(2.2D));
        Vec3 aim = target.getEyePosition().subtract(base).normalize();

        for (double side : new double[]{-0.95D, 0.95D}) {
            Vec3 muzzle = base.add(right.scale(side));
            SentinelCannonBoltEntity bolt = new SentinelCannonBoltEntity(this.level(), this, aim);
            bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
            this.level().addFreshEntity(bolt);
        }

        this.level().playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5F, 0.75F);
        this.triggerAnim("action_controller", "shoot");
        this.normalShotCooldown = 18;
    }

    public void fireMissiles(LivingEntity target) {
        if (this.level().isClientSide || this.missileCooldown > 0) return;

        Vec3 look = this.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 base = this.position().add(0, 4.0D, 0).add(look.scale(1.4D));

        for (double side : new double[]{-1.25D, 1.25D}) {
            Vec3 muzzle = base.add(right.scale(side));
            Vec3 aim = target.getEyePosition().subtract(muzzle).normalize();
            SentinelMissileEntity missile = new SentinelMissileEntity(this.level(), this, aim);
            missile.setPos(muzzle.x, muzzle.y, muzzle.z);
            this.level().addFreshEntity(missile);
        }

        this.level().playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.6F, 0.8F);
        this.triggerAnim("action_controller", "missile_shoot");
        this.missileCooldown = 100;
    }

    public void startStomp() {
        if (this.level().isClientSide || this.stompCooldown > 0 || this.isStomping()) return;
        this.entityData.set(STOMPING, true);
        this.stompTicks = 32;
        this.stompImpactDone = false;
        this.stompCooldown = 90;
        this.getNavigation().stop();
        this.triggerAnim("action_controller", "stomp");
    }

    private void applyStompImpact() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        AABB area = this.getBoundingBox().inflate(4.5D, 1.5D, 4.5D);
        List<LivingEntity> victims = this.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != this && entity.isAlive() && entity instanceof Monster && !this.isAlliedTo(entity));

        for (LivingEntity victim : victims) {
            Vec3 push = victim.position().subtract(this.position());
            if (push.horizontalDistanceSqr() < 0.01D) push = new Vec3(1, 0, 0);
            push = push.normalize();
            victim.hurt(this.damageSources().mobAttack(this), 16.0F);
            victim.setDeltaMovement(victim.getDeltaMovement().add(push.x * 1.35D, 0.35D, push.z * 1.35D));
            victim.hurtMarked = true;
        }

        FCServerParticles.send(serverLevel, ParticleTypes.EXPLOSION,
                FCServerParticles.Channel.EXPLOSION,
                this.getX(), this.getY() + 0.2D, this.getZ(), 5, 1.4D, 0.2D, 1.4D, 0.02D);
        FCServerParticles.send(serverLevel, ParticleTypes.CAMPFIRE_COSY_SMOKE,
                FCServerParticles.Channel.SMOKE,
                this.getX(), this.getY() + 0.1D, this.getZ(), 18, 2.0D, 0.15D, 2.0D, 0.03D);
        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.8F, 0.65F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;

        if (this.normalShotCooldown > 0) this.normalShotCooldown--;
        if (this.missileCooldown > 0) this.missileCooldown--;
        if (this.stompCooldown > 0) this.stompCooldown--;

        if (this.stompTicks > 0) {
            this.stompTicks--;
            this.getNavigation().stop();
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.25D, 1.0D, 0.25D));
            if (!this.stompImpactDone && this.stompTicks == 17) {
                this.stompImpactDone = true;
                this.applyStompImpact();
            }
            if (this.stompTicks == 0) this.entityData.set(STOMPING, false);
        }
    }

    private PlayState movementPredicate(AnimationState<SentinelWalkerEntity> state) {
        if (this.isStomping()) return PlayState.STOP;
        if (this.getDeltaMovement().horizontalDistanceSqr() > 0.001D) {
            state.getController().setAnimation(this.isRunningAnimation() ? RUN : WALK);
        } else {
            state.getController().setAnimation(IDLE);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement_controller", 4, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "action_controller", 0, state -> PlayState.STOP)
                .triggerableAnim("shoot", SHOOT)
                .triggerableAnim("missile_shoot", MISSILE_SHOOT)
                .triggerableAnim("stomp", STOMP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private static final class SentinelCombatGoal extends Goal {
        private final SentinelWalkerEntity sentinel;
        private int attackTimer;

        private SentinelCombatGoal(SentinelWalkerEntity sentinel) {
            this.sentinel = sentinel;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.sentinel.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            this.sentinel.setRunningAnimation(false);
            this.sentinel.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.sentinel.getTarget();
            if (target == null) return;

            this.sentinel.getLookControl().setLookAt(target, 35.0F, 25.0F);
            double distance = this.sentinel.distanceTo(target);

            if (this.sentinel.isStomping()) {
                this.sentinel.getNavigation().stop();
                return;
            }

            if (distance <= 5.2D && this.sentinel.stompCooldown <= 0) {
                this.sentinel.startStomp();
                return;
            }

            if (distance > 24.0D) {
                this.sentinel.setRunningAnimation(true);
                this.sentinel.getNavigation().moveTo(target, 1.05D);
            } else if (distance > 10.0D) {
                this.sentinel.setRunningAnimation(false);
                this.sentinel.getNavigation().moveTo(target, 0.62D);
            } else {
                this.sentinel.setRunningAnimation(false);
                this.sentinel.getNavigation().stop();
            }

            if (--this.attackTimer <= 0 && this.sentinel.hasLineOfSight(target)) {
                if (distance > 16.0D && this.sentinel.missileCooldown <= 0) {
                    this.sentinel.fireMissiles(target);
                    this.attackTimer = 40;
                } else if (distance < 40.0D) {
                    this.sentinel.fireCannons(target);
                    this.attackTimer = 22;
                }
            }
        }
    }
}
