package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Feudal Knight — themed troop of Feudal World settlements. A standalone melee wall: heavily plated
 * and immovable, shrugging off hits and knockback while holding the line, though slow and primitive
 * in reach. Its niche is armour + knockback resistance (a shield wall), distinct from the Mine
 * Guard's raw health tanking. Shared troop behaviour lives in {@link AbstractImperialTroopEntity}.
 */
public class FeudalKnightEntity extends AbstractImperialTroopEntity {
    public FeudalKnightEntity(EntityType<? extends FeudalKnightEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.GUARDSMAN_COMBAT_KNIFE.get()));
        this.setCustomName(Component.literal("Feudal Knight"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 16.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 30.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
    }
}
