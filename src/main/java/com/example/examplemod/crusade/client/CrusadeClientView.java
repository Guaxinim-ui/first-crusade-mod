package com.example.examplemod.crusade.client;

import java.util.List;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.crusade.CrusadePanelPacket;
import com.example.examplemod.crusade.CrusadePanelRequestPacket;
import com.example.examplemod.crusade.ImperialRegimentType;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What this client knows about a base's people — one base at a time, and only while it is looking.
 *
 * <h2>One slot, not a cache</h2>
 *
 * A player reads one Command Core at a time, so this holds exactly one answer. Keeping a map of
 * every base ever opened would be a cache that goes stale the moment a soldier dies somewhere else,
 * and stale is worse than empty here: an empty panel says "asking", a stale one says something
 * false with confidence.
 *
 * <h2>Asking is rate-limited</h2>
 *
 * The screen re-renders sixty times a second and would happily ask sixty times a second. The
 * request is therefore gated on the position actually changing, plus a refresh interval — so
 * flipping to the Garrison tab costs one packet and staying there costs one every few seconds.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CrusadeClientView {
    private CrusadeClientView() {
    }

    /** Client ticks between refreshes while a panel stays open. */
    private static final long REFRESH_TICKS = 40L;

    private static CrusadePanelPacket panel;
    private static BlockPos pending;
    private static long lastRequestTick;

    /** Called by the packet handler. */
    public static void accept(CrusadePanelPacket packet) {
        panel = packet;
    }

    /**
     * The panel for this base, asking for it if this client has not got it yet.
     *
     * <p>Returns {@code null} until the answer arrives, which the screen draws as "no data" rather
     * than as an empty garrison — a base with no soldiers and a base that has not answered yet are
     * different things and must not look the same.
     */
    public static CrusadePanelPacket panelFor(BlockPos corePos, long gameTime) {
        if (corePos == null) {
            return null;
        }

        boolean haveIt = panel != null && panel.corePos().equals(corePos);
        boolean stale = gameTime - lastRequestTick > REFRESH_TICKS;

        if ((!haveIt && !corePos.equals(pending)) || stale) {
            pending = corePos;
            lastRequestTick = gameTime;
            FirstCrusadeNetwork.CHANNEL.sendToServer(new CrusadePanelRequestPacket(corePos));
        }

        return haveIt ? panel : null;
    }

    public static List<CrusadePanelPacket.Row> roster() {
        return panel == null ? List.of() : panel.roster();
    }

    public static List<CrusadePanelPacket.Row> fallen() {
        return panel == null ? List.of() : panel.fallen();
    }

    public static ImperialRegimentType regiment() {
        return panel == null ? ImperialRegimentType.CRUSADE_GENERIC : panel.regiment();
    }

    /**
     * Leaving a world drops the panel.
     *
     * <p>Without this the next world opens its first Command Core showing the last world's dead.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        panel = null;
        pending = null;
        lastRequestTick = 0L;
    }
}
