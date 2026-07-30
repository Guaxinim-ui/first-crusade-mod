package com.example.examplemod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class LasgunShotEntity extends AbstractArrow {
    private static final double MICRO_EXPLOSION_RADIUS = 1.65D;
    private static final float MICRO_EXPLOSION_MAX_DAMAGE = 4.5F;

    private boolean microExplosive;

    public LasgunShotEntity(EntityType<? extends LasgunShotEntity> entityType, Level level) {
        super(entityType, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    public LasgunShotEntity(Level level, LivingEntity shooter) {
        super(FCRegistry.LASGUN_SHOT.get(), shooter, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    public void setMicroExplosive(boolean microExplosive) {
        this.microExplosive = microExplosive;
    }

    public boolean isMicroExplosive() {
        return this.microExplosive;
    }

    @Override
    public void tick() {
        super.tick();

        this.setNoGravity(true);

        if (this.level().isClientSide) {
            this.level().addParticle(
                    this.microExplosive ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK,
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
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (this.microExplosive && !this.level().isClientSide) {
            detonateMicroExplosion();
        }
    }

    /**
     * Bolter detonation: small anti-personnel splash only. This deliberately does not call
     * Level#explode, so it cannot break blocks, start fires or damage terrain.
     */
    private void detonateMicroExplosion() {
        Entity owner = this.getOwner();
        Vec3 center = this.position();
        AABB area = new AABB(center.x, center.y, center.z, center.x, center.y, center.z).inflate(MICRO_EXPLOSION_RADIUS);

        List<LivingEntity> victims = this.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity.isAlive() && entity != owner && distanceSquared(entity, center) <= MICRO_EXPLOSION_RADIUS * MICRO_EXPLOSION_RADIUS
        );

        for (LivingEntity victim : victims) {
            if (owner != null && FirstCrusadeFactionManager.areAllies(owner, victim)) {
                continue;
            }

            if (victim instanceof ImperialCitizenEntity
                    && owner != null
                    && FirstCrusadeFactionManager.getFaction(owner) == FirstCrusadeFaction.IMPERIUM) {
                continue;
            }

            double distance = Math.sqrt(distanceSquared(victim, center));
            float falloff = (float)Math.max(0.0D, 1.0D - distance / MICRO_EXPLOSION_RADIUS);
            float splashDamage = 1.0F + MICRO_EXPLOSION_MAX_DAMAGE * falloff;

            if (owner instanceof LivingEntity attacker) {
                victim.hurt(this.damageSources().mobProjectile(this, attacker), splashDamage);
            } else {
                victim.hurt(this.damageSources().explosion(this, null), splashDamage);
            }

            Vec3 push = victim.position().subtract(center);
            if (push.lengthSqr() > 1.0E-5D) {
                Vec3 normal = push.normalize().scale(0.22D * falloff);
                victim.push(normal.x, Math.max(0.05D, normal.y + 0.08D), normal.z);
            }
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                    2, 0.16D, 0.16D, 0.16D, 0.015D);
            serverLevel.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z,
                    7, 0.28D, 0.28D, 0.28D, 0.025D);
            serverLevel.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z,
                    5, 0.22D, 0.22D, 0.22D, 0.03D);
        }

        this.level().playSound(null, this.blockPosition(), FCWeaponSounds.BOLTER_IMPACT.get(),
                SoundSource.HOSTILE, 0.75F, 1.08F + this.random.nextFloat() * 0.10F);
        this.discard();
    }


    private static double distanceSquared(Entity entity, Vec3 point) {
        double dx = entity.getX() - point.x;
        double dy = entity.getY() - point.y;
        double dz = entity.getZ() - point.z;
        return dx * dx + dy * dy + dz * dz;
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("MicroExplosive", this.microExplosive);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.microExplosive = tag.getBoolean("MicroExplosive");
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}
