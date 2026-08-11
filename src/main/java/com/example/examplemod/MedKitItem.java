package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class MedKitItem extends Item {
    public MedKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack medKit = player.getItemInHand(hand);
        boolean isCreative = player.getAbilities().instabuild;

        if (player.getHealth() >= player.getMaxHealth()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("You are already at full health."), true);
            }

            return InteractionResultHolder.fail(medKit);
        }

        player.getCooldowns().addCooldown(this, 200);
        player.awardStat(Stats.ITEM_USED.get(this));

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.HONEY_DRINK,
                SoundSource.PLAYERS,
                0.8F,
                1.0F
        );

        if (!level.isClientSide) {
            // Field Recovery raises this; the helper is the only thing that knows by how much.
            player.heal(com.example.examplemod.progression.PlayerProgressionCombat
                    .healAmount(player, 6.0F));

            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    100,
                    0,
                    false,
                    false,
                    true
            ));

            if (!isCreative) {
                medKit.shrink(1);
            }
        }

        return InteractionResultHolder.sidedSuccess(medKit, level.isClientSide);
    }
}