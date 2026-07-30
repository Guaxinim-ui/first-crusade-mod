package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Adeptus Astartes infantry with two field loadouts: a chainsword assault marine
 * carrying a bolt-pistol sidearm, and a marine with the two-handed bolter.
 */
public class SpaceMarineEntity extends PathfinderMob implements GeoEntity, RangedAttackMob {
    public static final int LOADOUT_CHAINSWORD = 0;
    public static final int LOADOUT_BOLTER = 1;
    public static final int LOADOUT_DUAL_BOLTER = 2;

    private static final EntityDataAccessor<Integer> WEAPON_LOADOUT =
            SynchedEntityData.defineId(SpaceMarineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CHAINSAW_RUNNING =
            SynchedEntityData.defineId(SpaceMarineEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE_MELEE = RawAnimation.begin().thenLoop("animation.space_marine.idle_melee");
    private static final RawAnimation WALK_MELEE = RawAnimation.begin().thenLoop("animation.space_marine.walk_melee");
    private static final RawAnimation ATTACK_MELEE = RawAnimation.begin().thenPlay("animation.space_marine.attack_melee");

    private static final RawAnimation IDLE_BOLTER = RawAnimation.begin().thenLoop("animation.space_marine.idle_bolter");
    private static final RawAnimation WALK_BOLTER = RawAnimation.begin().thenLoop("animation.space_marine.walk_bolter");
    private static final RawAnimation FIRE_BOLTER = RawAnimation.begin().thenPlay("animation.space_marine.fire_bolter");

    private static final RawAnimation IDLE_DUAL = RawAnimation.begin().thenLoop("animation.space_marine.idle_dual");
    private static final RawAnimation WALK_DUAL = RawAnimation.begin().thenLoop("animation.space_marine.walk_dual");
    private static final RawAnimation FIRE_DUAL = RawAnimation.begin().thenPlay("animation.space_marine.fire_dual");
    private static final RawAnimation CHAINSAW_STOPPED = RawAnimation.begin().thenLoop("animation.space_marine.chainsword_stopped");
    private static final RawAnimation CHAINSAW_RUNNING_ANIMATION = RawAnimation.begin().thenLoop("animation.space_marine.chainsword_running");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final int NEOPHYTE_TRIAL_MIN_TICKS = 1200;
    private static final int NEOPHYTE_FALLBACK_TICKS = 24000;

    private BlockPos commandCorePos;
    private BlockPos guardPostPos;

    private boolean neophyte = false;
    private int neophyteTicks = 0;
    private boolean battleProven = false;
    private int chainswordCombatGraceTicks;
    private long nextChainswordLoopSound;

    public SpaceMarineEntity(EntityType<? extends SpaceMarineEntity> entityType, Level level) {
        super(entityType, level);
        prepareSpaceMarine();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(WEAPON_LOADOUT, LOADOUT_CHAINSWORD);
        this.entityData.define(CHAINSAW_RUNNING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SpaceMarineRangedAttackGoal(this));
        this.goalSelector.addGoal(1, new SpaceMarineMeleeAttackGoal(this));
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
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);

        // Only two field loadouts remain: a chainsword assault marine with a bolt-pistol
        // sidearm (~45%) or a marine with the two-handed bolter (~55%).
        if (tag == null || !tag.contains("WeaponLoadout")) {
            setWeaponLoadout(this.getRandom().nextFloat() < 0.45F ? LOADOUT_CHAINSWORD : LOADOUT_BOLTER);
        }

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

        if (this.neophyte) {
            this.neophyteTicks++;
            boolean trialComplete = (this.battleProven && this.neophyteTicks >= NEOPHYTE_TRIAL_MIN_TICKS)
                    || this.neophyteTicks >= NEOPHYTE_FALLBACK_TICKS;
            if (trialComplete) {
                matureIntoFullMarine();
            }
        }

        updateChainswordMotor();

        if (this.tickCount % 100 == 0) {
            prepareSpaceMarine();
            moveToGuardPostIfNeeded();
        }
    }

    private void updateChainswordMotor() {
        boolean shouldRun = getWeaponLoadout() == LOADOUT_CHAINSWORD
                && this.getTarget() != null
                && this.getTarget().isAlive();

        if (shouldRun) {
            this.chainswordCombatGraceTicks = ChainswordItem.COMBAT_GRACE_TICKS;
        } else if (this.chainswordCombatGraceTicks > 0) {
            this.chainswordCombatGraceTicks--;
        }

        boolean running = getWeaponLoadout() == LOADOUT_CHAINSWORD && this.chainswordCombatGraceTicks > 0;
        boolean wasRunning = this.entityData.get(CHAINSAW_RUNNING);

        if (running != wasRunning) {
            this.entityData.set(CHAINSAW_RUNNING, running);
            this.level().playSound(
                    null,
                    this.blockPosition(),
                    running ? FCWeaponSounds.CHAIN_SWORD_START.get() : FCWeaponSounds.CHAIN_SWORD_STOP.get(),
                    SoundSource.HOSTILE,
                    running ? 1.05F : 0.9F,
                    running ? 0.90F : 0.94F
            );
            if (running) {
                this.nextChainswordLoopSound = this.level().getGameTime() + 5L;
            }
        }

        if (running && this.level().getGameTime() >= this.nextChainswordLoopSound) {
            this.level().playSound(
                    null,
                    this.blockPosition(),
                    FCWeaponSounds.CHAIN_SWORD_LOOP.get(),
                    SoundSource.HOSTILE,
                    0.72F,
                    0.90F + this.getRandom().nextFloat() * 0.08F
            );
            this.nextChainswordLoopSound = this.level().getGameTime() + 14L;
        }
    }

    public boolean isChainswordRunning() {
        return this.entityData.get(CHAINSAW_RUNNING);
    }

    private void prepareSpaceMarine() {
        updateNameForState();
        this.setPersistenceRequired();
        equipAsSpaceMarine();
    }

    private void updateNameForState() {
        this.setCustomName(Component.literal(this.neophyte ? "Neophyte" : "Space Marine"));
        this.setCustomNameVisible(true);
    }

    public void beginAsNeophyte() {
        this.neophyte = true;
        this.neophyteTicks = 0;
        this.battleProven = false;
        applyNeophyteStats();
        updateNameForState();
    }

    public boolean isNeophyte() {
        return this.neophyte;
    }

    public void markBattleProven() {
        if (this.neophyte) {
            this.battleProven = true;
        }
    }

    private void applyNeophyteStats() {
        setBaseAttribute(Attributes.MAX_HEALTH, 55.0D);
        setBaseAttribute(Attributes.ATTACK_DAMAGE, 8.0D);
        setBaseAttribute(Attributes.ARMOR, 12.0D);
        this.setHealth(this.getMaxHealth());
    }

    private void applyFullMarineStats() {
        setBaseAttribute(Attributes.MAX_HEALTH, 90.0D);
        setBaseAttribute(Attributes.ATTACK_DAMAGE, 13.0D);
        setBaseAttribute(Attributes.ARMOR, 18.0D);
        this.setHealth(this.getMaxHealth());
    }

    private void matureIntoFullMarine() {
        this.neophyte = false;
        this.neophyteTicks = 0;
        this.battleProven = false;
        applyFullMarineStats();
        updateNameForState();

        if (this.commandCorePos != null && this.level() instanceof ServerLevel serverLevel) {
            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    this.commandCorePos,
                    Component.translatable("msg.firstcrusade.bcast.neophyte_matured")
            );
        }
    }

