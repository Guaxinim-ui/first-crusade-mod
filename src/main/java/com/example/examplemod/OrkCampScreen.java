package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Ork city's command panel: clan, level, populace, garrison, loot and Loot Pits, with a button
 * to raise a new pit — and, for an Imperial visitor, the button that declares a raid on the place.
 *
 * <h2>Two audiences, one panel</h2>
 *
 * The Ork half is untouched: an Ork player sees exactly what they saw before. The raid button is
 * added beneath it, in Imperial livery rather than Ork green, so it reads as something done <i>to</i>
 * this camp rather than one more thing the camp can do. It is drawn only for a viewer the server has
 * told us is Imperial — and the server checks again when the packet arrives, because a hidden button
 * is a courtesy and not a rule.
 */
public class OrkCampScreen extends AbstractContainerScreen<OrkCampMenu> {
    private static final int WAAAGH_COLOR = 0xFF7CCB5A;

    // Imperial livery for the raid button: dark blood red, gold trim.
    private static final int IMPERIAL_RED = 0xFF5A0F12;
    private static final int IMPERIAL_RED_HOVER = 0xFF8E1E20;
    private static final int IMPERIAL_GOLD = 0xFFD9B65C;

    private Button buildLootPitButton;
    private Button raidButton;
    private Button recruitBoyButton;
    private Button promoteNobButton;
    private Button waaaghButton;

    /**
     * Whether this viewer gets the §24 command row, decided once.
     *
     * <p>Read here and not per-frame because it also sets {@link #imageHeight}: the panel is taller
     * for an Ork, and a height that changed after {@code init} would leave the background painted to
     * one size and the widgets laid out to another.
     */
    private final boolean ork;

    public OrkCampScreen(OrkCampMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.ork = com.example.examplemod.progression.PlayerProgressionClientView.isOrk();
        this.imageWidth = 200;

        // An Imperial viewer sees exactly the panel they saw before — same height, same positions.
        // The four command buttons are the only reason to grow it, so they are the only ones that do.
        this.imageHeight = this.ork ? 252 : 200;
    }

