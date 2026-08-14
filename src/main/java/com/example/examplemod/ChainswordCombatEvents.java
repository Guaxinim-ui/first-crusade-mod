package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps every chainsword-wielding player or mob on the same combat/motor rules.
 *
 * <h2>Why the order of the checks matters here</h2>
 *
 * This handler fires for <b>every living entity in the world, every tick</b> — every cow, every
 * zombie, and every one of the three hundred Guardsmen in a battle. Almost none of them are holding
 * a chainsword. So the only question worth asking first is "is this a chainsword?", and everything
 * else has to wait behind that answer.
 *
 * <p>It used to compute {@code mobInCombat} — an instanceof, a {@code getTarget()} and an
 * {@code isAlive()} — for every mob in the world before looking at what it was holding, then throw
 * the answer away inside {@link #tickHand} for the 99% that hold no chainsword. Each piece is cheap;
 * multiplied by the entity count of a large battle and by twenty ticks a second, it stopped being
 * free. This is the same inversion already applied to {@code FirstCrusadeForgeEvents.onLivingTick},
 * for the same reason.
 */
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

        ItemStack mainHand = living.getMainHandItem();
        ItemStack offHand = living.getOffhandItem();

        boolean mainIsChainsword = mainHand.getItem() instanceof ChainswordItem;
        // Same guard the old code used: an entity whose offhand stack is the very same object as its
        // main hand must not be ticked twice.
        boolean offIsChainsword = offHand != mainHand && offHand.getItem() instanceof ChainswordItem;

        if (!mainIsChainsword && !offIsChainsword) {
            return;
        }

        boolean mobInCombat = false;
        if (living instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            mobInCombat = target != null && target.isAlive();
        }

        if (mainIsChainsword) {
            tickHand(mainHand, living, mobInCombat);
        }
        if (offIsChainsword) {
            tickHand(offHand, living, mobInCombat);
        }
    }

    /** Ticks one chainsword. Callers have already established that the stack is one. */
    private static void tickHand(ItemStack stack, LivingEntity living, boolean mobInCombat) {
        if (mobInCombat) {
            ChainswordItem.keepRunning(stack, living);
        }
        ChainswordItem.serverTick(stack, living);
    }
}
