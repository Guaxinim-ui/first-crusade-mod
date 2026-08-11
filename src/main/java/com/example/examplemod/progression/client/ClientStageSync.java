package com.example.examplemod.progression.client;

import java.util.UUID;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.progression.PlayerBody;
import com.example.examplemod.progression.PlayerProgressionClientView;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client end of the stage broadcast: where a stage the server sent becomes a body on screen.
 *
 * <h2>Remembering is not enough</h2>
 *
 * Writing the stage into {@link PlayerProgressionClientView} only changes the answer the size event
 * will give the <i>next</i> time something asks. An entity caches the box it last computed, so a
 * player who is told they grew keeps their old hitbox until something happens to invalidate it —
 * which on a client may be never. {@code refreshDimensions()} is that invalidation, and it is the
 * whole reason this class exists instead of a one-line call to {@code putStage}.
 *
 * <h2>Why it lives in the client package</h2>
 *
 * It names {@link Minecraft}. Loading that class on a dedicated server is a crash, so the packet
 * reaches it through {@code DistExecutor} and nothing on the common side imports it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ClientStageSync {
    private ClientStageSync() {
    }

    /** Records a player's body and reshapes them if they are already in the world. */
    public static void accept(UUID playerId, PlayerBody body) {
        PlayerProgressionClientView.putBody(playerId, body);

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            // The body arrived before the world did; the size event will read it when the player
            // spawns in, which is the ordinary login order.
            return;
        }

        Player player = level.getPlayerByUUID(playerId);
        if (player != null) {
            player.refreshDimensions();
        }
    }

    /**
     * Leaving a world drops every remembered stage.
     *
     * <p>Without this the map outlives the server that filled it, and the next world starts with
     * one player already the size the last world made them.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PlayerProgressionClientView.clear();
    }
}
