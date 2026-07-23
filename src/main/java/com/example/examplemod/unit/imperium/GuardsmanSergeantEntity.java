package com.example.examplemod.unit.imperium;

import javax.annotation.Nullable;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.FirstCrusadeHurtByTargetGoal;
import com.example.examplemod.GeoVariantTextured;
import com.example.examplemod.LasgunAimingEntity;
import com.example.examplemod.LasgunCombatPose;
import com.example.examplemod.ai.combat.FCRangedAttackGoal;
import com.example.examplemod.ai.combat.FCTargetPriority;
import com.example.examplemod.ai.formation.FCLeaderGoal;
import com.example.examplemod.ai.formation.FCSquad;
import com.example.examplemod.ai.formation.FCSquadLeader;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
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
 * The Guardsman Sergeant — squad leader, laspistol and chainsword.
 *
 * <p>Mechanically this is the Rifleman's stack plus one thing: it implements {@link FCSquadLeader}
 * and runs {@link FCLeaderGoal}, so it gathers nearby Imperial units into an {@link FCSquad} and
 * they arrange themselves around it. Everything else — faction, targeting, muzzle-origin shots,
 * variants, death direction — is the same machinery, which is the point of having built it once.</p>
 *
 * <h2>Fights differently from a rifleman</h2>
 * <p>Its profile makes it a close-quarters leader rather than a line trooper: a laspistol is short
 * ranged and fires single shots, so it holds at seven blocks instead of twelve, and unlike a
 * rifleman it does <em>not</em> back away from melee — a sergeant with a chainsword wants the enemy
 * close. High courage means it holds when the line wavers, which matters because the squad's
 * cohesion is anchored on it.</p>
 */
