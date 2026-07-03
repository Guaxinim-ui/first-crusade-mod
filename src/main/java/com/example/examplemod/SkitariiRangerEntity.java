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
 * Skitarii Ranger — themed troop of Forge cities (Adeptus Mechanicus). A standalone ranged trooper:
 * tougher and far better armoured than a Guardsman thanks to augmetics, fighting with the same
 * Lasgun shot. Shared troop behaviour lives in {@link AbstractImperialTroopEntity}.
 */
public class SkitariiRangerEntity extends AbstractImperialTroopEntity implements RangedAttackMob {
    public SkitariiRangerEntity(EntityType<? extends SkitariiRangerEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.LASGUN.get()));
        this.setCustomName(Component.literal("Skitarii Ranger"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 36.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0D, 30, 20.0F));
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
        projectile.setBaseDamage(6.0D);
        projectile.setKnockback(0);

        double xPower = target.getX() - this.getX();
        double yPower = target.getY(0.5D) - projectile.getY();
        double zPower = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(xPower * xPower + zPower * zPower);

        projectile.shoot(xPower, yPower + horizontalDistance * 0.05D, zPower, 3.6F, 0.5F);

        this.level().addFreshEntity(projectile);

        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                0.55F,
                1.7F
        );
    }
}
