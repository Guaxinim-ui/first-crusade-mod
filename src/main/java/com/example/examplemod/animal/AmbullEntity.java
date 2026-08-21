package com.example.examplemod.animal;

import java.util.List;

import javax.annotation.Nullable;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.effect.FaunaVisualEffects;
import com.example.examplemod.fauna.goal.FaunaAmbushGoal;
import com.example.examplemod.fauna.goal.FaunaBurrowGoal;

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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Ambull: the thing the miners talk about.
 *
 * <h2>Rare on purpose, and that is the whole design</h2>
 *
 * An Ambull is a two-and-a-half metre digging predator that hunts by heat and comes up through the
 * floor. In the lore it is an infestation, not a population — a mining crew meets one, once, and the
 * shaft gets sealed. So the spawn weight is the lowest in the mod by a wide margin, and everything
 * else about it follows from being rare: it hits hard enough to matter, it is worth killing for the
 * chitin, and meeting a second one in the same afternoon should feel wrong.
 *
 * <p>The temptation with a monster is to make it common enough to "be content". That is exactly
 * backwards — a rare thing that is genuinely dangerous is content; a common thing that is dangerous
 * is a tax. And a hostile animal with no ceiling on it is the failure this mod has already paid for
 * once, which is why it is wildlife (with the spawn cap) rather than a {@code Monster}.
 *
 * <h2>Passou a herdar de {@link FaunaEntity} (integracao dos modelos do Blockbench)</h2>
 *
 * O modelo aprovado do dono trouxe {@code burrow}, {@code emerge} e {@code attack_slam}, e essas tres
 * animacoes descrevem a criatura melhor do que qualquer numero: ela vive sob o chao e sobe atraves
 * dele. A base da fauna ja carrega a maquina de habilidades, o estado enterrado e os efeitos, entao a
 * mudanca custou trocar a superclasse — todo o comportamento anterior continua igual.
 *
 * <h2>Nao destroi terreno</h2>
 *
 * O briefing e explicito, e a razao pratica e mais forte que a regra: um Ambull que escava de verdade
 * deixa buraco permanente em todo lugar por onde passou, e depois de uma hora de jogo o deserto e um
 * queijo que ninguem consegue atravessar. "Enterrado" e um estado da entidade, e o que o jogador ve e
 * poeira, pedra saltando e um tremor.
 */
