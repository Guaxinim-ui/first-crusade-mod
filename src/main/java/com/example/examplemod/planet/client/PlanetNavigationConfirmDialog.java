package com.example.examplemod.planet.client;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.planet.PlanetDangerLevel;
import com.example.examplemod.planet.PlanetDefinition;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * "Confirm Planetary Travel" — the modal that stands between a selected destination and a launch.
 *
 * <h2>Why it is not a Screen</h2>
 *
 * A second {@code Screen} would close the terminal to open itself, and closing an
 * {@link net.minecraft.world.inventory.AbstractContainerMenu} tells the server the player walked
 * away. The launch request would then arrive with no menu open. So the dialog is drawn <i>inside</i>
 * the terminal: it owns the pointer while it is up, and the container underneath stays open and
 * valid the whole time.
 *
 * <h2>Extreme worlds are asked twice</h2>
 *
 * A destination at {@link PlanetDangerLevel#EXTREME} needs two presses of CONFIRM, and the second
 * one says what it is agreeing to. This is the one place the interface deliberately slows the player
 * down: arriving on Armageddon by misclick is a lost character, and an accidental launch is not a
 * mistake the game can undo.
 */
public class PlanetNavigationConfirmDialog {

    private static final int WIDTH = 260;
    private static final int BUTTON_WIDTH = 108;
    private static final int BUTTON_HEIGHT = 20;

    private final PlanetDefinition planet;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    /** How many more times CONFIRM must be pressed. Two for extreme worlds, one otherwise. */
    private int confirmationsLeft;

    public PlanetNavigationConfirmDialog(PlanetDefinition planet, Runnable onConfirm, Runnable onCancel) {
        this.planet = planet;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.confirmationsLeft = planet.dangerLevel().requiresDoubleConfirmation() ? 2 : 1;
    }

    private List<FormattedCharSequence> body(Font font) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        int textWidth = WIDTH - 24;

        lines.addAll(font.split(Component.translatable("gui.firstcrusade.planet.confirm.destination")
                .append(": ").append(planet.displayName().copy().withStyle(ChatFormatting.GOLD)), textWidth));

        lines.addAll(font.split(Component.translatable("gui.firstcrusade.planet.confirm.danger")
                .append(": ").append(planet.dangerLevel().displayName()), textWidth));

        lines.addAll(font.split(Component.translatable("gui.firstcrusade.planet.confirm.situation")
                .append(": ").append(planet.militaryStatus().displayName()), textWidth));

        lines.add(FormattedCharSequence.EMPTY);

        // The warning that changes: on the second pass it names the risk instead of describing it.
        Component warning = this.confirmationsLeft > 1
                ? Component.translatable("gui.firstcrusade.planet.confirm.warzone")
                        .withStyle(ChatFormatting.RED)
                : Component.translatable("gui.firstcrusade.planet.confirm.final")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        lines.addAll(font.split(warning, textWidth));

        if (!planet.travelCost().stack().isEmpty()) {
            lines.add(FormattedCharSequence.EMPTY);
            lines.addAll(font.split(Component.translatable("gui.firstcrusade.planet.travel_cost",
                            planet.travelCost().count(),
                            planet.travelCost().stack().getHoverName())
                    .withStyle(ChatFormatting.GRAY), textWidth));
        }

        return lines;
    }

    private int height(Font font) {
        return 30 + body(font).size() * 10 + BUTTON_HEIGHT + 14;
    }

    private int left(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    private int top(int screenHeight, Font font) {
        return (screenHeight - height(font)) / 2;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       int mouseX, int mouseY) {
        // Dim the terminal behind, so the modal reads as the only thing that can be clicked.
        graphics.fill(0, 0, screenWidth, screenHeight, 0xC0000000);

        int x = left(screenWidth);
        int y = top(screenHeight, font);
        int h = height(font);

        PlanetNavigationTheme.panel(graphics, x, y, WIDTH, h);
        PlanetNavigationTheme.brackets(graphics, x + 2, y + 2, WIDTH - 4, h - 4,
                this.planet.dangerLevel().colour());

        graphics.drawCenteredString(font, Component.translatable("gui.firstcrusade.planet.confirm.title"),
                x + WIDTH / 2, y + 8, PlanetNavigationTheme.GOLD);
        PlanetNavigationTheme.rule(graphics, x + 8, y + 20, WIDTH - 16);

        int cursor = y + 26;
        for (FormattedCharSequence line : body(font)) {
            graphics.drawString(font, line, x + 12, cursor, PlanetNavigationTheme.TEXT, false);
            cursor += 10;
        }

        int buttonY = y + h - BUTTON_HEIGHT - 8;
        drawButton(graphics, font, confirmLabel(), x + 10, buttonY, mouseX, mouseY,
                PlanetNavigationTheme.ALERT);
        drawButton(graphics, font, Component.translatable("gui.firstcrusade.planet.confirm.cancel"),
                x + WIDTH - BUTTON_WIDTH - 10, buttonY, mouseX, mouseY,
                PlanetNavigationTheme.FRAME_LIT);
    }

    private Component confirmLabel() {
        return this.confirmationsLeft > 1
                ? Component.translatable("gui.firstcrusade.planet.confirm.acknowledge")
                : Component.translatable("gui.firstcrusade.planet.confirm.confirm");
    }

    private void drawButton(GuiGraphics graphics, Font font, Component label,
                            int x, int y, int mouseX, int mouseY, int accent) {
        boolean hovered = inside(mouseX, mouseY, x, y);

        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT,
                hovered ? 0xFF2A343A : 0xFF1A2228);
        PlanetNavigationTheme.brackets(graphics, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, accent);

        graphics.drawCenteredString(font, label, x + BUTTON_WIDTH / 2,
                y + (BUTTON_HEIGHT - 8) / 2, hovered ? 0xFFFFFFFF : PlanetNavigationTheme.TEXT);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    /**
     * Handles a click while the dialog is up.
     *
     * <p>Always returns true: a modal that let clicks through to the list behind it would let a
     * player change the selection between confirming and launching, and the launch would go to a
     * planet the dialog never named.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int x = left(screenWidth);
        int y = top(screenHeight, font);
        int buttonY = y + height(font) - BUTTON_HEIGHT - 8;

        if (inside(mouseX, mouseY, x + 10, buttonY)) {
            this.confirmationsLeft--;

            if (this.confirmationsLeft <= 0) {
                this.onConfirm.run();
            }

            return true;
        }

        if (inside(mouseX, mouseY, x + WIDTH - BUTTON_WIDTH - 10, buttonY)) {
            this.onCancel.run();
            return true;
        }

        return true;
    }
}
