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
 * Jungle Fighter — themed troop of Death World settlements (Catachan-style veterans). A standalone
 * ranged skirmisher: fast and hard-hitting veteran who harries with a Lasgun and survives more than
 * his light armour suggests — the elite hit-and-run shooter, distinct from the heavy Kasrkin and the
 * frail Agri Militia. Shared troop behaviour lives in {@link AbstractImperialTroopEntity}.
 */
public class JungleFighterEntity extends AbstractImperialTroopEntity implements RangedAttackMob {
    public JungleFighterEntity(EntityType<? extends JungleFighterEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.LASGUN.get()));
        this.setCustomName(Component.literal("Jungle Fighter"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 38.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new ImperialLasgunAttackGoal<>(this, 1.15D, 20, 20.0F));
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
        projectile.setBaseDamage(6.5D);
        projectile.setKnockback(0);

        double xPower = target.getX() - this.getX();
        double yPower = target.getY(0.5D) - projectile.getY();
        double zPower = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(xPower * xPower + zPower * zPower);

        projectile.shoot(xPower, yPower + horizontalDistance * 0.05D, zPower, 3.7F, 0.6F);

        this.level().addFreshEntity(projectile);

        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                0.55F,
                1.6F
        );
    }
}
