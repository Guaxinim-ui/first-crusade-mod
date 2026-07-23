package com.example.examplemod.entity.vehicle;

import com.example.examplemod.registry.ModVehicleEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class ImperialBattleTankEntity extends Monster {
    public static final byte ACTION_IDLE = 0;
    public static final byte ACTION_AIM = 1;
    public static final byte ACTION_FIRE = 2;
    public static final byte ACTION_RELOAD = 3;

    private static final EntityDataAccessor<Float> TURRET_YAW =
            SynchedEntityData.defineId(ImperialBattleTankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CANNON_PITCH =
            SynchedEntityData.defineId(ImperialBattleTankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RECOIL =
            SynchedEntityData.defineId(ImperialBattleTankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> AMMO =
            SynchedEntityData.defineId(ImperialBattleTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RELOAD_TICKS =
            SynchedEntityData.defineId(ImperialBattleTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> ACTION =
            SynchedEntityData.defineId(ImperialBattleTankEntity.class, EntityDataSerializers.BYTE);

    public static final int MAGAZINE_SIZE = 6;
    public static final int RELOAD_DURATION = 80;
    public static final int FIRE_COOLDOWN = 46;
    public static final double MAX_ATTACK_DISTANCE = 52.0D;

    private int fireCooldown;
    private float idleScanCenterYaw;

    public ImperialBattleTankEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 45;
        this.setMaxUpStep(1.1F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240.0D)
                .add(Attributes.ARMOR, 24.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.14D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TURRET_YAW, 0.0F);
        this.entityData.define(CANNON_PITCH, 0.0F);
        this.entityData.define(RECOIL, 0.0F);
        this.entityData.define(AMMO, MAGAZINE_SIZE);
        this.entityData.define(RELOAD_TICKS, 0);
        this.entityData.define(ACTION, ACTION_IDLE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TankCombatGoal(this));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.55D, 140));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2,
                new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, this::isValidTankTarget));
    }

    private boolean isValidTankTarget(LivingEntity target) {
        return target != this
                && target.isAlive()
                && !(target instanceof ImperialBattleTankEntity)
                // Only fire on genuine enemies — never allied Imperial units/vehicles.
                && com.example.examplemod.FirstCrusadeFactionManager.canAttack(this, target);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.fireCooldown > 0) {
            this.fireCooldown--;
        }

        float recoil = this.getRecoil();
        if (recoil > 0.0F) {
            this.setRecoil(Math.max(0.0F, recoil - 0.16F));
        }

        if (!this.level().isClientSide && this.getTarget() == null && !this.isReloading()) {
            if (this.tickCount % 160 == 0) {
                this.idleScanCenterYaw = this.getYRot();
            }

            float scan = Mth.sin(this.tickCount * 0.035F) * 68.0F;
            this.setTurretYaw(Mth.approachDegrees(this.getTurretYaw(), this.idleScanCenterYaw + scan, 1.4F));
            this.setCannonPitch(Mth.approachDegrees(this.getCannonPitch(), -2.0F, 0.35F));
            this.setActionState(ACTION_IDLE);
        }
    }

    public void aimTurretAt(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double muzzleY = this.getY() + 2.25D;
        double targetY = target.getY() + target.getBbHeight() * 0.55D;

        float desiredYaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float desiredPitch = (float)(-Mth.atan2(targetY - muzzleY, horizontal) * Mth.RAD_TO_DEG);

        this.setTurretYaw(Mth.approachDegrees(this.getTurretYaw(), desiredYaw, 2.7F));
        this.setCannonPitch(Mth.approachDegrees(this.getCannonPitch(),
                Mth.clamp(desiredPitch, -12.0F, 18.0F), 1.3F));
    }

    public boolean isTurretAligned(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float desiredYaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Math.abs(Mth.wrapDegrees(desiredYaw - this.getTurretYaw())) < 4.5F;
    }

    public void fireMainCannon(LivingEntity target) {
        if (this.level().isClientSide || this.fireCooldown > 0 || this.isReloading() || this.getAmmo() <= 0) {
            return;
        }

        Vec3 direction = Vec3.directionFromRotation(this.getCannonPitch(), this.getTurretYaw()).normalize();
        Vec3 muzzle = new Vec3(this.getX(), this.getY() + 2.25D, this.getZ())
                .add(direction.scale(3.35D));

        TankShellEntity shell = new TankShellEntity(ModVehicleEntities.TANK_SHELL.get(), this.level());
        shell.setOwner(this);
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        shell.shoot(direction.x, direction.y, direction.z, 2.15F, 0.35F);

        this.level().addFreshEntity(shell);
        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                this.getSoundSource(), 3.2F, 0.72F + this.random.nextFloat() * 0.08F);

        this.setAmmo(this.getAmmo() - 1);
        this.fireCooldown = FIRE_COOLDOWN;
        this.setRecoil(1.0F);
        this.setActionState(ACTION_FIRE);

        if (this.getAmmo() <= 0) {
            this.beginReload();
        }
    }

    public void beginReload() {
        if (!this.isReloading()) {
            this.setReloadTicks(1);
            this.setActionState(ACTION_RELOAD);
            this.level().playSound(null, this.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN,
                    this.getSoundSource(), 1.1F, 0.75F);
        }
    }

    public void tickReload() {
        int ticks = this.getReloadTicks();
        if (ticks <= 0) {
            return;
        }

        ticks++;
        this.setReloadTicks(ticks);
        this.setActionState(ACTION_RELOAD);

        if (ticks == 30 || ticks == 52) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND,
                    this.getSoundSource(), 0.75F, ticks == 30 ? 1.35F : 0.95F);
        }

        if (ticks >= RELOAD_DURATION) {
            this.setAmmo(MAGAZINE_SIZE);
            this.setReloadTicks(0);
            this.setActionState(ACTION_IDLE);
            this.level().playSound(null, this.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                    this.getSoundSource(), 1.1F, 0.82F);
        }
    }

    public boolean isReloading() {
        return this.getReloadTicks() > 0;
    }

    public float getReloadProgress(float partialTick) {
        if (!this.isReloading()) {
            return 0.0F;
        }
        return Mth.clamp((this.getReloadTicks() + partialTick) / (float)RELOAD_DURATION, 0.0F, 1.0F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.32F;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            amount *= 0.70F;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return distance > 10.0F && super.causeFallDamage(distance, multiplier * 0.25F, source);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.MINECART_RIDING;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ANVIL_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_EXPLODE;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.1F, 0.62F);
    }

    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose,
                                         net.minecraft.world.entity.EntityDimensions dimensions) {
        return 2.55F;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
        if (!(entity instanceof ImperialBattleTankEntity)) {
            super.doPush(entity);
        }
    }

    public float getTurretYaw() {
        return this.entityData.get(TURRET_YAW);
    }

    public void setTurretYaw(float value) {
        this.entityData.set(TURRET_YAW, Mth.wrapDegrees(value));
    }

    public float getCannonPitch() {
        return this.entityData.get(CANNON_PITCH);
    }

    public void setCannonPitch(float value) {
        this.entityData.set(CANNON_PITCH, value);
    }

    public float getRecoil() {
        return this.entityData.get(RECOIL);
    }

    public void setRecoil(float value) {
        this.entityData.set(RECOIL, Mth.clamp(value, 0.0F, 1.0F));
    }

    public int getAmmo() {
        return this.entityData.get(AMMO);
    }

    public void setAmmo(int value) {
        this.entityData.set(AMMO, Mth.clamp(value, 0, MAGAZINE_SIZE));
    }

    public int getReloadTicks() {
        return this.entityData.get(RELOAD_TICKS);
    }

    public void setReloadTicks(int value) {
        this.entityData.set(RELOAD_TICKS, Math.max(0, value));
    }

    public byte getActionState() {
        return this.entityData.get(ACTION);
    }

    public void setActionState(byte state) {
        this.entityData.set(ACTION, state);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TankAmmo", this.getAmmo());
        tag.putInt("TankReloadTicks", this.getReloadTicks());
        tag.putFloat("TankTurretYaw", this.getTurretYaw());
        tag.putFloat("TankCannonPitch", this.getCannonPitch());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setAmmo(tag.contains("TankAmmo") ? tag.getInt("TankAmmo") : MAGAZINE_SIZE);
        this.setReloadTicks(tag.getInt("TankReloadTicks"));
        this.setTurretYaw(tag.getFloat("TankTurretYaw"));
        this.setCannonPitch(tag.getFloat("TankCannonPitch"));
    }

    private static class TankCombatGoal extends Goal {
        private final ImperialBattleTankEntity tank;

        private TankCombatGoal(ImperialBattleTankEntity tank) {
            this.tank = tank;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.tank.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.tank.getTarget();
            return target != null
                    && target.isAlive()
                    && this.tank.distanceToSqr(target) <= MAX_ATTACK_DISTANCE * MAX_ATTACK_DISTANCE * 1.35D;
        }

        @Override
        public void stop() {
            this.tank.getNavigation().stop();
            if (!this.tank.isReloading()) {
                this.tank.setActionState(ACTION_IDLE);
            }
        }

        @Override
        public void tick() {
            LivingEntity target = this.tank.getTarget();
            if (target == null) {
                return;
            }

            if (this.tank.isReloading()) {
                this.tank.getNavigation().stop();
                this.tank.tickReload();
                return;
            }

            this.tank.aimTurretAt(target);
            this.tank.setActionState(ACTION_AIM);

            double distanceSqr = this.tank.distanceToSqr(target);
            boolean hasSight = this.tank.getSensing().hasLineOfSight(target);

            if (distanceSqr > 32.0D * 32.0D) {
                this.tank.getNavigation().moveTo(target, 0.72D);
            } else if (distanceSqr < 10.0D * 10.0D) {
                Vec3 away = this.tank.position().subtract(target.position()).normalize().scale(8.0D);
                this.tank.getNavigation().moveTo(
                        this.tank.getX() + away.x,
                        this.tank.getY(),
                        this.tank.getZ() + away.z,
                        0.58D
                );
            } else {
                this.tank.getNavigation().stop();
            }

            if (this.tank.getAmmo() <= 0) {
                this.tank.beginReload();
                return;
            }

            if (distanceSqr <= MAX_ATTACK_DISTANCE * MAX_ATTACK_DISTANCE
                    && hasSight
                    && this.tank.isTurretAligned(target)
                    && this.tank.fireCooldown <= 0) {
                this.tank.fireMainCannon(target);
            }
        }
    }
}
