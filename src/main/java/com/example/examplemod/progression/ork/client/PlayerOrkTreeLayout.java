package com.example.examplemod.progression.ork.client;

/**
 * Where a page of the WAAAGH tree sits.
 *
 * <h2>Pure arithmetic, on purpose</h2>
 *
 * Not one Minecraft type appears here, which is what makes the layout <b>checkable</b>: it can be run
 * outside the game against every resolution and GUI scale and the answer inspected. Geometry worked
 * out inside {@code render()} can only be checked by looking at it, and looking at it is how a screen
 * ships with its bottom row under the hotbar.
 *
 * <h2>Pages, not a scroll strip</h2>
 *
 * The Imperial tree is one tall strip that scrolls, because it is one road with twenty-seven rows on
 * it. This one is five separate boards — Boy, Big Boy, Nob, Big Nob, Boss — and the design says so
 * explicitly: no zoom, no drag, no continuous scroll. A page either fits or the layout is wrong, so
 * there is no scroll offset in this record at all. Nothing can be off-screen for the reader to hunt
 * for.
 *
 * <h2>Which is why the row step is computed and not a constant</h2>
 *
 * At GUI scale 4 on a 1080p monitor the canvas is 480x270, and after the header and the footer there
 * are barely two hundred pixels left. A fixed 62-pixel row — what the Imperial strip uses, where
 * running past the bottom is what scrolling is for — would put the Nob page's third row and its
 * evolution node underneath the screen with no way to reach them. So the step is divided out of the
 * space that actually exists, clamped at both ends, and the node shrinks with it.
 *
 * <h2>Five columns, one per branch</h2>
 *
 * A column is a branch, on every page. That is the whole navigation: the same five verticals in the
 * same five places, so a player who learned where DAKKA lives on the Boy page already knows where it
 * is on the Warboss page.
 */
