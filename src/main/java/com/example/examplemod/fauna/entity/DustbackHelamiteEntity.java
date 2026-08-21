package com.example.examplemod.fauna.entity;

import com.example.examplemod.animal.AlarmedPanicGoal;
import com.example.examplemod.animal.HerdGoal;
import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.goal.FaunaLeapGoal;

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
 * O Dustback Helamite: a criatura que os nomades dos Ash Wastes usam para atravessar as cinzas.
 *
 * <h2>Neutro, e o coice e a razao</h2>
 *
 * Um animal de carga nao caca ninguem. O Helamite anda em grupo, foge quando apanha, e quem chega por
 * tras leva {@link #REAR_KICK} — que e o unico ataque dele com knockback alto. Isso da a criatura uma
 * regra que o jogador aprende sem texto: aproxime-se pela frente.
 *
 * <h2>Montaria: nao implementada, e nao por esquecimento</h2>
 *
 * O modelo do dono tem a animacao {@code mount_crouch} pronta, e o briefing diz que a montaria pode
 * esperar se a infraestrutura nao existir. Ela nao existe: o mod nao tem sela, nao tem controle de
 * montaria e nao tem nenhum outro animal montavel para herdar a mecanica. Meia montaria — um bicho em
 * que o jogador senta e nao consegue guiar — seria pior do que nenhuma.
 *
 * <p>Entao o gancho ficou preparado e a animacao registrada: {@link #MOUNT_CROUCH} existe, esta ligada
 * ao controlador, e o dia em que houver sela precisa apenas de um {@code startAbility}.
 */
public class DustbackHelamiteEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /** O salto grande. Preparacao longa: e um agachamento visivel antes de sair do chao. */
    private static final FaunaAbility MIGHTY_LEAP = new FaunaAbility("mighty_leap", 12, 24, 200);

    /** O coice. Knockback alto e o ponto todo. */
    private static final FaunaAbility REAR_KICK = new FaunaAbility("rear_kick", 6, 12, 120);

    /** Empinar em aviso. Sem dano — e a chance de recuar. */
    private static final FaunaAbility THREAT_REAR = new FaunaAbility("threat_rear", 0, 30, 300);

    /**
     * Agachar para receber cavaleiro. Preparada para a montaria futura, nunca disparada hoje.
     *
     * <p>Registrada no controlador de proposito: uma animacao que existe no arquivo e nao esta ligada
     * a nada e uma armadilha para a proxima pessoa, que vai procurar por ela e concluir que o modelo
     * esta incompleto.
     */
    public static final FaunaAbility MOUNT_CROUCH = new FaunaAbility("mount_crouch", 0, 30, 20);

    private static final double KICK_RANGE = 3.0D;

    public DustbackHelamiteEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /** Resistente e rapido, porque atravessa deserto. Dano baixo, porque nao e predador. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 18.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AlarmedPanicGoal(this, 1.8D));
        this.goalSelector.addGoal(2, new FaunaLeapGoal(this, MIGHTY_LEAP, 4.0D, 12.0D, 4.0D));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(4, new HerdGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Revida, mas nao caca: sem NearestAttackableTargetGoal nenhum.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    protected void awakeServerTick() {
        // O aviso: alguem perto, sem briga em curso. Um por cinco minutos no maximo, pelo cooldown.
        if (this.getTarget() == null && canUseAbility() && this.random.nextInt(200) == 0
                && nearestWatcher() != null) {
            startAbility(THREAT_REAR);
            return;
        }

        // O coice sai quando o alvo esta ATRAS. A checagem de angulo e o que da sentido a habilidade:
        // sem ela seria um segundo ataque frontal com nome de coice.
        LivingEntity target = this.getTarget();
        if (target != null && canUseAbility() && this.distanceTo(target) <= KICK_RANGE
                && isBehind(target)) {
            startAbility(REAR_KICK);
        }
    }

    /** True quando o alvo esta no hemisferio de tras do bicho. */
    private boolean isBehind(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();

        double facingX = -Math.sin(Math.toRadians(this.getYRot()));
        double facingZ = Math.cos(Math.toRadians(this.getYRot()));

        return dx * facingX + dz * facingZ < 0.0D;
    }

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == MIGHTY_LEAP) {
            this.getNavigation().stop();
            if (this.level() instanceof ServerLevel server) {
                com.example.examplemod.fauna.effect.FaunaVisualEffects.dustRing(server, this, 0.9D, 6);
            }
        } else if (started == THREAT_REAR) {
            this.playSound(FaunaSoundEvents.threatDisplay(), 1.0F, 1.1F);
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == MIGHTY_LEAP) {
            leap();
        } else if (active == REAR_KICK) {
            kick();
        }
    }

    private void leap() {
        LivingEntity target = this.getTarget();
        double dx;
        double dz;

        if (target != null) {
            dx = target.getX() - this.getX();
            dz = target.getZ() - this.getZ();
        } else {
            dx = -Math.sin(Math.toRadians(this.getYRot()));
            dz = Math.cos(Math.toRadians(this.getYRot()));
        }

        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return;
        }

        this.setDeltaMovement(dx / length * 0.85D, 0.72D, dz / length * 0.85D);
        this.playSound(FaunaSoundEvents.leapOff(), 0.9F, 0.9F);
    }

    private void kick() {
        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > KICK_RANGE + 1.0D) {
            return;
        }

        target.hurt(this.damageSources().mobAttack(this), 5.0F);
        knockBack(target, this, 1.6D, 0.45D);
        this.playSound(FaunaSoundEvents.largeBeastStep(), 1.0F, 0.7F);
    }

    /** Pouso: poeira e uma onda visual, como o briefing pede. */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (distance > 1.5F && this.level() instanceof ServerLevel server) {
            com.example.examplemod.fauna.effect.FaunaVisualEffects.dustRing(server, this, 1.4D, 8);
            this.playSound(FaunaSoundEvents.leapLand(), 0.7F, 1.0F);
        }

        return super.causeFallDamage(distance, multiplier, source);
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
        this.playSound(FaunaSoundEvents.largeBeastStep(), 0.15F, 1.2F);
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

    /** Nomade leva o bicho na coleira; entao pode ser laçado. */
    @Override
    public boolean canBeLeashed(Player player) {
        return true;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", "attack_bite");
        }

        return hit;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(isAlarmed() ? RUN : WALK);
        }));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("mighty_leap", RawAnimation.begin().thenPlay("mighty_leap"))
                .triggerableAnim("rear_kick", RawAnimation.begin().thenPlay("rear_kick"))
                .triggerableAnim("threat_rear", RawAnimation.begin().thenPlay("threat_rear"))
                .triggerableAnim("attack_bite", RawAnimation.begin().thenPlay("attack_bite"))
                .triggerableAnim("mount_crouch", RawAnimation.begin().thenPlay("mount_crouch")));
    }
}
