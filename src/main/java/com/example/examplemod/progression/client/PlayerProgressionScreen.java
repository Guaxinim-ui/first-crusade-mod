package com.example.examplemod.progression.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.progression.PlayerCommanderManager;
import com.example.examplemod.progression.PlayerCommanderNodeDefinition;
import com.example.examplemod.progression.PlayerCommanderProfile;
import com.example.examplemod.progression.PlayerCommanderRequirements;
import com.example.examplemod.progression.PlayerCommanderTree;
import com.example.examplemod.progression.PlayerEvolutionNodeDefinition;
import com.example.examplemod.progression.PlayerEvolutionStage;
import com.example.examplemod.progression.PlayerProgressionBalance;
import com.example.examplemod.progression.PlayerProgressionClientView;
import com.example.examplemod.progression.PlayerProgressionProfile;
import com.example.examplemod.progression.PlayerProgressionTree;
import com.example.examplemod.progression.PlayerSkillNodeDefinition;
import com.example.examplemod.progression.ProgressionActionPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Imperial Progression — two careers, one screen.
 *
 * <h2>Two tabs, one profile</h2>
 *
 * The Ascension is the tree that was always here; Imperial Command is the commander's own, bought
 * with its own points. They share a screen because they share a profile — the sync packet already
 * carries both — and switching between them sends nothing and fetches nothing. Each tab keeps its
 * own scroll and its own selection, so coming back to a tab finds it where it was left rather than
 * at the top with nothing chosen.
 *
 * <h2>Why the pages went away</h2>
 *
 * Six fixed pages made every screenful fit by construction, but they also made the tree feel like
 * six trees: the reader had to rebuild the thread across a page break. A single strip that scrolls
 * says the same thing with nothing in the way. What that buys, besides the reading: height stops
 * being a constraint, so an icon is the same size on every machine.
 *
 * <h2>The header is measured before it is drawn</h2>
 *
 * Its readouts are laid out into lines known to fit the space left of the panel. The old header
 * wrote them in one row from x=10 and let the last one run off the edge.
 */
public class PlayerProgressionScreen extends Screen {

    // ---------------------------------------------------------------- palette
    private static final int BACKGROUND = 0xF00A0A0C;
    private static final int CHROME = 0xFF12070A;
    private static final int FRAME = 0xFF3A2024;
    private static final int LINE_DONE = 0xFFC8322E;
    private static final int LINE_OPEN = 0xFF7A2426;
    private static final int LINE_LOCKED = 0xFF2A2024;
    private static final int GOLD = 0xFFD9B65C;
    private static final int WHITE = 0xFFF0EDE6;
    private static final int TEXT = 0xFFC8C4BC;
    private static final int TEXT_DIM = 0xFF7A736C;
    private static final int GREEN = 0xFF6FBF8A;
    private static final int RED = 0xFFB05050;
    private static final int NODE_LOCKED = 0xFF2A2C30;
    private static final int NODE_OPEN = 0xFF4E1416;
    private static final int NODE_DONE = 0xFF8E1E20;

    // ---------------------------------------------------------------- tab strip
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_WIDTH = 132;
    private static final int TAB_GAP = 4;
    private static final int TAB_LEFT = 10;

    /**
     * The tab the screen opens on.
     *
     * <p>Static, so it survives closing and reopening the screen within a session — a commander
     * checking their tree twice in a row should not have to click across each time. It is not
     * persisted: a fresh session opens on the Ascension, which is what a new player wants.
     */
    private static ProgressionTab lastTab = ProgressionTab.ASTARTES;

    private ProgressionTab tab = lastTab;

    private ProgressionTreeLayout layout;
    private final List<Placed> placed = new ArrayList<>();
    private final List<PlacedCommand> placedCommand = new ArrayList<>();

    /** The header's readouts, already wrapped to lines that fit. Rebuilt on resize and tab change. */
    private final List<List<Component>> headerLines = new ArrayList<>();

    /** One scroll per tab: switching back finds the tree where it was left. */
    private int astartesScroll;
    private int commanderScroll;

    private float pulse;
    private boolean draggingBar;

    @Nullable
    private PlayerSkillNodeDefinition selected;

    @Nullable
    private PlayerCommanderNodeDefinition selectedCommand;

    /**
     * A node and the box it occupies, in strip coordinates.
     *
     * <p>Strip coordinates, not screen: the scroll offset is taken off once, at draw and hit-test
     * time. Storing screen positions would mean rebuilding this list on every wheel notch.
     */
    private record Placed(PlayerSkillNodeDefinition node, int x, int stripY, int size) {
        int centreX() {
            return this.x + this.size / 2;
        }
    }

    private record PlacedCommand(PlayerCommanderNodeDefinition node, int x, int stripY, int size) {
        int centreX() {
            return this.x + this.size / 2;
        }
    }

    public PlayerProgressionScreen() {
        super(Component.translatable("gui.firstcrusade.progression.title"));
    }

    // ==================================================================== setup

    @Override
    protected void init() {
        super.init();

        // An Ork opening this screen is on the wrong board. K already routes by faction, so this is
        // a safety net for any other path that constructs it — and a redirect rather than a
        // placeholder, because there is a real WAAAGH tree to send him to now.
        if (PlayerProgressionClientView.isOrk() && this.minecraft != null) {
            this.minecraft.setScreen(
                    new com.example.examplemod.progression.ork.client.PlayerOrkProgressionScreen());
            return;
        }

        calculateResponsiveLayout();

        if (this.tab == ProgressionTab.ASTARTES) {
            this.astartesScroll = openingScroll();
        }
    }

