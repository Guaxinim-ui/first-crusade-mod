package com.example.examplemod;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/** Ork choppa with animation-synchronised swing and impact sounds. */
public class ChoppaItem extends SwordItem {
    public ChoppaItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (!entity.level().isClientSide) {
            entity.level().playSound(null, entity.blockPosition(), FCWeaponSounds.CHOPPA_SWING.get(),
                    soundSource(entity), 0.75F, 0.86F + entity.getRandom().nextFloat() * 0.12F);
        }
        return false;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            attacker.level().playSound(null, target.blockPosition(), FCWeaponSounds.CHOPPA_HIT.get(),
                    soundSource(attacker), 0.95F, 0.82F + attacker.getRandom().nextFloat() * 0.10F);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    private static SoundSource soundSource(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
    }
}
