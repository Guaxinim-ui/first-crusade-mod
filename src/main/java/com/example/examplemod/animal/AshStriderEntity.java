package com.example.examplemod.animal;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
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
 * Ash strider: legs with an animal on top.
 *
 * <h2>Built for a landscape, not for a fight</h2>
 *
 * The ash wastes are flat, poisonous and empty, and the strider is the answer to all three at once:
 * it stands high enough to keep its body out of the dust, it eats what grows on the crust, and its
 * only defence is distance. Nothing about it is aggressive. It sees a player at a range no other
 * animal in the mod bothers with, and it leaves.
 *
 * <p>The tall silhouette is doing real work — it is the one shape in the fauna set that is
 * recognisable from far away across open ground, which is exactly where this animal lives. In a
 * biome the brief calls "the emptiest in the mod, on purpose", one distant shape on the horizon is
 * the difference between empty and dead.
 */
public class AshStriderEntity extends FCAnimalEntity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation GRAZE = RawAnimation.begin().thenPlay("graze");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AshStriderEntity(EntityType<? extends AshStriderEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Long-legged and quick over open ground, but light: it survives by not being caught, and a
     * strider that stood and fought would be a different animal.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AlarmedPanicGoal(this, 1.7D));
        // Sixteen blocks, where the other prey animals use eight. The whole species is a flight
        // distance.
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 16.0F, 1.4D, 1.7D));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, SquigEntity.class,
                10.0F, 1.4D, 1.7D));
        this.goalSelector.addGoal(4, new HerdGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new GrazeGoal(this, 500, 100));
        this.goalSelector.addGoal(6, new SeekWaterGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return FCAnimals.ASH_STRIDER.get().create(level);
    }

    /** The same pod the Grox eats — one feed crop for the Imperium's animals, not five. */
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(FCAnimals.groxFeed());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.STRIDER_HAPPY;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STRIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STRIDER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state ->
                state.isMoving() ? state.setAndContinue(WALK) : state.setAndContinue(IDLE)));

        controllers.add(new AnimationController<>(this, "graze", 0, state -> PlayState.STOP)
                .triggerableAnim("graze", GRAZE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
