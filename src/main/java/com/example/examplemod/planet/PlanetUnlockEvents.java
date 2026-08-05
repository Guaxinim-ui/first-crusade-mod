package com.example.examplemod.planet;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;
import com.example.examplemod.MeganobEntity;
import com.example.examplemod.OrkNobEntity;
import com.example.examplemod.WarbossEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where unlocks actually come from, and where the launch countdown is advanced.
 *
 * <h2>Real triggers, not placeholders</h2>
 *
 * {@link PlanetUnlockRequirement} declares what a destination asks for; this class is the half that
 * notices it happened. Only triggers the mod can genuinely observe today are wired here:
 *
 * <ul>
 *   <li><b>Ork champion slain</b> — a Nob, Meganob or Warboss dying to a player. Opens Armageddon
 *       and is half of what the Ork world asks for.</li>
 *   <li><b>Crusadium forged</b> — crafting the plate, which is the mod's marker for advanced
 *       industry. Opens the forge world.</li>
 *   <li><b>First launch</b> — fired from {@link PlanetTravelManager} on the first completed trip.</li>
 * </ul>
 *
 * <p>The remaining requirements in the table use {@code TRIGGER_CAMPAIGN}, which nothing fires yet.
 * That is deliberate and visible: those destinations stay locked, the tooltip says exactly what is
 * missing, and an operator can open them with {@code /firstcrusade planet unlock}. A requirement
 * that silently unlocked itself would be worse than one that honestly waits.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class PlanetUnlockEvents {
    private PlanetUnlockEvents() {
    }

    /**
     * Advances every launch in progress.
     *
     * <p>On the END phase only — running on both phases would double the countdown speed, which is
     * the sort of bug that looks like "the timer feels wrong" and takes an hour to find.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PlanetTravelManager.tick(event.getServer());
        }
    }

    /** An Ork champion falling to a player is the mod's "you have fought the greenskins" mark. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity killed = event.getEntity();

        if (killed.level().isClientSide || !isOrkChampion(killed)) {
            return;
        }

        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer player) {
            PlanetTravelManager.grant(player, PlanetUnlockRequirement.TRIGGER_ORK_CHAMPION_SLAIN);
        }
    }

    private static boolean isOrkChampion(LivingEntity entity) {
        return entity instanceof OrkNobEntity
                || entity instanceof MeganobEntity
                || entity instanceof WarbossEntity;
    }

    /** Crafting Crusadium Plate is the mod's existing gate for "advanced industry exists". */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getCrafting().is(FCRegistry.CRUSADIUM_PLATE.get())) {
            PlanetTravelManager.grant(player, PlanetUnlockRequirement.TRIGGER_CRUSADIUM_FORGED);
        }
    }
}
