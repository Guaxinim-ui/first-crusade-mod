package com.example.examplemod.fauna.entity;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.effect.FaunaVisualEffects;
import com.example.examplemod.fauna.goal.FaunaCamouflageGoal;
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
 * O Catachan Devil: a fauna apex do mod.
 *
 * <h2>Nunca deve parecer um mob comum, e sao quatro coisas que garantem isso</h2>
 *
 * <ol>
 *   <li><b>Raridade</b> — peso de spawn 1 e teto de UM por 48 blocos
 *       ({@code FaunaSpawnRules.APEX_LIMIT}). Ele e uma historia, e historia que se repete no mesmo
 *       dia vira tarefa.</li>
 *   <li><b>Anuncio</b> — a estrutura dele (arvores quebradas, cadaveres, casulos) chega antes dele.
 *       O jogador sabe onde esta antes de ver o que e.</li>
 *   <li><b>Exibicao antes da briga</b> — {@link #THREAT_DISPLAY} levanta pincas e cauda. Ele nao
 *       ataca de surpresa; ele avisa, e o aviso e a parte assustadora.</li>
 *   <li><b>Numeros que doem</b> — 120 de vida e veneno forte no ferrao. Nao e um mob que se mata por
 *       acidente.</li>
 * </ol>
 *
 * <h2>A camuflagem NAO e invisibilidade</h2>
 *
 * Ver {@link FaunaCamouflageGoal}. Parado na vegetacao ele reduz o proprio alcance de deteccao, entra
 * na pose de camuflagem e fica mais escuro no renderer. Invisibilidade completa e proibida pelo
 * briefing, e a razao pratica e melhor que a regra: um apex invisivel matando o jogador nao produz a
 * historia "havia algo escondido ali", produz "o jogo bugou".
 */
public class CatachanDevilEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /** A picada de cauda. Preparacao longa e visivel: cauda erguida, ferrao brilhando. */
    private static final FaunaAbility TAIL_STING = new FaunaAbility("tail_sting", 14, 14, 180);

    /** As pincas. O ataque pesado. */
    private static final FaunaAbility PINCERS = new FaunaAbility("attack_pincers", 8, 14, 80);

    /** A exibicao. Sem dano — e o aviso, e o que faz o encontro ser memoravel. */
    private static final FaunaAbility THREAT_DISPLAY = new FaunaAbility("threat_display", 0, 44, 400);

    /** A pose de camuflagem. Segurada enquanto ele fica parado. */
    private static final FaunaAbility CAMOUFLAGE = new FaunaAbility("camouflage_stance", 0, 200, 100);

    private static final double STING_RANGE = 4.0D;
    private static final float STING_DAMAGE = 10.0F;

    /** Alcance de deteccao normal, e o reduzido durante a camuflagem. */
    private static final double SIGHT_NORMAL = 24.0D;
    private static final double SIGHT_CAMOUFLAGED = 8.0D;

    public CatachanDevilEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /** Os numeros de um mini-boss: vida de sobra, carapaca dura, golpe que mata Guardsman. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
                .add(Attributes.FOLLOW_RANGE, SIGHT_NORMAL);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaTerritorialGoal(this, THREAT_DISPLAY, 14.0D, 5.0D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(3, new FaunaCamouflageGoal(this, CAMOUFLAGE));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 14.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));

        // Come o que passa. Filtro por largura em vez de lista de especies: a fauna que vier depois
        // entra sozinha, e nada precisa voltar aqui.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class,
                10, true, false, prey -> prey.getBbWidth() < 1.8F
                        && !(prey instanceof CatachanDevilEntity)));
    }

    @Override
    protected void awakeServerTick() {
        LivingEntity target = this.getTarget();
        if (target == null || !canUseAbility()) {
            return;
        }

        double distance = this.distanceTo(target);

        if (distance <= STING_RANGE && this.random.nextInt(45) == 0) {
            startAbility(TAIL_STING);
        } else if (distance <= 3.5D && this.random.nextInt(60) == 0) {
            startAbility(PINCERS);
        }
    }

    // ------------------------------------------------------------------ camuflagem

    /**
     * True enquanto a pose de camuflagem esta ativa.
     *
     * <p>Publico porque o renderer le isto para escurecer o modelo. E o unico estado da fauna que o
     * cliente precisa saber sem pacote proprio: a habilidade em curso ja chega la pela animacao.
     */
    public boolean isCamouflaged() {
        return currentAbility() == CAMOUFLAGE;
    }

    /** Camuflado, ele deixa passar alvos que normalmente perseguiria. Custa oportunidade. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.getAttribute(Attributes.FOLLOW_RANGE) == null) {
            return;
        }

        double wanted = isCamouflaged() ? SIGHT_CAMOUFLAGED : SIGHT_NORMAL;
        if (this.getAttribute(Attributes.FOLLOW_RANGE).getBaseValue() != wanted) {
            this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(wanted);
        }
    }

    // ------------------------------------------------------------------ habilidades

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == THREAT_DISPLAY) {
            this.getNavigation().stop();
            this.playSound(FaunaSoundEvents.threatDisplay(), 1.6F, 0.6F);
            tremor(0.2F, 10, 14.0D);
        } else if (started == TAIL_STING) {
            this.getNavigation().stop();
        }
    }

    @Override
    protected void onAbilityTick(FaunaAbility active, int tickInPhase) {
        if (active == CAMOUFLAGE && tickInPhase % 40 == 0
                && this.level() instanceof ServerLevel server) {
            // Sinal minimo de que ele esta ali. Uma particula a cada dois segundos: quem esta atento
            // percebe, quem passa correndo nao. Zero particulas seria invisibilidade por outro nome.
            FaunaVisualEffects.stingGlow(server, this.getX(), this.getY() + 0.3D, this.getZ());
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == TAIL_STING) {
            sting();
        } else if (active == PINCERS) {
            pincers();
        }
    }

    private void sting() {
        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > STING_RANGE + 1.0D) {
            return;
        }

        target.hurt(this.damageSources().mobAttack(this), STING_DAMAGE);
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 2, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, true));

        if (this.level() instanceof ServerLevel server) {
            FaunaVisualEffects.stingGlow(server, target.getX(), target.getY() + 1.0D, target.getZ());
        }

        this.playSound(FaunaSoundEvents.mandibles(), 1.0F, 0.7F);
    }

    private void pincers() {
        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > 4.5D) {
            return;
        }

        target.hurt(this.damageSources().mobAttack(this), 16.0F);
        knockBack(target, this, 1.3D, 0.35D);
        this.playSound(FaunaSoundEvents.mandibles(), 1.2F, 0.5F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability",
                    this.random.nextBoolean() ? "attack_maw" : "attack_pincers");
        }

        return hit;
    }

    /** Apanhar quebra a camuflagem: quem foi visto nao esta mais escondido. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && isCamouflaged()) {
            cancelAbility();
        }

        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ som

    @Override
    protected SoundEvent getAmbientSound() {
        // Camuflado ele fica calado. Som ambiente durante a camuflagem entregaria a posicao e
        // desfaria a habilidade de graca.
        return isCamouflaged() ? null : FaunaSoundEvents.arthropodAmbient();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FaunaSoundEvents.arthropodHurt();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FaunaSoundEvents.arthropodDeath();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(FaunaSoundEvents.arthropodStep(), 0.2F, 0.6F);
    }

    // ------------------------------------------------------------------ criacao

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;                    // apex nao se reproduz: um por ninho, e o ninho e raro
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
                .triggerableAnim("tail_sting", RawAnimation.begin().thenPlay("tail_sting"))
                .triggerableAnim("attack_pincers", RawAnimation.begin().thenPlay("attack_pincers"))
                .triggerableAnim("attack_maw", RawAnimation.begin().thenPlay("attack_maw"))
                .triggerableAnim("threat_display", RawAnimation.begin().thenPlay("threat_display"))
                .triggerableAnim("camouflage_stance",
                        RawAnimation.begin().thenLoop("camouflage_stance")));
    }
}