    private void calculateResponsiveLayout() {
        // Two passes: the header's height depends on how its text wraps, and the wrap width depends
        // on the panel, which depends on the window. So the panel is worked out first, from a
        // throwaway layout, and the real one is built once the header height is known.
        ProgressionTreeLayout probe = ProgressionTreeLayout.compute(this.width, this.height, 0, rows());
        int headerHeight = calculateHeaderHeight(probe.contentRight());

        this.layout = ProgressionTreeLayout.compute(this.width, this.height, headerHeight, rows());

        calculateNodePositions();
        calculateCommandNodePositions();

        this.astartesScroll = this.layout.clampScroll(this.astartesScroll);
        this.commanderScroll = this.layout.clampScroll(this.commanderScroll);
    }

    /** Rows in the active tab's strip. */
    private int rows() {
        if (this.tab == ProgressionTab.COMMAND) {
            return PlayerCommanderTree.rows();
        }

        int deepest = 0;
        for (PlayerSkillNodeDefinition node : PlayerProgressionTree.all()) {
            deepest = Math.max(deepest, node.y());
        }
        return deepest + 1;
    }

    /**
     * Wraps the header's readouts into lines that fit, and returns the height they need.
     *
     * <p>Every line is measured against the room left of the panel before anything is committed to
     * it. That is the whole fix for the clipped corner: a readout that would not fit starts a new
     * line instead of running off the screen. The tab strip's own row is added on top.
     */
    private int calculateHeaderHeight(int rightEdge) {
        this.headerLines.clear();

        PlayerProgressionProfile profile = PlayerProgressionClientView.self();
        List<Component> parts = new ArrayList<>();

        if (profile == null) {
            parts.add(Component.translatable("gui.firstcrusade.progression.loading"));
        } else if (this.tab == ProgressionTab.COMMAND) {
            commandHeaderParts(profile.commander(), parts);
        } else {
            astartesHeaderParts(profile, parts);
        }

        int available = Math.max(80, rightEdge - 10);
        List<Component> line = new ArrayList<>();
        int used = 0;

        for (Component part : parts) {
            // A readout that will not fit even alone is trimmed rather than allowed off the edge.
            Component fitted = this.font.width(part) > available ? trim(part, available) : part;

            int wanted = this.font.width(fitted) + (line.isEmpty() ? 0 : 14);

            if (!line.isEmpty() && used + wanted > available) {
                this.headerLines.add(List.copyOf(line));
                line.clear();
                used = 0;
                wanted = this.font.width(fitted);
            }

            line.add(fitted);
            used += wanted;
        }

        if (!line.isEmpty()) {
            this.headerLines.add(List.copyOf(line));
        }

        // Title line, the tab strip, one line per wrapped row, then a little air above the rule.
        return 8 + 12 + TAB_HEIGHT + 4 + this.headerLines.size() * 11 + 4;
    }

    private void astartesHeaderParts(PlayerProgressionProfile profile, List<Component> parts) {
        parts.add(Component.translatable("gui.firstcrusade.progression.points", profile.points()));
        parts.add(Component.translatable("gui.firstcrusade.progression.xp",
                profile.xp(), profile.xpForNextLevel()));
        parts.add(Component.translatable("gui.firstcrusade.progression.stage",
                profile.stage().displayName()));
        parts.add(Component.translatable("gui.firstcrusade.progression.implants",
                profile.implantCount(), PlayerProgressionBalance.IMPLANT_COUNT));

        if (profile.stage() == PlayerEvolutionStage.NEOPHYTE) {
            parts.add(Component.translatable("gui.firstcrusade.progression.trial",
                    profile.trialOrkKills(), PlayerProgressionBalance.TRIAL_ORK_KILLS));
        }
    }

    private void commandHeaderParts(PlayerCommanderProfile commander, List<Component> parts) {
        parts.add(Component.translatable("gui.firstcrusade.command.level", commander.level()));
        parts.add(Component.translatable("gui.firstcrusade.command.xp",
                commander.xp(), commander.xpForNextLevel()));
        parts.add(Component.translatable("gui.firstcrusade.command.points", commander.points()));
        parts.add(Component.translatable("gui.firstcrusade.command.wins",
                commander.successfulRaids()));
        parts.add(Component.translatable("gui.firstcrusade.command.reinforcements",
                PlayerCommanderManager.reinforcementLimit(commander)));

        // The cooldown is a deadline in game time, and the client has no game time it can trust for
        // that comparison — so the readout is the wait itself, which does not need one.
        parts.add(Component.translatable("gui.firstcrusade.command.cooldown",
                PlayerCommanderManager.cooldownTicks(commander) / 20));
    }

    /** Cuts a readout to a width, with an ellipsis. Nothing in the header may leave the screen. */
    private Component trim(Component text, int width) {
        String ellipsis = "...";
        String plain = this.font.plainSubstrByWidth(text.getString(),
                Math.max(8, width - this.font.width(ellipsis)));

        return Component.literal(plain + ellipsis).withStyle(text.getStyle());
    }

    /** Places every node of the Astartes tree, once, in strip coordinates. */
    private void calculateNodePositions() {
        this.placed.clear();

        int[] columns = {this.layout.leftColumnX(), this.layout.centerColumnX(),
                this.layout.rightColumnX()};

        for (PlayerSkillNodeDefinition node : PlayerProgressionTree.all()) {
            int size = sizeOf(node);
            int centreX = node.isMajor() || node.isRoot()
                    ? this.layout.centerColumnX()
                    : columns[columnIndex(node.x())];

            int stripY = this.layout.rowTop(node.y());

            this.placed.add(new Placed(node, centreX - size / 2, stripY, size));
        }
    }

    /** The same arithmetic for the command tree, which uses the same three columns. */
    private void calculateCommandNodePositions() {
        this.placedCommand.clear();

        int[] columns = {this.layout.leftColumnX(), this.layout.centerColumnX(),
                this.layout.rightColumnX()};

        for (PlayerCommanderNodeDefinition node : PlayerCommanderTree.all()) {
            int size = node.isRoot()
                    ? this.layout.rootNodeSize()
                    : this.layout.implantNodeSize();

            int centreX = columns[columnIndex(node.x())];
            int stripY = this.layout.rowTop(node.y());

            this.placedCommand.add(new PlacedCommand(node, centreX - size / 2, stripY, size));
        }
    }

