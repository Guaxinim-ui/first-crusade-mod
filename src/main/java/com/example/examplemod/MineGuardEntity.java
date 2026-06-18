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
 * Mine Guard — themed troop of Mining cities (Industrial Enforcers / mine security). A standalone
 * melee bruiser: slow but very tough and hard-hitting, swinging an industrial blade. Contrasts the
 * fast, light Hive Enforcer with raw staying power, matching the Mining regiment's rugged identity.
 * Shared troop behaviour lives in {@link AbstractImperialTroopEntity}.
 */
public class MineGuardEntity extends AbstractImperialTroopEntity {
    public MineGuardEntity(EntityType<? extends MineGuardEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.GUARDSMAN_COMBAT_KNIFE.get()));
        this.setCustomName(Component.literal("Mine Guard"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 42.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
    }
}
