package com.example.examplemod.animal;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Grox: the Imperium's cattle, and the first animal of the Crusade's ecology.
 *
 * <h2>A cow that can kill you</h2>
 *
 * The lore is unusually specific for a background animal — a huge, bad-tempered reptile, farmed
 * across the Imperium for meat and hide, dangerous enough that herding one is a job. That gives the
 * mechanical shape directly: <b>peaceful until struck</b>, and then genuinely worth running from.
 * So it is a {@link NeutralMob} rather than a passive one. Hitting a Grox is a decision, and the
 * anger outlasts the swing.
 *
 * <p>The one addition on top of vanilla neutrality is the herd's own logic: <b>adults answer for a
 * calf</b>. Hurting a calf angers every adult within sight of it, in one query, at the moment of the
 * hit. It is the cheapest possible implementation and it is also the truthful one — a herd does not
 * post sentries, it reacts.
 *
 * <h2>What it does when nothing is happening</h2>
 *
 * Grazing ({@link GrazeGoal}), thirst ({@link SeekWaterGoal}) and herding ({@link HerdGoal}) are the
 * ecology, and each is written to cost nothing when idle — see those classes for the budgets. The
 * feed pod from Phase D is its tempt item, which is what that item was created for: it existed
 * before the animal did, and now it has a mouth to go into.
 *
 * <h2>Breeding needs a human</h2>
 *
 * A Grox falls in love only from a {@code grox_feed_pod} handed to it. Nothing in the world produces
 * that automatically, so a herd cannot grow on its own — the population is bounded by worldgen at
 * the bottom and by a player's decisions at the top. That is deliberate: the Ork pod taught this mod
 * what an unattended breeding loop does to a planet.
 */
public class GroxEntity extends com.example.examplemod.fauna.FaunaEntity implements NeutralMob {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /**
     * A carga: abaixa a cabeca, raspa o chao, para, e corre em linha reta.
     *
     * <p>O modelo aprovado do dono trouxe {@code charge} e {@code attack_gore}, e as duas mudam o que
     * um Grox provocado significa. Antes ele so perseguia; agora ele tem um golpe que o jogador pode
     * evitar saindo da frente — e e essa possibilidade que transforma "levei dano" em "errei".
     */
    private static final com.example.examplemod.fauna.FaunaAbility CHARGE =
            new com.example.examplemod.fauna.FaunaAbility("charge", 20, 30, 300);

    /** A chifrada de perto. */
    private static final com.example.examplemod.fauna.FaunaAbility GORE =
            new com.example.examplemod.fauna.FaunaAbility("attack_gore", 6, 12, 80);

    /** O aviso. Um Grox que vai carregar avisa primeiro; e gado, nao emboscada. */
    private static final com.example.examplemod.fauna.FaunaAbility THREAT =
            new com.example.examplemod.fauna.FaunaAbility("threat_display", 0, 30, 400);

    private static final double CHARGE_SPEED = 0.55D;
    private static final float CHARGE_DAMAGE = 10.0F;

    /** Direcao congelada da carga. Runtime: uma carga nao continua depois de um reload. */
    private net.minecraft.world.phys.Vec3 chargeDirection = net.minecraft.world.phys.Vec3.ZERO;

