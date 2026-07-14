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
import net.minecraft.world.level.Level;

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
 * The Warboss — the Ork answer to the Primarch. The biggest, meanest Ork of a camp, he leads the
 * WAAAGH! in person: he buffs and rallies every Ork around him and marches on the Imperial city
 * he was raised to crush. The Ork mirror of {@link PrimarchEntity}.
 *
 * <p>Rendered with GeckoLib from the owner's Blockbench model (geo/warboss.geo.json). The choppa,
 * shoota and boss-pole are bones in that model, so — unlike a vanilla humanoid mob — the weapons
 * are drawn by the model itself; the held POWER_KLAW item stays for combat/logic only.
 */
public class WarbossEntity extends PathfinderMob implements GeoEntity {
    private static final double LEADERSHIP_RADIUS = 24.0D;

    // Names must match the keys in assets/firstcrusade/animations/warboss.animation.json.
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.warboss.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.warboss.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.warboss.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos targetCorePos;

    public WarbossEntity(EntityType<? extends WarbossEntity> entityType, Level level) {
        super(entityType, level);
        prepareWarboss();
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
                .add(Attributes.MAX_HEALTH, 250.0D)
                .add(Attributes.ARMOR, 15.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D);
    }

    public void setTargetCore(BlockPos corePos) {
        this.targetCorePos = corePos == null ? null : corePos.immutable();
        this.setPersistenceRequired();
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
            leadTheWaaagh();
        }

        if (this.tickCount % 80 == 0) {
            marchOnTheCity();
        }
    }

    // Rally the boyz: buff every nearby Ork and throw the leaderless ones at the Warboss's foe.
    private void leadTheWaaagh() {
        LivingEntity warbossTarget = this.getTarget();

        List<PathfinderMob> boyz = this.level().getEntitiesOfClass(
                PathfinderMob.class,
                this.getBoundingBox().inflate(LEADERSHIP_RADIUS),
                mob -> mob != this && mob.isAlive() && FirstCrusadeFactionManager.areAllies(this, mob)
        );

        for (PathfinderMob boy : boyz) {
            boy.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0, false, false));

            if (warbossTarget != null
                    && boy.getTarget() == null
                    && FirstCrusadeFactionManager.canAttack(boy, warbossTarget)) {
                boy.setTarget(warbossTarget);
            }
        }
    }

    private void marchOnTheCity() {
        if (this.targetCorePos == null || this.getTarget() != null) {
            return;
        }

        double distance = this.distanceToSqr(
                this.targetCorePos.getX() + 0.5D,
                this.targetCorePos.getY(),
                this.targetCorePos.getZ() + 0.5D
        );

        if (distance > 9.0D) {
            this.getNavigation().moveTo(
                    this.targetCorePos.getX() + 0.5D,
                    this.targetCorePos.getY(),
                    this.targetCorePos.getZ() + 0.5D,
                    1.0D
            );
        }
    }

    private void prepareWarboss() {
        this.setCustomName(Component.literal("Ork Warboss"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();

        equipAsWarboss();
    }

    public void equipAsWarboss() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.POWER_KLAW.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide) {
            this.triggerAnim("attack", "attack");
        }
        return hit;
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

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        if (this.level().isClientSide || this.targetCorePos == null) {
            return;
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    this.targetCorePos,
                    Component.translatable("msg.firstcrusade.bcast.warboss_slain")
            );
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (this.targetCorePos != null) {
            tag.putLong("TargetCorePos", this.targetCorePos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("TargetCorePos")) {
            this.targetCorePos = BlockPos.of(tag.getLong("TargetCorePos"));
        }

        prepareWarboss();
    }

    // =========================
    // GeckoLib
    // =========================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Movement: walk while moving, idle otherwise.
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));

        // Attack: a one-shot, triggered from doHurtTarget above.
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
