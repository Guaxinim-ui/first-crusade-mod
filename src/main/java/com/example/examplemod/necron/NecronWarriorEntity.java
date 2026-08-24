package com.example.examplemod.necron;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The rank and file of a waking tomb.
 *
 * <h2>What makes it a Necron and not another skeleton</h2>
 *
 * Reanimation. A Warrior that drops gets back up unless something finishes it, which is the one
 * mechanic every piece of Necron fiction leads with, and it is also what makes a phalanx of them
 * feel different from the same number of Orks: killing them is not progress unless you keep killing
 * them. It is capped per body so a fight cannot become unwinnable.
 *
 * <p>Hostile to everyone. {@link com.example.examplemod.FirstCrusadeFactionManager} sorts any
 * {@link Monster} it does not recognise into {@code HOSTILE}, which is exactly right here and is why
 * this class adds nothing to that table: the Necrons are not the Imperium's enemy, they are
 * everyone's, and the Orks find that out on their own.
 */
public class NecronWarriorEntity extends Monster implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    /** How many times one body may reassemble itself. */
    private static final int MAX_REANIMATIONS = 2;

    /** Chance a given death is refused. Not certainty — a protocol that always works is a wall. */
    private static final float REANIMATION_CHANCE = 0.5F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int reanimations;

    public NecronWarriorEntity(EntityType<? extends NecronWarriorEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                // Tough rather than fast, and deliberately slower than a Guardsman: the threat is
                // that they do not stop, so being able to walk away from one has to stay true.
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));

        // Everything alive that is not another Necron. The faction manager already answers this,
        // so the goal asks it rather than listing types — a new mod unit becomes a target the day
        // it is added, without anyone remembering to come back here.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class,
                10, true, false, NecronTargets::isEnemyOfTheTomb));
    }

    /**
     * Reanimation protocols: refuse the death, stand back up.
     *
     * <p>Done in {@code die} rather than by cancelling the damage, so the kill still registers for
     * anything counting kills — an operation that needs three Necrons dead is not cheated by a body
     * that got up twice.
     */
    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide
                && this.reanimations < MAX_REANIMATIONS
                && this.random.nextFloat() < REANIMATION_CHANCE) {
            this.reanimations++;

            this.setHealth(this.getMaxHealth() * 0.5F);
            this.deathTime = 0;
            this.hurtTime = 0;

            this.level().broadcastEntityEvent(this, (byte) 35);
            this.playSound(net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8F, 0.6F);
            return;
        }

        super.die(cause);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Reanimations", this.reanimations);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.reanimations = tag.getInt("Reanimations");
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit) {
            triggerAnim("attack", "attack");
        }

        return hit;
    }

    /** Necrodermis does not burn and does not drown. */
    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state ->
                state.isMoving() ? state.setAndContinue(WALK) : state.setAndContinue(IDLE)));

        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * The Overlord's protocols: this body may reassemble itself again.
     *
     * <p>Reset rather than heal — see {@link NecronOverlordEntity#aiStep()} for why.
     */
    void restoreReanimations() {
        this.reanimations = 0;
    }
}