    /** The trees' x is -2, 0 or +2; the screen wants 0, 1 or 2. */
    private static int columnIndex(int treeX) {
        return Mth.clamp((treeX + 2) / 2, 0, 2);
    }

    private int sizeOf(PlayerSkillNodeDefinition node) {
        if (node.ascension()) {
            return this.layout.ascensionNodeSize();
        }
        if (node.implant()) {
            return this.layout.implantNodeSize();
        }
        if (node.isRoot()) {
            return this.layout.rootNodeSize();
        }
        return this.layout.normalNodeSize();
    }

    /**
     * Where the Ascension view starts: on whatever the player last reached.
     *
     * <p>A surgery beats everything — that is what they opened the screen to look at; then an organ
     * whose training is done; then the deepest node bought.
     */
    private int openingScroll() {
        PlayerProgressionProfile profile = PlayerProgressionClientView.self();
        if (profile == null) {
            return 0;
        }

        if (profile.isInSurgery()) {
            PlayerSkillNodeDefinition node = profile.surgeryNodeId() == null
                    ? null
                    : PlayerProgressionTree.node(profile.surgeryNodeId());
            if (node != null) {
                return this.layout.scrollToCentre(node.y());
            }
        }

        if (profile.stage() == PlayerEvolutionStage.NEOPHYTE) {
            return this.layout.maxScroll();
        }

        for (PlayerEvolutionNodeDefinition surgery : PlayerProgressionTree.surgeries()) {
            PlayerSkillNodeDefinition node = PlayerProgressionTree.node(surgery.nodeId());
            if (node == null || profile.hasImplant(node.id())) {
                continue;
            }
            if (node.prerequisites().stream().allMatch(profile::hasRank)) {
                return this.layout.scrollToCentre(node.y());
            }
        }

        int deepest = 0;
        for (PlayerSkillNodeDefinition node : PlayerProgressionTree.all()) {
            if (profile.rank(node.id()) > 0 || profile.hasImplant(node.id())) {
                deepest = Math.max(deepest, node.y());
            }
        }

        return this.layout.scrollToCentre(deepest);
    }

    private int scroll() {
        return this.tab == ProgressionTab.COMMAND ? this.commanderScroll : this.astartesScroll;
    }

    private void setScroll(int value) {
        if (this.tab == ProgressionTab.COMMAND) {
            this.commanderScroll = this.layout.clampScroll(value);
        } else {
            this.astartesScroll = this.layout.clampScroll(value);
        }
    }

    // ==================================================================== rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.pulse += partialTick * 0.06F;

        graphics.fill(0, 0, this.width, this.height, BACKGROUND);