public class GuardsmanSergeantEntity extends PathfinderMob
        implements RangedAttackMob, GeoEntity, FCUnit, FCSquadLeader, LasgunAimingEntity, GeoVariantTextured {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.guardsman_sergeant.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.guardsman_sergeant.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.guardsman_sergeant.run");
    private static final RawAnimation AIM = RawAnimation.begin().thenLoop("animation.guardsman_sergeant.aim");
    private static final RawAnimation DRAW = RawAnimation.begin()
            .thenPlay("animation.guardsman_sergeant.draw")
            .thenLoop("animation.guardsman_sergeant.aim");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("animation.guardsman_sergeant.shoot");
    private static final RawAnimation MELEE = RawAnimation.begin().thenPlay("animation.guardsman_sergeant.melee_attack");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.guardsman_sergeant.hurt");
    private static final RawAnimation ORDER = RawAnimation.begin().thenPlay("animation.guardsman_sergeant.order");
    private static final RawAnimation DEATH_FRONT = RawAnimation.begin().thenPlay("animation.guardsman_sergeant.death_front");
    private static final RawAnimation DEATH_BACK = RawAnimation.begin().thenPlay("animation.guardsman_sergeant.death_back");

    private static final EntityDataAccessor<Integer> COMBAT_POSE =
            SynchedEntityData.defineId(GuardsmanSergeantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMBAT_TICKS =
            SynchedEntityData.defineId(GuardsmanSergeantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(GuardsmanSergeantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DEATH_DIRECTION =
            SynchedEntityData.defineId(GuardsmanSergeantEntity.class, EntityDataSerializers.INT);

    private static final int[] VARIANT_OPTIONS = { 4, 2, 3, 4, 4, 3, 8 };

    /**
     * A close-quarters leader. Short effective range, single shots, and no retreat from melee —
     * the chainsword is not decoration.
     *
     * <p>{@code leads(8, 2.0)} is what makes {@link FCUnit#canLead()} true and sizes the squad.</p>
     */
    private static final FCCombatProfile PROFILE = FCCombatProfile.builder()
            .range(12.0F, 7.0F, 0.0F)
            .burst(2, 6, 26)
            .shootTiming(16, 8, 5)
            .reload(10, 32)
            .accuracy(0.86F)
            .mustHaltToFire()
            .speeds(1.1D, 1.0D)
            .courage(0.9F)
            .retreatHealthThreshold(0.2F)
            .usesCover(false)
            .backsAwayFromMelee(false)
            .melee(3.0F, 16)
            .heavyAttack(60, 0.25F)
            .perception(20, 26.0D, 120)
            .leads(8, 2.0D)
            .build();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    // NOT a field initializer: registerGoals() runs from the super constructor, BEFORE subclass
    // field initializers, so a `= new FCSquad(this)` here would still be null when FCLeaderGoal is
    // built — an NPE the first tick (Cannot invoke FCSquad.prune because this.squad is null).
    // Created lazily via getSquad() instead, which registerGoals() calls.
    private FCSquad squad;

    /** Ticks until this sergeant may bark another order, so it does not spam the animation. */
    private int orderCooldown;

    public GuardsmanSergeantEntity(EntityType<? extends GuardsmanSergeantEntity> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();

        if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanPassDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 7.0D)
                .add(Attributes.FOLLOW_RANGE, 36.0D);
    }

    // ------------------------------------------------------------------
    // FCUnit / FCSquadLeader
    // ------------------------------------------------------------------

    @Override
    public FirstCrusadeFaction getUnitFaction() {
        return FirstCrusadeFaction.IMPERIUM;
    }

    @Override
    public UnitRole getUnitRole() {
        return UnitRole.SQUAD_LEADER;
    }

    @Override
    public FCCombatProfile getCombatProfile() {
        return PROFILE;
    }

    @Override
    public String getUnitId() {
        return "guardsman_sergeant";
    }

    @Override
    public FCSquad getSquad() {
        if (this.squad == null) {
            this.squad = new FCSquad(this);
        }
        return this.squad;
    }

    // ------------------------------------------------------------------
    // Synced data
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(COMBAT_POSE, LasgunCombatPose.IDLE.ordinal());
        this.entityData.define(COMBAT_TICKS, 0);
        this.entityData.define(VARIANT, 0);
        this.entityData.define(DEATH_DIRECTION, 0);
    }

    @Override
    public LasgunCombatPose getLasgunCombatPose() {
        return LasgunCombatPose.fromId(this.entityData.get(COMBAT_POSE));
    }

    @Override
    public int getLasgunCombatTicks() {
        return this.entityData.get(COMBAT_TICKS);
    }

    @Override
    public void setLasgunCombatPose(LasgunCombatPose pose, int poseTicks) {
        LasgunCombatPose safePose = pose == null ? LasgunCombatPose.IDLE : pose;
        this.entityData.set(COMBAT_POSE, safePose.ordinal());
        this.entityData.set(COMBAT_TICKS, Math.max(0, poseTicks));
    }

    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, variant);
    }

    @Override
    public ResourceLocation getVariantTexture() {
        int markings = FCVariantSet.get(getVariant(), FCVariantSet.FIELD_MARKINGS);
        return new ResourceLocation(ExampleMod.MODID,
                "textures/entity/guardsman_sergeant_" + markings + ".png");
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

        if (tag.contains("FCVariant")) {
            setVariant(tag.getInt("FCVariant"));
        }

        if (tag.contains("FCDeathDirection")) {
            this.entityData.set(DEATH_DIRECTION, tag.getInt("FCDeathDirection"));
        }

        // The squad itself is deliberately not saved: it re-forms from FCLeaderGoal's next
        // recruitment sweep, which avoids holding stale entity references across a world load.
    }

    // ------------------------------------------------------------------
    // AI
    // ------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));

        // Squad bookkeeping sits at the top but claims no goal flags, so it runs alongside combat
        // rather than displacing it.
        this.goalSelector.addGoal(1, new FCLeaderGoal(this, getSquad(), PROFILE.maxFollowers()));

        this.goalSelector.addGoal(2, new FCRangedAttackGoal<>(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8D));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.orderCooldown > 0) {
            this.orderCooldown--;
        }

        if (!FCTargetPriority.shouldRescan(this, PROFILE.targetScanInterval())) {
            return;
        }

        LivingEntity current = this.getTarget();
        LivingEntity candidate = FCTargetPriority.selectTarget(this, PROFILE.targetScanRadius());

        if (!FCTargetPriority.shouldKeepTarget(this, current, candidate, PROFILE.targetScanRadius(), 20.0D)) {
            // Newly spotting an enemy is when a sergeant shouts and the squad reacts.
            if (candidate != null && current == null && this.orderCooldown <= 0) {
                triggerAnim("action", "order");
                this.orderCooldown = 100;
            }

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
    // Combat
    // ------------------------------------------------------------------

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide) {
            return;
        }

        FCProjectiles.fireLasBolt(
                this,
                target,
                FCWeaponMount.SERGEANT_LASPISTOL,
                2.2F,
                PROFILE.accuracy(),
                5.0D
        );

        FCProjectiles.spawnCasingEject(this, FCWeaponMount.SERGEANT_LASPISTOL);

        this.level().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLAZE_SHOOT,
                this.getSoundSource(),
                0.6F,
                1.9F + this.random.nextFloat() * 0.2F
        );

        triggerAnim("action", "shoot");
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            triggerAnim("action", "melee");
        }

        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);

        if (hurt && !this.level().isClientSide) {
            triggerAnim("action", "hurt");
        }

        return hurt;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide) {
            int direction = 0;

            if (damageSource.getEntity() != null) {
                double dx = damageSource.getEntity().getX() - this.getX();
                double dz = damageSource.getEntity().getZ() - this.getZ();
                double yaw = Math.toRadians(this.yBodyRot);

                direction = (dx * Math.sin(yaw) + dz * -Math.cos(yaw)) > 0.0D ? 1 : 0;
            }

            this.entityData.set(DEATH_DIRECTION, direction);

            // Release the squad so followers stop marching on a corpse.
            getSquad().getMembers().clear();
        }

        super.die(damageSource);
    }

    // ------------------------------------------------------------------
    // GeckoLib
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(
                        this.entityData.get(DEATH_DIRECTION) == 1 ? DEATH_BACK : DEATH_FRONT);
            }

            if (state.isMoving()) {
                return state.setAndContinue(this.isAggressive() ? RUN : WALK);
            }

            return switch (getLasgunCombatPose()) {
                case DRAWING -> state.setAndContinue(DRAW);
                case AIMING, SHOOTING, COOLDOWN -> state.setAndContinue(AIM);
                case IDLE -> state.setAndContinue(IDLE);
            };
        }));

        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("shoot", SHOOT)
                .triggerableAnim("melee", MELEE)
                .triggerableAnim("hurt", HURT)
                .triggerableAnim("order", ORDER));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
