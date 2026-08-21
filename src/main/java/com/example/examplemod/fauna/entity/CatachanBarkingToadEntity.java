package com.example.examplemod.fauna.entity;

import java.util.List;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.effect.FaunaVisualEffects;
import com.example.examplemod.fauna.goal.FaunaToxicBurstGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * O Catachan Barking Toad: nao caca ninguem, e mata quem chega perto.
 *
 * <h2>Ele nao persegue, e por isso e assustador</h2>
 *
 * Nao existe {@code NearestAttackableTargetGoal} nesta classe. O sapo fica onde esta. O que ele tem e
 * {@link FaunaToxicBurstGoal}: quem entra em seis blocos ouve a garganta inflar e tem alguns segundos
 * para sair. Isso inverte a relacao — o perigo nao vem atras do jogador, o jogador anda para dentro
 * dele. E a razao de a criatura ser memoravel com uma unica habilidade.
 *
 * <h2>Depois da descarga ele fica debilitado, e nao morre</h2>
 *
 * O briefing deixou as duas opcoes abertas. Escolhida a debilitacao: matar o sapo na propria
 * habilidade transformaria o encontro numa troca ("deixo ele explodir e pego o loot"), o que e
 * exatamente o oposto de uma criatura que se deve evitar. Debilitado, ele sobrevive, o jogador
 * envenenado tem de decidir se vale a pena voltar, e a criatura continua la depois.
 *
 * <h2>Nenhum bloco e destruido</h2>
 *
 * A nuvem e particula e efeito de status. O briefing proibe explosao que destrua terreno, e alem da
 * regra ha o motivo: um sapo raro que abre cratera deixa cicatriz permanente num bioma que ninguem
 * vai revisitar para ver.
 */
