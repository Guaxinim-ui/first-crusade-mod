package com.example.examplemod.campaign.wartable.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.campaign.wartable.WarTableOrder;
import com.example.examplemod.campaign.wartable.WarTableOrderPacket;
import com.example.examplemod.campaign.wartable.WarTableRequestPacket;
import com.example.examplemod.campaign.wartable.WarTableSnapshot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Imperial Strategium — the map of the whole Crusade.
 *
 * <h2>It draws a snapshot and asks for nothing else</h2>
 *
 * Every number on this screen came from {@link WarTableSnapshot}, which the server built. The screen
 * computes no control percentage, decides no ownership and validates nothing. Its only outbound
 * message is {@link WarTableRequestPacket}, which says "send it again" and carries no claim.
 *
 * <h2>Scissor, not trust</h2>
 *
 * Both lists clip with {@code enableScissor}/{@code disableScissor} rather than by only drawing rows
 * that fall inside the panel. {@link GuiGraphics} queues text and flushes it later, so a string drawn
 * at a y outside the panel still lands on top of whatever is painted afterwards — a scrolled list
 * bleeds its text over the frame, the header and the buttons. Calling {@code flush()} does not fix
 * it; the scissor rectangle does, because it clips at draw time.
 *
 * <h2>Two tabs</h2>
 *
 * FRONTS is the war: worlds down the left, the selected one's detail on the right. LOGISTICS is the
 * supply map, which is a different shape of data — lanes between worlds rather than a state per
 * world — and cramming it into the front detail would have made both unreadable.
 */
public class WarTableScreen extends Screen {

    private static final int WIDTH = 340;
    private static final int HEIGHT = 220;

    private static final int PANEL_BG = 0xF00A0C10;
    private static final int PANEL_BORDER = 0xFF6A5A2A;
    private static final int PANEL_INNER = 0xFF15181F;
    private static final int ROW_SELECTED = 0x60C8A040;
    private static final int ROW_HOVER = 0x30FFFFFF;

    private static final int LIST_WIDTH = 108;
    private static final int ROW_HEIGHT = 13;
    /**
     * Height reserved above the panels for the title and the two right-aligned lines.
     *
     * <p>32 and not 26. The header stacks a summary at y+8 and a broken-lanes warning at y+18, and
     * that second line runs to y+26 — while the divider was drawn at {@code HEADER_HEIGHT - 2}, i.e.
     * y+24, straight through the bottom of the text. It only shows when a lane is actually cut,
     * which is why it survived until a screenshot of a real save with twelve of them.
     */
    private static final int HEADER_HEIGHT = 32;

    /**
     * Height reserved under the panels for the two button rows.
     *
     * <p>Two rows, not one. Three order buttons plus the two tabs plus refresh do not fit across
     * 340px: laid out on one line the orders ran from x+162 to x+318 and refresh started at x+264,
     * so the last order sat underneath it and could not be clicked.
     */
    private static final int FOOTER_HEIGHT = 42;

    private enum Tab {
        FRONTS,
        LOGISTICS
    }

    private WarTableSnapshot snapshot;

    private Tab tab = Tab.FRONTS;
    private int selectedFront;

    /**
     * The sector the order buttons act on, by id.
     *
     * <p>Held by id rather than by index: a snapshot arriving between the click and the order can
     * reorder or shorten the list, and an index would then point at a different sector than the one
     * the player is looking at.
     */
    private String selectedSector = "";

    /** Screen y of each sector row drawn last frame, so a click can find which one was hit. */
    private final java.util.List<int[]> sectorRows = new java.util.ArrayList<>();
    private final java.util.List<String> sectorRowIds = new java.util.ArrayList<>();
    private int detailScroll;
    private int listScroll;

    private int left;
    private int top;

    public WarTableScreen(WarTableSnapshot snapshot) {
        super(Component.translatable("screen." + ExampleMod.MODID + ".war_table"));
        this.snapshot = snapshot;
        this.selectedFront = snapshot.currentFrontIndex();
    }