public record PlayerOrkTreeLayout(
        int headerHeight,
        int footerTop,
        int contentTop,
        int contentBottom,
        int contentLeft,
        int contentRight,
        int panelWidth,
        int nodeSize,
        int evolutionSize,
        int columnStep,
        int firstColumnX,
        int rowStep,
        int firstRowY,
        int evolutionY,
        int labelLines) {

    /** One column per branch: BRUTAL, TUFF, DAKKA, KUNNIN, WAAAGH. */
    public static final int COLUMNS = 5;

    // ---------------------------------------------------------------- node sizes
    public static final int NODE_MAX = 32;
    public static final int NODE_MIN = 20;
    public static final int EVOLUTION_BONUS = 10;

    /** The picture inside a node. */
    public static final float ICON_FILL = 0.66F;

    public static final int BORDER = 2;

    // ---------------------------------------------------------------- rows
    public static final int ROW_STEP_MAX = 62;
    public static final int ROW_STEP_MIN = 40;

    /** One line of name under a node, plus the gap that keeps it off the row below. */
    public static final int LABEL_HEIGHT = 10;
    public static final int ROW_GAP = 4;

    /**
     * The row step from which a name is allowed a second line.
     *
     * <p>One line cut "DAKKA ON DA MOVE" to "DAKKA ON DA" and "BOYZ, OVER 'ERE" to "BOYZ, OVER" —
     * the half of the name that says what the node <i>does</i>. Two lines fix that, but they cost
     * ten pixels out of the row, which a squeezed page does not have. So the second line is granted
     * only when the step can pay for it, and the node keeps its size either way.
     */
    public static final int TWO_LINE_ROW_STEP = NODE_MIN + LABEL_HEIGHT * 2 + ROW_GAP;

    // ---------------------------------------------------------------- chrome
    public static final int FOOTER_HEIGHT = 24;
    public static final int REGION_GAP = 5;
    public static final int TOP_PADDING = 6;

    /**
     * The panel is always a column, and it shrinks with the window.
     *
     * <h2>There used to be an overlay mode, and it was worse</h2>
     *
     * Below a threshold the panel used to float over the board instead of taking width from it. That
     * was meant to stop a fat panel squeezing the columns on a small window — but it meant the whole
     * rightmost branch vanished the moment anything was selected, and on a five-column tree losing a
     * column is losing a fifth of the page. Making the panel switch sides only moved which column
     * disappeared.
     *
     * <p>A narrower minimum solves the thing the overlay was invented for, without any of that: at a
     * 427-pixel canvas the panel takes 132 and the five columns still get 55 each, which is enough
     * for two lines of name. And the board never reflows, never hides anything, and the screen has
     * one layout instead of two.
     */
    public static final int PANEL_MIN = 132;
    public static final int PANEL_MAX = 208;
    public static final float PANEL_MAX_FRACTION = 0.30F;

    private static final int MARGIN = 8;

    /**
     * Works out one page.
     *
     * @param width        canvas width in GUI pixels
     * @param height       canvas height in GUI pixels
     * @param headerHeight what the caller measured the header to need
     * @param branchRows   the tallest branch column on this page, in nodes
     * @param hasEvolution whether this page ends in a "become the next thing" node
     */
    public static PlayerOrkTreeLayout compute(int width, int height, int headerHeight,
                                              int branchRows, boolean hasEvolution) {
        int footerTop = height - FOOTER_HEIGHT;
        int contentTop = headerHeight + REGION_GAP;
        int contentBottom = footerTop - REGION_GAP;

        int panelWidth = panelWidth(width);
        int contentLeft = MARGIN;
        int contentRight = width - panelWidth - MARGIN;

        int rows = Math.max(1, branchRows) + (hasEvolution ? 1 : 0);
        int available = Math.max(1, contentBottom - contentTop - TOP_PADDING);

        // Divided out of the space there is, not out of the space one would like. Clamped at the top
        // so a page with two rows does not fling them to the corners, and at the bottom so a page
        // with four does not squash them into an unreadable stack.
        int rowStep = Math.max(ROW_STEP_MIN, Math.min(ROW_STEP_MAX, available / rows));

        // The node has to leave room for its own label inside its row, whatever the step became.
        int labelLines = rowStep >= TWO_LINE_ROW_STEP ? 2 : 1;
        int rowRoom = rowStep - LABEL_HEIGHT * labelLines - ROW_GAP;
        int nodeSize = Math.max(NODE_MIN, Math.min(NODE_MAX, rowRoom));

        // The evolution node is drawn bigger, but never bigger than its row can hold. Letting the
        // bonus push past the row is how the last node on a page ends up with its name written
        // underneath the hotbar: on a squeezed layout the row is already exactly as tall as a node
        // plus its label, so there is no slack for a node ten pixels taller.
        int evolutionSize = Math.max(nodeSize, Math.min(nodeSize + EVOLUTION_BONUS, rowRoom));

        int columnWidth = Math.max(1, (contentRight - contentLeft) / COLUMNS);
        int firstColumnX = contentLeft + columnWidth / 2;

        // Centred vertically in whatever is left over. The row step is capped, so a short page — the
        // Boy board is one row of skills and a door — used up sixty pixels at the top and left a
        // third of the screen blank underneath. Sharing the slack top and bottom costs one
        // subtraction and makes every page look composed rather than dropped in from above.
        int slack = Math.max(0, available - rows * rowStep);
        int firstRowY = contentTop + TOP_PADDING + slack / 2;

        // The evolution sits on its own last row, centred horizontally by the screen — it belongs to
        // the page rather than to a branch, and putting it in the WAAAGH column would read as a
        // WAAAGH skill rather than as the door out of the page.
        int evolutionY = firstRowY + Math.max(0, rows - 1) * rowStep;

        return new PlayerOrkTreeLayout(
                headerHeight, footerTop, contentTop, contentBottom,
                contentLeft, contentRight, panelWidth,
                nodeSize, evolutionSize,
                columnWidth, firstColumnX,
                rowStep, firstRowY, evolutionY, labelLines);
    }

    /**
     * How wide the panel will be at this canvas width.
     *
     * <p>Public because the header has to wrap against the room it will actually have, and the
     * header is measured <i>before</i> a layout exists — its height is an input to
     * {@link #compute}. Wrapping against {@link #PANEL_MAX} instead pushed the readouts onto four
     * rows on a small window when three of them fitted on two, and every row of header is a row the
     * tree does not get.
     */
    public static int panelWidthFor(int width) {
        int wanted = Math.round(width * PANEL_MAX_FRACTION);
        return Math.max(PANEL_MIN, Math.min(PANEL_MAX, wanted));
    }

    private static int panelWidth(int width) {
        return panelWidthFor(width);
    }

    /** The centre of a branch column, 0 to 4 left to right. */
    public int columnCentre(int column) {
        return this.firstColumnX + column * this.columnStep;
    }

    /** The top of a row inside a column. */
    public int rowTop(int row) {
        return this.firstRowY + row * this.rowStep;
    }

    public int iconSizeFor(int nodeSize) {
        return Math.round(nodeSize * ICON_FILL);
    }

    /** How wide a label under a node may be, so two columns' labels never touch. */
    public int labelWidth() {
        return Math.max(this.nodeSize, this.columnStep - 6);
    }

    public int contentWidth() {
        return this.contentRight - this.contentLeft;
    }

    public int centreX() {
        return (this.contentLeft + this.contentRight) / 2;
    }
}
