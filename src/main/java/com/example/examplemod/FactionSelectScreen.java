package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The "choose your allegiance" screen shown once, the first time a player enters the world
 * (Origins-style): the Imperium of Man on the left, the Orks (WAAAGH!) on the right. It cannot be
 * dismissed without picking — Escape does nothing and there is no close button — so every player
 * starts the Crusade with a side. The pick is sent to the server ({@link SelectFactionPacket}), which
 * records it in {@link PlayerFactionData}.
 */
public class FactionSelectScreen extends Screen {
    private static final int IMPERIUM_COLOR = 0xFF4A86C8;
    private static final int ORKS_COLOR = 0xFF5BA832;

    public FactionSelectScreen() {
        super(Component.translatable("gui.firstcrusade.faction.title"));
    }

    @Override
    protected void init() {
        int half = this.width / 2;
        int buttonY = this.height / 2 + 10;
        int buttonWidth = 150;

        addRenderableWidget(Button.builder(
                PlayerFaction.IMPERIUM.getDisplayName(),
                button -> choose(PlayerFaction.IMPERIUM)
        ).bounds(half - buttonWidth - 10, buttonY, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(
                PlayerFaction.ORKS.getDisplayName(),
                button -> choose(PlayerFaction.ORKS)
        ).bounds(half + 10, buttonY, buttonWidth, 20).build());
    }

    private void choose(PlayerFaction faction) {
        ExampleMod.NETWORK_CHANNEL.sendToServer(new SelectFactionPacket(faction));

        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int half = this.width / 2;

        guiGraphics.drawCenteredString(this.font, this.title, half, 40, 0xFFFFD27D);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.firstcrusade.faction.subtitle"), half, 56, 0xFFBBBBBB);

        int descY = this.height / 2 - 36;
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.firstcrusade.faction.imperium.tag"), half - 85, descY, IMPERIUM_COLOR);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.firstcrusade.faction.imperium.desc"), half - 85, descY + 12, 0xFFDDDDDD);

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.firstcrusade.faction.orks.tag"), half + 85, descY, ORKS_COLOR);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.firstcrusade.faction.orks.desc"), half + 85, descY + 12, 0xFFDDDDDD);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // Origins-style: the player must pick a side, so the screen refuses to close on its own.
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