public class AmbullEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private static final FaunaAbility BURROW = new FaunaAbility("burrow", 0, 24, 300);

    /** Emergir. Cooldown zero: o golpe de chao vem logo depois e nao pode ser bloqueado por espera. */
    private static final FaunaAbility EMERGE = new FaunaAbility("emerge", 0, 24, 0);

    /** O golpe no chao. Preparacao de meio segundo: o aviso para sair do circulo. */
    private static final FaunaAbility GROUND_SLAM = new FaunaAbility("attack_slam", 10, 16, 200);

    private static final FaunaAbility BITE = FaunaAbility.instant("attack_bite", 12, 40);

    /** Quao perto ele deixa o jogador chegar antes de sair do chao. */
    private static final double EMERGE_DISTANCE = 5.0D;

    /** Raio do golpe de chao, e o dano dentro dele. */
    private static final double SLAM_RADIUS = 4.0D;
    private static final float SLAM_DAMAGE = 9.0F;

    public AmbullEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /**
     * Slow, armoured and very heavy-handed.
     *
     * <p>It cannot chase a player who runs — the speed is below a sprint on purpose. What it can do
     * is win any fight the player chooses to have, which puts the decision where it belongs.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaAmbushGoal(this, EMERGE, EMERGE_DISTANCE, 0.8D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new FaunaBurrowGoal(this, BURROW, 300));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Everything warm that is not another Ambull. It does not care whose side anyone is on,
        // which is the one thing that makes it different from every armed mob in the mod.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, LivingEntity.class, 10, true, false,
                target -> !(target instanceof AmbullEntity)
                        && target.getType().getCategory() != net.minecraft.world.entity.MobCategory.MISC));
    }

    // ------------------------------------------------------------------ habilidades

    @Override
    protected void awakeServerTick() {
        LivingEntity target = this.getTarget();

        if (target != null && canUseAbility() && this.distanceTo(target) <= SLAM_RADIUS
                && this.random.nextInt(50) == 0) {
            startAbility(GROUND_SLAM);
        }
    }

    /**
     * Enterrado, ele anuncia a propria posicao — e essa e a pista ambiental que o briefing pede.
     *
     * <p>Roda a cada 10 ticks e so quando ha jogador dentro do raio de habilidade (a porta esta em
     * {@code FaunaEntity#customServerAiStep}, que so chama {@code awakeServerTick} nesse caso). Um
     * Ambull enterrado num chunk sem ninguem por perto nao emite nada.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (isBurrowed() && this.tickCount % 10 == 0 && this.level() instanceof ServerLevel server
                && abilitiesAwake()) {
            FaunaVisualEffects.jumpingPebbles(server, this.getX(), this.getY(), this.getZ(), 3);

            if (this.tickCount % 40 == 0) {
                this.playSound(FaunaSoundEvents.burrow(), 0.35F, 0.5F);
                tremor(0.12F, 6, 10.0D);
            }
        }
    }

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == BURROW) {
            this.getNavigation().stop();
            this.playSound(FaunaSoundEvents.burrow(), 0.8F, 0.6F);
            dustRing(1.2D, 8);
        } else if (started == GROUND_SLAM) {
            this.getNavigation().stop();
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == BURROW) {
            setBurrowed(true);
        } else if (active == EMERGE) {
            emerge();
        } else if (active == GROUND_SLAM) {
            slam();
        }
    }

    private void emerge() {
        setBurrowed(false);
        this.playSound(FaunaSoundEvents.emerge(), 1.6F, 0.8F);

        if (this.level() instanceof ServerLevel server) {
            FaunaVisualEffects.emergeBurst(server, this);
        }

        tremor(0.55F, 14, 18.0D);
    }

    /**
     * O golpe no chao: poeira, pedras, tremor, empurrao e dano em area pequena.
     *
     * <p>Nenhum bloco muda. O briefing pede isso, e o motivo pratico e que um golpe que abre cratera
     * transforma a arena da luta em terreno intransponivel no meio dela — o jogador cai num buraco
     * feito pelo bicho que esta tentando mata-lo.
     */
    private void slam() {
        this.playSound(FaunaSoundEvents.groundSlam(), 1.4F, 0.5F);

        if (this.level() instanceof ServerLevel server) {
            FaunaVisualEffects.dustRing(server, this, SLAM_RADIUS * 0.6D, 12);
            FaunaVisualEffects.jumpingPebbles(server, this.getX(), this.getY(), this.getZ(), 20);
        }

        tremor(0.65F, 16, 20.0D);

        List<LivingEntity> hit = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(SLAM_RADIUS), this::isAreaTarget);

        for (LivingEntity victim : hit) {
            // Dano cai com a distancia: quem estava na borda quando o golpe saiu escapa com pouco,
            // o que da sentido a preparacao de meio segundo.
            float falloff = (float) Math.max(0.3D, 1.0D - this.distanceTo(victim) / SLAM_RADIUS);

            victim.hurt(this.damageSources().mobAttack(this), SLAM_DAMAGE * falloff);
            knockBack(victim, this, 1.5D * falloff, 0.5D);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", BITE.animation());
        }

        return hit;
    }

    /** Apanhar enterrado o traz para cima: o jogador que acerta merece ver o que acertou. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isBurrowed() && !this.level().isClientSide) {
            emerge();
        }

        return super.hurt(source, amount);
    }

    /** Ambulls are an infestation, not a herd. Two of them is already a problem. */
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    /** Nothing frightens it, so the alarm would only make it run from its own dinner. */
    @Override
    public void alarm() {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isBurrowed() ? null : FaunaSoundEvents.largeBeastAmbient();
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
    protected void playStepSound(net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        if (!isBurrowed()) {
            this.playSound(FaunaSoundEvents.largeBeastStep(), 0.3F, 0.7F);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state ->
                state.isMoving() ? state.setAndContinue(WALK) : state.setAndContinue(IDLE)));

        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("burrow", RawAnimation.begin().thenPlay("burrow"))
                .triggerableAnim("emerge", RawAnimation.begin().thenPlay("emerge"))
                .triggerableAnim("attack_slam", RawAnimation.begin().thenPlay("attack_slam"))
                .triggerableAnim("attack_bite", RawAnimation.begin().thenPlay("attack_bite"))
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("attack")));
    }
}
