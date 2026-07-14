package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Kasrkin — elite themed troop of Fortress cities (Militarum Tempestus / Cadian Kasr). A standalone
 * ranged trooper: heavily armoured storm-trooper that hits harder and survives more than a Guardsman
 * or a Skitarii, firing a more powerful hotshot Lasgun shot. Shared troop behaviour lives in
 * {@link AbstractImperialTroopEntity}.
 */
public class KasrkinEntity extends AbstractImperialTroopEntity implements RangedAttackMob {
    public KasrkinEntity(EntityType<? extends KasrkinEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.LASGUN.get()));
        this.setCustomName(Component.literal("Kasrkin"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 44.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ATTACK_DAMAGE, 6.5D)
                .add(Attributes.ARMOR, 13.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new ImperialLasgunAttackGoal<>(this, 1.0D, 25, 22.0F));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide) {
            return;
        }

        // Hold fire and sidestep when an ally blocks the lane (the bolt also ignores allies).
        if (!FriendlyFireGuard.hasClearShot(this, target)) {
            FriendlyFireGuard.strafeForClearShot(this, target);
            return;
        }

        LasgunShotEntity projectile = new LasgunShotEntity(this.level(), this);
        projectile.setBaseDamage(8.0D);
        projectile.setKnockback(1);

        double xPower = target.getX() - this.getX();
        double yPower = target.getY(0.5D) - projectile.getY();
        double zPower = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(xPower * xPower + zPower * zPower);

        projectile.shoot(xPower, yPower + horizontalDistance * 0.05D, zPower, 3.9F, 0.4F);

        this.level().addFreshEntity(projectile);

        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                0.6F,
                1.5F
        );
    }
}
