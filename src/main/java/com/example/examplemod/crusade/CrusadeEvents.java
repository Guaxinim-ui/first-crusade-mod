package com.example.examplemod.crusade;

import com.example.examplemod.AbstractImperialTroopEntity;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.GuardsmanEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The only thing that writes to a soldier's service record: death, and the deaths he causes.
 *
 * <h2>One event, both directions</h2>
 *
 * {@link LivingDeathEvent} already fires for every death in the world, so the Crusade needs no
 * listener of its own and no scan. When it fires, at most two things here are true: the corpse was
 * one of ours, and the killer was one of ours. Neither is true for the overwhelming majority of
 * deaths in a Minecraft world, so the handler's ordinary cost is two {@code instanceof} checks.
 *
 * <h2>Why the tallies are here and the merit is not</h2>
 *
 * Merit and rank already move through {@code GuardsmanEntity.recordOrkKill}, called from each Ork's
 * own {@code die()}. That path is left exactly as it was. This class writes the <i>record</i> — the
 * campaign's memory of the man — and nothing else, so the two can never double-count each other:
 * the entity owns what he can do, the record owns what he has done.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrusadeEvents {
    private CrusadeEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        // ---- the killer's tally -------------------------------------------------
        Entity killer = event.getSource().getEntity();
        if (killer instanceof LivingEntity soldier) {
            BlockPos home = homeOf(soldier);
            if (home != null) {
                ImperialSoldierCareerManager.recordKill(level, home, soldier, event.getEntity());
            }
        }

        // ---- and the fallen -----------------------------------------------------
        BlockPos ourHome = homeOf(event.getEntity());
        if (ourHome == null) {
            return;
        }

        ImperialSoldierRecord record = ImperialSoldierCareerManager.recordDeath(
                level, ourHome, event.getEntity().getUUID(), fateOf(event));

        if (record == null) {
            return;
        }

        announce(level, record);
    }

    /**
     * The Core a soldier belongs to, or {@code null} for anything that is not one of ours.
     *
     * <p>This is the whole membership test. A soldier with no Command Core — a spawn-egg trooper, a
     * unit from a destroyed base — has no roster, keeps no record, and costs this system nothing.
     */
    private static BlockPos homeOf(Entity entity) {
        if (entity instanceof GuardsmanEntity guardsman) {
            return guardsman.getCommandCorePos();
        }
        if (entity instanceof AbstractImperialTroopEntity troop) {
            return troop.getCommandCorePos();
        }

        return null;
    }

    /**
     * A short line for the roll of the dead.
     *
     * <p>Stored as a translation key rather than a sentence: the memorial is read in the player's
     * language, and a save written in one language should not be a save that is stuck in it.
     */
    private static String fateOf(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();

        if (killer == null) {
            return "death.firstcrusade.fate.unknown";
        }
        if (com.example.examplemod.progression.PlayerProgressionCombat.isWarboss(killer)) {
            return "death.firstcrusade.fate.warboss";
        }
        if (com.example.examplemod.progression.PlayerProgressionCombat.isEliteOrk(killer)) {
            return "death.firstcrusade.fate.elite";
        }
        if (com.example.examplemod.progression.PlayerProgressionCombat.isOrk(killer)) {
            return "death.firstcrusade.fate.orks";
        }

        return "death.firstcrusade.fate.unknown";
    }

    /**
     * Tells the nearby players a soldier is gone.
     *
     * <p>Only for soldiers who had made something of themselves. Announcing every recruit's death
     * would be chat spam during a raid and would cheapen the one line that should land — that the
     * veteran the player recognised is not coming back.
     */
    private static void announce(ServerLevel level, ImperialSoldierRecord record) {
        if (record.grade() == com.example.examplemod.ImperialTroopGrade.LINE
                && record.orkKills() < 5) {
            return;
        }

        Component message = Component.translatable("msg.firstcrusade.soldier.fallen",
                record.displayName(), record.orkKills()).withStyle(ChatFormatting.DARK_RED);

        // Only the players who could plausibly have known him. A Crusade-wide broadcast for one
        // Guardsman is the kind of noise that makes players stop reading chat.
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
