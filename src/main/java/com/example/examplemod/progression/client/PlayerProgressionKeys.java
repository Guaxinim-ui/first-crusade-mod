package com.example.examplemod.progression.client;

import org.lwjgl.glfw.GLFW;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.progression.PlayerProgressionAbility;
import com.example.examplemod.progression.ProgressionActionPacket;
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
 * The progression's keys: the tree, and the four things a progressed Imperial can do.
 *
 * <h2>Registered once, polled once</h2>
 *
 * Registration is a mod-bus event; polling is a client-tick event. They are two different buses, so
 * they are two different subscribers — putting both on one class with the wrong bus is the classic
 * "my keybind does nothing and nothing is logged" bug.
 *
 * <h2>Every key sends, none decides</h2>
 *
 * A press turns into one packet. The client does not check whether the player owns the node, whether
 * the cooldown has run out or whether there is a wall in the way — the server answers all three, and
 * a client that answered them itself would be a client that could answer them generously.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PlayerProgressionKeys {
    private PlayerProgressionKeys() {
    }

    private static final String CATEGORY = "key.categories.firstcrusade";

    /** K — the tree itself. */
    public static final KeyMapping OPEN_TREE = new KeyMapping(
            "key.firstcrusade.progression_tree", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    /** O — pray. */
    public static final KeyMapping PRAY = new KeyMapping(
            "key.firstcrusade.pray", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);

    /** V — combat roll. */
    public static final KeyMapping ROLL = new KeyMapping(
            "key.firstcrusade.roll", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /** B — Betcher's gland. */
    public static final KeyMapping ACID = new KeyMapping(
            "key.firstcrusade.acid", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    /** G — Sus-an stasis. */
    public static final KeyMapping STASIS = new KeyMapping(
            "key.firstcrusade.stasis", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /** Mod bus: the registration itself. */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_TREE);
            event.register(PRAY);
            event.register(ROLL);
            event.register(ACID);
            event.register(STASIS);
        }
    }

    /**
     * Forge bus: the poll.
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

        while (OPEN_TREE.consumeClick()) {
            // Ask for a fresh profile as the screen opens: the tree is drawn from what the server
            // last said, and "last said" should mean "a moment ago", not "at login".
            FirstCrusadeNetwork.CHANNEL.sendToServer(ProgressionActionPacket.request());

            // One key, two trees. Routed here rather than inside a screen that then has to branch in
            // every method it has — and an Ork must never be shown Doctrine Points, which is easiest
            // to guarantee when the screen holding them is simply never constructed for him.
            minecraft.setScreen(com.example.examplemod.progression.PlayerProgressionClientView.isOrk()
                    ? new com.example.examplemod.progression.ork.client
                            .PlayerOrkProgressionScreen()
                    : new PlayerProgressionScreen());
        }

        while (PRAY.consumeClick()) {
            send(PlayerProgressionAbility.PRAYER);
        }

        while (ROLL.consumeClick()) {
            send(PlayerProgressionAbility.ROLL);
        }

        while (ACID.consumeClick()) {
            send(PlayerProgressionAbility.ACID);
        }

        while (STASIS.consumeClick()) {
            send(PlayerProgressionAbility.STASIS);
        }
    }

    private static void send(PlayerProgressionAbility ability) {
        FirstCrusadeNetwork.CHANNEL.sendToServer(ProgressionActionPacket.ability(ability));
    }
}
