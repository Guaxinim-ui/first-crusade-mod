package com.example.examplemod.progression.ork;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;
import com.example.examplemod.WaaaghOverlordManager;
import com.example.examplemod.progression.PlayerProgressionManager;
import com.example.examplemod.progression.PlayerProgressionNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The one thing an Ork can do to the world that the WAAAGH counts: pull down an Imperial Core.
 *
 * <h2>Why this had to exist</h2>
 *
 * {@code countCoreDestroyed()} and {@code countMajorVictory()} were on the profile with no caller at
 * all, and the Warboss gate asks for two major victories — so Warboss was unreachable by any means
 * except an admin command. Killing things could never satisfy it, because a major victory is
 * deliberately not a kill: it is the moment a city stops existing.
 *
 * <h2>What it is worth, and what it is not</h2>
 *
 * A destroyed Core pays Krumpagem, Teef, a core tally and a major victory, and it is the only player
 * action in this system that touches the <b>global</b> WAAAGH. A kill never does, and a blow
 * certainly never does: the global tide is a readout of how the war is going, and wiring it to
 * combat would turn it into a readout of how long somebody has been grinding.
 *
 * <h2>Not counted twice, and not counted at all when it did not happen</h2>
 *
 * {@code LOWEST} priority and an explicit cancellation check, so a break another mod or a claim
 * protection refused never pays. Creative mode pays nothing either — a player who can place a Core
 * and break it again is a player who could hand himself the Warboss gate in a minute.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOrkWorldEvents {
    private PlayerOrkWorldEvents() {
    }

    /**
     * {@code LOWEST} so every listener that might cancel the break has already had its say.
     *
     * <p>{@code receiveCanceled} is deliberately left off: a cancelled event does not reach this
     * method at all, which is the behaviour wanted, and the explicit check below covers the case of
     * a listener at the same priority cancelling first.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }

        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !PlayerOrkProgressionRequirements.isOrk(player)) {
            return;
        }

        if (player.isCreative()) {
            return;
        }

        if (!event.getState().is(FCRegistry.IMPERIAL_COMMAND_CORE.get())) {
            return;
        }

        coreDestroyed(player);
    }

    /**
     * The payout for bringing a city down.
     *
     * <p>Written as one method with one commit at the end: five separate awards each ending in their
     * own save-and-sync would be five packets for one event, and a half-applied victory if any of
     * them threw.
     */
    private static void coreDestroyed(ServerPlayer player) {
        PlayerOrkProgressionProfile ork = PlayerOrkProgressionManager.profile(player);

        ork.addKrump(PlayerOrkProgressionBalance.KRUMP_CORE_DESTROYED);
        ork.addKrump(PlayerOrkProgressionBalance.KRUMP_MAJOR_VICTORY);
        ork.countCoreDestroyed();
        ork.countMajorVictory();

        // Through the same scaler every other Teef award uses, so TEEF IZ MONEY is worth the same
        // here as it is at the camp.
        int teef = PlayerOrkRewardModifiers.scaleTeef(ork,
                PlayerOrkProgressionBalance.TEEF_CORE_DESTROYED);
        ork.addTeef(teef);

        ServerLevel level = player.serverLevel();

        // The global tide, through the manager that already owns it — not a second SavedData, and
        // not WaaaghOverlordData reached into directly from here.
        WaaaghOverlordManager.contributeFromGreenskinVictory(level,
                PlayerOrkProgressionBalance.GLOBAL_WAAAGH_CORE_DESTROYED);

        PlayerProgressionManager.data(level).markChanged();
        PlayerProgressionNetwork.sync(player);

        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 1.6F, 0.5F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 1.0D, player.getZ(), 40, 1.2D, 1.0D, 1.2D, 0.05D);

        player.sendSystemMessage(Component.translatable("msg.firstcrusade.ork.core_krumped",
                PlayerOrkProgressionBalance.KRUMP_CORE_DESTROYED
                        + PlayerOrkProgressionBalance.KRUMP_MAJOR_VICTORY, teef)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }
}
