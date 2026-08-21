package com.example.examplemod.unit.imperium;

import javax.annotation.Nullable;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;
import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.FirstCrusadeHurtByTargetGoal;
import com.example.examplemod.GeoVariantTextured;
import com.example.examplemod.LasgunAimingEntity;
import com.example.examplemod.LasgunCombatPose;
import com.example.examplemod.ai.combat.FCRangedAttackGoal;
import com.example.examplemod.ai.combat.FCTargetPriority;
import com.example.examplemod.ai.formation.FCFormationGoal;
import com.example.examplemod.ai.formation.FCSquadMember;
import com.example.examplemod.unit.profile.FCCombatProfile;
import com.example.examplemod.unit.profile.FCUnit;
import com.example.examplemod.unit.profile.FCVariantSet;
import com.example.examplemod.unit.profile.UnitRole;
import com.example.examplemod.weapon.FCProjectiles;
import com.example.examplemod.weapon.FCWeaponMount;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Guardsman Rifleman — the mod's reference unit.
 *
 * <p>Everything about this class is meant to be copied by the units that follow it: it is the first
 * mob that implements {@link FCUnit} (so the faction manager never needs an {@code instanceof} for
 * it), the first that uses {@link FCRangedAttackGoal} instead of a bespoke attack goal, the first
 * whose shots leave an actual barrel via {@link FCWeaponMount}, and the first with synced visual
 * variation through {@link FCVariantSet}.</p>
 *
 * <h2>Why this replaces the old Guardsman rather than editing it</h2>
 * <p>{@code GuardsmanEntity} carries a large amount of city-management state — rank, chapter,
 * specialisation, merit, command-core binding — that the barracks, patrol and morale managers all
 * read. Rewriting it in place would have meant touching a dozen manager classes at once. This is a
 * separate entity type so the new stack can be proven in isolation; migrating the existing
 * Guardsman onto it is a follow-up, not a prerequisite.</p>
 */