    /** Replaces the picture without disturbing where the player was looking. See {@code WarTableClient}. */
    public void refresh(WarTableSnapshot updated) {
        this.snapshot = updated;

        // Clamp rather than reset: a front removed between snapshots must not leave the selection
        // pointing past the end of the list.
        this.selectedFront = Math.max(0, Math.min(this.selectedFront, updated.fronts().size() - 1));
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;

        int buttonY = this.top + HEIGHT - 20;

        addRenderableWidget(Button.builder(
                Component.translatable("screen." + ExampleMod.MODID + ".war_table.fronts"),
                button -> {
                    this.tab = Tab.FRONTS;
                    this.detailScroll = 0;
                }).bounds(this.left + 6, buttonY, 70, 16).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen." + ExampleMod.MODID + ".war_table.logistics"),
                button -> {
                    this.tab = Tab.LOGISTICS;
                    this.detailScroll = 0;
                }).bounds(this.left + 80, buttonY, 78, 16).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen." + ExampleMod.MODID + ".war_table.refresh"),
                button -> FirstCrusadeNetwork.CHANNEL.sendToServer(
                        new WarTableRequestPacket(this.snapshot.tablePos())))
                .bounds(this.left + WIDTH - 76, buttonY, 70, 16).build());

        // The three orders, on their own row above the tabs and aligned with the detail panel they
        // act on. They are always enabled and always send: whether an order is legal is a question
        // about the server's state, and a client greying them out from a snapshot would be deciding
        // it from a picture that is already seconds old. The server refuses with a reason, which is
        // both correct and more useful than a dead button.
        int orderX = this.left + LIST_WIDTH + 12;
        int orderY = buttonY - 18;

        for (WarTableOrder order : WarTableOrder.values()) {
            addRenderableWidget(Button.builder(order.displayName(),
                    button -> sendOrder(order))
                    .bounds(orderX, orderY, 68, 16).build());

            orderX += 70;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, PANEL_BG);
        drawBorder(graphics, this.left, this.top, WIDTH, HEIGHT, PANEL_BORDER);

        drawHeader(graphics);

        if (this.tab == Tab.FRONTS) {
            drawFrontList(graphics, mouseX, mouseY);
            drawFrontDetail(graphics);
        } else {
            drawLogistics(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ====================================================================================
    // Header
    // ====================================================================================

    private void drawHeader(GuiGraphics graphics) {
        graphics.drawString(this.font,
                Component.translatable("screen." + ExampleMod.MODID + ".war_table")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                this.left + 8, this.top + 7, 0xFFD9A441, false);

        String summary = Component.translatable("screen." + ExampleMod.MODID + ".war_table.summary",
                this.snapshot.crusadeScore(), this.snapshot.conquered(), this.snapshot.lost())
                .getString();

        graphics.drawString(this.font, summary,
                this.left + WIDTH - 8 - this.font.width(summary), this.top + 8, 0xFF9FB4C9, false);

        if (this.snapshot.brokenLanes() > 0) {
            String warning = Component.translatable(
                    "screen." + ExampleMod.MODID + ".war_table.broken_lanes",
                    this.snapshot.brokenLanes()).getString();

            graphics.drawString(this.font, warning,
                    this.left + WIDTH - 8 - this.font.width(warning), this.top + 18, 0xFFD05050, false);
        }

        graphics.fill(this.left + 6, this.top + HEADER_HEIGHT - 2,
                this.left + WIDTH - 6, this.top + HEADER_HEIGHT - 1, PANEL_BORDER);
    }

    // ====================================================================================
    // Front list
    // ====================================================================================

    private int listTop() {
        return this.top + HEADER_HEIGHT + 2;
    }

    private int listBottom() {
        return this.top + HEIGHT - FOOTER_HEIGHT;
    }

    private void drawFrontList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.left + 6;
        int y = listTop();
        int bottom = listBottom();

        graphics.fill(x, y, x + LIST_WIDTH, bottom, PANEL_INNER);

        graphics.enableScissor(x, y, x + LIST_WIDTH, bottom);

        for (int i = 0; i < this.snapshot.fronts().size(); i++) {
            WarTableSnapshot.FrontEntry front = this.snapshot.fronts().get(i);

            int rowY = y + 2 + i * ROW_HEIGHT - this.listScroll;

            if (i == this.selectedFront) {
                graphics.fill(x + 1, rowY - 1, x + LIST_WIDTH - 1, rowY + ROW_HEIGHT - 2, ROW_SELECTED);
            } else if (mouseX >= x && mouseX < x + LIST_WIDTH
                    && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 2
                    && mouseY >= y && mouseY < bottom) {
                graphics.fill(x + 1, rowY - 1, x + LIST_WIDTH - 1, rowY + ROW_HEIGHT - 2, ROW_HOVER);
            }

            graphics.drawString(this.font,
                    Component.translatable("planet." + ExampleMod.MODID + "." + front.frontPath())
                            .withStyle(front.state().colour()),
                    x + 4, rowY, 0xFFFFFFFF, false);

            // The bar is the whole point of the list: nine worlds' balance of power, comparable at a
            // glance without reading a single number.
            drawControlBar(graphics, x + 4, rowY + 9, LIST_WIDTH - 9, 2, front);
        }

        graphics.disableScissor();

        drawBorder(graphics, x, y, LIST_WIDTH, bottom - y, PANEL_BORDER);
    }

    /** Imperial / enemy / contested as one three-part bar. */
    private void drawControlBar(GuiGraphics graphics, int x, int y, int width, int height,
                                WarTableSnapshot.FrontEntry front) {
        int imperial = width * front.imperial() / 100;
        int ork = width * front.ork() / 100;
        int necron = width * front.necron() / 100;

        graphics.fill(x, y, x + width, y + height, 0xFF2A2E36);
        graphics.fill(x, y, x + imperial, y + height, 0xFFD9A441);
        graphics.fill(x + imperial, y, x + imperial + ork, y + height, 0xFF4FA84F);
        graphics.fill(x + imperial + ork, y, x + imperial + ork + necron, y + height, 0xFF49B6C4);
    }

    // ====================================================================================
    // Front detail
    // ====================================================================================

    private void drawFrontDetail(GuiGraphics graphics) {
        if (this.snapshot.fronts().isEmpty()) {
            return;
        }

        WarTableSnapshot.FrontEntry front =
                this.snapshot.fronts().get(Math.min(this.selectedFront, this.snapshot.fronts().size() - 1));

        int x = this.left + LIST_WIDTH + 12;
        int y = listTop();
        int right = this.left + WIDTH - 6;
        int bottom = listBottom();

        // Rebuilt every frame from what is actually drawn, so a row the scissor clipped away cannot
        // still be clicked. Deriving hit boxes from the same pass that paints them is the only way
        // the two cannot disagree.
        this.sectorRows.clear();
        this.sectorRowIds.clear();

        graphics.fill(x, y, right, bottom, PANEL_INNER);
        graphics.enableScissor(x, y, right, bottom);

        int line = y + 4 - this.detailScroll;
        int step = 10;

        graphics.drawString(this.font,
                Component.translatable("planet." + ExampleMod.MODID + "." + front.frontPath())
                        .withStyle(ChatFormatting.BOLD, front.state().colour()),
                x + 5, line, 0xFFFFFFFF, false);
        line += step + 2;

        graphics.drawString(this.font, front.state().displayName(), x + 5, line, 0xFFFFFFFF, false);
        graphics.drawString(this.font, front.intensity().displayName(), x + 110, line, 0xFFFFFFFF, false);
        line += step;

        drawControlBar(graphics, x + 5, line + 1, right - x - 10, 4, front);
        line += step;

        graphics.drawString(this.font, Component.translatable(
                        "screen." + ExampleMod.MODID + ".war_table.control",
                        front.imperial(), front.ork(), front.contested()),
                x + 5, line, 0xFFB9C6D4, false);
        line += step;

        graphics.drawString(this.font, Component.translatable(
                        "screen." + ExampleMod.MODID + ".war_table.bases",
                        front.cities(), front.camps()),
                x + 5, line, 0xFFB9C6D4, false);
        line += step;

        if (front.necronAwakening() > 0) {
            graphics.drawString(this.font, Component.translatable(
                            "screen." + ExampleMod.MODID + ".war_table.awakening",
                            front.necronAwakening()).withStyle(ChatFormatting.AQUA),
                    x + 5, line, 0xFFFFFFFF, false);
            line += step;
        }

        if (!front.objectiveKey().isEmpty()) {
            Component objective = front.objectiveTarget().isEmpty()
                    ? Component.translatable(front.objectiveKey())
                    : Component.translatable(front.objectiveKey(),
                            Component.translatable("sector." + ExampleMod.MODID + "."
                                    + front.objectiveTarget().toLowerCase(java.util.Locale.ROOT)));

            graphics.drawString(this.font,
                    Component.translatable("screen." + ExampleMod.MODID + ".war_table.objective")
                            .append(" ").append(objective).withStyle(ChatFormatting.GOLD),
                    x + 5, line, 0xFFFFFFFF, false);
            line += step + 2;
        }

        line = drawSection(graphics, x, line, "screen." + ExampleMod.MODID + ".war_table.operations");

        if (front.operations().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen." + ExampleMod.MODID + ".war_table.no_operations")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    x + 9, line, 0xFFFFFFFF, false);
            line += step;
        } else {
            for (WarTableSnapshot.OperationEntry operation : front.operations()) {
                String clock = operation.ticksLeft() <= 0 ? "" : "  " + (operation.ticksLeft() / 20) + "s";

                graphics.drawString(this.font,
                        operation.type().displayName().copy()
                                .append("  " + operation.progress() + "/" + operation.required() + clock)
                                .withStyle(operation.state().colour()),
                        x + 9, line, 0xFFFFFFFF, false);
                line += step;
            }
        }

        if (!front.deployments().isEmpty()) {
            line += 2;
            line = drawSection(graphics, x, line,
                    "screen." + ExampleMod.MODID + ".war_table.deployments");

            for (WarTableSnapshot.DeploymentEntry deployment : front.deployments()) {
                String bodies = deployment.materialised() > 0
                        ? " (+" + deployment.materialised() + ")"
                        : "";

                graphics.drawString(this.font,
                        deployment.faction().displayName().copy()
                                .append(" " + deployment.strength() + bodies + "  ")
                                .append(deployment.state().displayName())
                                .append(deployment.playerOrdered() ? " *" : ""),
                        x + 9, line, 0xFFFFFFFF, false);
                line += step;
            }
        }

        line += 2;
        line = drawSection(graphics, x, line, "screen." + ExampleMod.MODID + ".war_table.sectors");

        for (WarTableSnapshot.SectorEntry sector : front.sectors()) {
            String contest = (sector.contest() >= 0 ? "+" : "") + sector.contest();

            if (sector.id().equals(this.selectedSector)) {
                graphics.fill(x + 6, line - 1, right - 2, line + step - 2, ROW_SELECTED);
            }

            graphics.drawString(this.font,
                    sector.type().displayName().copy()
                            .append("  " + contest + (sector.disputed() ? " *" : ""))
                            .withStyle(sector.owner().colour()),
                    x + 9, line, 0xFFFFFFFF, false);

            // Only rows inside the panel are clickable, matching what the scissor let through.
            if (line >= y && line + step <= bottom) {
                this.sectorRows.add(new int[] {x + 6, right - 2, line - 1, line + step - 2});
                this.sectorRowIds.add(sector.id());
            }

            line += step;
        }

        if (!front.income().isEmpty()) {
            line += 2;
            line = drawSection(graphics, x, line, "screen." + ExampleMod.MODID + ".war_table.income");

            for (WarTableSnapshot.ResourceEntry income : front.income()) {
                graphics.drawString(this.font,
                        income.amount() + " " + income.resource().getDisplayName(),
                        x + 9, line, 0xFF8FD08F, false);
                line += step;
            }
        }

        if (!front.lastEvent().isEmpty()) {
            line += 2;
            graphics.drawString(this.font,
                    Component.literal(front.lastEvent()).withStyle(ChatFormatting.DARK_GRAY),
                    x + 5, line, 0xFFFFFFFF, false);
        }

        graphics.disableScissor();

        drawBorder(graphics, x, y, right - x, bottom - y, PANEL_BORDER);
    }

    private int drawSection(GuiGraphics graphics, int x, int y, String key) {
        graphics.drawString(this.font,
                Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE),
                x + 5, y, 0xFFFFFFFF, false);

        return y + 11;
    }

    // ====================================================================================
    // Logistics
    // ====================================================================================

    private void drawLogistics(GuiGraphics graphics) {
        int x = this.left + 6;
        int y = listTop();
        int right = this.left + WIDTH - 6;
        int bottom = listBottom();

        graphics.fill(x, y, right, bottom, PANEL_INNER);
        graphics.enableScissor(x, y, right, bottom);

        int line = y + 4 - this.detailScroll;

        if (this.snapshot.routes().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("screen." + ExampleMod.MODID + ".war_table.no_routes")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    x + 6, line, 0xFFFFFFFF, false);
        }

        for (WarTableSnapshot.RouteEntry route : this.snapshot.routes()) {
            Component origin = Component.translatable("planet." + ExampleMod.MODID + "." + route.originPath());
            Component destination = Component.translatable(
                    "planet." + ExampleMod.MODID + "." + route.destinationPath());

            graphics.drawString(this.font,
                    origin.copy().append(" -> ").append(destination)
                            .append("  " + route.resource().getDisplayName())
                            .append("  " + route.delivered() + "/" + route.amount())
                            .withStyle(route.state().colour()),
                    x + 6, line, 0xFFFFFFFF, false);
            line += 10;

            if (!route.reason().isEmpty()) {
                // Both halves are translation keys resolved here, on the client that knows the
                // language. The argument is fed through translatable unconditionally: a planet key
                // becomes the planet's name, and a bare resource name has no entry and comes out as
                // itself, so one path covers both.
                graphics.drawString(this.font,
                        Component.literal("   ").append(Component.translatable(route.reason(),
                                        Component.translatable(route.reasonArg())))
                                .withStyle(ChatFormatting.DARK_GRAY),
                        x + 6, line, 0xFFFFFFFF, false);
                line += 10;
            }
        }

        graphics.disableScissor();

        drawBorder(graphics, x, y, right - x, bottom - y, PANEL_BORDER);
    }

    // ====================================================================================
    // Input
    // ====================================================================================

    /** Sends an order for the selected sector, or says why there is nothing to send it about. */
    private void sendOrder(WarTableOrder order) {
        if (this.selectedSector.isEmpty()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable(
                        "msg." + ExampleMod.MODID + ".order.no_selection"), true);
            }
            return;
        }

        FirstCrusadeNetwork.CHANNEL.sendToServer(
                new WarTableOrderPacket(this.snapshot.tablePos(), order, this.selectedSector));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // A sector row: picks what the order buttons will act on.
        if (this.tab == Tab.FRONTS && button == 0) {
            for (int i = 0; i < this.sectorRows.size(); i++) {
                int[] row = this.sectorRows.get(i);

                if (mouseX >= row[0] && mouseX < row[1] && mouseY >= row[2] && mouseY < row[3]) {
                    this.selectedSector = this.sectorRowIds.get(i);
                    return true;
                }
            }
        }

        if (this.tab == Tab.FRONTS && button == 0) {
            int x = this.left + 6;
            int y = listTop();

            if (mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= y && mouseY < listBottom()) {
                int index = (int) ((mouseY - y - 2 + this.listScroll) / ROW_HEIGHT);

                if (index >= 0 && index < this.snapshot.fronts().size()) {
                    this.selectedFront = index;
                    this.detailScroll = 0;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean overList = this.tab == Tab.FRONTS
                && mouseX >= this.left + 6 && mouseX < this.left + 6 + LIST_WIDTH;

        if (overList) {
            int span = Math.max(0,
                    this.snapshot.fronts().size() * ROW_HEIGHT - (listBottom() - listTop()) + 6);
            this.listScroll = clamp(this.listScroll - (int) (delta * 12), 0, span);
        } else {
            // No exact content height is tracked, so the detail pane is clamped to a generous span
            // rather than to its real bottom. Scrolling a little past the end is a far smaller flaw
            // than a pane that refuses to reach its last line because the estimate was short.
            this.detailScroll = clamp(this.detailScroll - (int) (delta * 12), 0, 600);
        }

        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ====================================================================================
    // Drawing helpers
    // ====================================================================================

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int colour) {
        graphics.fill(x, y, x + width, y + 1, colour);
        graphics.fill(x, y + height - 1, x + width, y + height, colour);
        graphics.fill(x, y, x + 1, y + height, colour);
        graphics.fill(x + width - 1, y, x + width, y + height, colour);
    }
}
