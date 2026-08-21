package com.example.examplemod.fauna.entity;

import java.util.List;

import com.example.examplemod.animal.AlarmedPanicGoal;
import com.example.examplemod.animal.GrazeGoal;
import com.example.examplemod.animal.HerdGoal;
import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.goal.FaunaChargeGoal;
import com.example.examplemod.fauna.goal.FaunaTerritorialGoal;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * O Duskhorn: o herbivoro pesado de seis pernas das planicies alienigenas.
 *
 * <h2>A carga e uma linha reta, e isso e o desenho</h2>
 *
 * {@link #chargeDirection} e congelado no instante em que a corrida comeca e nao e recalculado. Uma
 * carga que persegue seria uma investida teleguiada, e tiraria do jogador a unica defesa que ele tem
 * contra duas toneladas em movimento: sair da frente. Assim, quem lê a preparacao — chifres abaixados,
 * pata raspando o chao, poeira — sobrevive.
 *
 * <h2>Trample: dano perto das patas, nao um segundo ataque</h2>
 *
 * {@link #TRAMPLE} atinge quem estiver colado ao corpo, num raio menor que o alcance de mordida. E o
 * que resolve o caso em que o jogador se encaixa embaixo do bicho, onde a mordida nao alcanca — sem
 * isso o Duskhorn tem um ponto cego que o jogador descobre em dois minutos e explora para sempre.
 */
public class DuskhornEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /** A carga. Preparacao de 20 ticks: um segundo inteiro de aviso. */
    private static final FaunaAbility CHARGE = new FaunaAbility("charge", 20, 40, 260);

    /** Chifrada direta. */
    private static final FaunaAbility GORE = new FaunaAbility("gore_attack", 6, 14, 60);

    /** Pisoteio: dano em quem esta colado nas patas. */
    private static final FaunaAbility TRAMPLE = new FaunaAbility("trample", 8, 12, 120);

    /** O aviso antes de tudo. */
    private static final FaunaAbility THREAT = new FaunaAbility("threat_display", 0, 36, 300);

    private static final FaunaAbility GRAZE = new FaunaAbility("graze", 0, 40, 200);

    /** Distancia em que o Duskhorn considera o jogador perto demais. */
    private static final double PERSONAL_SPACE = 8.0D;

    private static final double CHARGE_SPEED = 0.62D;
    private static final float CHARGE_DAMAGE = 14.0F;
    private static final double TRAMPLE_RADIUS = 2.2D;
    private static final float TRAMPLE_DAMAGE = 6.0F;

    /** Direcao congelada da carga. Runtime: uma carga interrompida por reload nao continua. */
    private Vec3 chargeDirection = Vec3.ZERO;

    public DuskhornEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /** Um muro com chifres: muita vida, muita resistencia a empurrao, lento. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 18.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaChargeGoal(this, CHARGE, 6.0D, 20.0D));
        this.goalSelector.addGoal(2, new FaunaTerritorialGoal(this, THREAT, PERSONAL_SPACE, 3.0D));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(4, new AlarmedPanicGoal(this, 1.4D));
        this.goalSelector.addGoal(5, new HerdGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new GrazeGoal(this, 500, 150));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    protected void awakeServerTick() {
        LivingEntity target = this.getTarget();
        if (target == null || !canUseAbility()) {
            return;
        }

        double distance = this.distanceTo(target);

        if (distance <= TRAMPLE_RADIUS && this.random.nextInt(30) == 0) {
            startAbility(TRAMPLE);
        } else if (distance <= 4.0D && this.random.nextInt(50) == 0) {
            startAbility(GORE);
        }
    }

    // ------------------------------------------------------------------ habilidades

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == CHARGE) {
            this.getNavigation().stop();
            this.playSound(FaunaSoundEvents.chargeRoar(), 1.6F, 0.7F);
        } else if (started == THREAT) {
            this.playSound(FaunaSoundEvents.threatDisplay(), 1.2F, 0.8F);
        }
    }

    @Override
    protected void onAbilityTick(FaunaAbility active, int tickInPhase) {
        if (active == CHARGE) {
            runCharge();
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == CHARGE) {
            beginCharge();
        } else if (active == GORE) {
            gore();
        } else if (active == TRAMPLE) {
            trample();
        }
    }

    /** Fim da preparacao: congela a direcao e liga a poeira. */
    private void beginCharge() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            cancelAbility();
            return;
        }

        Vec3 flat = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (flat.lengthSqr() < 1.0E-4D) {
            cancelAbility();
            return;
        }

        this.chargeDirection = flat.normalize();
        dustRing(1.4D, 8);
    }

    /**
     * Um tick de corrida.
     *
     * <p>Mexe em {@code setDeltaMovement} em vez de usar a navegacao de proposito: a navegacao
     * recalcularia a rota para o alvo, e a rota e exatamente o que esta carga NAO deve ter.
     */
    private void runCharge() {
        if (this.chargeDirection.lengthSqr() < 1.0E-4D) {
            return;
        }

        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(this.chargeDirection.x * CHARGE_SPEED, current.y,
                this.chargeDirection.z * CHARGE_SPEED);

        // Olhar na direcao da corrida: sem isto o modelo continua virado para o alvo enquanto o corpo
        // vai reto, e o bicho anda de lado.
        this.setYRot((float) (Math.atan2(this.chargeDirection.z, -this.chargeDirection.x)
                * (180.0D / Math.PI)) - 90.0F);
        this.yBodyRot = this.getYRot();

        hitEverythingInFront();
    }

    /**
     * O impacto da carga.
     *
     * <p>{@code getEntities} numa caixa colada ao corpo — pequena o suficiente para nao ser varredura.
     * Roda por tick durante os 40 ticks da carga, e so durante eles; a caixa tem o tamanho do bicho.
     */
    private void hitEverythingInFront() {
        List<LivingEntity> hit = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(0.4D), this::isAreaTarget);

        if (hit.isEmpty()) {
            return;
        }

        for (LivingEntity victim : hit) {
            victim.hurt(this.damageSources().mobAttack(this), CHARGE_DAMAGE);
            knockBack(victim, this, 2.4D, 0.5D);
        }

        tremor(0.5F, 10, 18.0D);
        dustRing(1.6D, 10);
        this.playSound(FaunaSoundEvents.largeBeastStep(), 1.4F, 0.5F);

        // Uma carga acerta uma vez. Continuar a correr atropelando o mesmo alvo a cada tick daria
        // 14 de dano vinte vezes, o que nao e uma carga, e uma serra.
        cancelAbility();
    }

    private void gore() {
        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > 5.0D) {
            return;
        }

        target.hurt(this.damageSources().mobAttack(this), 10.0F);
        knockBack(target, this, 1.0D, 0.42D);
        this.playSound(FaunaSoundEvents.threatDisplay(), 1.0F, 0.7F);
    }

    private void trample() {
        List<LivingEntity> hit = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(TRAMPLE_RADIUS), this::isAreaTarget);

        for (LivingEntity victim : hit) {
            victim.hurt(this.damageSources().mobAttack(this), TRAMPLE_DAMAGE);
        }

        dustRing(TRAMPLE_RADIUS, 8);
        tremor(0.2F, 6, 8.0D);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", "gore_attack");
        }

        return hit;
    }

    @Override
    public void ate() {
        super.ate();

        if (!this.level().isClientSide) {
            this.triggerAnim("ability", GRAZE.animation());
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
        this.playSound(FaunaSoundEvents.largeBeastStep(), 0.22F, 0.7F);
    }

    // ------------------------------------------------------------------ criacao

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                        AgeableMob partner) {
        return null;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(isAlarmed() || this.getTarget() != null ? RUN : WALK);
        }));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("charge", RawAnimation.begin().thenPlay("charge"))
                .triggerableAnim("gore_attack", RawAnimation.begin().thenPlay("gore_attack"))
                .triggerableAnim("trample", RawAnimation.begin().thenPlay("trample"))
                .triggerableAnim("threat_display", RawAnimation.begin().thenPlay("threat_display"))
                .triggerableAnim("graze", RawAnimation.begin().thenPlay("graze")));
    }
}
