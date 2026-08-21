package com.example.examplemod.fauna.entity;

import java.util.List;

import com.example.examplemod.animal.FCAnimalEntity;
import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.goal.FaunaTerritorialGoal;

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
 * O Cthellean Cudbear: predador territorial das florestas.
 *
 * <h2>Territorial, nao hostil</h2>
 *
 * O ciclo e olhar, rosnar, avisar, e so entao atacar — {@link FaunaTerritorialGoal}. O jogador que
 * recua depois do rugido nao briga; o que continua andando escolheu brigar. Essa e a diferenca entre
 * este bicho e um zumbi, e ela e a razao de o encontro com ele ser interessante mais de uma vez.
 *
 * <h2>O rugido territorial faz tres coisas, e uma delas e ecologia</h2>
 *
 * <ol>
 *   <li>avisa o jogador (som grave + tremor leve);</li>
 *   <li>aplica {@code WEAKNESS} curto em quem esta perto — o susto;</li>
 *   <li><b>espanta fauna menor</b>: alarma, num raio pequeno, os {@code FCAnimalEntity} de largura
 *       menor que a dele. E o unico lugar do mod em que uma especie interage com outra sem briga, e
 *       custa uma consulta por rugido, que tem cooldown de meio minuto.</li>
 * </ol>
 */
public class CthelleanCudbearEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /** O aviso. Sem dano: e a chance que o intruso tem. */
    private static final FaunaAbility ROAR = new FaunaAbility("territorial_roar", 0, 36, 400);

    /** O golpe pesado: levanta o corpo e desce. */
    private static final FaunaAbility MAUL = new FaunaAbility("maul", 10, 16, 140);

    /** Distancia em que o territorio conta como invadido. */
    private static final double TERRITORY_RADIUS = 12.0D;

    /** Quantos blocos de aproximacao depois do rugido contam como decisao do intruso. */
    private static final double APPROACH_MARGIN = 4.0D;

    private static final double MAUL_RANGE = 3.5D;
    private static final float MAUL_DAMAGE = 12.0F;

    public CthelleanCudbearEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /**
     * Muita vida, golpe forte, lento.
     *
     * <p>{@code FOLLOW_RANGE} em 16 e a regra "nao perseguir por centenas de blocos" onde ela de fato
     * mora. Um Cudbear que segue o jogador pela floresta inteira deixa de ser territorial e passa a
     * ser um caçador — e o territorio, que e o conceito todo da especie, desaparece.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaTerritorialGoal(this, ROAR, TERRITORY_RADIUS,
                APPROACH_MARGIN));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Sem NearestAttackableTargetGoal: quem escolhe alvo aqui e a goal territorial. Ter as duas
        // faria o urso atacar antes de avisar, e o aviso viraria enfeite.
    }

    @Override
    protected void awakeServerTick() {
        LivingEntity target = this.getTarget();

        if (target != null && canUseAbility() && this.distanceTo(target) <= MAUL_RANGE
                && this.random.nextInt(40) == 0) {
            startAbility(MAUL);
        }
    }

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == ROAR) {
            roar();
        } else if (started == MAUL) {
            this.getNavigation().stop();
        }
    }

    private void roar() {
        this.playSound(FaunaSoundEvents.territorialRoar(), 1.8F, 1.0F);
        tremor(0.3F, 12, 16.0D);

        if (this.level() instanceof ServerLevel server) {
            com.example.examplemod.fauna.effect.FaunaVisualEffects.breath(server, this, 6);
        }

        scareSmallerFauna();
        weakenNearby();
    }

    /**
     * Espanta a fauna menor: alarme, nao dano.
     *
     * <p>Usa o alarme que a Fase E ja construiu, que e um estado e nao uma goal — o bicho alarmado
     * corre por conta propria. Reaproveitar isso significa que espantar fauna custa uma consulta e um
     * campo por bicho, e nao um sistema de medo.
     */
    private void scareSmallerFauna() {
        List<FCAnimalEntity> neighbours = this.level().getEntitiesOfClass(FCAnimalEntity.class,
                this.getBoundingBox().inflate(TERRITORY_RADIUS),
                other -> other != this && other.getBbWidth() < this.getBbWidth());

        for (FCAnimalEntity neighbour : neighbours) {
            neighbour.alarm();
        }
    }

    private void weakenNearby() {
        List<Player> players = this.level().getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(TERRITORY_RADIUS),
                player -> !player.isCreative() && !player.isSpectator());

        for (Player player : players) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true));
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active != MAUL) {
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > MAUL_RANGE + 1.5D) {
            return;
        }

        target.hurt(this.damageSources().mobAttack(this), MAUL_DAMAGE);
        knockBack(target, this, 1.2D, 0.4D);

        dustRing(1.2D, 8);
        tremor(0.25F, 6, 10.0D);
        this.playSound(FaunaSoundEvents.largeBeastStep(), 1.2F, 0.6F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability",
                    this.random.nextBoolean() ? "attack_swipe" : "attack_bite");
        }

        return hit;
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
        this.playSound(FaunaSoundEvents.largeBeastStep(), 0.18F, 0.9F);
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
                .triggerableAnim("territorial_roar", RawAnimation.begin().thenPlay("territorial_roar"))
                .triggerableAnim("maul", RawAnimation.begin().thenPlay("maul"))
                .triggerableAnim("attack_swipe", RawAnimation.begin().thenPlay("attack_swipe"))
                .triggerableAnim("attack_bite", RawAnimation.begin().thenPlay("attack_bite")));
    }
}
