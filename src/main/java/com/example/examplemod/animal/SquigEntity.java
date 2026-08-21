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
public class SquigEntity extends com.example.examplemod.fauna.FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation HOP = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /** O salto sobre o alvo — a habilidade que o modelo aprovado do dono trouxe. */
    private static final com.example.examplemod.fauna.FaunaAbility LEAP =
            new com.example.examplemod.fauna.FaunaAbility("leap_attack", 4, 14, 80);

    /** O berro. Sem efeito mecanico: e o que faz um bando de squigs soar como um bando. */
    private static final com.example.examplemod.fauna.FaunaAbility ROAR =
            new com.example.examplemod.fauna.FaunaAbility("roar", 0, 20, 300);

    /** Ticks between hops while moving. Any faster and it reads as a bouncing ball. */
    private static final int HOP_INTERVAL = 14;

    public SquigEntity(EntityType<? extends net.minecraft.world.entity.animal.Animal> type,
                       Level level) {
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
        // O salto acima da mordida: um squig que pula quando ha espaco e morde quando nao ha e a
        // leitura correta do bicho, e ela sai de graca da ordem das goals.
        this.goalSelector.addGoal(1, new com.example.examplemod.fauna.goal.FaunaLeapGoal(
                this, LEAP, 3.0D, 7.0D, 2.0D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

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

    // ------------------------------------------------------------------ habilidades

    @Override
    protected void awakeServerTick() {
        // O berro sai quando ha alguem por perto e nada acontecendo. Cooldown de 15 s no proprio
        // FaunaAbility, entao um bando nao vira uma sirene.
        if (this.getTarget() == null && canUseAbility() && this.random.nextInt(180) == 0) {
            startAbility(ROAR);
        }
    }

    @Override
    protected void onAbilityStart(com.example.examplemod.fauna.FaunaAbility started) {
        if (started == ROAR) {
            this.playSound(net.minecraft.sounds.SoundEvents.HOGLIN_ANGRY, 1.0F, 1.6F);
        }
    }

    @Override
    protected void onAbilityStrike(com.example.examplemod.fauna.FaunaAbility active) {
        if (active != LEAP) {
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        // Mira o torso, nao os pes: um salto rasteiro passa por baixo do alvo e o squig aterrissa
        // atras dele sem tocar em nada.
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return;
        }

        this.setDeltaMovement(dx / length * 0.62D, 0.46D, dz / length * 0.62D);
        this.playSound(net.minecraft.sounds.SoundEvents.SLIME_JUMP, 0.8F, 1.4F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", "attack");
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
        controllers.add(new AnimationController<>(this, "movement", 3, state -> {
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(this.getTarget() != null ? RUN : HOP);
        }));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"))
                .triggerableAnim("leap_attack", RawAnimation.begin().thenPlay("leap_attack"))
                .triggerableAnim("roar", RawAnimation.begin().thenPlay("roar"))
                .triggerableAnim("eat", RawAnimation.begin().thenPlay("eat")));
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
