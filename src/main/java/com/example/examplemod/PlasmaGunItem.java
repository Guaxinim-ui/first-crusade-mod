package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Plasma Gun — Imperial energy weapon sitting between the Lasgun and the Bolter. It fires a
 * super-heated bolt that sets its target ablaze (the plasma burn), hits hard, but charges slowly
 * (long cooldown) and drains a Lasgun Power Cell per shot. Reuses {@link LasgunShotEntity}, made to
 * ignite what it strikes.
 */
public class PlasmaGunItem extends Item {
    private static final int FIRE_COOLDOWN_TICKS = 28;
    private static final double PLASMA_DAMAGE = 10.0D;

    public PlasmaGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack plasmaGun = player.getItemInHand(hand);
        boolean isCreative = player.getAbilities().instabuild;

        if (!isCreative && !consumePowerCellCharge(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("msg.firstcrusade.lasgun.no_charge"), true);
            }

            return InteractionResultHolder.fail(plasmaGun);
        }

        player.getCooldowns().addCooldown(this, FIRE_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS,
                0.7F,
                1.3F
        );

        if (!level.isClientSide) {
            LasgunShotEntity projectile = new LasgunShotEntity(level, player);
            projectile.setBaseDamage(PLASMA_DAMAGE);
            projectile.setKnockback(0);
            projectile.setSecondsOnFire(100);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.8F, 0.3F);

            level.addFreshEntity(projectile);

            if (!isCreative) {
                plasmaGun.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(plasmaGun, level.isClientSide);
    }

    private boolean consumePowerCellCharge(Player player) {
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack itemStack = player.getInventory().items.get(slot);

            if (itemStack.is(ExampleMod.LASGUN_POWER_CELL.get())) {
                int currentDamage = itemStack.getDamageValue();
                int maxDamage = itemStack.getMaxDamage();

                if (maxDamage <= 0) {
                    itemStack.shrink(1);
                    player.getInventory().setChanged();
                    return true;
                }

                if (currentDamage >= maxDamage - 1) {
                    itemStack.shrink(1);
                } else {
                    itemStack.setDamageValue(currentDamage + 1);
                }

                player.getInventory().setChanged();
                return true;
            }
        }

        return false;
    }
}
