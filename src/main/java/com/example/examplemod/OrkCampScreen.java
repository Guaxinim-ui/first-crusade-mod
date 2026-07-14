package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Ork city's command panel: a simple, single-screen GUI (no tabs yet) showing the city's clan,
 * level, populace (Grots), garrison (Boyz), loot stockpile and Loot Pits, with a button to raise a
 * new Loot Pit. The Ork mirror of the Imperial Command Core, built to the same solid-panel style as
 * {@link StrategiumScreen}; it will grow tabs/structures over time.
 */
public class OrkCampScreen extends AbstractContainerScreen<OrkCampMenu> {
    private static final int WAAAGH_COLOR = 0xFF7CCB5A;

    private Button buildLootPitButton;

    public OrkCampScreen(OrkCampMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 200;
        this.imageHeight = 176;
    }

    @Override
    protected void init() {
        super.init();

        this.buildLootPitButton = addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.firstcrusade.ork.build_loot_pit"),
                        button -> FirstCrusadeNetwork.CHANNEL.sendToServer(
                                new OrkCampActionPacket(this.menu.getCampPos(), OrkCampActionPacket.Action.BUILD_LOOT_PIT))
                ).bounds(this.leftPos + 10, this.topPos + 146, 180, 20).build()
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (this.buildLootPitButton != null) {
            this.buildLootPitButton.active = this.menu.getLoot() >= this.menu.getLootPitCost();
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xEE0B0B0B);
        g.fill(x, y, x + this.imageWidth, y + 18, 0xEE143A14);

        // Loot bar.
        float lootFraction = this.menu.getLootCap() <= 0 ? 0.0F : this.menu.getLoot() / (float) this.menu.getLootCap();
        drawBar(g, x + 10, y + 120, 180, 7, lootFraction, WAAAGH_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("gui.firstcrusade.ork.title"), 8, 5, 0xFF8FE08F, false);

        int line = 24;
        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.clan", this.menu.getClan().getDisplayName()),
                10, line, 0xFF8FE08F, false);
        line += 14;
        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.level", this.menu.getCampLevel()),
                10, line, 0xFFCFCFCF, false);
        line += 14;
        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.populace", this.menu.getGrots()),
                10, line, 0xFFCFCFCF, false);
        line += 14;
        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.garrison", this.menu.getBoyz()),
                10, line, 0xFFCFCFCF, false);
        line += 14;
        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.loot_pits", this.menu.getLootPits()),
                10, line, 0xFFCFCFCF, false);
        line += 14;
        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.loot", this.menu.getLoot(), this.menu.getLootCap()),
                10, line, 0xFFE0C68A, false);

        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.build_cost", this.menu.getLootPitCost()),
                10, 136, 0xFF909090, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    private void drawBar(GuiGraphics g, int x, int y, int width, int height, float fraction, int color) {
        g.fill(x, y, x + width, y + height, 0xFF202020);
        int filled = Math.round(width * Math.max(0.0F, Math.min(1.0F, fraction)));
        g.fill(x, y, x + filled, y + height, color);
        g.fill(x, y, x + width, y + 1, 0xFF143A14);
    }
}
