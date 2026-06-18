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
 * Penal Legionnaire — themed troop of Penal Colonies. A standalone melee charger: a reckless,
 * lightly-equipped convict thrown at the enemy. Fast and aggressive but fragile and cheap — the
 * glass-cannon swarm that fits the Penal Colony's identity (numerous, disposable). Contrasts the
 * balanced Enforcer and the tanky Mine Guard. Shared troop behaviour lives in
 * {@link AbstractImperialTroopEntity}.
 */
public class PenalLegionnaireEntity extends AbstractImperialTroopEntity {
    public PenalLegionnaireEntity(EntityType<? extends PenalLegionnaireEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.GUARDSMAN_COMBAT_KNIFE.get()));
        this.setCustomName(Component.literal("Penal Legionnaire"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.37D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35D, true));
    }
}