    private void setBaseAttribute(Attribute attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void moveToGuardPostIfNeeded() {
        if (this.guardPostPos == null || this.getTarget() != null) {
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
        return FirstCrusadeFactionManager.canAttack(this, target) && super.canAttack(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !FirstCrusadeFactionManager.canAttack(this, target)) {
            super.setTarget(null);
            return;
        }
        super.setTarget(target);
    }

    public int getWeaponLoadout() {
        return this.entityData.get(WEAPON_LOADOUT);
    }

    public void setWeaponLoadout(int loadout) {
        // Dual-bolter (2) is retired; clamp legacy saves down to the two-handed bolter.
        int safeLoadout = Math.max(LOADOUT_CHAINSWORD, Math.min(LOADOUT_BOLTER, loadout));
        this.entityData.set(WEAPON_LOADOUT, safeLoadout);
        if (!this.level().isClientSide) {
            equipAsSpaceMarine();
        }
    }

    public boolean isRangedLoadout() {
        return getWeaponLoadout() == LOADOUT_BOLTER || getWeaponLoadout() == LOADOUT_DUAL_BOLTER;
    }

    public boolean isDualBolterLoadout() {
        return getWeaponLoadout() == LOADOUT_DUAL_BOLTER;
    }

    public void equipAsSpaceMarine() {
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

        if (getWeaponLoadout() == LOADOUT_CHAINSWORD) {
            // Chainsword in the main hand, bolt pistol carried as an off-hand sidearm.
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.CHAINSWORD.get()));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(FCRegistry.BOLTER.get()));
        } else {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.BOLTER.get()));
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide || !isRangedLoadout() || target == null || !target.isAlive()) {
            return;
        }

        if (!FriendlyFireGuard.hasClearShot(this, target)) {
            FriendlyFireGuard.strafeForClearShot(this, target);
            return;
        }

        fireBolterRound(target, 0.0D, 12.0D, 0.65F);
        this.triggerAnim("attack", "fire_bolter");

        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                FCWeaponSounds.BOLTER_FIRE.get(),
                SoundSource.HOSTILE,
                0.85F,
                0.66F
        );
    }

    private void fireBolterRound(LivingEntity target, double sideOffset, double damage, float inaccuracy) {
        LasgunShotEntity projectile = new LasgunShotEntity(this.level(), this);
        projectile.setBaseDamage(damage);
        projectile.setKnockback(1);
        projectile.setMicroExplosive(true);

        double yawRadians = Math.toRadians(this.getYRot());
        double offsetX = Math.cos(yawRadians) * sideOffset;
        double offsetZ = Math.sin(yawRadians) * sideOffset;
        projectile.setPos(projectile.getX() + offsetX, projectile.getY() - 0.15D, projectile.getZ() + offsetZ);

        double xPower = target.getX() - projectile.getX();
        double yPower = target.getY(0.55D) - projectile.getY();
        double zPower = target.getZ() - projectile.getZ();
        double horizontalDistance = Math.sqrt(xPower * xPower + zPower * zPower);

        projectile.shoot(xPower, yPower + horizontalDistance * 0.04D, zPower, 4.4F, inaccuracy);
        this.level().addFreshEntity(projectile);
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

        if (this.level().isClientSide || this.commandCorePos == null) {
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

        tag.putBoolean("Neophyte", this.neophyte);
        tag.putInt("NeophyteTicks", this.neophyteTicks);
        tag.putBoolean("BattleProven", this.battleProven);
        tag.putInt("WeaponLoadout", getWeaponLoadout());
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

        this.neophyte = tag.getBoolean("Neophyte");
        this.neophyteTicks = tag.getInt("NeophyteTicks");
        this.battleProven = tag.getBoolean("BattleProven");
        if (tag.contains("WeaponLoadout")) {
            setWeaponLoadout(tag.getInt("WeaponLoadout"));
        }

        prepareSpaceMarine();
        if (this.neophyte) {
            applyNeophyteStats();
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide && getWeaponLoadout() == LOADOUT_CHAINSWORD) {
            this.chainswordCombatGraceTicks = ChainswordItem.COMBAT_GRACE_TICKS;
            this.triggerAnim("attack", "melee");
            this.level().playSound(null, target.blockPosition(), FCWeaponSounds.CHAIN_SWORD_HIT.get(),
                    SoundSource.HOSTILE, 1.1F, 0.86F + this.getRandom().nextFloat() * 0.10F);
        }
        return hit;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            boolean moving = state.isMoving();
            return switch (getWeaponLoadout()) {
                case LOADOUT_DUAL_BOLTER -> state.setAndContinue(moving ? WALK_DUAL : IDLE_DUAL);
                case LOADOUT_BOLTER -> state.setAndContinue(moving ? WALK_BOLTER : IDLE_BOLTER);
                default -> state.setAndContinue(moving ? WALK_MELEE : IDLE_MELEE);
            };
        }));

        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("melee", ATTACK_MELEE)
                .triggerableAnim("fire_bolter", FIRE_BOLTER)
                .triggerableAnim("fire_dual", FIRE_DUAL));

        controllers.add(new AnimationController<>(this, "chainsword_motor", 1, state ->
                state.setAndContinue(isChainswordRunning() ? CHAINSAW_RUNNING_ANIMATION : CHAINSAW_STOPPED)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    private static final class SpaceMarineRangedAttackGoal extends RangedAttackGoal {
        private final SpaceMarineEntity marine;

        private SpaceMarineRangedAttackGoal(SpaceMarineEntity marine) {
            super(marine, 1.0D, 18, 30.0F);
            this.marine = marine;
        }

        @Override
        public boolean canUse() {
            return this.marine.isRangedLoadout() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.marine.isRangedLoadout() && super.canContinueToUse();
        }
    }

    private static final class SpaceMarineMeleeAttackGoal extends MeleeAttackGoal {
        private final SpaceMarineEntity marine;

        private SpaceMarineMeleeAttackGoal(SpaceMarineEntity marine) {
            super(marine, 1.25D, true);
            this.marine = marine;
        }

        @Override
        public boolean canUse() {
            return !this.marine.isRangedLoadout() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.marine.isRangedLoadout() && super.canContinueToUse();
        }
    }
}
