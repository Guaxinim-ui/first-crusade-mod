package com.example.examplemod.progression.ork.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.progression.PlayerProgressionClientView;
import com.example.examplemod.progression.PlayerProgressionProfile;
import com.example.examplemod.progression.ork.PlayerOrkProgressionBalance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Fury bar — the one thing the WAAAGH puts on screen without being asked.
 *
 * <h2>It is not always there</h2>
 *
 * At zero it draws nothing at all, which is most of the time: Fury only exists in a fight, so a
 * permanent empty bar would be permanent clutter advertising a mechanic that is not running. It
 * appears when he starts getting angry and fades out with him.
 *
 * <h2>It shouts when it is full</h2>
 *
 * A full bar is the only state that means something the player has to <i>act</i> on — WAAAAAAAAAGH!
 * costs the whole hundred and gives nothing at ninety-nine. So at a hundred the bar pulses and says
 * so. Anywhere else it is a quiet green line.
 *
 * <h2>Nothing here computes Fury</h2>
 *
 * The value comes from the client's copy of the profile, which the server fills — the small
 * {@code SyncOrkFuryPacket} — and the decay is the same timestamp arithmetic the server runs, over
 * the same two numbers. The bar cannot drift from the value the shout will actually be checked
 * against, because it is not a second calculation.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PlayerOrkFuryHud {
    private PlayerOrkFuryHud() {
    }

    private static final int WIDTH = 110;
    private static final int HEIGHT = 6;

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "firstcrusade_ork_fury",
                (gui, graphics, partialTick, screenWidth, screenHeight) ->
                        render(graphics, screenWidth, screenHeight));
    }

    private static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        // Orks only. An Imperial has no Fury and never will, so there is nothing to draw him.
        if (!PlayerProgressionClientView.isOrk()) {
            return;
        }

        PlayerProgressionProfile profile = PlayerProgressionClientView.self();
        if (profile == null) {
            return;
        }

        int fury = profile.ork().fury(minecraft.player.level().getGameTime());
        if (fury <= 0) {
            return;
        }

        boolean full = fury >= PlayerOrkProgressionBalance.FURY_MAX;

        int x = (screenWidth - WIDTH) / 2;
        int y = screenHeight - 62;

        int filled = Math.max(1,
                WIDTH * fury / PlayerOrkProgressionBalance.FURY_MAX);

        // Pulsing is done by picking one of two greens on a slow alternation rather than by fading:
        // a HUD element that fades is one more thing to interpolate every frame, and a bar that
        // flicks between two shades reads as "look at me" just as well.
        boolean bright = full && (minecraft.player.tickCount / 5) % 2 == 0;

        int fill = full
                ? (bright ? 0xFF6BFF3C : 0xFF2E9E12)
                : 0xFF3F8F2A;

        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xC0000000);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF1A2412);
        graphics.fill(x, y, x + filled, y + HEIGHT, fill);

        Component label = full
                ? Component.translatable("gui.firstcrusade.ork.fury_full")
                : Component.translatable("gui.firstcrusade.ork.fury", fury,
                        PlayerOrkProgressionBalance.FURY_MAX);

        graphics.drawCenteredString(minecraft.font, label, screenWidth / 2, y - 11,
                full ? 0xFF8BFF5C : 0xFFA9C68C);
    }
}