public class GuardsmanRiflemanEntity extends PathfinderMob
        implements RangedAttackMob, GeoEntity, FCUnit, FCSquadMember, LasgunAimingEntity, GeoVariantTextured {

    // ---- Animations. Names must match assets/firstcrusade/animations/guardsman_rifleman.animation.json ----
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.guardsman_rifleman.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.guardsman_rifleman.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.guardsman_rifleman.run");
    private static final RawAnimation AIM = RawAnimation.begin().thenLoop("animation.guardsman_rifleman.aim");
    private static final RawAnimation DRAW = RawAnimation.begin()
            .thenPlay("animation.guardsman_rifleman.draw")
            .thenLoop("animation.guardsman_rifleman.aim");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("animation.guardsman_rifleman.shoot");
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlay("animation.guardsman_rifleman.reload");
    private static final RawAnimation MELEE = RawAnimation.begin().thenPlay("animation.guardsman_rifleman.melee_attack");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.guardsman_rifleman.hurt");
    private static final RawAnimation DEATH_FRONT = RawAnimation.begin().thenPlay("animation.guardsman_rifleman.death_front");
    private static final RawAnimation DEATH_BACK = RawAnimation.begin().thenPlay("animation.guardsman_rifleman.death_back");

    private static final EntityDataAccessor<Integer> COMBAT_POSE =
            SynchedEntityData.defineId(GuardsmanRiflemanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMBAT_TICKS =
            SynchedEntityData.defineId(GuardsmanRiflemanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(GuardsmanRiflemanEntity.class, EntityDataSerializers.INT);
    /** Which death animation to play. Synced so every client shows the same fall. */
    private static final EntityDataAccessor<Integer> DEATH_DIRECTION =
            SynchedEntityData.defineId(GuardsmanRiflemanEntity.class, EntityDataSerializers.INT);

    /**
     * How many options each variant field has for this unit. Three helmets, four heads, three skin
     * tones and so on — the roll never produces an index the texture set cannot draw.
     */
    private static final int[] VARIANT_OPTIONS = { 4, 3, 3, 4, 4, 3, 8 };

    /**
     * A disciplined line trooper: controlled three-round bursts, holds its ground at about twelve
     * blocks, backs off if something reaches melee range, uses cover, and is brave but not suicidal.
     */
    private static final FCCombatProfile PROFILE = FCCombatProfile.builder()
            .range(20.0F, 12.0F, 4.0F)
            .burst(3, 5, 34)
            .shootTiming(20, 10, 6)
            .reload(12, 40)
            .accuracy(0.90F)
            .mustHaltToFire()
            .speeds(1.0D, 1.2D)
            .courage(0.7F)
            .retreatHealthThreshold(0.3F)
            .usesCover(true)
            .backsAwayFromMelee(true)
            .melee(2.5F, 20)
            .perception(20, 24.0D, 100)
            .build();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Ticks of hurt animation left to play. */
    private int hurtAnimationTicks;

    /**
     * The sergeant this trooper is currently following, if any.
     *
     * <p>Not persisted: squads re-form on their own after a world load (see
     * {@link com.example.examplemod.ai.formation.FCSquad}), which is cheaper and more robust than
     * resolving saved entity references.</p>
     */
    @Nullable
    private Mob squadLeader;

    public GuardsmanRiflemanEntity(EntityType<? extends GuardsmanRiflemanEntity> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.LASGUN.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);

        if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanPassDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // ------------------------------------------------------------------
    // FCUnit
    // ------------------------------------------------------------------

    @Override
    public FirstCrusadeFaction getUnitFaction() {
        return FirstCrusadeFaction.IMPERIUM;
    }

    @Override
    public UnitRole getUnitRole() {
        return UnitRole.LINE_INFANTRY;
    }

    @Override
    public FCCombatProfile getCombatProfile() {
        return PROFILE;
    }

    @Override
    public String getUnitId() {
        return "guardsman_rifleman";
    }

    // ------------------------------------------------------------------
    // FCSquadMember
    // ------------------------------------------------------------------

    @Override
    @Nullable
    public Mob getSquadLeader() {
        return this.squadLeader;
    }

    @Override
    public void setSquadLeader(@Nullable Mob leader) {
        this.squadLeader = leader;
    }

    // ------------------------------------------------------------------
    // Synced data
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(COMBAT_POSE, LasgunCombatPose.IDLE.ordinal());
        this.entityData.define(COMBAT_TICKS, POSE_NEVER_STARTED);
        this.entityData.define(VARIANT, 0);
        this.entityData.define(DEATH_DIRECTION, 0);
    }

    @Override
    public LasgunCombatPose getLasgunCombatPose() {
        return LasgunCombatPose.fromId(this.entityData.get(COMBAT_POSE));
    }

    @Override
    public int getLasgunCombatTicks() {
        int start = this.entityData.get(COMBAT_TICKS);
        return start == POSE_NEVER_STARTED ? 0 : Math.max(0, (int) this.level().getGameTime() - start);
    }

    @Override
    public void setLasgunCombatPose(LasgunCombatPose pose, int poseTicks) {
        LasgunCombatPose safePose = pose == null ? LasgunCombatPose.IDLE : pose;
        this.entityData.set(COMBAT_POSE, safePose.ordinal());
        this.entityData.set(COMBAT_TICKS,
                (int) this.level().getGameTime() - Math.max(0, poseTicks));
    }

    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, variant);
    }

    /**
     * Variant textures share one model. The texture index comes from the markings field so a squad
     * shows mixed regimental colours; the remaining fields are consumed by the renderer's layers.
     */
    @Override
    public ResourceLocation getVariantTexture() {
        int markings = FCVariantSet.get(getVariant(), FCVariantSet.FIELD_MARKINGS);
        return new ResourceLocation(ExampleMod.MODID,
                "textures/entity/guardsman_rifleman_" + markings + ".png");
    }

    /** Slight per-unit size difference, so a squad does not look stamped from one mould. */
    public float getVariantScale() {
        return FCVariantSet.scaleFor(getVariant());
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        setVariant(FCVariantSet.random(this.random, VARIANT_OPTIONS));
        return super.finalizeSpawn(level, difficulty, spawnType, spawnData, dataTag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FCVariant", getVariant());
        tag.putInt("FCDeathDirection", this.entityData.get(DEATH_DIRECTION));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        // Reading the tag back verbatim is what keeps a trooper's appearance stable across a
        // save/load cycle; rolling a fresh variant here would reshuffle every squad on reload.
        if (tag.contains("FCVariant")) {
            setVariant(tag.getInt("FCVariant"));
        }

        if (tag.contains("FCDeathDirection")) {
            this.entityData.set(DEATH_DIRECTION, tag.getInt("FCDeathDirection"));
        }
    }

    // ------------------------------------------------------------------
    // AI
    // ------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        // Above the attack goal: a pinned soldier takes cover instead of shooting. The goal only
        // runs while suppression is over its threshold, so it hands movement straight back once the
        // fire lets up — which is what makes a firefight ebb rather than freeze.
        this.goalSelector.addGoal(1, new com.example.examplemod.ai.combat.FCCoverGoal(this));

        this.goalSelector.addGoal(2, new FCRangedAttackGoal<>(this));

        // Below the attack goal on purpose: a trooper with an enemy in range fights instead of
        // dressing ranks. Formation is what the squad does when it is not shooting.
        this.goalSelector.addGoal(4, new FCFormationGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8D));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
    }

    /**
     * Target selection runs here rather than through a vanilla targeting goal so it can use the
     * role-weighted scorer and, more importantly, so it can be throttled: scanning every tick for
     * every trooper is the main AI cost in a large battle.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.hurtAnimationTicks > 0) {
            this.hurtAnimationTicks--;
        }

        if (!FCTargetPriority.shouldRescan(this, PROFILE.targetScanInterval())) {
            return;
        }

        // The squad's decision comes first, and taking it means running no scan at all — which is
        // the entire point of squad targeting. A rifleman that inherits its sergeant's target costs
        // nothing to aim, and the squad concentrates its fire instead of each man wounding a
        // different Ork.
        //
        // Individuality is not lost: FirstCrusadeHurtByTargetGoal sits at priority 1 in the target
        // selector, so anything that actually shoots this trooper still takes precedence over what
        // the sergeant is pointing at.
        LivingEntity squadTarget = this.getSquadTarget();

        if (squadTarget != null
                && FirstCrusadeFactionManager.canAttack(this, squadTarget)
                && this.distanceTo(squadTarget) <= PROFILE.targetScanRadius()) {
            if (this.getTarget() != squadTarget) {
                this.setTarget(squadTarget);
            }
            return;
        }

        // Unattached, or the squad has nobody in sight: decide alone.
        LivingEntity current = this.getTarget();
        LivingEntity candidate = FCTargetPriority.selectTarget(this, PROFILE.targetScanRadius());

        if (!FCTargetPriority.shouldKeepTarget(this, current, candidate, PROFILE.targetScanRadius(), 20.0D)) {
            this.setTarget(candidate);
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

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ------------------------------------------------------------------
    // Shooting
    // ------------------------------------------------------------------

    /**
     * Fires one las-bolt from the barrel.
     *
     * <p>The bolt is spawned by {@link FCProjectiles} at the {@code muzzle} bone position rather
     * than at the shooter's eyes, which is the fix for shots visibly leaving a trooper's face.</p>
     */
    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide) {
            return;
        }

        FCProjectiles.fireLasBolt(
                this,
                target,
                FCWeaponMount.GUARDSMAN_LASGUN,
                2.4F,
                PROFILE.accuracy(),
                4.5D
        );

        FCProjectiles.spawnCasingEject(this, FCWeaponMount.GUARDSMAN_LASGUN);

        this.level().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                com.example.examplemod.FCWeaponSounds.LASGUN_FIRE.get(),
                this.getSoundSource(),
                0.7F,
                1.6F + this.random.nextFloat() * 0.2F
        );

        triggerAnim("action", "shoot");
    }

    // ------------------------------------------------------------------
    // Damage and death
    // ------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);

        if (hurt && !this.level().isClientSide) {
            this.hurtAnimationTicks = 8;
            triggerAnim("action", "hurt");
        }

        return hurt;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide) {
            // Fall away from whatever killed us: shot from the front, fall backwards.
            int direction = 0;

            if (damageSource.getEntity() != null) {
                double dx = damageSource.getEntity().getX() - this.getX();
                double dz = damageSource.getEntity().getZ() - this.getZ();
                double yaw = Math.toRadians(this.yBodyRot);
                double forwardX = Math.sin(yaw);
                double forwardZ = -Math.cos(yaw);

                direction = (dx * forwardX + dz * forwardZ) > 0.0D ? 1 : 0;
            }

            this.entityData.set(DEATH_DIRECTION, direction);
        }

        super.die(damageSource);
    }

    // ------------------------------------------------------------------
    // GeckoLib
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Locomotion and weapon carriage. Transition length 5 ticks keeps direction changes from
        // restarting the walk cycle, which is what makes stock mob animation look robotic.
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(
                        this.entityData.get(DEATH_DIRECTION) == 1 ? DEATH_BACK : DEATH_FRONT);
            }

            LasgunCombatPose pose = getLasgunCombatPose();

            if (state.isMoving()) {
                // Run when closing on an enemy, walk otherwise. Deliberately keyed off aggression
                // alone: an earlier version also consulted the currently playing animation, which
                // was both unverifiable against this GeckoLib version and self-reinforcing (running
                // because you are already running).
                return state.setAndContinue(this.isAggressive() ? RUN : WALK);
            }

            return switch (pose) {
                case DRAWING -> state.setAndContinue(DRAW);
                case AIMING, SHOOTING, COOLDOWN -> state.setAndContinue(AIM);
                case IDLE -> state.setAndContinue(IDLE);
            };
        }));

        // One-shot actions layered over locomotion, fired by triggerAnim from the server.
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("shoot", SHOOT)
                .triggerableAnim("reload", RELOAD)
                .triggerableAnim("melee", MELEE)
                .triggerableAnim("hurt", HURT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
