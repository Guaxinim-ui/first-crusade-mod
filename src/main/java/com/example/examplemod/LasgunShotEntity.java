package com.example.examplemod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

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

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        Entity hitEntity = hitResult.getEntity();
        Entity shooter = this.getOwner();

        // Friendly fire protection:
        // Guardsmen shots do not damage other Guardsmen.
        if (shooter instanceof GuardsmanEntity && hitEntity instanceof GuardsmanEntity) {
            this.discard();
            return;
        }

        super.onHitEntity(hitResult);
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}