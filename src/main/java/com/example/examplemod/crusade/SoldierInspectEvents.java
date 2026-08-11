package com.example.examplemod.crusade;

import com.example.examplemod.AbstractImperialTroopEntity;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.GuardsmanEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Right-click a soldier and he tells you who he is.
 *
 * <h2>Chat, not a screen</h2>
 *
 * The brief is explicit that this does not need a GUI, and it should not have one. A menu costs a
 * container, a packet pair and a screen class, and it interrupts the player to show six numbers. Four
 * lines on the chat log do the same job, cost one message, and can be read while the shooting
 * continues.
 *
 * <h2>Cost</h2>
 *
 * A player interaction is already an event, and this handler leaves immediately for anything that is
 * not one of the mod's soldiers. Nothing polls, nothing scans, and a garrison nobody clicks on is a
 * garrison this class never runs for.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoldierInspectEvents {
    private SoldierInspectEvents() {
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos home = homeOf(event.getTarget());
        if (home == null) {
            return;
        }

        ImperialSoldierRecord record = ImperialSoldierCareerManager.record(
                level, home, event.getTarget().getUUID());

        if (record == null) {
            return;
        }

        report(player, record, home, level);

        // Consume the click so an empty hand does not also swing at the man being inspected, and so
        // nothing else claims the interaction. SUCCESS rather than CONSUME keeps the arm swing,
        // which is the feedback that the click registered.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static BlockPos homeOf(Entity entity) {
        if (entity instanceof GuardsmanEntity guardsman) {
            return guardsman.getCommandCorePos();
        }
        if (entity instanceof AbstractImperialTroopEntity troop) {
            return troop.getCommandCorePos();
        }

        return null;
    }

    private static void report(ServerPlayer player, ImperialSoldierRecord record, BlockPos home,
                               ServerLevel level) {
        ImperialSoldierRoster roster = ImperialCrusadeData.get(level).peek(home);
        long days = record.serviceTicks(level.getGameTime()) / 24000L;

        player.sendSystemMessage(record.displayName().copy()
                .withStyle(record.regiment().colour(), ChatFormatting.BOLD));

        player.sendSystemMessage(Component.empty()
                .append(record.regiment().displayName())
                .append(Component.literal("  ·  " + days + "d in service")
                        .withStyle(ChatFormatting.DARK_GRAY)));

        player.sendSystemMessage(Component.literal(
                "Orks " + record.orkKills()
                        + "   Elites " + record.eliteKills()
                        + "   Raids " + record.raidsJoined() + "/" + record.raidsWon())
                .withStyle(ChatFormatting.GRAY));

        if (roster != null) {
            player.sendSystemMessage(Component.literal(
                    "Garrison " + roster.servingCount() + "   Fallen " + roster.totalFallen()
                            + "   Home " + home.toShortString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
