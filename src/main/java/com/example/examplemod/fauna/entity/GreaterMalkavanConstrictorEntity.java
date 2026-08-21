package com.example.examplemod.fauna.entity;

import javax.annotation.Nullable;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.goal.FaunaConstrictGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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
 * A Greater Malkavan Constrictor: a serpente das selvas e pantanos.
 *
 * <h2>O modelo nao tem `walk`, e isso mudou o controlador</h2>
 *
 * As animacoes do dono para esta especie sao {@code idle, slither, strike, threat_display, constrict,
 * coil, swallow} — nao existe {@code walk} nem {@code run}. Uma cobra nao anda, ela desliza. Entao o
 * controlador de movimento usa {@code slither}, e {@code coil} e a pose de descanso em vez de
 * {@code idle} quando ela esta parada e sem alvo. Isto e o exemplo de por que o nome da animacao
 * pertence a especie e nao a um enum global de habilidades.
 *
 * <h2>A constricao tem tres saidas, e todas funcionam</h2>
 *
 * Ver {@link FaunaConstrictGoal}. Aqui esta a terceira, que e a que importa mais: {@link #hurt} quebra
 * a constricao. Bater na cobra solta o alvo — isso da ao jogador preso <b>e</b> aos companheiros dele
 * uma acao que resolve a situacao, o que e a diferenca entre uma habilidade dificil e uma injusta.
 */
public class GreaterMalkavanConstrictorEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SLITHER = RawAnimation.begin().thenLoop("slither");
    private static final RawAnimation COIL = RawAnimation.begin().thenLoop("coil");

    /** A investida da cabeca. Rapida: e o ataque normal dela. */
    private static final FaunaAbility STRIKE = new FaunaAbility("strike", 4, 10, 40);

    /** A constricao. A fase ativa e o tempo maximo que o alvo fica preso. */
    private static final FaunaAbility CONSTRICT = new FaunaAbility("constrict", 6, 100, 300);

    /** Cabeca erguida, aviso. */
    private static final FaunaAbility THREAT = new FaunaAbility("threat_display", 0, 30, 240);

    private static final double CONSTRICT_REACH = 2.5D;

    /** Dano por pulso de aperto, e o intervalo entre pulsos em ticks. */
    private static final float SQUEEZE_DAMAGE = 3.0F;
    private static final int SQUEEZE_INTERVAL = 20;

    /** Quem esta preso agora. Runtime: uma constricao nao sobrevive ao reload. */
    @Nullable
    private LivingEntity constricted;

    public GreaterMalkavanConstrictorEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /** Vida media, dano medio, sem armadura. O perigo dela e prender, nao aguentar. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaConstrictGoal(this, CONSTRICT, CONSTRICT_REACH));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void awakeServerTick() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        if (canUseAbility() && this.distanceTo(target) <= 4.0D && this.random.nextInt(30) == 0) {
            startAbility(STRIKE);
        }
    }

    // ------------------------------------------------------------------ constricao

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == THREAT) {
            this.playSound(FaunaSoundEvents.serpentStrike(), 1.0F, 0.7F);
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == STRIKE) {
            strike();
        } else if (active == CONSTRICT) {
            grab();
        }
    }

    private void strike() {
        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > 5.0D) {
            return;
        }

        target.hurt(this.damageSources().mobAttack(this), 7.0F);
        this.playSound(FaunaSoundEvents.serpentStrike(), 0.9F, 1.3F);
    }

    private void grab() {
        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > CONSTRICT_REACH + 1.0D
                || !FaunaConstrictGoal.canConstrict(target)) {
            cancelAbility();
            return;
        }

        this.constricted = target;
        this.getNavigation().stop();
    }

    /**
     * Um tick de aperto.
     *
     * <p>Lentidao e fraqueza reaplicadas em vez de um efeito longo: se a constricao quebrar, o alvo
     * fica livre em segundos e nao com um minuto de lentidao herdado de uma situacao que acabou.
     */
    @Override
    protected void onAbilityTick(FaunaAbility active, int tickInPhase) {
        if (active != CONSTRICT) {
            return;
        }

        LivingEntity victim = this.constricted;
        if (victim == null || !victim.isAlive() || this.distanceTo(victim) > CONSTRICT_REACH + 2.0D) {
            cancelAbility();
            return;
        }

        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, true));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0, false, true));

        if (tickInPhase % SQUEEZE_INTERVAL == 0) {
            victim.hurt(this.damageSources().mobAttack(this), SQUEEZE_DAMAGE);
        }
    }

    @Override
    protected void onAbilityEnd(FaunaAbility finished) {
        if (finished == CONSTRICT) {
            this.constricted = null;
        }
    }

    /** True enquanto ela esta apertando alguem — lido pela animacao e pelo debug. */
    public boolean isConstricting() {
        return this.constricted != null;
    }

    /**
     * Apanhar quebra a constricao.
     *
     * <p>A saida que o briefing pede em letras claras. E ela e checada antes de {@code super.hurt}
     * porque o golpe pode matar a cobra, e nesse caso o {@code onAbilityEnd} nao seria alcancado.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && this.constricted != null && amount > 0.0F) {
            cancelAbility();
        }

        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", "strike");
        }

        return hit;
    }

    // ------------------------------------------------------------------ som

    @Override
    protected SoundEvent getAmbientSound() {
        return FaunaSoundEvents.serpentAmbient();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FaunaSoundEvents.serpentHurt();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FaunaSoundEvents.serpentDeath();
    }

    /** Cobra nao pisa. Deslizar e silencioso, e som de passo aqui seria mentira. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
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
            if (state.isMoving()) {
                return state.setAndContinue(SLITHER);
            }

            // Enrolada quando nao ha nada a fazer; alerta quando ha alvo. As duas poses de parado
            // dizem coisas diferentes, e e de graca: o modelo tem as duas.
            return state.setAndContinue(this.getTarget() == null ? COIL : IDLE);
        }));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("strike", RawAnimation.begin().thenPlay("strike"))
                .triggerableAnim("constrict", RawAnimation.begin().thenPlay("constrict"))
                .triggerableAnim("threat_display", RawAnimation.begin().thenPlay("threat_display"))
                .triggerableAnim("swallow", RawAnimation.begin().thenPlay("swallow")));
    }
}
