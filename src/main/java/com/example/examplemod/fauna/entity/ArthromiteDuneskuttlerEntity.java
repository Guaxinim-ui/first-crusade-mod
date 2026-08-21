package com.example.examplemod.fauna.entity;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;
import com.example.examplemod.fauna.FaunaSoundEvents;
import com.example.examplemod.fauna.effect.FaunaVisualEffects;
import com.example.examplemod.fauna.goal.FaunaAmbushGoal;
import com.example.examplemod.fauna.goal.FaunaBurrowGoal;

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
 * O Arthromite Duneskuttler: predador de emboscada dos ermos de cinza.
 *
 * <h2>O ciclo, e onde cada parte dele vive</h2>
 *
 * <pre>
 *   sem ninguem perto     -> FaunaBurrowGoal enterra
 *   jogador a 32 blocos   -> FaunaAmbushGoal anda sob o chao, largando poeira (a pista ambiental)
 *   jogador a 6 blocos    -> emerge: jato de areia, som de carapaca, animacao
 *   emergiu               -> ambush_charge: um empurrao de velocidade curto, e depois briga normal
 * </pre>
 *
 * <p>Nenhum bloco e alterado em nenhuma dessas etapas. "Enterrado" e estado da entidade — modelo nao
 * desenhado, colisao fora — e nao escavacao. Um Duneskuttler que abrisse tuneis reais deixaria o
 * deserto permanentemente esburacado depois de uma hora, e o buraco nao fecha nunca.
 *
 * <h2>A poeira usa o bloco do chao</h2>
 *
 * O mesmo bicho emerge em cinza de Armageddon, em crosta de sal e em cascalho de Macragge. Uma cor
 * fixa de areia estaria errada em dois dos tres. Ver {@code FaunaVisualEffects#groundParticle}.
 */
public class ArthromiteDuneskuttlerEntity extends FaunaEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    private static final FaunaAbility BURROW = new FaunaAbility("burrow", 0, 20, 200);

    /**
     * Emergir. Cooldown zero de proposito: o {@code ambush_charge} vem logo depois, e um cooldown
     * aqui faria o encadeamento falhar em silencio — o bicho sairia do chao e ficaria parado.
     */
    private static final FaunaAbility EMERGE = new FaunaAbility("emerge", 0, 20, 0);

    private static final FaunaAbility AMBUSH_CHARGE = new FaunaAbility("ambush_charge", 0, 16, 160);

    private static final FaunaAbility MANDIBLES = FaunaAbility.instant("attack_mandibles", 12, 40);

    /** Quantos blocos de distancia bastam para o jato de areia valer a pena. */
    private static final double EMERGE_DISTANCE = 6.0D;

    public ArthromiteDuneskuttlerEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    /**
     * Carapaca boa, corpo pequeno, e um golpe que dói.
     *
     * <p>A armadura alta e o que faz a emboscada importar: o jogador que reage rapido perde pouca
     * vida, e o que leva o primeiro golpe de surpresa paga por isso.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FaunaAmbushGoal(this, EMERGE, EMERGE_DISTANCE, 1.0D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(3, new FaunaBurrowGoal(this, BURROW, 200));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ habilidades

    @Override
    protected void onAbilityStart(FaunaAbility started) {
        if (started == BURROW) {
            this.playSound(FaunaSoundEvents.burrow(), 0.6F, 0.7F);
            if (this.level() instanceof ServerLevel server) {
                FaunaVisualEffects.dustRing(server, this, 0.8D, 6);
            }
        }
    }

    @Override
    protected void onAbilityStrike(FaunaAbility active) {
        if (active == BURROW) {
            setBurrowed(true);
            return;
        }

        if (active == EMERGE) {
            emerge();
            return;
        }

        if (active == AMBUSH_CHARGE) {
            dashAtTarget();
        }
    }

    private void emerge() {
        setBurrowed(false);
        this.playSound(FaunaSoundEvents.emerge(), 1.2F, 1.3F);

        if (this.level() instanceof ServerLevel server) {
            FaunaVisualEffects.emergeBurst(server, this);
        }

        tremor(0.35F, 8, 12.0D);
    }

    @Override
    protected void onAbilityEnd(FaunaAbility finished) {
        // O encadeamento emerge -> investida. Feito aqui e nao numa goal porque as duas partes sao a
        // MESMA acao do ponto de vista do jogador: o bicho sai do chao ja vindo para cima dele.
        if (finished == EMERGE && this.getTarget() != null) {
            startAbility(AMBUSH_CHARGE);
        }
    }

    private void dashAtTarget() {
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

        this.setDeltaMovement(dx / length * 0.6D, 0.2D, dz / length * 0.6D);
    }

    @Override
    protected void onAbilityTick(FaunaAbility active, int tickInPhase) {
        if (active == AMBUSH_CHARGE && this.level() instanceof ServerLevel server
                && tickInPhase % 4 == 0) {
            FaunaVisualEffects.burrowTrail(server, this);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", MANDIBLES.animation());
            this.playSound(FaunaSoundEvents.mandibles(), 0.7F, 1.4F);
        }

        return hit;
    }

    /** Apanhar enquanto enterrado tira do chao — senao o jogador bate no nada e nada responde. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isBurrowed() && !this.level().isClientSide) {
            emerge();
        }

        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ som

    @Override
    protected SoundEvent getAmbientSound() {
        return isBurrowed() ? null : FaunaSoundEvents.arthropodAmbient();
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
        if (!isBurrowed()) {
            this.playSound(FaunaSoundEvents.arthropodStep(), 0.15F, 1.0F);
        }
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
                .triggerableAnim("burrow", RawAnimation.begin().thenPlay("burrow"))
                .triggerableAnim("emerge", RawAnimation.begin().thenPlay("emerge"))
                .triggerableAnim("ambush_charge", RawAnimation.begin().thenPlay("ambush_charge"))
                .triggerableAnim("attack_mandibles", RawAnimation.begin().thenPlay("attack_mandibles"))
                .triggerableAnim("attack_claw", RawAnimation.begin().thenPlay("attack_claw")));
    }
}
