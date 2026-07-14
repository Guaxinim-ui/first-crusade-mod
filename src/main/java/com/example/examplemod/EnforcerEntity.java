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
 * Enforcer — themed troop of Hive cities (Adeptus Arbites / hive enforcers). A standalone melee
 * brawler: riot-armoured law enforcer that wades in with a shock maul, contrasting the ranged
 * Guardsman/Skitarii/Kasrkin. Fits the Hive's identity of cheap but numerous troops (2x pop).
 * Shared troop behaviour lives in {@link AbstractImperialTroopEntity}.
 */
public class EnforcerEntity extends AbstractImperialTroopEntity {
    public EnforcerEntity(EntityType<? extends EnforcerEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.GUARDSMAN_COMMAND_BATON.get()));
        this.setCustomName(Component.literal("Enforcer"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
    }
}
