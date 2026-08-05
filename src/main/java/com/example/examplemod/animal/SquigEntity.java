package com.example.examplemod.animal;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Squig: a ball of teeth that came with the Orks.
 *
 * <h2>Hostile, and the only Phase E animal that is</h2>
 *
 * A squig is fungus and appetite. It bites anything that is not a greenskin, which makes it the one
 * piece of Ork territory that fights back on its own — walk into ash waste and the ground has an
 * opinion about you.
 *
 * <p>It is still an {@link FCAnimalEntity} and not a {@code Monster}, and that is a decision about
 * <b>population</b> rather than about temperament: the wildlife base is what carries the spawn cap,
 * and a hostile thing that breeds off the scenery with no ceiling is precisely the failure that cost
 * this mod a planet once already. A squig is dangerous; it is not allowed to be unlimited.
 *
 * <h2>It hops</h2>
 *
 * No walk cycle — it has no legs worth the name. The movement animation is a hop, and the entity
 * jumps as it moves so the two agree.
 */
public class SquigEntity extends FCAnimalEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation HOP = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    /** Ticks between hops while moving. Any faster and it reads as a bouncing ball. */
    private static final int HOP_INTERVAL = 14;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SquigEntity(EntityType<? extends SquigEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Bites players, not the greenskins that farm it. The mod's Ork units are Monsters, so
        // "anything living that is not a Monster and not wildlife" is the honest description.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, LivingEntity.class, 10, true, false, SquigEntity::isPrey));
    }

    private static boolean isPrey(LivingEntity target) {
        return !(target instanceof net.minecraft.world.entity.monster.Monster)
                && !(target instanceof FCAnimalEntity);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        // The hop: a small upward shove whenever it is moving on the ground. Cheaper than a jump
        // control and it lines up with the animation, which is the only thing that matters here.
        if (!this.level().isClientSide && this.onGround()
                && this.tickCount % HOP_INTERVAL == 0
                && this.getDeltaMovement().horizontalDistanceSqr() > 0.001D) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.32D, 0.0D));
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("attack", "attack");
        }

        return hit;
    }

    /** Squigs are grown by Orks from spores, not bred. Nothing in the mod grows them. */
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SLIME_SQUISH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 3, state ->
                state.isMoving() ? state.setAndContinue(HOP) : state.setAndContinue(IDLE)));

        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /** The same filter as the target goal, so retaliation cannot make it bite an Ork either. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return isPrey(target) && super.canAttack(target);
    }

    /**
     * A squig does not panic.
     *
     * <p>The alarm in {@link FCAnimalEntity} exists for prey — animals that scatter when a shell
     * lands. A squig that ran away would stop being the thing that makes Ork ground unpleasant to
     * walk across, which is its entire reason to exist. Overriding it here, rather than adding a
     * flag to the base, keeps the exception where the exception is.
     */
    @Override
    public void alarm() {
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(SoundEvents.SLIME_SQUISH_SMALL, 0.12F, 1.0F);
    }
}
