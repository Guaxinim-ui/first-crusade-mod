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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
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
 * Cyber-mastiff: the Arbites' dog, more implant than animal.
 *
 * <h2>The one animal that picks a side</h2>
 *
 * Every other species in Phase E is scenery that runs away from the war. This one was bred into it:
 * a mastiff with a cortical implant, kept by Imperial law enforcement, and the only wildlife in the
 * mod that <b>hunts</b>. It attacks monsters on sight — which on these planets means greenskins —
 * and leaves everything else alone.
 *
 * <p>It is deliberately <i>not</i> tameable. The mod has no pet system, and half a pet system is
 * worse than none: a player who can lead one home and then cannot feed it, heal it or command it has
 * been given a bug, not a companion. What it is instead is a fact about Imperial ground — where
 * mastiffs run, the Arbites have been.
 */
public class CyberMastiffEntity extends com.example.examplemod.fauna.FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /**
     * A varredura do auspex. Sem dano, sem alvo novo: e a pose que diz "voce foi visto".
     *
     * <p>Nada de ray tracing — o briefing pede isso e ele esta certo. O olho acende, sai um som
     * eletronico, e o cao ja tinha o alvo pela goal normal. Um scan que de fato descobrisse alvos
     * seria uma segunda busca por entidade rodando em cima da que ja existe.
     */
    private static final com.example.examplemod.fauna.FaunaAbility AUSPEX_SCAN =
            new com.example.examplemod.fauna.FaunaAbility("auspex_scan", 0, 30, 200);

    /** O bote. */
    private static final com.example.examplemod.fauna.FaunaAbility POUNCE =
            new com.example.examplemod.fauna.FaunaAbility("pounce_attack", 4, 12, 100);

    /** Encarar e rosnar antes de correr. */
    private static final com.example.examplemod.fauna.FaunaAbility THREAT =
            new com.example.examplemod.fauna.FaunaAbility("threat_display", 0, 24, 200);

    /** Chance de a mordida travar a presa, e por quantos ticks. */
    private static final float LOCK_CHANCE = 0.35F;
    private static final int LOCK_TICKS = 60;

    public CyberMastiffEntity(EntityType<? extends net.minecraft.world.entity.animal.Animal> type,
                              Level level) {
        super(type, level);
    }

    /** Fast and sharp rather than tough: it is a hunting dog, not a bodyguard. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new com.example.examplemod.fauna.goal.FaunaLeapGoal(
                this, POUNCE, 3.0D, 8.0D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(3, new AlarmedPanicGoal(this, 1.5D));
        this.goalSelector.addGoal(4, new HerdGoal(this, 1.1D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());

        // "Monster" is the mod's greenskins and nothing else that walks these planets, so the
        // broad type is also the accurate one — and it keeps working when a new Ork unit is added.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Monster.class, 10, true, false, target -> !(target instanceof FCAnimalEntity)));
    }

    // ------------------------------------------------------------------ habilidades

    /**
     * A sequencia que o briefing descreve: encara, rosna, varre, corre.
     *
     * <p>As tres primeiras sao uma habilidade cada e acontecem <b>antes</b> de o cao se mover, o que e
     * o que separa um cao de guarda de um mob hostil: ele te da tempo de sair da area restrita.
     */
    @Override
    protected void awakeServerTick() {
        if (!canUseAbility()) {
            return;
        }

        LivingEntity target = this.getTarget();

        if (target != null) {
            // Alvo novo e longe: varre antes de correr.
            if (this.distanceTo(target) > 6.0D && this.random.nextInt(60) == 0) {
                startAbility(AUSPEX_SCAN);
            }
            return;
        }

        if (this.random.nextInt(240) == 0 && nearestWatcher() != null) {
            startAbility(THREAT);
        }
    }

    @Override
    protected void onAbilityStart(com.example.examplemod.fauna.FaunaAbility started) {
        if (started == AUSPEX_SCAN) {
            this.getNavigation().stop();
            this.playSound(com.example.examplemod.fauna.FaunaSoundEvents.auspexScan(), 0.8F, 1.6F);
        } else if (started == THREAT) {
            this.playSound(SoundEvents.WOLF_GROWL, 1.0F, 0.8F);
        }
    }

    /** O olho aceso durante a varredura. Uma particula a cada cinco ticks, e so nesses 30 ticks. */
    @Override
    protected void onAbilityTick(com.example.examplemod.fauna.FaunaAbility active, int tickInPhase) {
        if (active == AUSPEX_SCAN && tickInPhase % 5 == 0
                && this.level() instanceof ServerLevel server) {
            com.example.examplemod.fauna.effect.FaunaVisualEffects.scanBeam(server, this);
        }
    }

    @Override
    protected void onAbilityStrike(com.example.examplemod.fauna.FaunaAbility active) {
        if (active != POUNCE) {
            return;
        }

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

        this.setDeltaMovement(dx / length * 0.65D, 0.4D, dz / length * 0.65D);
    }

    /**
     * Combat lock: a mordida que prende.
     *
     * <p>Lentidao curta em vez de imobilizacao total. Prender o jogador de verdade seria remover o
     * controle dele, e um cao de seguranca nao pode fazer isso — a leitura pretendida e "estou preso e
     * tenho de me soltar", nao "perdi o teclado".
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (!hit || this.level().isClientSide) {
            return hit;
        }

        this.triggerAnim("ability", "attack_bite");

        if (target instanceof LivingEntity victim && this.random.nextFloat() < LOCK_CHANCE) {
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                    LOCK_TICKS, 2, false, true));

            this.playSound(com.example.examplemod.fauna.FaunaSoundEvents.combatLock(), 0.9F, 1.2F);

            if (this.level() instanceof ServerLevel server) {
                com.example.examplemod.fauna.effect.FaunaVisualEffects.sparks(server, victim);
            }
        }

        return hit;
    }

    /** A mastiff pack does not breed in the field; the Arbites issue them. */
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
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        // Passo de metal: e a metade implante do bicho, e e o que o distingue de um lobo de ouvido.
        this.playSound(com.example.examplemod.fauna.FaunaSoundEvents.mastiffStep(), 0.1F, 1.4F);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(this.getTarget() != null ? RUN : WALK);
        }));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("attack_bite", RawAnimation.begin().thenPlay("attack_bite"))
                .triggerableAnim("pounce_attack", RawAnimation.begin().thenPlay("pounce_attack"))
                .triggerableAnim("auspex_scan", RawAnimation.begin().thenPlay("auspex_scan"))
                .triggerableAnim("threat_display", RawAnimation.begin().thenPlay("threat_display")));
    }
}
