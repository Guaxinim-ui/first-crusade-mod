package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The city's field commander (Lord Commander). One per Imperial settlement, granted — never
 * recruited — once the city reaches the Fortified Settlement age. He is the physical leader the
 * {@link CityMilitaryManager} builds attack squads around: troops march in formation behind him
 * and he walks at the front of every expedition. If he dies the settlement loses its ability to
 * launch attacks until a replacement rises (respawn cooldown handled by the military manager),
 * and the marching squad falls back home.
 *
 * Strong melee fighter with a Chainsword, but his real value is command, not stats.
 */
public class CityCommanderEntity extends AbstractImperialTroopEntity {
    public CityCommanderEntity(EntityType<? extends CityCommanderEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.CHAINSWORD.get()));
        this.setCustomName(Component.literal("Lord Commander"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 16.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerCombatGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, true));
    }

    // The commander is granted by the military manager, not trained in a Barracks, so his death
    // must not free a recruit slot in the Core's tally.
    @Override
    protected boolean countsTowardGarrisonTally() {
        return false;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide
                && this.level() instanceof ServerLevel serverLevel
                && this.getCommandCorePos() != null) {
            StrategicWarAIData data = StrategicWarAIData.get(serverLevel);
            StrategicSettlementRecord record = data.getImperial(this.getCommandCorePos());

            if (record != null) {
                record.onCommanderDied(serverLevel.getGameTime());
                data.setDirty();
            }
        }

        super.die(damageSource);
    }
}
