package com.example.examplemod.entity.vehicle;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class TankShellEntity extends Projectile implements ItemSupplier {
    private int life;

    public TankShellEntity(EntityType<? extends TankShellEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 motion = this.getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
        }

        if (!this.isRemoved()) {
            Vec3 next = this.position().add(motion);
            this.setPos(next.x, next.y, next.z);
            this.setDeltaMovement(motion.scale(0.995D).add(0.0D, -0.012D, 0.0D));
            this.updateRotation();
        }

        this.life++;
        if (this.life > 160) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity != this.getOwner();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);

        if (!this.level().isClientSide) {
            Entity target = result.getEntity();
            Entity owner = this.getOwner();

            if (owner instanceof LivingEntity livingOwner) {
                target.hurt(this.damageSources().mobProjectile(this, livingOwner), 28.0F);
            } else {
                target.hurt(this.damageSources().generic(), 28.0F);
            }

            this.detonate();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide && result.getType() != HitResult.Type.ENTITY) {
            this.detonate();
        }
    }

    private void detonate() {
        if (this.isRemoved()) {
            return;
        }

        this.level().explode(
                this,
                this.getX(),
                this.getY(),
                this.getZ(),
                3.4F,
                Level.ExplosionInteraction.MOB
        );
        this.discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
