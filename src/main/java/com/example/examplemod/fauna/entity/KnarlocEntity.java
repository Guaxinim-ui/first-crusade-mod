package com.example.examplemod.fauna.entity;

import com.example.examplemod.animal.HerdGoal;
import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.goal.FaunaLeapGoal;
import com.example.examplemod.fauna.goal.FaunaTerritorialGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * O Knarloc: o animal de guerra dos Kroot.
 *
 * <h2>Domesticado ou selvagem, e o que muda</h2>
 *
 * O mesmo bicho aparece de duas formas. O selvagem e territorial: avisa e ataca quem insiste. O que
 * nasce num {@code kroot_knarloc_pen} vem com {@link #isFromStructure()} — persistente, e mais calmo,
 * porque um acampamento onde os bichos atacam a primeira coisa que passa nao e um acampamento.
 *
 * <p>A "aliança com os Kroot" mencionada no briefing e essa marca e nada mais. O mod nao tem faccao
 * Kroot: nao existe entidade Kroot, nao existe {@code FirstCrusadeFaction.KROOT}. Inventar uma
 * afiliacao a uma faccao que nao existe daria um campo que nada le — e o mod ja tem a licao do
 * {@code usesCover(true)} que ninguem consulta. Quando houver Kroot, o gancho e este.
 */
public class KnarlocEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    private static final FaunaAbility LEAP = new FaunaAbility("leap_attack", 6, 16, 140);
    private static final FaunaAbility REAR_KICK = new FaunaAbility("rear_kick", 6, 12, 110);
    private static final FaunaAbility THREAT = new FaunaAbility("threat_display", 0, 30, 260);
    private static final FaunaAbility FEED = new FaunaAbility("feed", 0, 40, 300);

    private static final double TERRITORY_RADIUS = 10.0D;
    private static final double KICK_RANGE = 3.2D;

    public KnarlocEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /** Rapido para o tamanho, mordida forte, pouca armadura: nao e um tanque, e uma arma. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 45.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaLeapGoal(this, LEAP, 4.0D, 10.0D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new FaunaTerritorialGoal(this, THREAT, TERRITORY_RADIUS, 4.0D));
        this.goalSelector.addGoal(4, new HerdGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    /**
     * O Knarloc de acampamento nao avisa quem passa longe: o territorio dele e o cercado.
     *
     * <p>Feito reduzindo o alcance do aviso em vez de removendo a goal, porque a goal e registrada no
     * construtor e a origem so e conhecida depois — a estrutura marca o bicho apos cria-lo.
     */
    @Override
    protected boolean abilitiesAwake() {
        if (isFromStructure() && this.getTarget() == null) {
            return this.level().getNearestPlayer(this, TERRITORY_RADIUS * 0.5D) != null;
        }

        return super.abilitiesAwake();
    }

    @Override
    protected void awakeServerTick() {
        LivingEntity target = this.getTarget();
        if (target == null || !canUseAbility()) {
            return;
        }

        if (this.distanceTo(target) <= KICK_RANGE && isBehind(target)
                && this.random.nextInt(35) == 0) {
            startAbility(REAR_KICK);
        }
    }

    private boolean isBehind(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();

        double facingX = -Math.sin(Math.toRadians(this.getYRot()));
        double facingZ = Math.cos(Math.toRadians(this.getYRot()));

        return dx * facingX + dz * facingZ < 0.0D;
    }

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == THREAT) {
            this.playSound(FaunaSoundEvents.threatDisplay(), 1.2F, 1.2F);
        } else if (started == LEAP) {
            this.getNavigation().stop();
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == LEAP) {
            leap();
        } else if (active == REAR_KICK) {
            kick();
        }
    }

    private void leap() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return;
        }

        this.setDeltaMovement(dx / length * 0.8D, 0.5D, dz / length * 0.8D);
        this.playSound(FaunaSoundEvents.leapOff(), 0.9F, 1.1F);

        if (this.level() instanceof ServerLevel server) {
            com.example.examplemod.fauna.effect.FaunaVisualEffects.dustRing(server, this, 0.8D, 5);
        }
    }

    private void kick() {
        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > KICK_RANGE + 1.0D) {
            return;
        }

        target.hurt(this.damageSources().mobAttack(this), 8.0F);
        knockBack(target, this, 1.5D, 0.42D);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", "attack_bite");
        }

        return hit;
    }

    @Override
    public void ate() {
        super.ate();

        if (!this.level().isClientSide) {
            this.triggerAnim("ability", FEED.animation());
            this.heal(2.0F);
        }
    }

    // ------------------------------------------------------------------ som

    @Override
    protected SoundEvent getAmbientSound() {
        return FaunaSoundEvents.largeBeastAmbient();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FaunaSoundEvents.largeBeastHurt();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FaunaSoundEvents.largeBeastDeath();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(FaunaSoundEvents.largeBeastStep(), 0.16F, 1.1F);
    }

    // ------------------------------------------------------------------ criacao

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    /** Bicho de acampamento anda na coleira: e assim que os Kroot o levam. */
    @Override
    public boolean canBeLeashed(Player player) {
        return true;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(this.getTarget() != null ? RUN : WALK);
        }));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("leap_attack", RawAnimation.begin().thenPlay("leap_attack"))
                .triggerableAnim("rear_kick", RawAnimation.begin().thenPlay("rear_kick"))
                .triggerableAnim("threat_display", RawAnimation.begin().thenPlay("threat_display"))
                .triggerableAnim("attack_bite", RawAnimation.begin().thenPlay("attack_bite"))
                .triggerableAnim("feed", RawAnimation.begin().thenPlay("feed")));
    }
}
