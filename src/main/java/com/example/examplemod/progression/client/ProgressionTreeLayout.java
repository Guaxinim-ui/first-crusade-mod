package com.example.examplemod.progression.client;

/**
 * Where the progression tree's rows sit, and how far the view can travel down them.
 *
 * <h2>Pure arithmetic, on purpose</h2>
 *
 * Not one Minecraft type appears here, which is what makes the layout <b>testable</b>: it can be run
 * outside the game against every resolution and GUI scale and the answer checked. Geometry worked out
 * inside {@code render()} can only be checked by looking at it, and looking at it is how a screen
 * ships with its header running off the edge.
 *
 * <h2>One column, scrolled</h2>
 *
 * The tree is a single strip twenty-seven rows tall — the root, then a skills row and an organ row
 * per cycle, then the ascension. Nothing is paged and nothing is scaled to fit: the node sizes are
 * fixed, and when the strip is taller than the window the window moves. That is why
 * {@link #totalHeight} exists and why there is no tier ladder any more — height stopped being a
 * constraint the moment scrolling became the answer to it.
 *
 * <h2>The header is measured, not guessed</h2>
 *
 * Its height arrives as an argument, because only the caller can ask the font how wide a line of
 * text is. The layout's job is to put the content below whatever that turned out to be.
 */
public record ProgressionTreeLayout(
        int headerHeight,
        int footerTop,
        int contentTop,
        int contentBottom,
        int contentLeft,
        int contentRight,
        int panelWidth,
        int normalNodeSize,
        int implantNodeSize,
        int rootNodeSize,
        int ascensionNodeSize,
        int rowStep,
        int topPadding,
        int totalHeight,
        int leftColumnX,
        int centerColumnX,
        int rightColumnX) {

    // ---------------------------------------------------------------- node sizes, fixed
    public static final int NORMAL_SIZE = 30;
    public static final int IMPLANT_SIZE = 38;
    public static final int ROOT_SIZE = 34;
    public static final int ASCENSION_SIZE = 44;

    /** The picture inside a node: 26 of 42, 33 of 52, 38 of 60. */
    public static final float ICON_FILL = 0.63F;

    public static final int BORDER = 2;

    /**
     * One row of the strip.
     *
     * <p>Tall enough for the biggest ordinary node plus its two label lines plus a gap —
     * 38 + 18 + 6 — so no row can ever write into the one below it, whatever kind of node it holds.
     *
     * <p>Shrunk with the nodes when the tree was pulled back: at 42px nodes and a 78px step only a
     * row and a half was on screen at once, which reads as standing with your nose against the
     * board rather than as looking at a tree.
     */
    public static final int ROW_STEP = 62;

    public static final int LABEL_LINES = 2;
    public static final int LABEL_HEIGHT = 18;

    public static final int FOOTER_HEIGHT = 26;
    public static final int REGION_GAP = 6;
    public static final int TOP_PADDING = 14;
    public static final int BOTTOM_PADDING = 24;

    public static final int PANEL_MIN = 190;
    public static final int PANEL_MAX = 215;
    public static final float PANEL_MAX_FRACTION = 0.28F;

    /** Below this canvas width the panel only appears once something has been picked. */
    public static final int PANEL_OVERLAY_BELOW = 640;

    /** How far one notch of the wheel travels. Roughly a third of a row: smooth, not jumpy. */
    public static final int SCROLL_STEP = 26;

    private static final int MARGIN = 8;

    /**
     * Works out the strip.
     *
     * @param width        canvas width in GUI pixels
     * @param height       canvas height in GUI pixels
     * @param headerHeight what the caller measured the header to need
     * @param rows         how many rows the tree has, root and ascension included
     */
    public static ProgressionTreeLayout compute(int width, int height, int headerHeight, int rows) {
        int footerTop = height - FOOTER_HEIGHT;
        int contentTop = headerHeight + REGION_GAP;
        int contentBottom = footerTop - REGION_GAP;

        int panelWidth = panelWidth(width);
        int contentLeft = MARGIN;
        int contentRight = width - panelWidth - MARGIN;

        int totalHeight = TOP_PADDING + rows * ROW_STEP + BOTTOM_PADDING;

        // Three columns, grouped rather than flung to the corners: a third of a 1080p width each
        // would put the outer nodes against the edges and stop a row reading as one cycle.
        int columnWidth = Math.max(1, (contentRight - contentLeft) / 3);
        // Held apart by a constant, not by the node size: the nodes got smaller and the
        // columns should not have closed in behind them.
        int spread = Math.min(columnWidth, NORMAL_SIZE + 96);
        int centre = (contentLeft + contentRight) / 2;

        return new ProgressionTreeLayout(
                headerHeight, footerTop, contentTop, contentBottom,
                contentLeft, contentRight, panelWidth,
                NORMAL_SIZE, IMPLANT_SIZE, ROOT_SIZE, ASCENSION_SIZE,
                ROW_STEP, TOP_PADDING, totalHeight,
                centre - spread, centre, centre + spread);
    }

    private static int panelWidth(int width) {
        int wanted = Math.round(width * PANEL_MAX_FRACTION);
        return Math.max(PANEL_MIN, Math.min(PANEL_MAX, wanted));
    }

    /** True when the panel is a permanent column; false when it only appears over a selection. */
    public static boolean panelIsColumn(int width) {
        return width >= PANEL_OVERLAY_BELOW;
    }

    public int viewportHeight() {
        return Math.max(1, this.contentBottom - this.contentTop);
    }

    /** How far down the strip the view may travel. Zero when the whole tree already fits. */
    public int maxScroll() {
        return Math.max(0, this.totalHeight - viewportHeight());
    }

    public int clampScroll(int scroll) {
        return Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** The top of a row in strip coordinates, before the scroll offset is taken off. */
    public int rowTop(int row) {
        return this.topPadding + row * this.rowStep;
    }

    /**
     * The scroll that puts a row in the middle of the view.
     *
     * <p>Used when the screen opens on whatever the player last reached: landing with the node
     * centred reads as "here you are", where landing with it at the very top reads as an accident.
     */
    public int scrollToCentre(int row) {
        return clampScroll(rowTop(row) - viewportHeight() / 2 + this.rowStep / 2);
    }

    public int iconSizeFor(int nodeSize) {
        return Math.round(nodeSize * ICON_FILL);
    }

    /** How wide a label under a node may be: node + 28, and node + 50 for an organ. */
    public int labelWidthFor(int nodeSize, boolean major) {
        return nodeSize + (major ? 50 : 28);
    }

    public int contentWidth() {
        return this.contentRight - this.contentLeft;
    }
}
