package com.example.examplemod.progression.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.progression.PlayerProgressionClientView;
import com.example.examplemod.progression.PlayerProgressionProfile;
import com.example.examplemod.progression.PlayerProgressionTree;
import com.example.examplemod.progression.PlayerSkillNodeDefinition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The one thing the progression puts on the screen without being asked: the surgery bar.
 *
 * <h2>Only while it matters</h2>
 *
 * A permanent progression readout would be clutter — the tree is one key away. This draws nothing at
 * all unless the player is on the operating table, where a minute of waiting with no feedback would
 * read as a broken implant rather than as a working one.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PlayerProgressionHud {
    private PlayerProgressionHud() {
    }

    private static final int WIDTH = 120;
    private static final int HEIGHT = 5;

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "firstcrusade_surgery",
                (gui, graphics, partialTick, screenWidth, screenHeight) ->
                        render(graphics, screenWidth, screenHeight));
    }

    private static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        PlayerProgressionProfile profile = PlayerProgressionClientView.self();
        if (profile == null || !profile.isInSurgery()) {
            return;
        }

        String nodeId = profile.surgeryNodeId();
        PlayerSkillNodeDefinition node = nodeId == null ? null : PlayerProgressionTree.node(nodeId);
        if (node == null) {
            return;
        }

        long now = minecraft.player.level().getGameTime();
        float progress = profile.surgeryProgress(now);

        int x = (screenWidth - WIDTH) / 2;
        int y = screenHeight - 62;

        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xC0000000);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF2A1216);
        graphics.fill(x, y, x + (int) (WIDTH * progress), y + HEIGHT, 0xFF8E3BAF);

        Component label = Component.translatable(
                "gui.firstcrusade.progression.implanting", node.displayName());

        graphics.drawCenteredString(minecraft.font, label,
                screenWidth / 2, y - 11, 0xFFD9B65C);
    }
}
