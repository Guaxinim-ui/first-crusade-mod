package com.example.examplemod;

import net.minecraft.network.chat.Component;
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
 * Sister of Battle — themed troop of Shrine cities (Adepta Sororitas). A standalone ranged zealot:
 * well-armoured and devout, laying down heavy bolter-grade fire (a hard-hitting, knockback-heavy
 * Lasgun shot). Shared troop behaviour lives in {@link AbstractImperialTroopEntity}.
 */
public class SisterOfBattleEntity extends AbstractImperialTroopEntity implements RangedAttackMob {
    public SisterOfBattleEntity(EntityType<? extends SisterOfBattleEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.LASGUN.get()));
        this.setCustomName(Component.literal("Sister of Battle"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 11.0D)
                .add(Attributes.FOLLOW_RANGE, 38.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new ImperialLasgunAttackGoal<>(this, 1.0D, 24, 21.0F));
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
        projectile.setBaseDamage(7.0D);
        projectile.setKnockback(1);
        projectile.setMicroExplosive(true);

        double xPower = target.getX() - this.getX();
        double yPower = target.getY(0.5D) - projectile.getY();
        double zPower = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(xPower * xPower + zPower * zPower);

        projectile.shoot(xPower, yPower + horizontalDistance * 0.05D, zPower, 3.7F, 0.45F);

        this.level().addFreshEntity(projectile);

        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                FCWeaponSounds.BOLTER_FIRE.get(),
                SoundSource.HOSTILE,
                0.6F,
                1.4F
        );
    }
}
