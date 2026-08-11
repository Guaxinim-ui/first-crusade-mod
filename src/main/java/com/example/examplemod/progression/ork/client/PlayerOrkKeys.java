package com.example.examplemod.progression.ork.client;

import org.lwjgl.glfw.GLFW;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.progression.PlayerProgressionClientView;
import com.example.examplemod.progression.ProgressionActionPacket;
import com.example.examplemod.progression.ork.PlayerOrkAbility;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The WAAAGH's four keys.
 *
 * <h2>Which four, and why not the Imperial ones</h2>
 *
 * K, O, V, B and G already belong to the progression, and R to declaring a raid. Reusing any of them
 * would mean a key that does one thing for an Imperial and another for an Ork — and a player who
 * changed factions would have to relearn his hands. H, X, J and Z are free in vanilla 1.20.1 and free
 * in this mod, and all four are rebindable like any other.
 *
 * <h2>Nothing is sent by a player who is not an Ork</h2>
 *
 * The faction check is the client declining to bother the server, not a security measure: the server
 * refuses these abilities from a non-Ork whatever arrives. Without it an Imperial with a hand near Z
 * would be sending packets nobody will ever act on.
 *
 * <h2>Registered once, polled once</h2>
 *
 * Registration is a mod-bus event; polling is a client-tick event. Two buses, two subscribers —
 * putting both on one class with the wrong bus is the classic "my keybind does nothing and nothing is
 * logged" bug.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PlayerOrkKeys {
    private PlayerOrkKeys() {
    }

    private static final String CATEGORY = "key.categories.firstcrusade";

    /** H — 'eadbutt. Next to the hand that is already holding a choppa. */
    public static final KeyMapping HEADBUTT = new KeyMapping(
            "key.firstcrusade.ork_headbutt", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    /** X — WAAAAAAAAAGH! */
    public static final KeyMapping WAAAGH = new KeyMapping(
            "key.firstcrusade.ork_waaagh", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    /** J — point at a git, or call the Boyz over. */
    public static final KeyMapping ORDER = new KeyMapping(
            "key.firstcrusade.ork_order", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    /** Z — run at it. */
    public static final KeyMapping CHARGE = new KeyMapping(
            "key.firstcrusade.ork_charge", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    /** Mod bus: the registration itself. */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(HEADBUTT);
            event.register(WAAAGH);
            event.register(ORDER);
            event.register(CHARGE);
        }
    }

    /**
     * Client bus: the poll.
     *
     * <p>{@code consumeClick} drains the queued presses, so a key held down fires once rather than
     * twenty times a second — which for an ability on a cooldown is the difference between one
     * request and a flood of refusals.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        // Drained either way: a press made while playing an Imperial must not be sitting in the
        // queue waiting for the day the player switches sides.
        boolean ork = PlayerProgressionClientView.isOrk();

        while (HEADBUTT.consumeClick()) {
            send(PlayerOrkAbility.HEADBUTT, ork);
        }

        while (WAAAGH.consumeClick()) {
            send(PlayerOrkAbility.WAAAGH_ROAR, ork);
        }

        while (ORDER.consumeClick()) {
            send(PlayerOrkAbility.BOSS_ORDER, ork);
        }

        while (CHARGE.consumeClick()) {
            send(PlayerOrkAbility.CHARGE, ork);
        }
    }

    private static void send(PlayerOrkAbility ability, boolean ork) {
        if (!ork) {
            return;
        }

        FirstCrusadeNetwork.CHANNEL.sendToServer(ProgressionActionPacket.orkAbility(ability));
    }
}