        // An Ork must never be shown the Astartes tree. init() already redirected him to the WAAAGH
        // screen; this covers the single frame between construction and that redirect landing, and
        // it draws nothing rather than a tree he cannot use.
        if (PlayerProgressionClientView.isOrk() || this.layout == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderTree(graphics, mouseX, mouseY);

        // The scissor is off before either bar is drawn: it is what kept the footer's text off the
        // screen the first time round.
        renderHeader(graphics);
        renderFooter(graphics);
        renderPanel(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTree(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(0, this.layout.contentTop(),
                this.layout.contentRight() + ProgressionTreeLayout.REGION_GAP,
                this.layout.contentBottom());

        if (this.tab == ProgressionTab.COMMAND) {
            renderCommandTree(graphics);
        } else {
            PlayerProgressionProfile profile = PlayerProgressionClientView.self();
            renderConnectors(graphics, profile);

            for (Placed entry : this.placed) {
                renderNode(graphics, entry, profile, mouseX, mouseY);
            }
        }

        graphics.disableScissor();
        renderScrollBar(graphics);
    }

    private int screenY(int stripY) {
        return this.layout.contentTop() + stripY - scroll();
    }

    /** A node is only worth drawing when some part of it is on screen. */
    private boolean visible(int stripY, int size) {
        int y = screenY(stripY);
        return y + size + ProgressionTreeLayout.LABEL_HEIGHT >= this.layout.contentTop()
                && y <= this.layout.contentBottom();
    }

    /**
     * Every wire, drawn before the nodes and stopping at their edges.
     *
     * <p>A line that ends at a node's centre runs under the icon, and the first icon with a
     * transparent middle turns it into a stripe across somebody's lungs.
     */
    private void renderConnectors(GuiGraphics graphics, @Nullable PlayerProgressionProfile profile) {
        for (Placed entry : this.placed) {
            for (String parentId : entry.node().prerequisites()) {
                PlayerSkillNodeDefinition parent = PlayerProgressionTree.node(parentId);
                Placed from = parent == null ? null : placedOf(parent);
                if (from == null
                        || (!visible(entry.stripY(), entry.size())
                        && !visible(from.stripY(), from.size()))) {
                    continue;
                }

                boolean live = profile != null && isComplete(profile, parent);
                boolean reachable = profile != null && stateOf(profile, entry.node()) != State.LOCKED;
                int colour = live ? LINE_DONE : (reachable ? LINE_OPEN : LINE_LOCKED);

                elbow(graphics, from.centreX(), from.stripY(), from.size(),
                        entry.centreX(), entry.stripY(), colour);

                if (entry.node().implant() && live) {
                    int cx = entry.centreX();
                    int top = screenY(entry.stripY());
                    graphics.fill(cx - 5, top - 3, cx + 5, top - 1, GOLD);
                }
            }
        }
    }

    private void elbow(GuiGraphics graphics, int fromX, int fromStripY, int fromSize,
                       int toX, int toStripY, int colour) {
        int t = 3;
        int startY = screenY(fromStripY) + fromSize + ProgressionTreeLayout.LABEL_HEIGHT;
        int endY = screenY(toStripY);
        int midY = (startY + endY) / 2;

        vertical(graphics, fromX, startY, midY, colour, t);
        horizontal(graphics, fromX, toX, midY, colour, t);
        vertical(graphics, toX, midY, endY, colour, t);
    }

    private void vertical(GuiGraphics graphics, int x, int y1, int y2, int colour, int t) {
        graphics.fill(x - t / 2, Math.min(y1, y2), x - t / 2 + t, Math.max(y1, y2), colour);
    }

    private void horizontal(GuiGraphics graphics, int x1, int x2, int y, int colour, int t) {
        graphics.fill(Math.min(x1, x2) - t / 2, y - t / 2, Math.max(x1, x2) + t / 2, y - t / 2 + t,
                colour);
    }

    private void renderNode(GuiGraphics graphics, Placed entry,
                            @Nullable PlayerProgressionProfile profile, int mouseX, int mouseY) {
        if (!visible(entry.stripY(), entry.size())) {
            return;
        }

        PlayerSkillNodeDefinition node = entry.node();
        State state = stateOf(profile, node);
        boolean implanted = node.implant() && profile != null && profile.hasImplant(node.id());

        int x = entry.x();
        int y = screenY(entry.stripY());
        int size = entry.size();

        graphics.fill(x, y, x + size, y + size, switch (state) {
            case LOCKED -> NODE_LOCKED;
            case AVAILABLE -> NODE_OPEN;
            case DONE -> NODE_DONE;
        });

        int border = node.branch().colour();
        if (state == State.LOCKED) {
            border = desaturate(border, 0.65F);
        }
        if (node.implant() || node.ascension()) {
            border = implanted || state == State.DONE ? GOLD : desaturate(GOLD, 0.45F);
        }
        if (state == State.AVAILABLE) {
            border = blend(border, WHITE, (Mth.sin(this.pulse) + 1.0F) * 0.15F);
        }

        outline(graphics, x, y, x + size, y + size, border, ProgressionTreeLayout.BORDER);

        if (node.ascension()) {
            outline(graphics, x - 2, y - 2, x + size + 2, y + size + 2, WHITE, 1);
        }
        if (node == this.selected) {
            outline(graphics, x - 3, y - 3, x + size + 3, y + size + 3, WHITE, 2);
        }

        int iconSize = this.layout.iconSizeFor(size);
        int iconX = x + (size - iconSize) / 2;
        int iconY = y + (size - iconSize) / 2;

        if (state == State.LOCKED) {
            graphics.setColor(0.45F, 0.45F, 0.48F, 0.7F);
        }
        graphics.blit(node.icon().texture(), iconX, iconY, 0, 0, iconSize, iconSize,
                iconSize, iconSize);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (implanted || (node.ascension() && state == State.DONE)) {
            graphics.fill(x + 2, y + 2, x + size - 2, y + 3, 0x60FFE9A8);
        }

        renderNodeLabel(graphics, entry, y, profile, state, implanted);
    }

    /**
     * The short name and the rank, under the node.
     *
     * <p>Under it, never on it: a rank written across the face covers the one thing the icon exists
     * to show. Each line is centred on its own, and both are cut to the node's label width.
     */
    private void renderNodeLabel(GuiGraphics graphics, Placed entry, int y,
                                 @Nullable PlayerProgressionProfile profile, State state,
                                 boolean implanted) {
        if (profile == null) {
            return;
        }

        PlayerSkillNodeDefinition node = entry.node();
        int centreX = entry.centreX();
        int labelY = y + entry.size() + 2;
        int room = this.layout.labelWidthFor(entry.size(), node.isMajor());

        List<FormattedCharSequence> name = this.font.split(shortName(node), room);
        if (!name.isEmpty()) {
            graphics.drawCenteredString(this.font, name.get(0), centreX, labelY,
                    state == State.LOCKED ? TEXT_DIM : TEXT);
            labelY += 9;
        }

        Component status = statusOf(node, profile, state, implanted);
        if (status == null) {
            return;
        }

        List<FormattedCharSequence> statusLines = this.font.split(status, room);
        if (!statusLines.isEmpty()) {
            graphics.drawCenteredString(this.font, statusLines.get(0), centreX, labelY,
                    statusColour(node, state));
        }
    }

    @Nullable
    private Component statusOf(PlayerSkillNodeDefinition node, PlayerProgressionProfile profile,
                               State state, boolean implanted) {
        if (node.implant()) {
            if (implanted) {
                return Component.translatable("gui.firstcrusade.progression.implanted");
            }
            long met = node.prerequisites().stream().filter(profile::hasRank).count();
            return Component.translatable("gui.firstcrusade.progression.requirements_of",
                    met, node.prerequisites().size());
        }

        if (node.ascension()) {
            if (profile.stage() == PlayerEvolutionStage.SPACE_MARINE) {
                return Component.translatable("gui.firstcrusade.progression.state_done");
            }
            return Component.translatable(state == State.AVAILABLE
                    ? "gui.firstcrusade.progression.state_open"
                    : "gui.firstcrusade.progression.state_locked");
        }

        if (node.isRoot()) {
            return null;
        }

        return Component.literal(profile.rank(node.id()) + "/" + node.maxRanks());
    }

    private int statusColour(PlayerSkillNodeDefinition node, State state) {
        if (node.isMajor()) {
            return state == State.DONE ? GOLD : TEXT_DIM;
        }
        return state == State.LOCKED ? TEXT_DIM : TEXT;
    }

    private static Component shortName(PlayerSkillNodeDefinition node) {
        return Component.translatable("node.firstcrusade." + node.id() + ".short");
    }

    // ==================================================================== the command tree

    private void renderCommandTree(GuiGraphics graphics) {
        PlayerProgressionProfile profile = PlayerProgressionClientView.self();
        PlayerCommanderProfile commander = profile == null ? null : profile.commander();

        for (PlacedCommand entry : this.placedCommand) {
            for (String parentId : entry.node().prerequisites()) {
                PlacedCommand from = placedCommandOf(parentId);
                if (from == null
                        || (!visible(entry.stripY(), entry.size())
                        && !visible(from.stripY(), from.size()))) {
                    continue;
                }

                boolean live = commander != null
                        && PlayerCommanderRequirements.owns(commander, parentId);
                boolean reachable = commander != null
                        && commandStateOf(commander, entry.node()) != State.LOCKED;

                elbow(graphics, from.centreX(), from.stripY(), from.size(),
                        entry.centreX(), entry.stripY(),
                        live ? LINE_DONE : (reachable ? LINE_OPEN : LINE_LOCKED));
            }
        }

        for (PlacedCommand entry : this.placedCommand) {
            renderCommandNode(graphics, entry, commander);
        }
    }

    private void renderCommandNode(GuiGraphics graphics, PlacedCommand entry,
                                   @Nullable PlayerCommanderProfile commander) {
        if (!visible(entry.stripY(), entry.size())) {
            return;
        }

        State state = commandStateOf(commander, entry.node());

        int x = entry.x();
        int y = screenY(entry.stripY());
        int size = entry.size();

        graphics.fill(x, y, x + size, y + size, switch (state) {
            case LOCKED -> NODE_LOCKED;
            case AVAILABLE -> NODE_OPEN;
            case DONE -> NODE_DONE;
        });

        int border = switch (state) {
            case LOCKED -> desaturate(GOLD, 0.65F);
            case AVAILABLE -> blend(GOLD, WHITE, (Mth.sin(this.pulse) + 1.0F) * 0.15F);
            case DONE -> GOLD;
        };

        outline(graphics, x, y, x + size, y + size, border, ProgressionTreeLayout.BORDER);

        if (entry.node() == this.selectedCommand) {
            outline(graphics, x - 3, y - 3, x + size + 3, y + size + 3, WHITE, 2);
        }

        int iconSize = this.layout.iconSizeFor(size);
        int iconX = x + (size - iconSize) / 2;
        int iconY = y + (size - iconSize) / 2;

        if (state == State.LOCKED) {
            graphics.setColor(0.45F, 0.45F, 0.48F, 0.7F);
        }
        graphics.blit(entry.node().icon().texture(), iconX, iconY, 0, 0, iconSize, iconSize,
                iconSize, iconSize);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (state == State.DONE) {
            graphics.fill(x + 2, y + 2, x + size - 2, y + 3, 0x60FFE9A8);
        }

        int labelY = y + size + 2;
        int room = this.layout.labelWidthFor(size, true);

        List<FormattedCharSequence> name = this.font.split(entry.node().shortName(), room);
        if (!name.isEmpty()) {
            graphics.drawCenteredString(this.font, name.get(0), entry.centreX(), labelY,
                    state == State.LOCKED ? TEXT_DIM : TEXT);
            labelY += 9;
        }

        Component status = entry.node().isRoot()
                ? Component.translatable("gui.firstcrusade.command.free")
                : (state == State.DONE
                ? Component.translatable("gui.firstcrusade.command.taken")
                : Component.translatable("gui.firstcrusade.command.cost", entry.node().cost()));

        List<FormattedCharSequence> statusLines = this.font.split(status, room);
        if (!statusLines.isEmpty()) {
            graphics.drawCenteredString(this.font, statusLines.get(0), entry.centreX(), labelY,
                    state == State.DONE ? GOLD : (state == State.LOCKED ? TEXT_DIM : TEXT));
        }
    }

    @Nullable
    private PlacedCommand placedCommandOf(String nodeId) {
        for (PlacedCommand entry : this.placedCommand) {
            if (entry.node().id().equals(nodeId)) {
                return entry;
            }
        }
        return null;
    }

    private State commandStateOf(@Nullable PlayerCommanderProfile commander,
                                 PlayerCommanderNodeDefinition node) {
        if (commander == null) {
            return State.LOCKED;
        }
        if (PlayerCommanderRequirements.owns(commander, node)) {
            return State.DONE;
        }

        for (String id : node.prerequisites()) {
            if (!PlayerCommanderRequirements.owns(commander, id)) {
                return State.LOCKED;
            }
        }

        if (commander.successfulRaids() < node.requiredWins()) {
            return State.LOCKED;
        }

        return State.AVAILABLE;
    }

    // ==================================================================== scroll bar

    /** The bar on the right of the strip: the only thing that says how much tree is left. */
    private void renderScrollBar(GuiGraphics graphics) {
        int max = this.layout.maxScroll();
        if (max <= 0) {
            return;
        }

        int x = this.layout.contentRight() + 2;
        int top = this.layout.contentTop();
        int height = this.layout.viewportHeight();

        int thumbHeight = Math.max(20, height * height / this.layout.totalHeight());
        int thumbY = top + (height - thumbHeight) * scroll() / max;

        graphics.fill(x, top, x + 4, top + height, 0x50000000);
        graphics.fill(x, thumbY, x + 4, thumbY + thumbHeight, FRAME);
        graphics.fill(x, thumbY, x + 1, thumbY + thumbHeight, GOLD);
    }

    private void outline(GuiGraphics graphics, int x1, int y1, int x2, int y2, int colour, int t) {
        graphics.fill(x1, y1, x2, y1 + t, colour);
        graphics.fill(x1, y2 - t, x2, y2, colour);
        graphics.fill(x1, y1, x1 + t, y2, colour);
        graphics.fill(x2 - t, y1, x2, y2, colour);
    }

    private static int desaturate(int colour, float amount) {
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int grey = (int) (r * 0.3F + g * 0.59F + b * 0.11F);

        return 0xFF000000
                | ((int) (r + (grey - r) * amount) << 16)
                | ((int) (g + (grey - g) * amount) << 8)
                | (int) (b + (grey - b) * amount);
    }

    private static int blend(int from, int to, float amount) {
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;

        return 0xFF000000
                | ((int) (fr + (((to >> 16) & 0xFF) - fr) * amount) << 16)
                | ((int) (fg + (((to >> 8) & 0xFF) - fg) * amount) << 8)
                | (int) (fb + ((to & 0xFF) - fb) * amount);
    }

    // ==================================================================== header and footer

    private void renderHeader(GuiGraphics graphics) {
        int bottom = this.layout.headerHeight();

        graphics.fill(0, 0, this.width, bottom, CHROME);
        graphics.fill(0, bottom - 1, this.width, bottom, FRAME);

        graphics.drawString(this.font, this.title, 10, 8, GOLD, false);

        renderTabs(graphics);

        int y = 8 + 12 + TAB_HEIGHT + 4;
        for (List<Component> line : this.headerLines) {
            int x = 10;
            for (Component part : line) {
                graphics.drawString(this.font, part, x, y, TEXT, false);
                x += this.font.width(part) + 14;
            }
            y += 11;
        }
    }

    /** The two tabs, drawn rather than made of widgets, to match the rest of the chrome. */
    private void renderTabs(GuiGraphics graphics) {
        int y = tabTop();

        for (ProgressionTab candidate : ProgressionTab.values()) {
            int x = tabLeft(candidate);
            boolean active = candidate == this.tab;

            graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, active ? NODE_DONE : 0xFF1C1216);
            outline(graphics, x, y, x + TAB_WIDTH, y + TAB_HEIGHT,
                    active ? GOLD : FRAME, 1);

            graphics.drawCenteredString(this.font, candidate.title(),
                    x + TAB_WIDTH / 2, y + 4, active ? WHITE : TEXT_DIM);
        }
    }

    private int tabTop() {
        return 8 + 12;
    }

    private int tabLeft(ProgressionTab candidate) {
        return TAB_LEFT + candidate.ordinal() * (TAB_WIDTH + TAB_GAP);
    }

    /** One line, centred, and only the form that {@link net.minecraft.client.gui.Font} says fits. */
    private void renderFooter(GuiGraphics graphics) {
        int top = this.layout.footerTop();

        graphics.fill(0, top, this.width, this.height, CHROME);
        graphics.fill(0, top, this.width, top + 1, FRAME);

        Component hint = Component.translatable("gui.firstcrusade.progression.scroll_hint");
        if (this.font.width(hint) > this.width - 20) {
            hint = Component.translatable("gui.firstcrusade.progression.scroll_hint_short");
        }

        graphics.drawCenteredString(this.font, hint, this.width / 2, top + 9, TEXT_DIM);
    }

    // ==================================================================== detail panel

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean column = ProgressionTreeLayout.panelIsColumn(this.width);
        boolean hasSelection = this.tab == ProgressionTab.COMMAND
                ? this.selectedCommand != null
                : this.selected != null;

        if (!column && !hasSelection) {
            return;
        }

        int panelWidth = this.layout.panelWidth();
        int left = this.width - panelWidth;

        graphics.fill(left, 0, this.width, this.height, CHROME);
        graphics.fill(left, 0, left + 1, this.height, FRAME);

        if (!hasSelection) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.progression.select_a_node"),
                    left + 8, this.layout.headerHeight() + 10, TEXT_DIM, false);
            return;
        }

        if (this.tab == ProgressionTab.COMMAND) {
            renderCommandPanel(graphics, left, panelWidth, mouseX, mouseY);
        } else {
            renderAstartesPanel(graphics, left, panelWidth, mouseX, mouseY);
        }
    }

    /**
     * The lowest line the panel may write on.
     *
     * <p>Measured, not assumed: the action button sits at {@code footerTop - 28}, and text drawn
     * past that runs under the button and then over the footer. On a short window at GUI scale 3
     * there is room for the icon, the name and two or three lines — so the panel stops there rather
     * than drawing a description across its own button, which is what it was doing.
     */
    private int panelTextLimit() {
        return this.layout.footerTop() - 34;
    }

    private void renderAstartesPanel(GuiGraphics graphics, int left, int panelWidth,
                                     int mouseX, int mouseY) {
        PlayerSkillNodeDefinition node = this.selected;
        if (node == null) {
            return;
        }

        PlayerProgressionProfile profile = PlayerProgressionClientView.self();
        int width = panelWidth - 16;
        int top = this.layout.headerHeight() + 8;
        int limit = panelTextLimit();
        int y = top;

        int big = node.ascension() ? 60 : (node.implant() ? 52 : 40);
        graphics.blit(node.icon().texture(), left + 8, y, 0, 0, big, big, big, big);

        for (FormattedCharSequence line : this.font.split(
                node.displayName().copy().withStyle(ChatFormatting.BOLD), width - big - 10)) {
            graphics.drawString(this.font, line, left + big + 14, y, node.branch().colour(), false);
            y += 10;
        }
        graphics.drawString(this.font, node.branch().displayName(), left + big + 14, y, TEXT_DIM,
                false);

        y = top + big + 6;

        for (FormattedCharSequence line : this.font.split(node.description(), width)) {
            if (y > limit) {
                break;
            }
            graphics.drawString(this.font, line, left + 8, y, TEXT_DIM, false);
            y += 9;
        }
        y += 4;

        if (profile == null) {
            return;
        }

        int rank = profile.rank(node.id());

        if (y <= limit && !node.isMajor() && !node.isRoot()) {
            graphics.drawString(this.font, Component.translatable(
                            "gui.firstcrusade.progression.rank", rank, node.maxRanks()),
                    left + 8, y, TEXT, false);
            y += 11;

            y = labelled(graphics, left, y, width, "gui.firstcrusade.progression.current",
                    PlayerProgressionTree.describe(node, rank));

            if (rank < node.maxRanks()) {
                y = labelled(graphics, left, y, width, "gui.firstcrusade.progression.next",
                        PlayerProgressionTree.describe(node, rank + 1));
                graphics.drawString(this.font, Component.translatable(
                                "gui.firstcrusade.progression.cost", node.costOfRank(rank + 1)),
                        left + 8, y, GOLD, false);
                y += 12;
            }
        }

        if (y <= limit && !node.prerequisites().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.progression.requires"),
                    left + 8, y, TEXT_DIM, false);
            y += 10;

            for (String id : node.prerequisites()) {
                PlayerSkillNodeDefinition required = PlayerProgressionTree.node(id);
                if (required == null) {
                    continue;
                }

                boolean met = isComplete(profile, required);
                for (FormattedCharSequence line : this.font.split(
                        Component.literal(met ? "+ " : "- ").append(required.displayName()),
                        width - 6)) {
                    if (y > limit) {
                        break;
                    }
                    graphics.drawString(this.font, line, left + 12, y, met ? GREEN : RED, false);
                    y += 9;
                }
            }
            y += 2;
        }

        PlayerEvolutionNodeDefinition surgery = PlayerProgressionTree.surgery(node.id());
        if (surgery != null) {
            for (FormattedCharSequence line : this.font.split(Component.translatable(
                    "gui.firstcrusade.progression.surgery_cost",
                    surgery.geneSeed(), surgery.minCoreLevel()), width)) {
                if (y > limit) {
                    break;
                }
                graphics.drawString(this.font, line, left + 8, y, TEXT_DIM, false);
                y += 9;
            }
        }

        renderActionButton(graphics, actionLabel(node, profile), left, panelWidth, mouseX, mouseY);
    }

    private void renderCommandPanel(GuiGraphics graphics, int left, int panelWidth,
                                    int mouseX, int mouseY) {
        PlayerCommanderNodeDefinition node = this.selectedCommand;
        PlayerProgressionProfile profile = PlayerProgressionClientView.self();

        if (node == null || profile == null) {
            return;
        }

        PlayerCommanderProfile commander = profile.commander();

        int width = panelWidth - 16;
        int top = this.layout.headerHeight() + 8;
        int limit = panelTextLimit();
        int y = top;
        int big = 52;

        graphics.blit(node.icon().texture(), left + 8, y, 0, 0, big, big, big, big);

        for (FormattedCharSequence line : this.font.split(
                node.displayName().copy().withStyle(ChatFormatting.BOLD), width - big - 10)) {
            graphics.drawString(this.font, line, left + big + 14, y, GOLD, false);
            y += 10;
        }

        y = top + big + 6;

        // The price and the gate come before the prose. On a short window the description is the
        // first thing to be cut, and it should be: "Cost: 3 CP" and "Raids won required: 4 (you
        // have 1)" are what the commander opened the panel to find out.
        if (y <= limit && !node.isRoot()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.command.cost", node.cost()),
                    left + 8, y, GOLD, false);
            y += 11;
        }

        if (y <= limit && node.requiredWins() > 0) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.command.requires_wins",
                            node.requiredWins(), commander.successfulRaids()),
                    left + 8, y,
                    commander.successfulRaids() >= node.requiredWins() ? GREEN : RED, false);
            y += 11;
        }

        if (y <= limit && !node.prerequisites().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.progression.requires"),
                    left + 8, y, TEXT_DIM, false);
            y += 10;

            for (String id : node.prerequisites()) {
                PlayerCommanderNodeDefinition required = PlayerCommanderTree.node(id);
                if (required == null) {
                    continue;
                }

                boolean met = PlayerCommanderRequirements.owns(commander, required);
                for (FormattedCharSequence line : this.font.split(
                        Component.literal(met ? "+ " : "- ").append(required.displayName()),
                        width - 6)) {
                    if (y > limit) {
                        break;
                    }
                    graphics.drawString(this.font, line, left + 12, y, met ? GREEN : RED, false);
                    y += 9;
                }
            }
            y += 2;
        }

        for (FormattedCharSequence line : this.font.split(node.description(), width)) {
            if (y > limit) {
                break;
            }
            graphics.drawString(this.font, line, left + 8, y, TEXT_DIM, false);
            y += 9;
        }

        Component label = commandStateOf(commander, node) == State.DONE
                ? Component.translatable("gui.firstcrusade.command.taken")
                : Component.translatable("gui.firstcrusade.command.unlock");

        renderActionButton(graphics, label, left, panelWidth, mouseX, mouseY);
    }

    private int labelled(GuiGraphics graphics, int left, int y, int width, String key,
                         Component value) {
        graphics.drawString(this.font, Component.translatable(key), left + 8, y, TEXT_DIM, false);
        y += 10;

        for (FormattedCharSequence line : this.font.split(value, width - 6)) {
            graphics.drawString(this.font, line, left + 14, y, TEXT, false);
            y += 9;
        }

        return y + 2;
    }

    private void renderActionButton(GuiGraphics graphics, Component label, int left, int panelWidth,
                                    int mouseX, int mouseY) {
        int x = left + 8;
        int y = this.layout.footerTop() - 28;
        int width = panelWidth - 16;

        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 20;

        graphics.fill(x, y, x + width, y + 20, hovered ? NODE_DONE : 0xFF2A1216);
        outline(graphics, x, y, x + width, y + 20, GOLD, 1);
        graphics.drawCenteredString(this.font, label, x + width / 2, y + 6, TEXT);
    }

    private Component actionLabel(PlayerSkillNodeDefinition node, PlayerProgressionProfile profile) {
        if (node.ascension()) {
            return Component.translatable("gui.firstcrusade.progression.ascend");
        }
        if (node.implant()) {
            return profile.hasImplant(node.id())
                    ? Component.translatable("gui.firstcrusade.progression.implanted")
                    : Component.translatable("gui.firstcrusade.progression.operate");
        }
        if (node.isRoot()) {
            return Component.translatable("gui.firstcrusade.progression.root_node");
        }
        if (profile.rank(node.id()) >= node.maxRanks()) {
            return Component.translatable("gui.firstcrusade.progression.maxed");
        }
        return Component.translatable("gui.firstcrusade.progression.buy");
    }

    // ==================================================================== state

    private enum State {
        LOCKED, AVAILABLE, DONE
    }

    private State stateOf(@Nullable PlayerProgressionProfile profile,
                          PlayerSkillNodeDefinition node) {
        if (profile == null) {
            return State.LOCKED;
        }
        if (isComplete(profile, node)) {
            return State.DONE;
        }

        for (String id : node.prerequisites()) {
            PlayerSkillNodeDefinition required = PlayerProgressionTree.node(id);
            if (required != null && !isComplete(profile, required)) {
                return State.LOCKED;
            }
        }

        return State.AVAILABLE;
    }

    private boolean isComplete(PlayerProgressionProfile profile, PlayerSkillNodeDefinition node) {
        if (node.ascension()) {
            return profile.stage() == PlayerEvolutionStage.SPACE_MARINE;
        }
        return node.implant() ? profile.hasImplant(node.id()) : profile.rank(node.id()) > 0;
    }

    @Nullable
    private Placed placedOf(PlayerSkillNodeDefinition node) {
        for (Placed entry : this.placed) {
            if (entry.node() == node) {
                return entry;
            }
        }
        return null;
    }

    // ==================================================================== input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) {
            return false;
        }

        // Continuous, and only vertical: the strip never moves sideways.
        setScroll(scroll() - (int) Math.signum(delta) * ProgressionTreeLayout.SCROLL_STEP);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int page = this.layout.viewportHeight() - ProgressionTreeLayout.ROW_STEP;

        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP -> {
                setScroll(scroll() - page);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                setScroll(scroll() + page);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                setScroll(0);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                setScroll(this.layout.maxScroll());
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                setScroll(scroll() + ProgressionTreeLayout.SCROLL_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                setScroll(scroll() - ProgressionTreeLayout.SCROLL_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                switchTab(this.tab == ProgressionTab.ASTARTES
                        ? ProgressionTab.COMMAND
                        : ProgressionTab.ASTARTES);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickedTab(mouseX, mouseY)) {
            return true;
        }

        if (hasSelection() && overActionButton(mouseX, mouseY)) {
            commit();
            return true;
        }

        if (overScrollBar(mouseX, mouseY)) {
            this.draggingBar = true;
            dragBarTo(mouseY);
            return true;
        }

        if (clickedNode(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Switching tabs, which is deliberately cheap.
     *
     * <p>No packet, no {@code rebuildWidgets}, no reload of the profile — the client already holds
     * both careers. Only the layout is recomputed, because the two trees are different heights.
     */
    private boolean clickedTab(double mouseX, double mouseY) {
        int y = tabTop();

        if (mouseY < y || mouseY >= y + TAB_HEIGHT) {
            return false;
        }

        for (ProgressionTab candidate : ProgressionTab.values()) {
            int x = tabLeft(candidate);

            if (mouseX >= x && mouseX < x + TAB_WIDTH) {
                switchTab(candidate);
                return true;
            }
        }

        return false;
    }

    private void switchTab(ProgressionTab next) {
        if (next == this.tab) {
            return;
        }

        this.tab = next;
        lastTab = next;

        // The selection belonging to the other tree would be meaningless here, and the panel would
        // happily draw it. Dropping the incompatible one is the whole of the "clear selection" rule.
        this.selected = null;
        this.selectedCommand = null;

        calculateResponsiveLayout();
    }

    private boolean hasSelection() {
        return this.tab == ProgressionTab.COMMAND
                ? this.selectedCommand != null
                : this.selected != null;
    }

    private boolean clickedNode(double mouseX, double mouseY) {
        if (mouseY < this.layout.contentTop() || mouseY > this.layout.contentBottom()) {
            return false;
        }

        if (this.tab == ProgressionTab.COMMAND) {
            for (PlacedCommand entry : this.placedCommand) {
                int y = screenY(entry.stripY());

                if (mouseX >= entry.x() && mouseX < entry.x() + entry.size()
                        && mouseY >= y && mouseY < y + entry.size()) {
                    this.selectedCommand = entry.node();
                    return true;
                }
            }
            return false;
        }

        for (Placed entry : this.placed) {
            int y = screenY(entry.stripY());

            if (mouseX >= entry.x() && mouseX < entry.x() + entry.size()
                    && mouseY >= y && mouseY < y + entry.size()) {
                this.selected = entry.node();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingBar) {
            dragBarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean overScrollBar(double mouseX, double mouseY) {
        int x = this.layout.contentRight() + 2;
        return this.layout.maxScroll() > 0
                && mouseX >= x && mouseX < x + 4
                && mouseY >= this.layout.contentTop() && mouseY <= this.layout.contentBottom();
    }

    private void dragBarTo(double mouseY) {
        int top = this.layout.contentTop();
        int height = this.layout.viewportHeight();
        double fraction = Mth.clamp((mouseY - top) / height, 0.0D, 1.0D);

        setScroll((int) (fraction * this.layout.maxScroll()));
    }

    private boolean overActionButton(double mouseX, double mouseY) {
        int x = this.width - this.layout.panelWidth() + 8;
        int y = this.layout.footerTop() - 28;

        return mouseX >= x && mouseX < x + this.layout.panelWidth() - 16
                && mouseY >= y && mouseY < y + 20;
    }

    private void commit() {
        if (this.tab == ProgressionTab.COMMAND) {
            if (this.selectedCommand != null && !this.selectedCommand.isRoot()) {
                FirstCrusadeNetwork.CHANNEL.sendToServer(
                        ProgressionActionPacket.commandUnlock(this.selectedCommand.id()));
            }
            return;
        }

        PlayerSkillNodeDefinition node = this.selected;
        if (node == null) {
            return;
        }

        if (node.ascension()) {
            FirstCrusadeNetwork.CHANNEL.sendToServer(ProgressionActionPacket.ascend());
        } else if (node.implant()) {
            FirstCrusadeNetwork.CHANNEL.sendToServer(ProgressionActionPacket.implant(node.id()));
        } else if (!node.isRoot()) {
            FirstCrusadeNetwork.CHANNEL.sendToServer(ProgressionActionPacket.unlock(node.id()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
