package com.example.examplemod.entity.projectile;

import com.example.examplemod.performance.graphics.FCServerParticles;
import com.example.examplemod.registry.ModCombatVehicleContent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SentinelMissileEntity extends Projectile implements ItemSupplier {
    private int life;

    public SentinelMissileEntity(EntityType<? extends SentinelMissileEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public SentinelMissileEntity(Level level, LivingEntity owner, Vec3 direction) {
        this(ModCombatVehicleContent.SENTINEL_MISSILE.get(), level);
        this.setOwner(owner);
        this.setDeltaMovement(direction.normalize().scale(1.65D));
    }

    @Override protected void defineSynchedData() {}

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof Monster && !entity.is(this.getOwner()) &&
                (this.getOwner() == null || !this.getOwner().isAlliedTo(entity));
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) this.detonate();
        if (this.isRemoved()) return;

        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        if (this.level() instanceof ServerLevel serverLevel) {
            // Every tick of the flight, so this is the one smoke source in the mod that is a rate
            // rather than an event — the reason the SMOKE channel is worth having a dial at all.
            FCServerParticles.send(serverLevel, ParticleTypes.SMOKE, FCServerParticles.Channel.SMOKE,
                    this.getX(), this.getY(), this.getZ(), 2, 0.05D, 0.05D, 0.05D, 0.01D);
        }
        if (++this.life > 100 || !this.level().hasChunkAt(this.blockPosition())) this.discard();
    }

    private void detonate() {
        if (this.level().isClientSide || this.isRemoved()) return;
        Entity owner = this.getOwner();
        AABB area = this.getBoundingBox().inflate(3.8D);
        List<LivingEntity> victims = this.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity instanceof Monster && entity.isAlive() && (owner == null || !owner.isAlliedTo(entity)));
        for (LivingEntity victim : victims) {
            double distance = Math.sqrt(victim.distanceToSqr(this));
            float damage = (float)Math.max(5.0D, 20.0D - distance * 3.0D);
            if (owner instanceof LivingEntity attacker) victim.hurt(this.damageSources().mobProjectile(this, attacker), damage);
            else victim.hurt(this.damageSources().explosion(this, null), damage);
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            FCServerParticles.send(serverLevel, ParticleTypes.EXPLOSION,
                    FCServerParticles.Channel.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(), 4, 0.5D, 0.5D, 0.5D, 0.05D);
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.9F);
        this.discard();
    }

    @Override protected void onHit(net.minecraft.world.phys.HitResult result) { this.detonate(); }
    @Override public boolean isPickable() { return false; }
    @Override public @NotNull ItemStack getItem() { return new ItemStack(Items.FIREWORK_ROCKET); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
