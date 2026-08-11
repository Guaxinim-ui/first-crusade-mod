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

public class LasgunItem extends Item {
    public LasgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack lasgun = player.getItemInHand(hand);
        boolean isCreative = player.getAbilities().instabuild;

        if (!isCreative && !consumePowerCellCharge(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("msg.firstcrusade.lasgun.no_charge"), true);
            }

            return InteractionResultHolder.fail(lasgun);
        }

        // Fire Discipline shortens this; the helper is the only thing that knows by how much.
        player.getCooldowns().addCooldown(this,
                com.example.examplemod.progression.PlayerProgressionCombat.cooldownTicks(player, 16));
        player.awardStat(Stats.ITEM_USED.get(this));

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS,
                0.55F,
                1.9F
        );

        if (!level.isClientSide) {
            LasgunShotEntity projectile = new LasgunShotEntity(level, player);
            projectile.setBaseDamage(7.0D);
            projectile.setKnockback(0);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 4.2F, 0.2F);

            level.addFreshEntity(projectile);

            if (!isCreative) {
                lasgun.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(lasgun, level.isClientSide);
    }

    private boolean consumePowerCellCharge(Player player) {
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack itemStack = player.getInventory().items.get(slot);

            if (itemStack.is(FCRegistry.LASGUN_POWER_CELL.get())) {
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