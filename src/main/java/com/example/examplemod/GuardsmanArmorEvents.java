package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class GuardsmanArmorEvents {
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;

        if (player.level().isClientSide) {
            return;
        }

        if (player.tickCount % 40 != 0) {
            return;
        }

        if (isWearingFullGuardsmanSet(player)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    60,
                    0,
                    false,
                    false,
                    true
            ));

            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    60,
                    0,
                    false,
                    false,
                    true
            ));
        }
    }

    private static boolean isWearingFullGuardsmanSet(Player player) {
        ItemStack boots = player.getInventory().armor.get(0);
        ItemStack leggings = player.getInventory().armor.get(1);
        ItemStack chestplate = player.getInventory().armor.get(2);
        ItemStack helmet = player.getInventory().armor.get(3);

        return helmet.is(FCRegistry.GUARDSMAN_HELMET.get())
                && chestplate.is(FCRegistry.GUARDSMAN_CHESTPLATE.get())
                && leggings.is(FCRegistry.GUARDSMAN_LEGGINGS.get())
                && boots.is(FCRegistry.GUARDSMAN_BOOTS.get());
    }
}