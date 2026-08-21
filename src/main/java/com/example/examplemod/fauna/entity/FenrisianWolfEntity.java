package com.example.examplemod.fauna.entity;

import java.util.List;

import com.example.examplemod.animal.AlarmedPanicGoal;
import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.goal.FaunaLeapGoal;
import com.example.examplemod.fauna.goal.FaunaPackGoal;

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
 * O Fenrisian Wolf: predador de matilha das regioes frias.
 *
 * <h2>A matilha e um efeito, nao um sistema</h2>
 *
 * Nao existe objeto "matilha" aqui. O que existe e {@link FaunaPackGoal}, que propaga o alvo para os
 * vizinhos, e {@link #HOWL}, que da um bonus temporario a quem esta em volta de quem uivou. Cinco
 * lobos convergindo no mesmo alvo com a mesma velocidade parecem coordenados, e o custo total e uma
 * consulta a cada tres segundos no lobo que tem alvo. Coordenacao de verdade — flanquear, cercar —
 * exigiria estado partilhado e um pathfinder por decisao, e o briefing pede explicitamente para nao
 * fazer isso.
 *
 * <h2>O uivo</h2>
 *
 * Buff pequeno de dano e velocidade, aplicado por atributo temporario e nao por {@code MobEffect}. A
 * razao e visual: efeito de status desenha particulas em volta do mob, e um lobo com nuvem de
 * particulas roxas nao le como lobo reforcado, le como lobo envenenado.
 */
public class FenrisianWolfEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /** Salto curto sobre o alvo. */
    private static final FaunaAbility POUNCE = new FaunaAbility("pounce", 4, 12, 90);

    /** O uivo da matilha. */
    private static final FaunaAbility HOWL = new FaunaAbility("howl", 0, 40, 600);

    /** Alcance do bonus do uivo, em blocos. */
    private static final double HOWL_RADIUS = 16.0D;

    /** Duracao do bonus do uivo, em ticks. Trinta segundos. */
    private static final int HOWL_BOOST_TICKS = 600;

    /** Velocidade de corrida com o bonus do uivo. */
    private static final double HOWL_SPEED = 0.36D;
    private static final double BASE_SPEED = 0.32D;

    private int boostedUntil;

    public FenrisianWolfEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /**
     * Rapido, mordida forte, pouca vida.
     *
     * <p>A leitura pretendida: um lobo sozinho perde para um Guardsman, tres ganham. O que faz a
     * matilha perigosa e o numero, e para isso o individuo tem de ser fragil — senao a matilha seria
     * apenas cinco vezes intransponivel.
     *
     * <p>{@code FOLLOW_RANGE} em 24 nao e economia de CPU por acaso: e a regra de "nao perseguir por
     * centenas de blocos" escrita onde ela funciona. {@code NearestAttackableTargetGoal} infla a
     * caixa de busca pelo FOLLOW_RANGE, entao este numero e ao mesmo tempo comportamento e custo.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaLeapGoal(this, POUNCE, 3.0D, 8.0D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new FaunaPackGoal(this, HOWL_RADIUS));
        this.goalSelector.addGoal(4, new AlarmedPanicGoal(this, 1.5D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));

        // Caca o que e menor que ela. Sem lista de especies: qualquer Animal com menos de um bloco e
        // meio de largura e presa, o que inclui a fauna futura sem precisar voltar aqui.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class,
                10, true, false, prey -> prey.getBbWidth() < 1.5F
                        && !(prey instanceof FenrisianWolfEntity)));
    }

    // ------------------------------------------------------------------ uivo

    @Override
    protected void awakeServerTick() {
        // O uivo sai quando ha caca em curso e a matilha tem mais de um membro. Uivar sozinho num
        // campo vazio seria som sem funcao, e o briefing pede som com cooldown, nao som constante.
        if (this.getTarget() != null && canUseAbility() && this.random.nextInt(120) == 0
                && packSize() > 1) {
            startAbility(HOWL);
        }
    }

    private int packSize() {
        List<FenrisianWolfEntity> pack = this.level().getEntitiesOfClass(FenrisianWolfEntity.class,
                this.getBoundingBox().inflate(HOWL_RADIUS), other -> other.isAlive());
        return pack.size();
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == HOWL) {
            howl();
            return;
        }

        if (active == POUNCE) {
            leapAtTarget();
        }
    }

    private void howl() {
        this.playSound(FaunaSoundEvents.wolfHowl(), 1.6F, 0.9F + this.random.nextFloat() * 0.2F);

        List<FenrisianWolfEntity> pack = this.level().getEntitiesOfClass(FenrisianWolfEntity.class,
                this.getBoundingBox().inflate(HOWL_RADIUS), other -> other.isAlive());

        for (FenrisianWolfEntity member : pack) {
            member.receiveHowl();
        }
    }

    /** Recebe o bonus do uivo de um vizinho. */
    private void receiveHowl() {
        this.boostedUntil = this.tickCount + HOWL_BOOST_TICKS;
        setSpeed(HOWL_SPEED);
    }

    private void setSpeed(double speed) {
        if (this.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        }
    }

    /** True enquanto o bonus do uivo dura — lido pelo dano e pela animacao. */
    public boolean isHowlBoosted() {
        return this.tickCount < this.boostedUntil;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.boostedUntil > 0 && this.tickCount >= this.boostedUntil) {
            this.boostedUntil = 0;
            setSpeed(BASE_SPEED);
        }
    }

    private void leapAtTarget() {
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

        this.setDeltaMovement(dx / length * 0.7D, 0.42D, dz / length * 0.7D);
        this.playSound(FaunaSoundEvents.leapOff(), 0.8F, 1.4F);
    }

    /** O bonus do uivo entra no dano aqui, e nao como atributo: um lugar so, e visivel. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            if (isHowlBoosted()) {
                target.hurt(this.damageSources().mobAttack(this), 2.0F);
            }
            this.triggerAnim("ability", "attack_bite");
        }

        return hit;
    }

    // ------------------------------------------------------------------ som

    @Override
    protected SoundEvent getAmbientSound() {
        return FaunaSoundEvents.wolfAmbient();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FaunaSoundEvents.wolfHurt();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FaunaSoundEvents.wolfDeath();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(net.minecraft.sounds.SoundEvents.WOLF_STEP, 0.12F, 1.0F);
    }

    // ------------------------------------------------------------------ criacao

    @Override
    public boolean isFood(ItemStack stack) {
        return false;                   // nao se domestica um lobo de Fenris com nada que exista aqui
    }

    @Override
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                        AgeableMob partner) {
        return null;                    // a matilha nasce da worldgen, nao de um par no pasto
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(this.getTarget() != null || isHowlBoosted() ? RUN : WALK);
        }));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("pounce", RawAnimation.begin().thenPlay("pounce"))
                .triggerableAnim("howl", RawAnimation.begin().thenPlay("howl"))
                .triggerableAnim("attack_bite", RawAnimation.begin().thenPlay("attack_bite"))
                .triggerableAnim("attack_claw", RawAnimation.begin().thenPlay("attack_claw"))
                .triggerableAnim("threat_display", RawAnimation.begin().thenPlay("threat_display")));
    }
}
