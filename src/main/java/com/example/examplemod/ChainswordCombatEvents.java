package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps every chainsword-wielding player or mob on the same combat/motor rules. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChainswordCombatEvents {
    private ChainswordCombatEvents() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide || living instanceof SpaceMarineEntity) {
            // Space Marines use an inline Blockbench weapon and manage its motor/bone themselves.
            return;
        }

        boolean mobInCombat = living instanceof Mob mob
                && mob.getTarget() != null
                && mob.getTarget().isAlive();

        tickHand(living.getMainHandItem(), living, mobInCombat);
        if (living.getOffhandItem() != living.getMainHandItem()) {
            tickHand(living.getOffhandItem(), living, mobInCombat);
        }
    }

    private static void tickHand(ItemStack stack, LivingEntity living, boolean mobInCombat) {
        if (!(stack.getItem() instanceof ChainswordItem)) {
            return;
        }

        if (mobInCombat) {
            ChainswordItem.keepRunning(stack, living);
        }
        ChainswordItem.serverTick(stack, living);
    }
}
