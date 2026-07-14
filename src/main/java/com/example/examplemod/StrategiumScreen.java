package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Strategium research GUI: shows the Imperium's current Age and progress, the cost/timer of the
 * next Age's research, the bench's Iron/Scrap stockpile, and the Orks' WAAAGH! Age for reference.
 * The single button starts the next research; funding is done by right-clicking the block with
 * Iron/Scrap. Drawn with solid panels (no texture) like {@link ImperialCommandCoreScreen}.
 */
public class StrategiumScreen extends AbstractContainerScreen<StrategiumMenu> {
    private static final int CRUSADE_COLOR = 0xFF3A7AFF;
    private static final int WAAAGH_COLOR = 0xFF4CA02C;

    private Button researchButton;

    public StrategiumScreen(StrategiumMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 200;
        this.imageHeight = 176;
    }

    @Override
    protected void init() {
        super.init();

        this.researchButton = addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.firstcrusade.strategium.research"),
                        button -> FirstCrusadeNetwork.CHANNEL.sendToServer(
                                new StrategiumActionPacket(this.menu.getBenchPos()))
                ).bounds(this.leftPos + 10, this.topPos + 130, 180, 20).build()
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (this.researchButton != null) {
            this.researchButton.active = !this.menu.isResearching() && !this.menu.isImperiumMaxAge();
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Panel + title bar.
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xEE0B0B0B);
        g.fill(x, y, x + this.imageWidth, y + 18, 0xEE3A2A12);

        // Crusade Age progress bar.
        drawBar(g, x + 10, y + 36, 180, 7, this.menu.getImperiumProgress(), CRUSADE_COLOR);

        // Research timer bar (only while a research is running).
        if (this.menu.isResearching()) {
            float done = researchFraction();
            drawBar(g, x + 10, y + 74, 180, 7, done, 0xFFD6B85A);
        }

        // WAAAGH! Age progress bar (read-only reference).
        drawBar(g, x + 10, y + 116, 180, 7, this.menu.getWaaaghProgress(), WAAAGH_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("block.firstcrusade.strategium"), 8, 5, 0xFFD6B85A, false);

        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.strategium.crusade_age", this.menu.getImperiumAge()),
                10, 24, 0xFF9CC2FF, false);

        if (this.menu.isImperiumMaxAge()) {
            g.drawString(this.font, Component.translatable("gui.firstcrusade.strategium.max_age"),
                    10, 50, 0xFFB0B0B0, false);
        } else if (this.menu.isResearching()) {
            g.drawString(this.font,
                    Component.translatable("gui.firstcrusade.strategium.researching",
                            this.menu.getResearchTicksRemaining() / 20),
                    10, 50, 0xFFE0C68A, false);
        } else {
            g.drawString(this.font,
                    Component.translatable("gui.firstcrusade.strategium.cost",
                            this.menu.getResearchIronCost(), this.menu.getResearchScrapCost()),
                    10, 50, 0xFFCFCFCF, false);
        }

        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.strategium.stock",
                        this.menu.getStoredIron(), this.menu.getStoredScrap()),
                10, 90, 0xFFCFCFCF, false);

        g.drawString(this.font,
                Component.translatable("gui.firstcrusade.strategium.waaagh_age", this.menu.getWaaaghAge()),
                10, 104, 0xFF8FE08F, false);

        g.drawString(this.font, Component.translatable("gui.firstcrusade.strategium.hint"),
                10, 156, 0xFF707070, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    private float researchFraction() {
        int total = this.menu.getResearchTotalTicks();
        if (total <= 0) {
            return 0.0F;
        }
        float done = (total - this.menu.getResearchTicksRemaining()) / (float) total;
        return done < 0.0F ? 0.0F : (done > 1.0F ? 1.0F : done);
    }

    private void drawBar(GuiGraphics g, int x, int y, int width, int height, float fraction, int color) {
        g.fill(x, y, x + width, y + height, 0xFF202020);
        int filled = Math.round(width * Math.max(0.0F, Math.min(1.0F, fraction)));
        g.fill(x, y, x + filled, y + height, color);
        g.fill(x, y, x + width, y + 1, 0xFF3A2A12);
    }
}
