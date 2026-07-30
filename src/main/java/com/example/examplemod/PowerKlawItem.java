package com.example.examplemod;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/** Power Klaw with a short field-charge sound followed by a heavy crushing impact. */
public class PowerKlawItem extends SwordItem {
    public PowerKlawItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (!entity.level().isClientSide) {
            entity.level().playSound(null, entity.blockPosition(), FCWeaponSounds.POWER_KLAW_CHARGE.get(),
                    soundSource(entity), 0.82F, 0.78F + entity.getRandom().nextFloat() * 0.08F);
        }
        return false;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            attacker.level().playSound(null, target.blockPosition(), FCWeaponSounds.POWER_KLAW_CRUSH.get(),
                    soundSource(attacker), 1.1F, 0.70F + attacker.getRandom().nextFloat() * 0.08F);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    private static SoundSource soundSource(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
    }
}
