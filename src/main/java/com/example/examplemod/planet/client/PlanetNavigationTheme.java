package com.example.examplemod.planet.client;

import com.example.examplemod.ExampleMod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The terminal's look, in one place: colours, frames and the small pieces of chrome every panel
 * draws.
 *
 * <h2>Drawn, not textured</h2>
 *
 * The frames, rails and fills below are rectangles, not a nine-sliced background image. For a screen
 * that has to stretch across any window size and hold three resizable panels, a drawn frame is the
 * cheaper and more honest choice: it scales exactly, it never needs a texture atlas entry, and
 * changing the palette is changing five constants rather than repainting a PNG.
 *
 * <p>The textures that <i>are</i> files are the ones that carry information a rectangle cannot: the
 * planet portraits and the small status markers.
 */
public final class PlanetNavigationTheme {
    private PlanetNavigationTheme() {
    }

    // ---------------------------------------------------------------- palette

    /** Dark, slightly blue steel — the body of every panel. */
    public static final int PANEL = 0xF0141A1E;

    /** One step darker, for the wells that hold the list and the description. */
    public static final int WELL = 0xF00E1216;

    /** The metal frame around a panel. */
    public static final int FRAME = 0xFF3A464E;

    /** The lit edge on the top and left of a frame, which is what makes it read as metal. */
    public static final int FRAME_LIT = 0xFF5C6C76;

    /** Imperial gold: headings and the active selection. */
    public static final int GOLD = 0xFFD9B65C;

    /** The terminal's own green: technical readouts and available destinations. */
    public static final int TERMINAL_GREEN = 0xFF6FBF8A;

    /** Warning red: extreme danger, enemy control, refusals. */
    public static final int ALERT = 0xFFD24A3C;

    /** Ordinary body text. */
    public static final int TEXT = 0xFFC8D0D4;

    /** Secondary text: labels, hints, anything the eye should skip first. */
    public static final int TEXT_DIM = 0xFF7A868C;

    /** Locked rows and disabled buttons. */
    public static final int DISABLED = 0xFF4A5258;

    // ---------------------------------------------------------------- markers

    private static final String GUI = "textures/gui/planet_navigation/";

    public static ResourceLocation marker(String name) {
        return new ResourceLocation(ExampleMod.MODID, GUI + name + ".png");
    }

    public static final ResourceLocation LOCK = marker("locked");
    public static final ResourceLocation IMPERIAL_EMBLEM = marker("imperial_emblem");

    // ---------------------------------------------------------------- drawing

    /**
     * A framed panel: fill, dark border, and a lit top-left edge.
     *
     * <p>The lit edge is the whole trick — two one-pixel lines are the difference between a
     * rectangle and a piece of machinery.
     */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, fill);

        graphics.fill(x, y, x + width, y + 1, FRAME_LIT);
        graphics.fill(x, y, x + 1, y + height, FRAME_LIT);
        graphics.fill(x, y + height - 1, x + width, y + height, FRAME);
        graphics.fill(x + width - 1, y, x + width, y + height, FRAME);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        panel(graphics, x, y, width, height, PANEL);
    }

    /** A heading rail: a gold line with a title sitting on it. */
    public static void heading(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                               net.minecraft.network.chat.Component title, int x, int y, int width) {
        graphics.drawString(font, title, x + 4, y, GOLD, false);
        graphics.fill(x + 2, y + 10, x + width - 2, y + 11, 0x60D9B65C);
    }

    /**
     * The corner brackets that mark a focused element — four short right angles instead of a full
     * outline, which reads as a targeting reticle rather than as a selected cell in a spreadsheet.
     */
    public static void brackets(GuiGraphics graphics, int x, int y, int width, int height, int colour) {
        int arm = Math.min(6, Math.min(width, height) / 3);

        graphics.fill(x, y, x + arm, y + 1, colour);
        graphics.fill(x, y, x + 1, y + arm, colour);

        graphics.fill(x + width - arm, y, x + width, y + 1, colour);
        graphics.fill(x + width - 1, y, x + width, y + arm, colour);

        graphics.fill(x, y + height - 1, x + arm, y + height, colour);
        graphics.fill(x, y + height - arm, x + 1, y + height, colour);

        graphics.fill(x + width - arm, y + height - 1, x + width, y + height, colour);
        graphics.fill(x + width - 1, y + height - arm, x + width, y + height, colour);
    }

    /** A horizontal separator inside a panel. */
    public static void rule(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, 0x40FFFFFF & FRAME_LIT);
    }
}
