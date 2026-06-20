package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * Chainsword — Adeptus Astartes close-combat weapon. A heavy, slow-swinging sword whose revving
 * teeth tear through armour and flesh; the shredding is represented as a short burst of fire on the
 * struck target. Built on vanilla {@link SwordItem} like the Guardsman Combat Knife.
 */
public class ChainswordItem extends SwordItem {
    private static final int SHRED_FIRE_SECONDS = 3;

    public ChainswordItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.setSecondsOnFire(SHRED_FIRE_SECONDS);
        return super.hurtEnemy(stack, target, attacker);
    }
}
