package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
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

public class OrkBoyEntity extends Monster implements GeoEntity {
    // Animation names below must match the keys inside assets/firstcrusade/animations/ork_boy.animation.json.
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public OrkBoyEntity(EntityType<? extends OrkBoyEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(ExampleMod.CHOPPA.get()));
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
this.targetSelector.addGoal(2, new FirstCrusadeNearestEnemyTargetGoal(this));
    }

    @Override
    public void die(DamageSource damageSource) {
        Entity attacker = damageSource.getEntity();

        if (!this.level().isClientSide && attacker instanceof GuardsmanEntity guardsman) {
            guardsman.recordOrkKill(1);
        }

        super.die(damageSource);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide) {
            // Fire the chop animation on every client tracking this Ork.
            this.triggerAnim("attack", "attack");
        }
        return hit;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
public boolean canAttack(LivingEntity target) {
    if (!FirstCrusadeFactionManager.canAttack(this, target)) {
        return false;
    }

    return super.canAttack(target);
}

@Override
public void setTarget(@Nullable LivingEntity target) {
    if (target != null && !FirstCrusadeFactionManager.canAttack(this, target)) {
        super.setTarget(null);
        return;
    }

    super.setTarget(target);
}

    // =========================
    // GeckoLib
    // =========================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Movement: walk while moving, idle otherwise.
        controllers.add(new AnimationController<>(this, "movement", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));

        // Attack: a one-shot, triggered from doHurtTarget above.
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
