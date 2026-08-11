package com.example.examplemod.progression.ork.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.OrkClan;
import com.example.examplemod.progression.PlayerProgressionClientView;
import com.example.examplemod.progression.PlayerProgressionProfile;
import com.example.examplemod.progression.ProgressionActionPacket;
import com.example.examplemod.progression.ork.PlayerOrkEvolutionChecklist;
import com.example.examplemod.progression.ork.PlayerOrkEvolutionStage;
import com.example.examplemod.progression.ork.PlayerOrkIcon;
import com.example.examplemod.progression.ork.PlayerOrkNodeEffects;
import com.example.examplemod.progression.ork.PlayerOrkProgressionBalance;
import com.example.examplemod.progression.ork.PlayerOrkProgressionProfile;
import com.example.examplemod.progression.ork.PlayerOrkProgressionRequirements;
import com.example.examplemod.progression.ork.PlayerOrkProgressionTree;
import com.example.examplemod.progression.ork.PlayerOrkSkillBranch;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * DA WAAAGH! TREE — five boards, one per size of Ork.
 *
 * <h2>Why this is not a tab on the Imperial screen</h2>
 *
 * {@code PlayerProgressionScreen} is already fourteen hundred lines carrying two architectures — the
 * Astartes strip and the Command tree — with a scroll offset per tab, a drag handler and a header
 * built around Doctrine Points. Adding a third vocabulary to it would have meant a third set of
 * branches in every one of its methods, and every one of those branches would have had to remember
 * not to show an Ork a Gene Seed readout. A separate screen has no such branch to forget.
 *
 * <h2>Pages, and why they are not scrolling</h2>
 *
 * The Imperial tree is one road and scrolls like one. This is five boards, and the design says so:
 * no zoom, no drag, no continuous scroll. The reason is that an Ork's tree is gated by <i>size</i> —
 * a Boy cannot buy anything on the Nob board no matter how much he scrolls to it — so a page break
 * at each rung is not an arbitrary chop, it is the shape of the thing. It also means nothing is ever
 * off-screen: {@link PlayerOrkTreeLayout} divides the rows out of the height that exists rather than
 * running past the bottom and expecting the reader to go looking.
 *
 * <p>The wheel moves exactly one page per notch, with a short debounce, because a trackpad reports a
 * flick as a dozen notches and five pages would go past in one gesture.
 *
 * <h2>Nothing Imperial is on it</h2>
 *
 * No Doctrine, no XP, no implants, no Gene Seed, no Commander. Those are the Imperium's words for
 * progress, and an Ork screen that borrowed them would be telling the player his WAAAGH is the other
 * faction's system with a green header.
 *
 * <h2>The client decides nothing</h2>
 *
 * Every grey-out and every checklist tick comes from
 * {@link PlayerOrkProgressionRequirements#checkBuyRules} and
 * {@link PlayerOrkEvolutionChecklist} — the same code and the same table the server refuses with. A
 * click sends an id. Cost, rank, eligibility and stage are all decided again on the server.
 */
public class PlayerOrkProgressionScreen extends Screen {

    // ---------------------------------------------------------------- palette
    //
    // Green, rust and bone. Deliberately nothing from the Imperial screen's red-and-gold: the two
    // trees should not be mistakable for each other at a glance across a room.

    private static final int BACKGROUND = 0xF0080A07;
    private static final int CHROME = 0xFF0E1410;
    private static final int FRAME = 0xFF2A3A22;
    private static final int LINE_DONE = 0xFF6ABF3A;
    private static final int LINE_OPEN = 0xFF3E6B28;
    private static final int LINE_LOCKED = 0xFF232A20;
    private static final int GREEN = 0xFF6ADF3A;
    private static final int WHITE = 0xFFEDF0E6;
    private static final int TEXT = 0xFFBCC4B4;
    private static final int TEXT_DIM = 0xFF6E7868;
    private static final int YELLOW = 0xFFD8B03C;
    private static final int RED = 0xFFC05048;
    private static final int NODE_LOCKED = 0xFF262B24;
    private static final int NODE_OPEN = 0xFF2F4A22;
    private static final int NODE_DONE = 0xFF54962C;

    private static final int PAGES = PlayerOrkEvolutionStage.values().length;

    /**
     * Milliseconds a wheel notch blocks the next one.
     *
     * <p>A trackpad flick arrives as ten or more notches in a few frames. Without this, one gesture
     * crosses all five pages and lands wherever it ran out — which reads as the screen having no
     * pages at all.
     */
    private static final long WHEEL_DEBOUNCE_MS = 150L;

    /**
     * The page the screen opens on.
     *
     * <p>Static, so closing and reopening within a session comes back where it was left. Not
     * persisted: a fresh session opens on whatever the player currently is, which is set in
     * {@link #init}.
     */
    @Nullable
    private static PlayerOrkEvolutionStage lastPage;

    private PlayerOrkEvolutionStage page = PlayerOrkEvolutionStage.ORK_BOY;

    private PlayerOrkTreeLayout layout;
    private final List<Placed> placed = new ArrayList<>();

    /** The header's readouts, already wrapped to lines that fit beside the panel. */
    private final List<List<Component>> headerLines = new ArrayList<>();

    @Nullable
    private PlayerOrkProgressionTree.Node selected;

    private long lastWheelMs;
    private float pulse;

    /**
     * The y the panel's text must stop above — the top of the action button.
     *
     * <p>Set while the panel renders and zero otherwise, so the tree's own text is never clipped by
     * a limit that belongs to the panel.
     */
    private int panelFloor;

    /** Open when the player is a Nob with no klan. Nothing else on the screen works until it closes. */
    private boolean klanModal;

    /** A node and the box it occupies. */
    private record Placed(PlayerOrkProgressionTree.Node node, int x, int y, int size) {
        int centreX() {
            return this.x + this.size / 2;
        }

        int centreY() {
            return this.y + this.size / 2;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.size
                    && mouseY >= this.y && mouseY < this.y + this.size;
        }
    }

    public PlayerOrkProgressionScreen() {
        super(Component.translatable("gui.firstcrusade.ork.title"));
    }

    // ==================================================================== setup

    @Override
    protected void init() {
        PlayerOrkProgressionProfile ork = ork();

        // Opens on what he is, not on the bottom of the ladder: a Big Nob who presses K wants his
        // own board, and landing on the Boy page every time reads as the screen having forgotten him.
        if (lastPage == null) {
            this.page = ork == null ? PlayerOrkEvolutionStage.ORK_BOY : ork.stage();
        } else {
            this.page = lastPage;
        }

        // A Nob without a klan cannot buy anything at all — the server refuses every node — so the
        // modal is not a nag, it is the only thing on this screen that would do anything.
        this.klanModal = ork != null
                && ork.stage().isAtLeast(PlayerOrkEvolutionStage.ORK_NOB) && !ork.hasClan();

        rebuild();
    }

    /** Recomputes the header, the layout and where this page's nodes sit. */
    private void rebuild() {
        buildHeader();

        List<PlayerOrkProgressionTree.Node> evolution = new ArrayList<>();
        PlayerOrkProgressionTree.Node root = null;

        Map<PlayerOrkSkillBranch, List<PlayerOrkProgressionTree.Node>> columns =
                new EnumMap<>(PlayerOrkSkillBranch.class);

        for (PlayerOrkSkillBranch branch : PlayerOrkSkillBranch.values()) {
            columns.put(branch, new ArrayList<>());
        }

        for (PlayerOrkProgressionTree.Node node : PlayerOrkProgressionTree.all()) {
            if (node.minimumStage() != this.page) {
                continue;
            }

            if (node.id().equals(PlayerOrkProgressionTree.ROOT_ID)) {
                // The root gets a row to itself. Left in the WAAAGH column it landed level with the
                // four skills that name it as a parent, and every join then ran down out of it,
                // sideways, and back UP into a node on the same row — a horizontal rail straight
                // through the row of labels. A parent has to be above its children for an elbow to
                // mean anything.
                root = node;
            } else if (node.isEvolution()) {
                evolution.add(node);
            } else {
                columns.get(node.branch()).add(node);
            }
        }

        int tallest = 0;
        for (List<PlayerOrkProgressionTree.Node> column : columns.values()) {
            tallest = Math.max(tallest, column.size());
        }

        int rootRows = root == null ? 0 : 1;
        int headerHeight = 8 + this.headerLines.size() * 11 + 4;

        this.layout = PlayerOrkTreeLayout.compute(this.width, this.height, headerHeight,
                tallest + rootRows, !evolution.isEmpty());

        this.placed.clear();

        if (root != null) {
            int size = this.layout.evolutionSize();
            this.placed.add(new Placed(root, this.layout.centreX() - size / 2,
                    this.layout.rowTop(0), size));
        }

        PlayerOrkSkillBranch[] branches = PlayerOrkSkillBranch.values();
        for (int column = 0; column < branches.length; column++) {
            List<PlayerOrkProgressionTree.Node> nodes = columns.get(branches[column]);

            for (int row = 0; row < nodes.size(); row++) {
                int size = this.layout.nodeSize();
                this.placed.add(new Placed(nodes.get(row),
                        this.layout.columnCentre(column) - size / 2,
                        this.layout.rowTop(row + rootRows), size));
            }
        }

        // The evolution belongs to the page rather than to a branch, so it sits centred on its own
        // last row. Putting it in the WAAAGH column would read as a WAAAGH skill.
        for (PlayerOrkProgressionTree.Node node : evolution) {
            int size = this.layout.evolutionSize();
            this.placed.add(new Placed(node, this.layout.centreX() - size / 2,
                    this.layout.evolutionY(), size));
        }

        // A selection from the previous page is not on this one.
        if (this.selected != null && this.selected.minimumStage() != this.page) {
            this.selected = null;
        }
    }

    /**
     * The seven readouts, wrapped into lines that fit left of the panel.
     *
     * <p>Measured before being drawn. The Imperial header's first version wrote its readouts in one
     * row from a fixed x and let the last one run off the edge; there is no reason to ship that bug
     * twice.
     */
    private void buildHeader() {
        this.headerLines.clear();

        PlayerOrkProgressionProfile ork = ork();
        if (ork == null) {
            return;
        }

        long now = this.minecraft == null || this.minecraft.player == null
                ? 0L : this.minecraft.player.level().getGameTime();

        List<Component> readouts = List.of(
                readout("size", ork.stage().displayName()),
                readout("krump", Component.literal(String.valueOf(ork.krumpScore()))),
                readout("teef", Component.literal(String.valueOf(ork.teef()))),
                readout("fury", Component.literal(ork.fury(now) + "/"
                        + PlayerOrkProgressionBalance.FURY_MAX)),
                readout("klan", ork.hasClan()
                        ? Component.literal(ork.clan().getDisplayName())
                        : Component.translatable("gui.firstcrusade.ork.no_klan")),
                readout("waaagh", Component.literal(
                        String.valueOf(PlayerProgressionClientView.globalWaaaghTier()))),
                readout("page", Component.literal((this.page.ordinal() + 1) + "/" + PAGES)));

        // Wrapped against the room the header actually has, which is everything left of the panel —
        // and against the panel's real width, not its maximum. The layout does not exist yet when
        // this runs (the header's height is an input to it), so the width is asked for directly.
        int available = Math.max(80,
                this.width - PlayerOrkTreeLayout.panelWidthFor(this.width) - 24);

        List<Component> line = new ArrayList<>();

        // The first row starts after the title, so it has that much less to spend. Budgeting every
        // row the same is how the title ends up written over the first readout on a narrow window.
        int used = titleWidth();

        for (Component readout : readouts) {
            int wanted = this.font.width(readout) + 14;

            if (!line.isEmpty() && used + wanted > available) {
                this.headerLines.add(List.copyOf(line));
                line.clear();
                used = 0;
            }

            line.add(readout);
            used += wanted;
        }

        if (!line.isEmpty()) {
            this.headerLines.add(List.copyOf(line));
        }
    }

    private Component readout(String key, Component value) {
        return Component.translatable("gui.firstcrusade.ork.head." + key, value);
    }

    // ==================================================================== rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.pulse += partialTick * 0.06F;

        graphics.fill(0, 0, this.width, this.height, BACKGROUND);

        // The readouts are rebuilt every frame, not cached from init(). Teef, Fury and the klan all
        // change while the screen is open — picking a klan left the header still reading "KLAN:
        // none yet" until the screen was closed and reopened, which reads as the choice not having
        // taken. Seven strings and seven font measurements per frame is nothing; a stale header is
        // a player clicking the same button twice.
        refreshHeader();

        // The klan modal is exclusive: it swallows every click and nothing behind it can be used
        // until it closes. So nothing behind it is drawn either — not as tidiness, but because
        // GuiGraphics queues text and flushes it after every fill, so a tree drawn first prints its
        // whole vocabulary straight through the modal's blackout. Not drawing it is both the fix and
        // the honest rendering of "there is one decision to make here".
        if (this.klanModal) {
            renderKlanModal(graphics, mouseX, mouseY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderHeader(graphics);

        // The tree is clipped to the board, and the board stops where the panel starts.
        //
        // This is not tidiness — it is the only thing that works. GuiGraphics does not draw text
        // when asked; it queues it into a buffer that is flushed by render type at the end of the
        // frame, so the tree's labels come out *after* any fill, however late that fill is painted.
        // Painting the panel over them printed "RUN TA KRUMP" and a stray "0/3" straight through the
        // panel and over its own text, and an explicit flush() did not fix it either. A scissor
        // does, because applyScissor flushes and then clips everything that follows — so the
        // lettering is drawn while the clip is still on, and lands inside the board or not at all.
        graphics.enableScissor(0, this.layout.headerHeight(), panelLeft(),
                this.layout.footerTop());
        renderTree(graphics, mouseX, mouseY);
        graphics.disableScissor();

        renderFooter(graphics);
        renderPanel(graphics, mouseX, mouseY);
        renderHoverTooltip(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * The full name and rank of whatever the cursor is over.
     *
     * <h3>Why a tooltip and not a bigger label</h3>
     *
     * A dense page — the Nob board is three rows of branches plus the evolution — cannot give each
     * name two lines on a small window: four rows into two hundred pixels leaves a row step of
     * thirty-eight, and a node plus two lines of text needs forty-four. So on exactly the page with
     * the most nodes on it, "BIG CHOPPA" is cut to "BIG". Every way of buying that back costs
     * something worse: a smaller node, a scaled-down font at four pixels tall, or fewer nodes per
     * page than the tier actually has.
     *
     * <p>The tooltip sidesteps the trade entirely. The label stays as big a hint as fits, and the
     * full name is one hover away — without a click, which is what the panel already costs.
     */
    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerOrkProgressionProfile ork = ork();
        if (ork == null) {
            return;
        }

        for (Placed placed : this.placed) {
            if (!placed.contains(mouseX, mouseY)) {
                continue;
            }

            PlayerOrkProgressionTree.Node node = placed.node();
            List<Component> lines = new ArrayList<>();

            lines.add(node.displayName().copy().withStyle(ChatFormatting.GREEN));

            if (node.maxRank() > 1) {
                lines.add(Component.translatable("gui.firstcrusade.ork.rank",
                        ork.rank(node.id()), node.maxRank()).withStyle(ChatFormatting.GRAY));
            }

            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    /** The panel's left edge. A fixed column on the right, at every window size. */
    private int panelLeft() {
        return this.width - this.layout.panelWidth();
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.layout.headerHeight(), CHROME);
        graphics.fill(0, this.layout.headerHeight() - 1, this.width, this.layout.headerHeight(),
                FRAME);

        graphics.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.title"), 8, 5, GREEN, false);

        int y = 5;

        // Indexed rather than indexOf: two header rows can hold equal lists — two rows of the same
        // readouts is unlikely but a list's indexOf compares by value, so it would silently indent
        // the wrong one.
        for (int index = 0; index < this.headerLines.size(); index++) {
            int x = index == 0 ? 8 + titleWidth() : 8;

            for (Component readout : this.headerLines.get(index)) {
                graphics.drawString(this.font, readout, x, y, TEXT, false);
                x += this.font.width(readout) + 14;
            }

            y += 11;
        }
    }

    /**
     * Rebuilds the readouts, and reflows the whole board only if that changed the row count.
     *
     * <p>The header's height feeds the layout, so a header that grew a row while the screen was open
     * would draw over the top row of nodes. Comparing the count is what keeps the common case — the
     * numbers changing inside the same number of rows — free of a layout pass.
     */
    private void refreshHeader() {
        int before = this.headerLines.size();
        buildHeader();

        if (this.headerLines.size() != before) {
            rebuild();
        }
    }

    /** How much room the title takes on the header's first row. */
    private int titleWidth() {
        return this.font.width(Component.translatable("gui.firstcrusade.ork.title")) + 16;
    }

    private void renderTree(GuiGraphics graphics, int mouseX, int mouseY) {
        PlayerOrkProgressionProfile ork = ork();
        if (ork == null) {
            return;
        }

        // The joins first, so a line never crosses the face of a node.
        for (Placed placed : this.placed) {
            for (String parentId : placed.node().parents()) {
                Placed parent = find(parentId);
                if (parent == null) {
                    // The parent is on an earlier page. A stub pointing up says "this comes from
                    // somewhere back there" without pretending to draw a line off the board.
                    graphics.fill(placed.centreX(), placed.y() - 6,
                            placed.centreX() + 1, placed.y(),
                            ork.rank(parentId) > 0 ? LINE_DONE : LINE_LOCKED);
                    continue;
                }

                join(graphics, parent, placed, ork);
            }
        }

        for (Placed placed : this.placed) {
            renderNode(graphics, placed, ork, mouseX, mouseY);
        }
    }

    /** An elbow, never a diagonal: down out of the parent, across, then down into the child. */
    private void join(GuiGraphics graphics, Placed parent, Placed child,
                      PlayerOrkProgressionProfile ork) {
        int colour = ork.rank(child.node().id()) > 0 ? LINE_DONE
                : ork.rank(parent.node().id()) > 0 ? LINE_OPEN : LINE_LOCKED;

        int fromY = parent.y() + parent.size();
        int toY = child.y();

        // The crossbar goes below the parent's name, not halfway between the two nodes. Halfway put
        // it straight through the label — the vertical segments are hidden behind the text that is
        // drawn after them, but a horizontal rail running the width of the board is not.
        int clearOfLabel = fromY + PlayerOrkTreeLayout.LABEL_HEIGHT + 2;
        int midY = toY - fromY > PlayerOrkTreeLayout.LABEL_HEIGHT + 6
                ? clearOfLabel
                : fromY + Math.max(2, (toY - fromY) / 2);

        graphics.fill(parent.centreX(), fromY, parent.centreX() + 1, midY, colour);

        int left = Math.min(parent.centreX(), child.centreX());
        int right = Math.max(parent.centreX(), child.centreX()) + 1;
        graphics.fill(left, midY, right, midY + 1, colour);

        graphics.fill(child.centreX(), midY, child.centreX() + 1, toY, colour);
    }

    private void renderNode(GuiGraphics graphics, Placed placed, PlayerOrkProgressionProfile ork,
                            int mouseX, int mouseY) {
        PlayerOrkProgressionTree.Node node = placed.node();
        int rank = ork.rank(node.id());
        boolean owned = rank > 0;
        boolean open = !owned && available(ork, node);

        int fill = owned ? NODE_DONE : open ? NODE_OPEN : NODE_LOCKED;
        int border = node.branch().colour();

        if (!owned && !open) {
            border = LINE_LOCKED;
        }

        if (this.selected == node) {
            // The pulse is on the selection only. A screen where everything breathes is a screen
            // where nothing is highlighted.
            border = ((int) (Math.sin(this.pulse) * 40) + 215) << 24 | (border & 0x00FFFFFF);
        }

        graphics.fill(placed.x() - PlayerOrkTreeLayout.BORDER, placed.y() - PlayerOrkTreeLayout.BORDER,
                placed.x() + placed.size() + PlayerOrkTreeLayout.BORDER,
                placed.y() + placed.size() + PlayerOrkTreeLayout.BORDER, border);
        graphics.fill(placed.x(), placed.y(), placed.x() + placed.size(),
                placed.y() + placed.size(), fill);

        PlayerOrkIcon icon = PlayerOrkIcon.of(node);
        int iconSize = this.layout.iconSizeFor(placed.size());
        int iconX = placed.x() + (placed.size() - iconSize) / 2;
        int iconY = placed.y() + (placed.size() - iconSize) / 2;

        // A locked node's picture is drained rather than hidden: the reader should be able to see
        // what he is working towards, and just not be able to mistake it for something he has.
        if (!owned && !open) {
            graphics.setColor(0.45F, 0.48F, 0.44F, 0.7F);
        }

        blitIcon(graphics, icon, iconX, iconY, iconSize);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Rank pips, bottom right. Only for nodes that have more than one rank — a "1/1" on a
        // one-shot is a readout that never changes.
        if (node.maxRank() > 1) {
            String pips = rank + "/" + node.maxRank();
            graphics.drawString(this.font, pips,
                    placed.x() + placed.size() - this.font.width(pips) + 1,
                    placed.y() + placed.size() - 7,
                    owned ? WHITE : TEXT_DIM, true);
        }

        // As many lines as the row can pay for — one on a squeezed page, two when there is room.
        // A single line cut "DAKKA ON DA MOVE" to "DAKKA ON DA", losing exactly the half that says
        // what the node does. Anything past the allowance is still cut rather than allowed to write
        // into the row below.
        List<FormattedCharSequence> wrapped =
                this.font.split(node.displayName(), this.layout.labelWidth());

        int labelY = placed.y() + placed.size() + 3;
        int colour = owned ? WHITE : open ? TEXT : TEXT_DIM;

        for (int line = 0; line < Math.min(wrapped.size(), this.layout.labelLines()); line++) {
            graphics.drawCenteredString(this.font, wrapped.get(line), placed.centreX(), labelY,
                    colour);
            labelY += PlayerOrkTreeLayout.LABEL_HEIGHT;
        }

        if (placed.contains(mouseX, mouseY)) {
            graphics.fill(placed.x(), placed.y(), placed.x() + placed.size(),
                    placed.y() + placed.size(), 0x30FFFFFF);
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        graphics.fill(0, this.layout.footerTop(), this.width, this.height, CHROME);
        graphics.fill(0, this.layout.footerTop(), this.width, this.layout.footerTop() + 1, FRAME);

        int y = this.layout.footerTop() + 8;

        // The page strip: five marks, the current one filled. It is the whole navigation model in
        // twenty pixels, and it is what tells a reader there are exactly five boards.
        int markWidth = 14;
        int stripWidth = PAGES * (markWidth + 3);
        int x = 8;

        for (int index = 0; index < PAGES; index++) {
            boolean current = index == this.page.ordinal();
            graphics.fill(x, y, x + markWidth, y + 3, current ? GREEN : LINE_LOCKED);
            x += markWidth + 3;
        }

        graphics.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.page",
                        this.page.displayName(), this.page.ordinal() + 1, PAGES),
                8 + stripWidth + 8, y - 2, TEXT, false);

        Component hint = Component.translatable("gui.firstcrusade.ork.page_hint");
        graphics.drawString(this.font, hint, this.width - this.font.width(hint) - 8, y - 2,
                TEXT_DIM, false);
    }

    // ==================================================================== the panel

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelWidth = this.layout.panelWidth();
        int left = panelLeft();

        graphics.fill(left, this.layout.headerHeight(), this.width, this.layout.footerTop(), CHROME);
        graphics.fill(left, this.layout.headerHeight(), left + 1, this.layout.footerTop(), FRAME);

        PlayerOrkProgressionTree.Node node = this.selected;
        PlayerOrkProgressionProfile ork = ork();

        if (node == null || ork == null) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.ork.pick_one"),
                    left + 8, this.layout.headerHeight() + 10, TEXT_DIM, false);
            return;
        }

        int x = left + 8;
        int inner = panelWidth - 16;
        int y = this.layout.headerHeight() + 8;

        this.panelFloor = actionBox(left, panelWidth)[1] - 4;

        PlayerOrkIcon icon = PlayerOrkIcon.of(node);
        blitIcon(graphics, icon, x, y, 24);

        y = drawWrapped(graphics, node.displayName().copy().withStyle(ChatFormatting.BOLD),
                x + 30, y + 2, inner - 30, WHITE);
        y = Math.max(y, this.layout.headerHeight() + 8 + 26) + 4;

        y = drawWrapped(graphics, node.description(), x, y, inner, TEXT_DIM) + 5;

        if (node.isEvolution()) {
            y = renderEvolutionPanel(graphics, node, ork, x, y, inner);
        } else {
            y = renderSkillPanel(graphics, node, ork, x, y, inner);
        }

        renderActionButton(graphics, node, ork, left, panelWidth, mouseX, mouseY);
        this.panelFloor = 0;
    }

    /** Rank, effect now, effect next, cost, parents, minimum size, and why not. */
    private int renderSkillPanel(GuiGraphics graphics, PlayerOrkProgressionTree.Node node,
                                 PlayerOrkProgressionProfile ork, int x, int y, int inner) {
        int rank = ork.rank(node.id());

        if (node.maxRank() > 1) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.ork.rank", rank, node.maxRank()),
                    x, y, YELLOW, false);
            y += 11;
        }

        Component now = PlayerOrkNodeEffects.describe(node, rank);
        if (now != null) {
            y = drawWrapped(graphics,
                    Component.translatable("gui.firstcrusade.ork.effect_now", now), x, y, inner,
                    LINE_DONE) + 2;
        }

        Component next = PlayerOrkNodeEffects.describeNext(node, rank);
        if (next != null) {
            y = drawWrapped(graphics,
                    Component.translatable("gui.firstcrusade.ork.effect_next", next), x, y, inner,
                    TEXT) + 2;
        }

        if (rank < node.maxRank()) {
            int cost = node.costFor(rank + 1);
            boolean afford = ork.teef() >= cost;

            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.ork.cost", cost),
                    x, y, afford ? YELLOW : RED, false);
            y += 12;
        }

        y = renderRequirements(graphics, node, ork, x, y, inner);

        // Why it cannot be bought, in the server's own words — this is the same check the server
        // refuses with, so the panel can never promise something the click will not deliver.
        PlayerOrkProgressionRequirements.Result check =
                PlayerOrkProgressionRequirements.checkBuyRules(ork, node);

        if (!check.ok()) {
            y = drawWrapped(graphics, check.reason(), x, y + 2, inner, RED);
        }

        return y;
    }

    private int renderRequirements(GuiGraphics graphics, PlayerOrkProgressionTree.Node node,
                                   PlayerOrkProgressionProfile ork, int x, int y, int inner) {
        if (node.minimumStage() != PlayerOrkEvolutionStage.ORK_BOY) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.ork.needs_size",
                            node.minimumStage().displayName()),
                    x, y, ork.stage().isAtLeast(node.minimumStage()) ? TEXT_DIM : RED, false);
            y += 11;
        }

        for (String parentId : node.parents()) {
            if (parentId.equals(PlayerOrkProgressionTree.ROOT_ID)) {
                continue;
            }

            PlayerOrkProgressionTree.Node parent = PlayerOrkProgressionTree.node(parentId);
            if (parent == null) {
                continue;
            }

            y = drawWrapped(graphics,
                    Component.translatable("gui.firstcrusade.ork.needs_node", parent.displayName()),
                    x, y, inner, ork.rank(parentId) > 0 ? TEXT_DIM : RED);
        }

        return y;
    }

    /**
     * An evolution shows a checklist, never a cost.
     *
     * <p>There is nothing to save up for: the rung is bought with things already done. A Teef price
     * on this panel would be a price that does not exist.
     */
    private int renderEvolutionPanel(GuiGraphics graphics, PlayerOrkProgressionTree.Node node,
                                     PlayerOrkProgressionProfile ork, int x, int y, int inner) {
        graphics.drawString(this.font,
                Component.translatable("gui.firstcrusade.ork.checklist"), x, y, YELLOW, false);
        y += 12;

        List<PlayerOrkEvolutionChecklist.Line> lines = PlayerOrkEvolutionChecklist.of(
                ork, node.stage(), PlayerProgressionClientView.globalWaaaghTier());

        for (PlayerOrkEvolutionChecklist.Line line : lines) {
            String mark = line.met() ? "✔ " : "✖ ";

            graphics.drawString(this.font,
                    Component.literal(mark).append(line.label())
                            .append(Component.literal(" " + line.have() + "/" + line.need())),
                    x, y, line.met() ? LINE_DONE : RED, false);
            y += 11;
        }

        if (ork.stage().next() != node.stage()) {
            y = drawWrapped(graphics, Component.translatable("msg.firstcrusade.ork.wrong_step"),
                    x, y + 2, inner, TEXT_DIM);
        }

        return y;
    }

    private void renderActionButton(GuiGraphics graphics, PlayerOrkProgressionTree.Node node,
                                    PlayerOrkProgressionProfile ork, int left, int panelWidth,
                                    int mouseX, int mouseY) {
        int[] box = actionBox(left, panelWidth);
        boolean enabled = actionEnabled(node, ork);
        boolean hovered = enabled && mouseX >= box[0] && mouseX < box[2]
                && mouseY >= box[1] && mouseY < box[3];

        graphics.fill(box[0], box[1], box[2], box[3], enabled ? (hovered ? NODE_DONE : NODE_OPEN)
                : LINE_LOCKED);
        graphics.fill(box[0], box[1], box[2], box[1] + 1, enabled ? LINE_DONE : FRAME);

        graphics.drawCenteredString(this.font, actionLabel(node, ork),
                (box[0] + box[2]) / 2, box[1] + 6, enabled ? WHITE : TEXT_DIM);
    }

    private int[] actionBox(int left, int panelWidth) {
        int bottom = this.layout.footerTop() - 8;
        return new int[]{left + 8, bottom - 18, left + panelWidth - 8, bottom};
    }

    private Component actionLabel(PlayerOrkProgressionTree.Node node,
                                  PlayerOrkProgressionProfile ork) {
        if (node.isEvolution()) {
            return Component.translatable("gui.firstcrusade.ork.grow");
        }

        int rank = ork.rank(node.id());
        return rank >= node.maxRank()
                ? Component.translatable("gui.firstcrusade.ork.maxed")
                : Component.translatable("gui.firstcrusade.ork.buy", node.costFor(rank + 1));
    }

    private boolean actionEnabled(PlayerOrkProgressionTree.Node node,
                                  PlayerOrkProgressionProfile ork) {
        if (node.isEvolution()) {
            return ork.stage().next() == node.stage()
                    && PlayerOrkEvolutionChecklist.complete(PlayerOrkEvolutionChecklist.of(
                            ork, node.stage(), PlayerProgressionClientView.globalWaaaghTier()));
        }

        return PlayerOrkProgressionRequirements.checkBuyRules(ork, node).ok();
    }

    // ==================================================================== the klan modal

    /**
     * Five big plates, and no way past them.
     *
     * <p>A Nob without a klan is refused every purchase by the server, so a dismissible prompt would
     * leave a player clicking nodes that silently do nothing. The choice is permanent, which is why
     * it gets the whole screen rather than a dropdown in a corner.
     */
    private void renderKlanModal(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xF0060A05);

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.firstcrusade.ork.title"), this.width / 2, 16, GREEN);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.firstcrusade.ork.pick_klan"),
                this.width / 2, 34, WHITE);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.firstcrusade.ork.pick_klan_hint"),
                this.width / 2, 48, TEXT_DIM);

        OrkClan[] clans = OrkClan.values();

        for (int index = 0; index < clans.length; index++) {
            int[] box = klanBox(index);
            boolean hovered = mouseX >= box[0] && mouseX < box[2]
                    && mouseY >= box[1] && mouseY < box[3];

            graphics.fill(box[0], box[1], box[2], box[3], hovered ? NODE_OPEN : CHROME);
            graphics.fill(box[0], box[1], box[2], box[1] + 2, hovered ? GREEN : FRAME);

            graphics.drawCenteredString(this.font,
                    Component.literal(clans[index].getDisplayName()),
                    (box[0] + box[2]) / 2, box[1] + 10, hovered ? WHITE : TEXT);

            List<FormattedCharSequence> tactics = this.font.split(
                    Component.translatable("gui.firstcrusade.ork.klan." + clans[index].name()
                            .toLowerCase(java.util.Locale.ROOT)),
                    box[2] - box[0] - 10);

            int y = box[1] + 24;
            for (FormattedCharSequence line : tactics) {
                graphics.drawCenteredString(this.font, line, (box[0] + box[2]) / 2, y, TEXT_DIM);
                y += 10;
            }
        }
    }

    /** The five plates, side by side across the middle of the screen. */
    private int[] klanBox(int index) {
        int count = OrkClan.values().length;
        int gap = 6;
        int available = this.width - 32 - gap * (count - 1);
        int plateWidth = Math.max(60, available / count);
        int left = 16 + index * (plateWidth + gap);
        int top = this.height / 2 - 40;

        return new int[]{left, top, left + plateWidth, top + 80};
    }

    // ==================================================================== input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.klanModal) {
            OrkClan[] clans = OrkClan.values();

            for (int index = 0; index < clans.length; index++) {
                int[] box = klanBox(index);
                if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) {
                    // The name travels; the server resolves it strictly and refuses anything else.
                    FirstCrusadeNetwork.CHANNEL.sendToServer(
                            ProgressionActionPacket.orkSelectClan(clans[index].name()));
                    this.klanModal = false;
                    return true;
                }
            }

            // Nothing else on the screen is reachable while the modal is up.
            return true;
        }

        PlayerOrkProgressionProfile ork = ork();

        if (this.selected != null && ork != null) {
            int[] box = actionBox(panelLeft(), this.layout.panelWidth());

            if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3]) {
                act(this.selected, ork);
                return true;
            }
        }

        for (Placed placed : this.placed) {
            if (placed.contains(mouseX, mouseY)) {
                this.selected = placed.node();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Sends the id, and nothing else.
     *
     * <p>No cost, no rank, no claim about eligibility. {@link #actionEnabled} decided whether to
     * draw the button lit; the server decides whether anything happens.
     */
    private void act(PlayerOrkProgressionTree.Node node, PlayerOrkProgressionProfile ork) {
        if (!actionEnabled(node, ork)) {
            return;
        }

        FirstCrusadeNetwork.CHANNEL.sendToServer(node.isEvolution()
                ? ProgressionActionPacket.orkEvolve(node.id())
                : ProgressionActionPacket.orkUnlock(node.id()));

        // The server answers with a fresh profile; asking for it now means the panel updates on the
        // next frame rather than the next time the screen is opened.
        FirstCrusadeNetwork.CHANNEL.sendToServer(ProgressionActionPacket.request());
    }

    /**
     * Exactly one page per notch, however many notches the hardware reports.
     *
     * <p>The debounce is the whole point: a trackpad flick is a dozen events in three frames, and
     * without a floor between them one gesture crosses every page the tree has.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.klanModal || delta == 0.0D) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastWheelMs < WHEEL_DEBOUNCE_MS) {
            return true;
        }

        this.lastWheelMs = now;
        turnPage(delta > 0.0D ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.klanModal) {
            // Escape closes the screen, not the modal: the choice is not optional, and a modal that
            // can be dismissed is a modal a player gets stuck behind without knowing why.
            return keyCode == GLFW.GLFW_KEY_ESCAPE
                    ? super.keyPressed(keyCode, scanCode, modifiers) : true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            turnPage(-1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            turnPage(1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Moves a page and stops at the ends. Wrapping around would lose the reader. */
    private void turnPage(int step) {
        int target = Math.max(0, Math.min(PAGES - 1, this.page.ordinal() + step));

        if (target == this.page.ordinal()) {
            return;
        }

        this.page = PlayerOrkEvolutionStage.values()[target];
        lastPage = this.page;
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================================================================== plumbing

    @Nullable
    private PlayerOrkProgressionProfile ork() {
        PlayerProgressionProfile self = PlayerProgressionClientView.self();
        return self == null ? null : self.ork();
    }

    @Nullable
    private Placed find(String nodeId) {
        for (Placed placed : this.placed) {
            if (placed.node().id().equals(nodeId)) {
                return placed;
            }
        }

        return null;
    }

    private boolean available(PlayerOrkProgressionProfile ork,
                              PlayerOrkProgressionTree.Node node) {
        if (node.isEvolution()) {
            return ork.stage().next() == node.stage();
        }

        return PlayerOrkProgressionRequirements.checkBuyRules(ork, node).ok();
    }

    /**
     * Draws the whole 40x40 icon scaled into a box of {@code size}.
     *
     * <p>The texture's real dimensions are deliberately <b>not</b> passed as the last two arguments.
     * {@code blit} builds its UVs as {@code uOffset / textureWidth}, so passing the true 40 with a
     * destination of 26 samples the top-left 26 pixels of the picture and throws the rest away —
     * which looks exactly like a broken icon and not at all like a scaling bug. Declaring the
     * texture to be the size being drawn makes the UVs run 0 to 1, which is what "draw all of it,
     * this big" means here. Same call the Imperial tree makes, for the same reason.
     */
    private void blitIcon(GuiGraphics graphics, PlayerOrkIcon icon, int x, int y, int size) {
        graphics.blit(icon.texture(), x, y, 0, 0, size, size, size, size);
    }

    /**
     * Draws a component wrapped to a width and returns the y below it.
     *
     * <p>Stops at {@link #panelFloor}, which is the top of the action button. A long description on a
     * short window would otherwise write straight through the button and out of the panel — and the
     * button is the only thing on the panel that does anything.
     */
    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width,
                            int colour) {
        for (FormattedCharSequence line : this.font.split(text, width)) {
            if (this.panelFloor > 0 && y + 10 > this.panelFloor) {
                return y;
            }

            graphics.drawString(this.font, line, x, y, colour, false);
            y += 10;
        }

        return y;
    }
}