    /** How long a provoked Grox stays provoked. The vanilla neutral-mob range. */
    private static final UniformInt ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);

    /** How far a calf's distress carries to the adults, in blocks. */
    private static final double CALF_DEFENCE_RADIUS = 16.0D;

    /** One mouthful per this many ticks on average, for an adult; calves eat far more often. */
    private static final int GRAZE_ODDS_ADULT = 600;
    private static final int GRAZE_ODDS_CALF = 120;

    private int remainingPersistentAngerTime;

    @Nullable
    private UUID persistentAngerTarget;

    public GroxEntity(EntityType<? extends net.minecraft.world.entity.animal.Animal> type,
                      Level level) {
        super(type, level);
    }

    /**
     * Heavy, slow, armoured and hard to shift.
     *
     * <p>The numbers say "livestock you do not want to annoy": more health than a cow by a wide
     * margin, real armour from the hide, knockback resistance because nothing about a Grox is
     * light, and a hit that hurts. Movement is slower than a player's walk — a Grox that could
     * chase you down would stop being livestock and start being a monster.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AlarmedPanicGoal(this, 1.6D));
        // A carga acima do combate corpo-a-corpo: um Grox com espaco carrega, um Grox encurralado
        // morde. A ordem das goals e o que decide isso, sem nenhuma linha de logica extra.
        this.goalSelector.addGoal(2, new com.example.examplemod.fauna.goal.FaunaChargeGoal(
                this, CHARGE, 5.0D, 16.0D));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.1D,
                Ingredient.of(FCAnimals.groxFeed()), false));
        this.goalSelector.addGoal(6, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(7, new SeekWaterGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new GrazeGoal(this, GRAZE_ODDS_ADULT, GRAZE_ODDS_CALF));
        this.goalSelector.addGoal(9, new HerdGoal(this, 1.0D));
        this.goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    // ------------------------------------------------------------------ the herd answers

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!super.hurt(source, amount)) {
            return false;
        }

        // A calf cannot defend itself, so the adults do it. One query, only on the hit — the same
        // rule the alarm follows: danger reports itself, nothing patrols for it.
        if (!this.level().isClientSide && this.isBaby()
                && source.getEntity() instanceof LivingEntity attacker) {
            defendCalf(attacker);
        }

        return true;
    }

    private void defendCalf(LivingEntity attacker) {
        if (attacker instanceof GroxEntity) {
            return;
        }

        List<GroxEntity> adults = this.level().getEntitiesOfClass(GroxEntity.class,
                this.getBoundingBox().inflate(CALF_DEFENCE_RADIUS),
                other -> other != this && !other.isBaby() && other.isAlive());

        for (GroxEntity adult : adults) {
            adult.setTarget(attacker);
            adult.setPersistentAngerTarget(attacker.getUUID());
            adult.startPersistentAngerTimer();
        }
    }

    // ------------------------------------------------------------------ a carga

    @Override
    protected void awakeServerTick() {
        if (!canUseAbility()) {
            return;
        }

        LivingEntity target = this.getTarget();

        if (target != null && this.distanceTo(target) <= 4.0D && this.random.nextInt(45) == 0) {
            startAbility(GORE);
            return;
        }

        // O aviso: alguem chegou perto de um Grox que nao esta bravo. Cooldown de 20 s.
        if (target == null && this.random.nextInt(220) == 0 && nearestWatcher() != null) {
            startAbility(THREAT);
        }
    }

    @Override
    protected void onAbilityStart(com.example.examplemod.fauna.FaunaAbility started) {
        if (started == CHARGE) {
            this.getNavigation().stop();
            this.playSound(com.example.examplemod.fauna.FaunaSoundEvents.chargeRoar(), 1.2F, 0.9F);
        } else if (started == THREAT) {
            this.playSound(com.example.examplemod.fauna.FaunaSoundEvents.threatDisplay(), 1.0F, 1.0F);
        }
    }

    /** Fim da preparacao: congela a direcao. Depois disso a carga nao vira mais. */
    @Override
    protected void onAbilityStrike(com.example.examplemod.fauna.FaunaAbility active) {
        if (active == GORE) {
            gore();
            return;
        }

        if (active != CHARGE) {
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null) {
            cancelAbility();
            return;
        }

        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(
                target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());

        if (flat.lengthSqr() < 1.0E-4D) {
            cancelAbility();
            return;
        }

        this.chargeDirection = flat.normalize();
        dustRing(1.1D, 6);
    }

    @Override
    protected void onAbilityTick(com.example.examplemod.fauna.FaunaAbility active, int tickInPhase) {
        if (active != CHARGE || this.chargeDirection.lengthSqr() < 1.0E-4D) {
            return;
        }

        net.minecraft.world.phys.Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(this.chargeDirection.x * CHARGE_SPEED, current.y,
                this.chargeDirection.z * CHARGE_SPEED);

        this.setYRot((float) (Math.atan2(this.chargeDirection.z, -this.chargeDirection.x)
                * (180.0D / Math.PI)) - 90.0F);
        this.yBodyRot = this.getYRot();

        List<LivingEntity> hit = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(0.35D), this::isAreaTarget);

        if (hit.isEmpty()) {
            return;
        }

        for (LivingEntity victim : hit) {
            victim.hurt(this.damageSources().mobAttack(this), CHARGE_DAMAGE);
            knockBack(victim, this, 1.9D, 0.45D);
        }

        dustRing(1.3D, 8);
        tremor(0.3F, 8, 12.0D);

        // Uma carga acerta uma vez. Sem isto, correr por cima do alvo aplicaria o dano a cada tick.
        cancelAbility();
    }

    private void gore() {
        LivingEntity target = this.getTarget();
        if (target == null || this.distanceTo(target) > 5.0D) {
            return;
        }

        target.hurt(this.damageSources().mobAttack(this), 8.0F);
        knockBack(target, this, 0.9D, 0.4D);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit && !this.level().isClientSide) {
            this.triggerAnim("ability", "attack");
        }

        return hit;
    }

    // ------------------------------------------------------------------ neutral mob

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.level() instanceof ServerLevel server) {
            this.updatePersistentAnger(server, true);
        }
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.remainingPersistentAngerTime = time;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(ANGER_TIME.sample(this.random));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (this.level() instanceof ServerLevel server) {
            this.readPersistentAngerSaveData(server, tag);
        }
    }

    // ------------------------------------------------------------------ husbandry

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(FCAnimals.groxFeed());
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return FCAnimals.GROX.get().create(level);
    }

    /**
     * A herd arrives as a herd, and roughly one in six of it is a calf.
     *
     * <p>Vanilla's default is 5%, which on a group of four means most herds have none at all. The
     * higher number is what makes a group read as a family from a distance instead of as four copies
     * of the same animal.
     */
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        if (data == null) {
            data = new AgeableMob.AgeableMobGroupData(0.15F);
        }

        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return true;
    }

    /**
     * A mouthful of grass or of water heals a little.
     *
     * <p>This is the only thing eating does, and the omission is the design: feeding must never
     * push the animal toward breeding, or grazing would become an unattended population loop —
     * the exact shape of the Ork pod. Recovery is a reward the herd can give itself; a calf is not.
     */
    @Override
    public void ate() {
        super.ate();

        if (!this.level().isClientSide) {
            this.heal(1.0F);
        }
    }

    // ------------------------------------------------------------------ sound
    //
    // Borrowed from the Ravager rather than the Cow. A Grox is a two-tonne armoured reptile, and
    // the cattle sounds would undo everything the model and the stat block say about it. No new
    // audio asset is spent on a Phase E animal; ambience is Phase F's budget.

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.RAVAGER_STEP, 0.15F, 1.0F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (!state.isMoving()) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(
                    isAlarmed() || this.getTarget() != null ? RUN : WALK);
        }));

        // Um controlador so para tudo o que e disparado. Antes eram dois ("attack" e "graze"), e
        // cada habilidade nova exigiria mais um: com um nome de gatilho por animacao, adicionar
        // habilidade deixou de mexer na lista de controladores.
        controllers.add(new AnimationController<>(this, "ability", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"))
                .triggerableAnim("attack_gore", RawAnimation.begin().thenPlay("attack_gore"))
                .triggerableAnim("charge", RawAnimation.begin().thenPlay("charge"))
                .triggerableAnim("threat_display", RawAnimation.begin().thenPlay("threat_display"))
                .triggerableAnim("graze", RawAnimation.begin().thenPlay("graze")));
    }

    /**
     * A Grox never turns on another Grox.
     *
     * <p>Not redundant with the calf defence, which already excludes its own kind: a shove in a
     * crowded pen can still register as a hit, and a herd that stampedes into a brawl over it would
     * be the sort of emergent nonsense nobody asked for.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof GroxEntity) && super.canAttack(target);
    }
}