    @Override
    protected void init() {
        super.init();

        this.buildLootPitButton = addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.firstcrusade.ork.build_loot_pit"),
                        button -> FirstCrusadeNetwork.CHANNEL.sendToServer(
                                new OrkCampActionPacket(this.menu.getCampPos(),
                                        OrkCampActionPacket.Action.BUILD_LOOT_PIT))
                ).bounds(this.leftPos + 10, this.topPos + 146, 180, 20).build()
        );

        if (this.menu.isViewerImperial()) {
            this.raidButton = addRenderableWidget(
                    Button.builder(
                            Component.translatable("gui.firstcrusade.assault.start_raid"),
                            button -> FirstCrusadeNetwork.CHANNEL.sendToServer(
                                    new OrkCampActionPacket(this.menu.getCampPos(),
                                            OrkCampActionPacket.Action.START_IMPERIAL_RAID))
                    ).bounds(this.leftPos + 10, this.topPos + 170, 180, 22).build()
            );

            this.raidButton.setTooltip(Tooltip.create(raidTooltip()));
        }

        // The Ork half of the same panel. Drawn from the client's synced faction, and it is only
        // courtesy: the server checks the faction again before converting anything, so a client
        // that drew the button anyway would still be refused.
        if (this.ork) {
            // Two columns, because four commands stacked full-width would push the panel past the
            // height of a 1080p window at GUI scale 3.
            this.recruitBoyButton = addRenderableWidget(
                    orkButton("recruit_boy", OrkCampActionPacket.Action.RECRUIT_BOY,
                            this.leftPos + 10, this.topPos + 170, 88));

            this.promoteNobButton = addRenderableWidget(
                    orkButton("promote_nob", OrkCampActionPacket.Action.PROMOTE_NOB,
                            this.leftPos + 102, this.topPos + 170, 88));

            addRenderableWidget(
                    orkButton("cycle_target", OrkCampActionPacket.Action.CYCLE_TARGET,
                            this.leftPos + 10, this.topPos + 194, 88));

            this.waaaghButton = addRenderableWidget(
                    orkButton("order_waaagh", OrkCampActionPacket.Action.ORDER_WAAAGH,
                            this.leftPos + 102, this.topPos + 194, 88));

            addRenderableWidget(
                    Button.builder(
                            Component.translatable("gui.firstcrusade.ork.stash_teef"),
                            button -> FirstCrusadeNetwork.CHANNEL.sendToServer(
                                    new OrkCampActionPacket(this.menu.getCampPos(),
                                            OrkCampActionPacket.Action.STASH_TEEF))
                    ).bounds(this.leftPos + 10, this.topPos + 218, 180, 22).build()
            ).setTooltip(Tooltip.create(
                    Component.translatable("gui.firstcrusade.ork.stash_teef.tip")));
        }
    }

    /** One command button: same label/tooltip convention for all four, so adding a fifth is one line. */
    private Button orkButton(String key, OrkCampActionPacket.Action action, int x, int y, int width) {
        Button button = Button.builder(
                Component.translatable("gui.firstcrusade.ork." + key),
                b -> FirstCrusadeNetwork.CHANNEL.sendToServer(
                        new OrkCampActionPacket(this.menu.getCampPos(), action))
        ).bounds(x, y, width, 20).build();

        button.setTooltip(Tooltip.create(Component.translatable("gui.firstcrusade.ork." + key + ".tip")));

        return button;
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (this.buildLootPitButton != null) {
            this.buildLootPitButton.active = this.menu.getLoot() >= this.menu.getLootPitCost();
        }

        if (this.raidButton != null) {
            this.raidButton.active = !this.menu.isUnderAssault();
            this.raidButton.setTooltip(Tooltip.create(this.menu.isUnderAssault()
                    ? Component.translatable("gui.firstcrusade.assault.start_raid.active")
                    : raidTooltip()));
        }

        // Greyed out only for the two things the client can actually see: loot in hand and a cap it
        // was told. Everything else — standing Boyz, the field cap, whether a target still exists —
        // is the server's to answer, and it answers with a reason rather than a dead button.
        if (this.recruitBoyButton != null) {
            this.recruitBoyButton.active = this.menu.getLoot() >= this.menu.getBoyCost()
                    && this.menu.getBoyz() < this.menu.getGarrisonCap();
        }

        if (this.promoteNobButton != null) {
            this.promoteNobButton.active = this.menu.getLoot() >= this.menu.getNobCost()
                    && this.menu.getNobz() < this.menu.getNobCap();
        }

        if (this.waaaghButton != null) {
            this.waaaghButton.active = this.menu.hasTarget();
        }
    }

    /**
     * What the raid button explains, plus the shortcut that does the same thing.
     *
     * <p>The key is mentioned here because this panel is where a player first meets the raid — and
     * finding out afterwards that they never had to walk up to the block is the kind of discovery
     * that should not need a wiki.
     */
    private Component raidTooltip() {
        return Component.translatable("gui.firstcrusade.assault.start_raid.tip")
                .copy()
                .append("\n")
                .append(Component.translatable("gui.firstcrusade.assault.start_raid.key"));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xEE0B0B0B);
        g.fill(x, y, x + this.imageWidth, y + 18, 0xEE143A14);

        // Loot bar.
        float lootFraction = this.menu.getLootCap() <= 0
                ? 0.0F
                : this.menu.getLoot() / (float) this.menu.getLootCap();
        drawBar(g, x + 10, y + 120, 180, 7, lootFraction, WAAAGH_COLOR);

        if (this.raidButton != null) {
            drawImperialPlate(g, mouseX, mouseY);
        }
    }

    /**
     * The Imperial plate behind the raid button, drawn under the widget itself.
     *
     * <p>Vanilla's button texture is grey-blue and would read as one more Ork option. Filling the
     * same rectangle first — dark red, gold border, an Aquila-ish mark on the left — is enough to
     * make it obviously Imperial without replacing the widget.
     */
    private void drawImperialPlate(GuiGraphics g, int mouseX, int mouseY) {
        int x = this.raidButton.getX();
        int y = this.raidButton.getY();
        int width = this.raidButton.getWidth();
        int height = this.raidButton.getHeight();

        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int fill = this.raidButton.active
                ? (hovered ? IMPERIAL_RED_HOVER : IMPERIAL_RED)
                : 0xFF2A2024;

        g.fill(x, y, x + width, y + height, fill);

        // Gold border.
        g.fill(x, y, x + width, y + 1, IMPERIAL_GOLD);
        g.fill(x, y + height - 1, x + width, y + height, IMPERIAL_GOLD);
        g.fill(x, y, x + 1, y + height, IMPERIAL_GOLD);
        g.fill(x + width - 1, y, x + width, y + height, IMPERIAL_GOLD);

        // A small sword mark on the left, so the button carries a symbol and not only a colour.
        int sx = x + 8;
        int sy = y + 4;
        g.fill(sx + 2, sy, sx + 4, sy + 12, IMPERIAL_GOLD);
        g.fill(sx, sy + 4, sx + 6, sy + 6, IMPERIAL_GOLD);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("gui.firstcrusade.ork.title"),
                8, 5, 0xFF8FE08F, false);

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

        if (this.ork) {
            // Second column of the stats block, NOT the gap between buttons. Put between the button
            // rows these landed on top of them — a 20-tall button starting at 146 owns 146-166, and
            // a label at 160 is inside it. The stat lines on the left are short enough that the
            // right half of the panel is free, and using it costs no height at all.
            g.drawString(this.font,
                    Component.translatable("gui.firstcrusade.ork.nobz_of",
                            this.menu.getNobz(), this.menu.getNobCap()),
                    118,24, 0xFFCFCFCF, false);

            g.drawString(this.font,
                    Component.translatable("gui.firstcrusade.ork.boyz_of",
                            this.menu.getBoyz(), this.menu.getGarrisonCap()),
                    118,38, 0xFFCFCFCF, false);

            g.drawString(this.font,
                    this.menu.hasTarget()
                            ? Component.translatable("gui.firstcrusade.ork.target_at",
                                    this.menu.getTargetDistance())
                            : Component.translatable("gui.firstcrusade.ork.target_none"),
                    118,52, this.menu.hasTarget() ? 0xFFE0C68A : 0xFF909090, false);
        }

        if (this.menu.isUnderAssault()) {
            g.drawString(this.font,
                    Component.translatable("gui.firstcrusade.assault.camp_under_attack"),
                    10, this.ork ? 242 : 194, 0xFFFF7070, false);
        }
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
