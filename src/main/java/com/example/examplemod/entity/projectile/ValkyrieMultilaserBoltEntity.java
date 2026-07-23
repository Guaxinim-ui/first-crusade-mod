package com.example.examplemod.entity.projectile;

import com.example.examplemod.registry.ModCombatVehicleContent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

public final class ValkyrieMultilaserBoltEntity extends Projectile implements ItemSupplier {
    private int life;

    public ValkyrieMultilaserBoltEntity(EntityType<? extends ValkyrieMultilaserBoltEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public ValkyrieMultilaserBoltEntity(Level level, LivingEntity owner, Vec3 direction) {
        this(ModCombatVehicleContent.VALKYRIE_MULTILASER_BOLT.get(), level);
        this.setOwner(owner);
        this.setDeltaMovement(direction.normalize().scale(3.2D));
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
        if (hit.getType() != HitResult.Type.MISS) this.onHit(hit);
        if (this.isRemoved()) return;
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        if (++this.life > 80 || !this.level().hasChunkAt(this.blockPosition())) this.discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity owner = this.getOwner();
        LivingEntity attacker = owner instanceof LivingEntity living ? living : null;
        result.getEntity().hurt(attacker != null ? this.damageSources().mobProjectile(this, attacker) : this.damageSources().magic(), 12.0F);
        result.getEntity().invulnerableTime = 0;
        this.discard();
    }

    @Override protected void onHitBlock(net.minecraft.world.phys.BlockHitResult result) { this.discard(); }
    @Override public boolean isPickable() { return false; }
    @Override public @NotNull ItemStack getItem() { return new ItemStack(Items.REDSTONE_TORCH); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
