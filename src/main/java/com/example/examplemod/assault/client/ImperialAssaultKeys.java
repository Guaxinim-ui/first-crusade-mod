package com.example.examplemod.assault.client;

import org.lwjgl.glfw.GLFW;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.assault.ImperialRaidKeyPacket;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>R</b> — declare an Imperial raid on the nearest Ork camp.
 *
 * <h2>Registered on one bus, polled on another</h2>
 *
 * Registration is a mod-bus event; polling is a client-tick event. Putting both on one class with
 * the wrong bus is the classic "my keybind does nothing and nothing is logged" bug — the same shape
 * as {@code PlayerProgressionKeys}, which is why this file mirrors it exactly.
 *
 * <h2>The key sends; the server decides</h2>
 *
 * A press is one empty packet. This class does not check the player's faction, their command
 * authority, the cooldown, or whether a camp is even nearby — every one of those is answered on the
 * server, which is the only side that can answer them honestly.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ImperialAssaultKeys {
    private ImperialAssaultKeys() {
    }

    private static final String CATEGORY = "key.categories.firstcrusade";

    /**
     * R — free in vanilla 1.20.1, and next to the movement keys, which is where a hand already is
     * when the decision to attack gets made.
     */
    public static final KeyMapping DECLARE_RAID = new KeyMapping(
            "key.firstcrusade.declare_raid", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /** Mod bus: the registration itself. */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(DECLARE_RAID);
        }
    }

    /** Client bus: the poll. {@code consumeClick} drains the queue, so a press fires exactly once. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        while (DECLARE_RAID.consumeClick()) {
            FirstCrusadeNetwork.CHANNEL.sendToServer(new ImperialRaidKeyPacket());
        }
    }
}