public class CatachanBarkingToadEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation HOP = RawAnimation.begin().thenLoop("hop");

    /**
     * A carga. Cooldown ZERO de proposito.
     *
     * <p>{@link FaunaToxicBurstGoal} encadeia carga -> descarga, e {@link #canUseAbility()} exige
     * cooldown terminado. Um cooldown aqui faria o sapo carregar e nunca descarregar — o defeito seria
     * silencioso, porque a animacao de carga tocaria perfeitamente.
     */
    private static final FaunaAbility TOXIC_CHARGE =
            new FaunaAbility("toxic_burst_charge", 0, 50, 0);

    /** A descarga. O cooldown real da habilidade esta aqui: dois minutos. */
    private static final FaunaAbility TOXIC_BURST = new FaunaAbility("toxic_burst", 0, 20, 2400);

    /** Exibicao de ameaca, antes mesmo da carga. */
    private static final FaunaAbility THREAT = new FaunaAbility("threat_display", 0, 24, 200);

    /** Distancia em que a carga comeca. */
    private static final double TRIGGER_DISTANCE = 6.0D;

    /** Distancia em que ele desiste. Maior que a de gatilho, senao pisca na fronteira. */
    private static final double ABORT_DISTANCE = 10.0D;

    /** Raio da nuvem, em blocos. */
    private static final double CLOUD_RADIUS = 6.0D;

    /** Ticks de debilitacao depois da descarga. Um minuto. */
    private static final int EXHAUSTION_TICKS = 1200;

    private int exhaustedUntil;

    public CatachanBarkingToadEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /**
     * Pouca vida, quase nenhum ataque fisico.
     *
     * <p>Todo o perigo esta na habilidade. Um sapo que tambem morde forte teria duas ameacas e nenhuma
     * identidade; assim, quem consegue chegar nele o mata facil — e chegar nele e o problema.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.18D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 12.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaToxicBurstGoal(this, TOXIC_CHARGE, TOXIC_BURST,
                TRIGGER_DISTANCE, ABORT_DISTANCE));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        // Revida, e nada mais. Sem caca: o sapo nunca sai atras de ninguem.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected void awakeServerTick() {
        if (isExhausted() || this.getTarget() != null || !canUseAbility()) {
            return;
        }

        if (this.random.nextInt(150) == 0 && nearestWatcher() != null) {
            startAbility(THREAT);
        }
    }

    // ------------------------------------------------------------------ toxina

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == TOXIC_CHARGE) {
            this.getNavigation().stop();
            this.playSound(FaunaSoundEvents.toxinCharge(), 1.4F, 0.6F);
        } else if (started == THREAT) {
            this.playSound(FaunaSoundEvents.toadAmbient(), 1.2F, 0.6F);
        }
    }

    /** A carga: particula verde crescendo, som repetido. Um por segundo, nunca por tick. */
    @Override
    protected void onAbilityTick(FaunaAbility active, int tickInPhase) {
        if (active != TOXIC_CHARGE || !(this.level() instanceof ServerLevel server)) {
            return;
        }

        if (tickInPhase % 8 == 0) {
            // O raio da nuvem de aviso cresce com a carga: a pista visual diz quanto tempo resta.
            double growth = 0.3D + (tickInPhase / (double) TOXIC_CHARGE.active()) * 0.9D;
            FaunaVisualEffects.toxinCharge(server, this, growth);
        }

        if (tickInPhase % 20 == 0) {
            this.playSound(FaunaSoundEvents.toxinCharge(), 1.0F, 0.5F + tickInPhase / 100.0F);
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == TOXIC_BURST) {
            burst();
        }
    }

    private void burst() {
        this.playSound(FaunaSoundEvents.toxinBurst(), 1.8F, 1.1F);

        if (this.level() instanceof ServerLevel server) {
            FaunaVisualEffects.toxicCloud(server, this, CLOUD_RADIUS);
        }

        List<LivingEntity> caught = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(CLOUD_RADIUS), this::isAreaTarget);

        for (LivingEntity victim : caught) {
            // Dano escalado pela distancia: quem estava na borda da nuvem quando ela saiu escapa
            // com pouco. Sem isso o aviso de recuar nao teria recompensa parcial.
            double distance = this.distanceTo(victim);
            float falloff = (float) Math.max(0.2D, 1.0D - distance / CLOUD_RADIUS);

            victim.hurt(this.damageSources().mobAttack(this), 6.0F * falloff);
            victim.addEffect(new MobEffectInstance(MobEffects.POISON,
                    (int) (200 * falloff), 1, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                    (int) (140 * falloff), 0, false, true));
        }

        this.exhaustedUntil = this.tickCount + EXHAUSTION_TICKS;
    }

    /** True enquanto ele esta debilitado depois de uma descarga. */
    public boolean isExhausted() {
        return this.tickCount < this.exhaustedUntil;
    }

    /**
     * Debilitado ele nao regenera e apanha mais.
     *
     * <p>E o custo da habilidade, e o que da ao jogador uma janela para mata-lo se quiser o loot.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, isExhausted() ? amount * 1.5F : amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", "attack_bite");
        }

        return hit;
    }

    // ------------------------------------------------------------------ som

    @Override
    protected SoundEvent getAmbientSound() {
        return FaunaSoundEvents.toadAmbient();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FaunaSoundEvents.toadHurt();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FaunaSoundEvents.toadDeath();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
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

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Guarda o que RESTA da debilitacao, nao o instante de fim: tickCount volta ao valor salvo da
        // entidade, mas comparar um instante absoluto contra ele depois de um reload daria numero sem
        // relacao com o tempo decorrido.
        tag.putInt("ExhaustedTicks", Math.max(0, this.exhaustedUntil - this.tickCount));
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.exhaustedUntil = this.tickCount + tag.getInt("ExhaustedTicks");
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            // Sapo debilitado anda; sapo inteiro pula. A diferenca e visivel de longe e diz o estado
            // dele sem barra de vida nenhuma.
            return state.setAndContinue(isExhausted() ? WALK : HOP);
        }));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("toxic_burst_charge", RawAnimation.begin().thenPlay("toxic_burst_charge"))
                .triggerableAnim("toxic_burst", RawAnimation.begin().thenPlay("toxic_burst"))
                .triggerableAnim("threat_display", RawAnimation.begin().thenPlay("threat_display"))
                .triggerableAnim("attack_bite", RawAnimation.begin().thenPlay("attack_bite")));
    }
}
