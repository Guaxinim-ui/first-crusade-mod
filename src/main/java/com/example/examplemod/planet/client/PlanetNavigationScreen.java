package com.example.examplemod.planet.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.planet.PlanetDangerLevel;
import com.example.examplemod.planet.PlanetDefinition;
import com.example.examplemod.planet.PlanetFaction;
import com.example.examplemod.planet.PlanetNavigationMenu;
import com.example.examplemod.planet.PlanetSounds;
import com.example.examplemod.planet.PlanetTravelRequestPacket;
import com.example.examplemod.planet.PlanetTravelState;
import com.example.examplemod.planet.PlanetUnlockRequirement;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Imperial Planetary Navigation — the destination browser.
 *
 * <h2>Three panels, one selection</h2>
 *
 * Left is the filtered, searchable list; centre is the selected world turning against the stars;
 * right is everything written about it. All three read from one field ({@link #selected}), which is
 * why clicking a row updates the whole screen and why nothing has to be kept in sync.
 *
 * <h2>The filter is a menu, not a row of chips</h2>
 *
 * Nine filter buttons laid across the top is what the first version did, and at anything below a
 * very wide window they wrapped into the panels underneath and drew over the planet's name. A single
 * <b>hamburger button that opens a vertical list</b> costs one click and cannot collide with
 * anything: it is drawn last, over everything, and it closes as soon as a choice is made.
 *
 * <p>The same reasoning governs the rest of the layout — every panel gets a fixed region computed
 * from the window size, and every piece of text is either wrapped or truncated to its region.
 * Nothing is allowed to decide for itself how much room it takes.
 *
 * <h2>Nothing here decides anything</h2>
 *
 * The screen paints the state the server sent with the menu and sends one packet when the player
 * confirms. It never checks an unlock itself, never resolves a dimension, and never assumes a launch
 * succeeded — the server closes the screen when the countdown starts.
 */
public class PlanetNavigationScreen extends AbstractContainerScreen<PlanetNavigationMenu> {

    /** Filters offered by the hamburger menu, in order. */
    private enum Filter {
        ALL, IMPERIUM, ORKS, NECRONS, CONTESTED, SAFE, DANGEROUS, UNLOCKED, LOCKED;

        Component label() {
            return Component.translatable(
                    "gui.firstcrusade.planet.filter." + name().toLowerCase(Locale.ROOT));
        }

        boolean matches(PlanetNavigationMenu.Entry entry) {
            PlanetDefinition planet = entry.definition();

            return switch (this) {
                case ALL -> true;
                case IMPERIUM -> planet.dominantFaction() == PlanetFaction.IMPERIUM;
                case ORKS -> planet.dominantFaction() == PlanetFaction.ORKS;
                case NECRONS -> planet.dominantFaction() == PlanetFaction.NECRONS;
                case CONTESTED -> planet.dominantFaction() == PlanetFaction.CONTESTED;
                case SAFE -> planet.dangerLevel() == PlanetDangerLevel.LOW
                        || planet.dangerLevel() == PlanetDangerLevel.MODERATE;
                case DANGEROUS -> planet.dangerLevel() == PlanetDangerLevel.HIGH
                        || planet.dangerLevel() == PlanetDangerLevel.EXTREME;
                case UNLOCKED -> entry.state() != PlanetTravelState.LOCKED;
                case LOCKED -> entry.state() == PlanetTravelState.LOCKED;
            };
        }
    }

    private static final int MARGIN = 14;
    private static final int ROW_HEIGHT = 30;

    /**
     * Column widths as the screen would like them, and as narrow as it will let them go.
     *
     * <p>The first version pinned the two side columns at their ideal width and gave the centre
     * whatever was left. At the default Minecraft window — 854×480, GUI scale auto, which is a 427×240
     * canvas — "whatever was left" is 44 pixels: the planet is wider than the panel that holds it and
     * the Return button underneath is too narrow to read its own label. The side columns now give way
     * first, in proportion to their size, until the centre has {@link #CENTRE_MIN} to work with.
     */
    private static final int LIST_WIDTH_MAX = 148;
    private static final int LIST_WIDTH_MIN = 110;
    private static final int INFO_WIDTH_MAX = 182;
    private static final int INFO_WIDTH_MIN = 128;
    private static final int CENTRE_MIN = 104;

    /** The gap between two columns. */
    private static final int GAP = 8;

    /** Title bar, then the search + filter row. Fixed, so no panel has to guess where it starts. */
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 28;

    private static final int FILTER_BUTTON_MAX = 74;
    private static final int FILTER_BUTTON_MIN = 44;
    private static final int MENU_ITEM_HEIGHT = 13;

    /** How small a line may be set before the screen gives up and truncates it instead. */
    private static final float MIN_TEXT_SCALE = 0.7F;

    private final List<PlanetNavigationMenu.Entry> visible = new ArrayList<>();

    @Nullable
    private PlanetNavigationMenu.Entry selected;

    private boolean confirmedSelection;
    private Filter filter = Filter.ALL;
    private boolean filterMenuOpen;
    private String search = "";
    private int scroll;
    private int descriptionScroll;
    private float spin;

    private EditBox searchBox;
    private Button actionButton;
    private Button returnButton;

    @Nullable
    private PlanetNavigationConfirmDialog dialog;

    public PlanetNavigationScreen(PlanetNavigationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 0;
        this.imageHeight = 0;
    }

    // ================================================================= layout

    private int contentLeft() {
        return MARGIN;
    }

    private int contentTop() {
        return MARGIN;
    }

    private int contentWidth() {
        return this.width - MARGIN * 2;
    }

    private int contentHeight() {
        return this.height - MARGIN * 2;
    }

    private int panelTop() {
        return contentTop() + HEADER_HEIGHT;
    }

    private int panelHeight() {
        return contentHeight() - HEADER_HEIGHT - FOOTER_HEIGHT;
    }

    private int listLeft() {
        return contentLeft() + 4;
    }

    /** What the three columns have to share, once the insets and the two gaps are taken out. */
    private int columnsRoom() {
        return contentWidth() - 8 - GAP * 2;
    }

    /**
     * How much a side column has to give up so the centre can keep {@link #CENTRE_MIN}.
     *
     * <p>Split between the two in proportion to their ideal widths, so the list and the dossier
     * narrow together rather than one of them collapsing while the other keeps its whole width.
     */
    private int shrinkOf(int idealWidth) {
        int shortfall = LIST_WIDTH_MAX + INFO_WIDTH_MAX + CENTRE_MIN - columnsRoom();
        if (shortfall <= 0) {
            return 0;
        }

        return shortfall * idealWidth / (LIST_WIDTH_MAX + INFO_WIDTH_MAX);
    }

    private int listInnerWidth() {
        return Math.max(LIST_WIDTH_MIN, LIST_WIDTH_MAX - shrinkOf(LIST_WIDTH_MAX));
    }

    private int infoWidth() {
        return Math.max(INFO_WIDTH_MIN, INFO_WIDTH_MAX - shrinkOf(INFO_WIDTH_MAX));
    }

    private int infoLeft() {
        return contentLeft() + contentWidth() - infoWidth() - 4;
    }

    private int centreLeft() {
        return listLeft() + listInnerWidth() + GAP;
    }

    private int centreWidth() {
        return infoLeft() - centreLeft() - GAP;
    }

    /** The hamburger shrinks with its column, so the search box never disappears behind it. */
    private int filterButtonWidth() {
        return Math.min(FILTER_BUTTON_MAX, Math.max(FILTER_BUTTON_MIN, listInnerWidth() / 2));
    }

    private int filterButtonX() {
        return listLeft() + listInnerWidth() - filterButtonWidth();
    }

    private int filterButtonY() {
        return contentTop() + 22;
    }

    @Override
    protected void init() {
        super.init();

        int searchWidth = listInnerWidth() - filterButtonWidth() - 4;

        this.searchBox = new EditBox(this.font, listLeft() + 1, filterButtonY() + 1,
                searchWidth - 2, 12, Component.translatable("gui.firstcrusade.planet.search"));
        this.searchBox.setHint(Component.translatable("gui.firstcrusade.planet.search")
                .withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setMaxLength(32);
        this.searchBox.setResponder(text -> {
            this.search = text.toLowerCase(Locale.ROOT);
            this.scroll = 0;
            rebuild();
        });
        addRenderableWidget(this.searchBox);

        int buttonY = contentTop() + contentHeight() - 23;

        this.returnButton = Button.builder(returnLabel(), button -> requestReturn())
                .bounds(centreLeft(), buttonY, centreWidth(), 20)
                .build();
        addRenderableWidget(this.returnButton);

        this.actionButton = Button.builder(
                        Component.translatable("button.firstcrusade.select_destination"),
                        button -> onAction())
                .bounds(infoLeft(), buttonY, infoWidth(), 20)
                .build();
        addRenderableWidget(this.actionButton);

        rebuild();
        updateButtons();
    }

    private Component returnLabel() {
        if (!this.menu.canReturn()) {
            return Component.translatable("button.firstcrusade.return_unavailable");
        }

        return Component.translatable("button.firstcrusade.return_to",
                this.menu.returnDestinationName());
    }

    private void rebuild() {
        this.visible.clear();

        for (PlanetNavigationMenu.Entry entry : this.menu.getEntries()) {
            if (!this.filter.matches(entry)) {
                continue;
            }

            if (!this.search.isEmpty()) {
                String name = entry.definition().displayName().getString().toLowerCase(Locale.ROOT);
                if (!name.contains(this.search)) {
                    continue;
                }
            }

            this.visible.add(entry);
        }

        clampScroll();
    }

    private int visibleRows() {
        return Math.max(1, (panelHeight() - 4) / ROW_HEIGHT);
    }

    private void clampScroll() {
        int overflow = Math.max(0, this.visible.size() - visibleRows());
        this.scroll = Math.max(0, Math.min(this.scroll, overflow));
    }

    private void select(@Nullable PlanetNavigationMenu.Entry entry) {
        this.selected = entry;
        this.confirmedSelection = false;
        this.descriptionScroll = 0;
        updateButtons();
    }

    /**
     * Keeps both buttons' label and enabled state honest.
     *
     * <p>The first version simply greyed a button out, and that is how a working screen comes to
     * look broken: the player sees a dead button and no reason for it. Now the label itself says
     * what is wrong, and the tooltip says it in full.
     */
    private void updateButtons() {
        if (this.actionButton == null || this.returnButton == null) {
            return;
        }

        setLabel(this.returnButton, returnLabel());
        this.returnButton.active = this.menu.canReturn() && this.menu.getTerminalBlocker().ok();

        if (this.selected == null) {
            setLabel(this.actionButton,
                    Component.translatable("button.firstcrusade.select_destination"));
            this.actionButton.active = false;
            return;
        }

        switch (this.selected.state()) {
            case LOCKED -> {
                setLabel(this.actionButton,
                        Component.translatable("button.firstcrusade.destination_locked"));
                this.actionButton.active = false;
            }
            case DISCOVERED_NOT_DEPLOYABLE -> {
                setLabel(this.actionButton,
                        Component.translatable("button.firstcrusade.not_deployable"));
                this.actionButton.active = false;
            }
            case CURRENT -> {
                setLabel(this.actionButton,
                        Component.translatable("button.firstcrusade.already_here"));
                this.actionButton.active = false;
            }
            case AVAILABLE -> {
                setLabel(this.actionButton, Component.translatable(this.confirmedSelection
                        ? "button.firstcrusade.initiate_travel"
                        : "button.firstcrusade.select_destination"));
                this.actionButton.active = this.menu.getTerminalBlocker().ok();
            }
        }
    }

    /** Every button label is cut to its own button; the full text stays in the tooltip. */
    private void setLabel(Button button, Component label) {
        button.setMessage(fitText(label, button.getWidth() - 8));
    }

    private void onAction() {
        if (this.selected == null || !this.selected.state().canTravel()) {
            return;
        }

        if (!this.confirmedSelection) {
            this.confirmedSelection = true;
            playSound(PlanetSounds.SELECTED.get());
            updateButtons();
            return;
        }

        this.dialog = new PlanetNavigationConfirmDialog(this.selected.definition(), this::confirmTravel,
                () -> {
                    this.dialog = null;
                    playSound(PlanetSounds.TRAVEL_CANCEL.get());
                });
    }

    private void confirmTravel() {
        this.dialog = null;

        if (this.selected == null) {
            return;
        }

        playSound(PlanetSounds.TRAVEL_CONFIRM.get());
        FirstCrusadeNetwork.CHANNEL.sendToServer(new PlanetTravelRequestPacket(
                this.selected.definition().id(), this.menu.getTerminalPos()));
    }

    private void requestReturn() {
        playSound(PlanetSounds.TRAVEL_CONFIRM.get());
        FirstCrusadeNetwork.CHANNEL.sendToServer(
                PlanetTravelRequestPacket.returnHome(this.menu.getTerminalPos()));
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
        }
    }

    /**
     * Cuts a line to fit its column, with an ellipsis. Nothing in this screen may overflow.
     *
     * <p>Returns a {@link Component} rather than only a drawable sequence because the two buttons
     * need it too: {@link Button} centres whatever message it is given and will happily draw a label
     * wider than itself, which is what "Return: Macragge" does in a narrow window.
     */
    private Component fitText(Component text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }

        String plain = this.font.plainSubstrByWidth(text.getString(), width - this.font.width("..."));
        return Component.literal(plain + "...").withStyle(text.getStyle());
    }

    private FormattedCharSequence fit(Component text, int width) {
        return fitText(text, width).getVisualOrderText();
    }

    /**
     * Draws a line that has to fit, shrinking the type before cutting the words.
     *
     * <p>An ellipsis costs the player the information: "Agri World Verda…" and "Hive World Necro…"
     * are two worlds whose names the list never actually shows. Type set at {@link #MIN_TEXT_SCALE}
     * of full size is still perfectly legible at any GUI scale, and it shows the whole name — so the
     * column shrinks the letters first and only truncates what does not fit even then.
     */
    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        int full = this.font.width(text);

        if (full <= width) {
            graphics.drawString(this.font, text, x, y, colour, false);
            return;
        }

        float scale = Math.max(MIN_TEXT_SCALE, width / (float) full);

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, fit(text, (int) (width / scale)), 0, 0, colour, false);
        graphics.pose().popPose();
    }

    /** {@link #drawFitted} for a line centred on {@code centreX}. */
    private void drawFittedCentred(GuiGraphics graphics, Component text, int centreX, int y,
                                   int width, int colour) {
        int full = this.font.width(text);

        if (full <= width) {
            graphics.drawString(this.font, text, centreX - full / 2, y, colour, false);
            return;
        }

        float scale = Math.max(MIN_TEXT_SCALE, width / (float) full);
        FormattedCharSequence line = fit(text, (int) (width / scale));
        int drawn = (int) (this.font.width(line) * scale);

        graphics.pose().pushPose();
        graphics.pose().translate(centreX - drawn / 2.0F, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, line, 0, 0, colour, false);
        graphics.pose().popPose();
    }

    // ================================================================= rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.spin += partialTick * 0.35F;

        PlanetNavigationConfirmDialog modal = this.dialog;

        // While the modal is up, the terminal behind it is not drawn at all.
        //
        // It used to be drawn dimmed underneath, and the terminal's own readouts came out written
        // across the modal. Two attempts at ordering the layers failed — drawing the modal last did
        // not put it on top, and neither did closing the draw batch before it — so the layering is
        // no longer relied upon: what is not drawn cannot bleed. A modal that owns the whole screen
        // is also the honest reading of a dialog that will not let you click anything else.
        if (modal != null) {
            renderBackground(graphics);
            modal.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
            return;
        }

        renderBackground(graphics);
        PlanetNavigationTheme.panel(graphics, contentLeft(), contentTop(),
                contentWidth(), contentHeight());

        graphics.drawString(this.font, this.title, contentLeft() + 8, contentTop() + 7,
                PlanetNavigationTheme.GOLD, false);
        PlanetNavigationTheme.rule(graphics, contentLeft() + 6, contentTop() + 18,
                contentWidth() - 12);

        renderList(graphics, mouseX, mouseY);
        renderPortrait(graphics);
        renderInfo(graphics, mouseX, mouseY);
        renderTerminalStatus(graphics);

        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }

        // The filter button and its menu are drawn after the widgets so the open menu covers them,
        // and the tooltip after that so it covers everything.
        renderFilterControl(graphics, mouseX, mouseY);

        renderRowTooltip(graphics, mouseX, mouseY);
        renderButtonTooltip(graphics, mouseX, mouseY);
    }

    private void renderFilterControl(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = filterButtonX();
        int y = filterButtonY();
        boolean hovered = inside(mouseX, mouseY, x, y, filterButtonWidth(), 14);

        graphics.fill(x, y, x + filterButtonWidth(), y + 14, hovered ? 0xFF2A343A : 0xFF1A2228);
        PlanetNavigationTheme.brackets(graphics, x, y, filterButtonWidth(), 14,
                this.filterMenuOpen ? PlanetNavigationTheme.GOLD : PlanetNavigationTheme.FRAME_LIT);

        // The three bars. Cheaper than a glyph and readable at any GUI scale.
        for (int bar = 0; bar < 3; bar++) {
            graphics.fill(x + 4, y + 4 + bar * 3, x + 11, y + 5 + bar * 3,
                    PlanetNavigationTheme.TEXT);
        }

        graphics.drawString(this.font, fit(this.filter.label(), filterButtonWidth() - 18),
                x + 15, y + 3, PlanetNavigationTheme.TEXT, false);

        if (!this.filterMenuOpen) {
            return;
        }

        Filter[] values = Filter.values();
        int menuHeight = values.length * MENU_ITEM_HEIGHT + 2;
        int menuY = y + 15;

        PlanetNavigationTheme.panel(graphics, x, menuY, filterButtonWidth(), menuHeight,
                0xF00A0E12);

        for (int i = 0; i < values.length; i++) {
            int itemY = menuY + 1 + i * MENU_ITEM_HEIGHT;
            boolean itemHovered = inside(mouseX, mouseY, x + 1, itemY,
                    filterButtonWidth() - 2, MENU_ITEM_HEIGHT);
            boolean active = values[i] == this.filter;

            if (itemHovered) {
                graphics.fill(x + 1, itemY, x + filterButtonWidth() - 1, itemY + MENU_ITEM_HEIGHT,
                        0x40D9B65C);
            }

            graphics.drawString(this.font, fit(values[i].label(), filterButtonWidth() - 10),
                    x + 5, itemY + 3,
                    active ? PlanetNavigationTheme.GOLD : PlanetNavigationTheme.TEXT, false);
        }
    }

    private void renderTerminalStatus(GuiGraphics graphics) {
        if (this.menu.getTerminalBlocker().ok()) {
            return;
        }

        graphics.drawString(this.font,
                fit(this.menu.getTerminalBlocker().message(), contentWidth() - 16),
                contentLeft() + 8, contentTop() + contentHeight() - 36,
                PlanetNavigationTheme.ALERT, false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = listLeft();
        int y = panelTop();
        int width = listInnerWidth();
        int height = panelHeight();

        PlanetNavigationTheme.panel(graphics, x, y, width, height, PlanetNavigationTheme.WELL);
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        int rowY = y + 2 - this.scroll * ROW_HEIGHT;
        for (PlanetNavigationMenu.Entry entry : this.visible) {
            renderRow(graphics, entry, x + 2, rowY, width - 4, mouseX, mouseY);
            rowY += ROW_HEIGHT;
        }

        graphics.disableScissor();

        if (this.visible.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.firstcrusade.planet.no_results"),
                    x + 6, y + 8, PlanetNavigationTheme.TEXT_DIM, false);
        }

        // Scrollbar, only when there is something to scroll.
        int overflow = this.visible.size() - visibleRows();
        if (overflow > 0) {
            int trackHeight = height - 4;
            int thumbHeight = Math.max(12, trackHeight * visibleRows() / this.visible.size());
            int thumbY = y + 2 + (trackHeight - thumbHeight) * this.scroll / overflow;
            graphics.fill(x + width - 3, y + 2, x + width - 1, y + height - 2, 0x40000000);
            graphics.fill(x + width - 3, thumbY, x + width - 1, thumbY + thumbHeight,
                    PlanetNavigationTheme.FRAME_LIT);
        }
    }

    private void renderRow(GuiGraphics graphics, PlanetNavigationMenu.Entry entry,
                           int x, int y, int width, int mouseX, int mouseY) {
        PlanetDefinition planet = entry.definition();
        boolean locked = entry.state() == PlanetTravelState.LOCKED;
        boolean isSelected = entry == this.selected;
        boolean hovered = inside(mouseX, mouseY, x, y, width, ROW_HEIGHT - 2);

        graphics.fill(x, y, x + width, y + ROW_HEIGHT - 2,
                isSelected ? 0x50D9B65C : (hovered ? 0x30FFFFFF : 0x30000000));

        if (isSelected) {
            PlanetNavigationTheme.brackets(graphics, x, y, width, ROW_HEIGHT - 2,
                    PlanetNavigationTheme.GOLD);
        }

        int iconSize = 20;
        int iconY = y + 3;
        if (locked) {
            graphics.setColor(0.35F, 0.35F, 0.35F, 1.0F);
        }
        graphics.blit(planet.iconTexture(), x + 3, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (locked) {
            graphics.blit(PlanetNavigationTheme.LOCK, x + 3 + iconSize - 9, iconY + iconSize - 9,
                    0, 0, 8, 8, 8, 8);
        }

        // Everything right of the icon has to share what is left with the danger pips and the
        // faction stripe, so the text column is computed rather than assumed.
        int textX = x + iconSize + 7;
        int pipRoom = 4 * 5 + 4;
        int textWidth = width - (textX - x) - pipRoom;

        int nameColour = locked ? PlanetNavigationTheme.DISABLED
                : (entry.state() == PlanetTravelState.CURRENT ? PlanetNavigationTheme.TERMINAL_GREEN
                        : PlanetNavigationTheme.TEXT);

        drawFitted(graphics, planet.displayName(), textX, y + 4, textWidth, nameColour);
        drawFitted(graphics,
                planet.worldType().displayName().copy().withStyle(ChatFormatting.ITALIC),
                textX, y + 15, textWidth + pipRoom - 6,
                locked ? PlanetNavigationTheme.DISABLED : PlanetNavigationTheme.TEXT_DIM);

        int pips = switch (planet.dangerLevel()) {
            case LOW -> 1;
            case MODERATE -> 2;
            case HIGH -> 3;
            case EXTREME -> 4;
            case UNKNOWN -> 0;
        };

        int pipX = x + width - 4;
        for (int i = 0; i < pips; i++) {
            graphics.fill(pipX - 4, y + 4, pipX - 1, y + 9,
                    locked ? PlanetNavigationTheme.DISABLED : planet.dangerLevel().colour());
            pipX -= 5;
        }

        graphics.fill(x + width - 2, y + 13, x + width - 1, y + ROW_HEIGHT - 5,
                locked ? PlanetNavigationTheme.DISABLED : planet.dominantFaction().colour());
    }

    private void renderRowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        PlanetNavigationMenu.Entry hovered = rowAt(mouseX, mouseY);
        if (hovered == null || this.filterMenuOpen) {
            return;
        }

        PlanetDefinition planet = hovered.definition();
        List<Component> lines = new ArrayList<>();

        lines.add(planet.displayName().copy().withStyle(ChatFormatting.GOLD));
        lines.add(planet.worldType().displayName().copy().withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.firstcrusade.planet.faction")
                .append(": ").append(planet.dominantFaction().displayName())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.firstcrusade.planet.danger")
                .append(": ").append(planet.dangerLevel().displayName())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());

        switch (hovered.state()) {
            case LOCKED -> {
                lines.add(Component.translatable("gui.firstcrusade.planet.locked_destination")
                        .withStyle(ChatFormatting.RED));
                lines.add(Component.translatable("gui.firstcrusade.planet.requirements")
                        .withStyle(ChatFormatting.GRAY));
                for (PlanetUnlockRequirement requirement : hovered.missing()) {
                    lines.add(Component.literal("- ").append(requirement.description())
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            case DISCOVERED_NOT_DEPLOYABLE -> {
                lines.add(Component.translatable("gui.firstcrusade.planet.data_acquired")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("gui.firstcrusade.planet.not_deployable")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            case CURRENT -> lines.add(Component.translatable("gui.firstcrusade.planet.you_are_here")
                    .withStyle(ChatFormatting.AQUA));
            case AVAILABLE -> {
                lines.add(Component.translatable("gui.firstcrusade.planet.available")
                        .withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("gui.firstcrusade.planet.click_to_inspect")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        ItemStack cost = planet.travelCost().stack();
        if (!cost.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.firstcrusade.planet.travel_cost",
                    cost.getCount(), cost.getHoverName()).withStyle(ChatFormatting.GRAY));
        }

        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** A disabled button explains itself on hover — a dead control with no reason reads as a bug. */
    private void renderButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.actionButton == null || this.returnButton == null) {
            return;
        }

        if (this.returnButton.isMouseOver(mouseX, mouseY)) {
            // Disabled: say why. Enabled but cut short by a narrow window: say the whole thing.
            if (!this.returnButton.active) {
                graphics.renderTooltip(this.font,
                        Component.translatable("gui.firstcrusade.planet.return_hint")
                                .withStyle(ChatFormatting.GRAY), mouseX, mouseY);
                return;
            }

            Component full = returnLabel();
            if (this.font.width(full) > this.returnButton.getWidth() - 8) {
                graphics.renderTooltip(this.font, full.copy().withStyle(ChatFormatting.GRAY),
                        mouseX, mouseY);
            }
            return;
        }

        if (!this.actionButton.active && this.actionButton.isMouseOver(mouseX, mouseY)
                && this.selected != null) {
            Component reason = switch (this.selected.state()) {
                case LOCKED -> Component.translatable("gui.firstcrusade.planet.locked_destination");
                case DISCOVERED_NOT_DEPLOYABLE ->
                        Component.translatable("gui.firstcrusade.planet.not_deployable");
                case CURRENT -> Component.translatable("gui.firstcrusade.planet.you_are_here");
                case AVAILABLE -> this.menu.getTerminalBlocker().message();
            };

            graphics.renderTooltip(this.font, reason.copy().withStyle(ChatFormatting.GRAY),
                    mouseX, mouseY);
        }
    }

    private void renderPortrait(GuiGraphics graphics) {
        int x = centreLeft();
        int y = panelTop();
        int width = centreWidth();
        int height = panelHeight();

        PlanetNavigationTheme.panel(graphics, x, y, width, height, PlanetNavigationTheme.WELL);
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        for (int i = 0; i < 70; i++) {
            int hash = Math.abs((i * 73856093) ^ (i * 19349663));
            int sx = x + 3 + hash % Math.max(1, width - 6);
            int sy = y + 3 + (hash / 7) % Math.max(1, height - 6);
            graphics.fill(sx, sy, sx + 1, sy + 1, (i % 3 == 0) ? 0x60FFFFFF : 0x30FFFFFF);
        }

        if (this.selected == null) {
            graphics.disableScissor();
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.firstcrusade.planet.select_prompt"),
                    x + width / 2, y + height / 2 - 4, PlanetNavigationTheme.TEXT_DIM);
            return;
        }

        PlanetDefinition planet = this.selected.definition();

        drawFitted(graphics, planet.displayName(), x + 6, y + 6, width - 12,
                PlanetNavigationTheme.GOLD);

        int size = Math.max(48, Math.min(112, Math.min(width - 28, height - 72)));
        int px = x + (width - size) / 2;
        int py = y + 22;

        renderHalo(graphics, px + size / 2, py + size / 2, size / 2 + 5,
                planet.atmosphereColour());

        int offset = (int) (this.spin % size);
        graphics.enableScissor(px, py, px + size, py + size);
        graphics.blit(planet.largeTexture(), px - offset, py, 0, 0, size, size, size, size);
        graphics.blit(planet.largeTexture(), px - offset + size, py, 0, 0, size, size, size, size);
        graphics.disableScissor();

        for (int moon = 0; moon < planet.moons(); moon++) {
            double angle = this.spin * 0.04D + moon * 2.1D;
            int mx = px + size / 2 + (int) (Math.cos(angle) * (size * 0.66D));
            int my = py + size / 2 + (int) (Math.sin(angle) * (size * 0.30D));
            graphics.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFFB0B8BC);
        }

        // These three were the only lines on the screen drawn with no width to obey, which is how
        // "Control: Imperium of Man" ended up sliced open at both edges of its own panel.
        int statusY = py + size + 6;
        int statusWidth = width - 8;

        drawFittedCentred(graphics, planet.worldType().displayName(),
                x + width / 2, statusY, statusWidth, PlanetNavigationTheme.TEXT_DIM);
        drawFittedCentred(graphics,
                Component.translatable("gui.firstcrusade.planet.control",
                        planet.dominantFaction().displayName()),
                x + width / 2, statusY + 11, statusWidth, planet.dominantFaction().colour());
        drawFittedCentred(graphics, this.selected.state().displayName(),
                x + width / 2, statusY + 22, statusWidth,
                switch (this.selected.state()) {
                    case AVAILABLE -> PlanetNavigationTheme.TERMINAL_GREEN;
                    case CURRENT -> 0xFF6FD0E0;
                    case DISCOVERED_NOT_DEPLOYABLE -> PlanetNavigationTheme.GOLD;
                    case LOCKED -> PlanetNavigationTheme.ALERT;
                });

        graphics.disableScissor();
    }

    /**
     * The atmospheric glow behind a planet, drawn as scanlines of a circle.
     *
     * <p>The first version filled three nested rectangles, which put a hard green square around
     * every world — the halo has to follow the disc or it reads as a background panel rather than as
     * an atmosphere. Two dozen horizontal fills is still nothing, and it is round.
     */
    private void renderHalo(GuiGraphics graphics, int cx, int cy, int radius, int colour) {
        int rgb = colour & 0x00FFFFFF;

        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            if (half <= 0) {
                continue;
            }

            // Fades out toward the rim: alpha falls with distance from the centre.
            double edge = Math.abs(dy) / (double) radius;
            int alpha = (int) (56 * (1.0 - edge * edge)) << 24;
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, alpha | rgb);
        }
    }

    private void renderInfo(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = infoLeft();
        int y = panelTop();
        int width = infoWidth();
        int height = panelHeight();

        PlanetNavigationTheme.panel(graphics, x, y, width, height, PlanetNavigationTheme.WELL);

        if (this.selected == null) {
            return;
        }

        PlanetDefinition planet = this.selected.definition();
        int textX = x + 6;
        int textWidth = width - 12;
        int cursor = y + 6;

        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        drawFitted(graphics, planet.displayName().copy().withStyle(ChatFormatting.BOLD),
                textX, cursor, textWidth, PlanetNavigationTheme.GOLD);
        cursor += 13;

        cursor = field(graphics, "gui.firstcrusade.planet.world_type",
                planet.worldType().displayName(), textX, cursor, textWidth,
                PlanetNavigationTheme.TEXT);
        cursor = field(graphics, "gui.firstcrusade.planet.faction",
                planet.dominantFaction().displayName(), textX, cursor, textWidth,
                planet.dominantFaction().colour());
        cursor = field(graphics, "gui.firstcrusade.planet.danger",
                planet.dangerLevel().displayName(), textX, cursor, textWidth,
                planet.dangerLevel().colour());
        cursor = field(graphics, "gui.firstcrusade.planet.military",
                planet.militaryStatus().displayName(), textX, cursor, textWidth,
                PlanetNavigationTheme.TEXT);
        cursor = field(graphics, "gui.firstcrusade.planet.resources",
                planet.resources(), textX, cursor, textWidth, PlanetNavigationTheme.TEXT);

        PlanetNavigationTheme.rule(graphics, textX, cursor, textWidth);
        cursor += 5;

        graphics.drawString(this.font, Component.translatable("gui.firstcrusade.planet.description"),
                textX, cursor, PlanetNavigationTheme.TEXT_DIM, false);
        cursor += 11;

        // The description scrolls inside whatever room is left, and the amount left is measured
        // rather than assumed — the fields above it are different heights on different planets.
        List<FormattedCharSequence> body = this.font.split(planet.description(), textWidth);
        int room = (y + height - 4) - cursor;
        int lineCount = Math.max(1, room / 10);
        int maxScroll = Math.max(0, body.size() - lineCount);
        this.descriptionScroll = Math.min(this.descriptionScroll, maxScroll);

        for (int i = this.descriptionScroll; i < body.size(); i++) {
            if (cursor > y + height - 12) {
                break;
            }
            graphics.drawString(this.font, body.get(i), textX, cursor,
                    PlanetNavigationTheme.TEXT, false);
            cursor += 10;
        }

        graphics.disableScissor();

        if (maxScroll > 0) {
            graphics.drawString(this.font,
                    Component.literal(this.descriptionScroll < maxScroll ? "▼" : "▲"),
                    x + width - 10, y + height - 11, PlanetNavigationTheme.TEXT_DIM, false);
        }
    }

    private int field(GuiGraphics graphics, String labelKey, Component value,
                      int x, int y, int width, int colour) {
        graphics.drawString(this.font, Component.translatable(labelKey), x, y,
                PlanetNavigationTheme.TEXT_DIM, false);

        int cursor = y + 10;
        for (FormattedCharSequence line : this.font.split(value, width - 4)) {
            graphics.drawString(this.font, line, x + 4, cursor, colour, false);
            cursor += 10;
        }

        return cursor + 2;
    }

    // ================================================================= input

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Nullable
    private PlanetNavigationMenu.Entry rowAt(double mouseX, double mouseY) {
        int x = listLeft();
        int top = panelTop();

        if (!inside(mouseX, mouseY, x, top, listInnerWidth(), panelHeight())) {
            return null;
        }

        int index = (int) ((mouseY - (top + 2)) / ROW_HEIGHT) + this.scroll;
        return index >= 0 && index < this.visible.size() ? this.visible.get(index) : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.dialog != null) {
            return this.dialog.mouseClicked(mouseX, mouseY, this.width, this.height);
        }

        // The open filter menu owns the pointer: it is drawn over the panels, so it has to be
        // asked before them or a click would fall through to whatever is underneath.
        if (this.filterMenuOpen && handleFilterMenuClick(mouseX, mouseY)) {
            return true;
        }

        if (inside(mouseX, mouseY, filterButtonX(), filterButtonY(), filterButtonWidth(), 14)) {
            this.filterMenuOpen = !this.filterMenuOpen;
            playSound(PlanetSounds.SELECTED.get());
            return true;
        }

        if (this.filterMenuOpen) {
            this.filterMenuOpen = false;
            return true;
        }

        PlanetNavigationMenu.Entry clicked = rowAt(mouseX, mouseY);
        if (clicked != null) {
            playSound(clicked.state() == PlanetTravelState.LOCKED
                    ? PlanetSounds.LOCKED.get() : PlanetSounds.SELECTED.get());
            select(clicked);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleFilterMenuClick(double mouseX, double mouseY) {
        Filter[] values = Filter.values();
        int x = filterButtonX();
        int menuY = filterButtonY() + 15;

        for (int i = 0; i < values.length; i++) {
            int itemY = menuY + 1 + i * MENU_ITEM_HEIGHT;

            if (inside(mouseX, mouseY, x + 1, itemY, filterButtonWidth() - 2, MENU_ITEM_HEIGHT)) {
                this.filter = values[i];
                this.filterMenuOpen = false;
                this.scroll = 0;
                playSound(PlanetSounds.SELECTED.get());
                rebuild();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.dialog != null || this.filterMenuOpen) {
            return true;
        }

        if (inside(mouseX, mouseY, listLeft(), panelTop(), listInnerWidth(), panelHeight())) {
            this.scroll -= (int) Math.signum(delta);
            clampScroll();
            return true;
        }

        if (inside(mouseX, mouseY, infoLeft(), panelTop(), infoWidth(), panelHeight())) {
            this.descriptionScroll = Math.max(0, this.descriptionScroll - (int) Math.signum(delta));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (this.dialog != null) {
                this.dialog = null;
                playSound(PlanetSounds.TRAVEL_CANCEL.get());
                return true;
            }
            if (this.filterMenuOpen) {
                this.filterMenuOpen = false;
                return true;
            }
        }

        if (this.searchBox != null && this.searchBox.isFocused() && keyCode != 256) {
            return this.searchBox.keyPressed(keyCode, scanCode, modifiers)
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
