package com.example.examplemod.necron;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The one who gives the orders, and the reason the tomb woke at all.
 *
 * <h2>He repairs his own</h2>
 *
 * An Overlord's presence is not extra damage — it is that the Warriors around him stop staying
 * dead. Rather than invent a second mechanic, he simply resets their reanimation count, so a
 * phalanx fought next to him grinds down far slower than the same phalanx fought without him. The
 * play that follows from that is the one the fiction wants: kill the crown first.
 *
 * <p>Ticked on the same 40-tick cadence the rest of the mod uses for area effects, not every tick.
 */
public class NecronOverlordEntity extends Monster implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    /** How far his protocols reach. */
    private static final double COMMAND_RADIUS = 16.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NecronOverlordEntity(EntityType<? extends NecronOverlordEntity> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class,
                10, true, false, NecronTargets::isEnemyOfTheTomb));
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.level().isClientSide || this.tickCount % 40 != 0) {
            return;
        }

        // Reset, not heal: healing them would make him a second health bar, and a fight against a
        // health bar you cannot see is the least readable thing a boss can do. Resetting the count
        // is felt as "they got up again", which the player can see and act on.
        for (NecronWarriorEntity warrior : this.level().getEntitiesOfClass(NecronWarriorEntity.class,
                this.getBoundingBox().inflate(COMMAND_RADIUS))) {
            warrior.restoreReanimations();
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit) {
            triggerAnim("attack", "attack");
        }

        return hit;
    }

    /** The tomb notices when its Overlord falls. */
    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide) {
            this.level().players().forEach(player -> player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("msg.firstcrusade.necron.overlord_down")
                            .withStyle(net.minecraft.ChatFormatting.GREEN)));
        }

        super.die(cause);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state ->
                state.isMoving() ? state.setAndContinue(WALK) : state.setAndContinue(IDLE)));

        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
