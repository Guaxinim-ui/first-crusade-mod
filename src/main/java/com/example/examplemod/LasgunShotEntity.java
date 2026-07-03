package com.example.examplemod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class LasgunShotEntity extends AbstractArrow {
    public LasgunShotEntity(EntityType<? extends LasgunShotEntity> entityType, Level level) {
        super(entityType, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    public LasgunShotEntity(Level level, LivingEntity shooter) {
        super(ExampleMod.LASGUN_SHOT.get(), shooter, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();

        this.setNoGravity(true);

        if (this.level().isClientSide) {
            this.level().addParticle(
                    ParticleTypes.ELECTRIC_SPARK,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    0.0D,
                    0.0D,
                    0.0D
            );

            this.level().addParticle(
                    ParticleTypes.END_ROD,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    0.0D,
                    0.0D,
                    0.0D
            );
        }

        if (this.tickCount > 40) {
            this.discard();
        }
    }

    // Friendly fire protection: a las-bolt passes CLEAN THROUGH every ally of its shooter's
    // faction (Guardsman, themed troops, Space Marines, citizens — and Ork shots through Orks)
    // and keeps flying until it reaches an actual enemy. The projectile therefore can never
    // damage a friendly body, no matter how bunched up the firing line is.
    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }

        Entity shooter = this.getOwner();

        if (shooter == null || !(target instanceof LivingEntity living)) {
            return true;
        }

        if (FirstCrusadeFactionManager.areAllies(shooter, living)) {
            return false;
        }

        // Citizens are faction-NEUTRAL (so nothing targets them), which areAllies treats as
        // "not allied" — shield them from Imperial guns explicitly.
        if (living instanceof ImperialCitizenEntity
                && FirstCrusadeFactionManager.getFaction(shooter) == FirstCrusadeFaction.IMPERIUM) {
            return false;
        }

        return true;
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}