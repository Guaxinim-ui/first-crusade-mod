package com.example.examplemod;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

public class ImperialCommandCoreScreen extends AbstractContainerScreen<ImperialCommandCoreMenu> {
    // The Build tab is gone with the city builder: there is no structure to raise, no mine to
    // place and no builder tool to hand out, so a tab of dead buttons would only be a lie about
    // what the base can do. Its former index is not reserved — the tabs simply renumber.
    // The Core stopped being a town hall and became an operations room, so the tabs are the
    // headings a commander actually reads: what the base is, who is in it, what it is doing, what
    // the Apothecarion is growing, and what the campaign has cost. City/Defense kept their code and
    // took new names rather than being rewritten — the panels behind them already said the right
    // things, they were only filed under the wrong words.
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_GARRISON = 1;
    private static final int TAB_MILITARY = 2;
    private static final int TAB_DEFENSE = 3;
    private static final int TAB_APOTHECARION = 4;
    private static final int TAB_RECORD = 5;
    private static final int TAB_RESOURCES = 6;
    private static final int TAB_WAR = 7;

    private static final String[] TAB_KEYS = {
            "gui.firstcrusade.tab.overview",
            "gui.firstcrusade.tab.garrison",
            "gui.firstcrusade.tab.military",
            "gui.firstcrusade.tab.vox",
            "gui.firstcrusade.tab.apothecarion",
            "gui.firstcrusade.tab.record",
            "gui.firstcrusade.tab.resources",
            "gui.firstcrusade.tab.war"
    };

    private int activeTab = TAB_OVERVIEW;

    /**
     * Tabs that only report, and therefore get the full window width.
     *
     * <p>Kept as one predicate rather than repeated at each call site: the background, the text
     * column and any future scrollbar all have to agree about which tabs are wide, and three copies
     * of that list is three chances for them to disagree.
     */
    private static boolean isReadOnlyTab(int tab) {
        return tab == TAB_GARRISON || tab == TAB_APOTHECARION || tab == TAB_RECORD;
    }

    // City tab
    private Button upgradeButton;
    private Button governanceButton;
    private Button surveyBorderButton;
    // Military tab
    private Button recruitButton;
    private Button cycleSpecialistButton;
    private Button promoteSpecialistButton;
    // Defense tab
    private Button repairButton;
    private Button reinforcementButton;
    private Button rallyButton;
    private Button fortifyButton;
    private Button forceRaidButton;
    // Resources tab
    private Button depositButton;
    private Button withdrawIronButton;
    private Button withdrawCoalButton;
    private Button withdrawScrapButton;
    private Button withdrawGoldButton;
    private Button withdrawEmeraldButton;
    private Button withdrawCrusadiumButton;
    private Button withdrawFoodButton;

    public ImperialCommandCoreScreen(ImperialCommandCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        // Widened from 320 when the Crusade tabs landed. Eight tabs need 8 + 8*59 = 480, and a
        // tab narrower than its label is a tab whose label is cut in half — which is what 48px did
        // to "APOTHECARION" and "Resources". Every panel is positioned from leftPos, so widening
        // the window moves nothing else.
        this.imageWidth = 480;
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        clearButtonReferences();
        addTabButtons();

        switch (this.activeTab) {
            // Read-only panels. Without these cases they fell through to the City buttons, which
            // then sat on top of the roster text.
            case TAB_GARRISON, TAB_APOTHECARION, TAB_RECORD -> { }
            case TAB_MILITARY -> initMilitaryTab();
            case TAB_DEFENSE -> initDefenseTab();
            case TAB_RESOURCES -> initResourcesTab();
            case TAB_WAR -> { /* the War Table has no buttons, only the map */ }
            default -> initCityTab();
        }

        updateButtonStates();
    }

    private void clearButtonReferences() {
        this.upgradeButton = null;
        this.recruitButton = null;
        this.cycleSpecialistButton = null;
        this.promoteSpecialistButton = null;
        this.repairButton = null;
        this.reinforcementButton = null;
        this.rallyButton = null;
        this.fortifyButton = null;
        this.forceRaidButton = null;
        this.depositButton = null;
        this.withdrawIronButton = null;
        this.withdrawCoalButton = null;
        this.withdrawScrapButton = null;
        this.withdrawGoldButton = null;
        this.withdrawEmeraldButton = null;
        this.withdrawCrusadiumButton = null;
        this.withdrawFoodButton = null;
    }

    private void addTabButtons() {
        int tabY = this.topPos + 20;
        int tabWidth = 54;

        for (int i = 0; i < TAB_KEYS.length; i++) {
            int index = i;
            int tabX = this.leftPos + 8 + i * 59;

            Button tab = this.addRenderableWidget(
                    Button.builder(
                            Component.translatable(TAB_KEYS[i]),
                            button -> {
                                this.activeTab = index;
                                this.rebuildWidgets();
                            }
                    ).bounds(tabX, tabY, tabWidth, 16).build()
            );

            // The active tab is shown as disabled so the player can see where they are.
            tab.active = this.activeTab != index;
        }
    }

    private int buttonX() {
        return this.leftPos + 160;
    }

    private int buttonY(int row) {
        return this.topPos + 46 + row * 20;
    }

    private void initCityTab() {
        this.upgradeButton = addActionButton(buttonX(), buttonY(0), 148, 18, "gui.firstcrusade.button.upgrade_city", ImperialCommandCoreAction.UPGRADE_CITY);
        this.governanceButton = addActionButton(buttonX(), buttonY(1), 148, 18, "gui.firstcrusade.button.appoint_governor", ImperialCommandCoreAction.TOGGLE_GOVERNANCE);
        this.surveyBorderButton = addActionButton(buttonX(), buttonY(2), 148, 18, "gui.firstcrusade.button.survey_border", ImperialCommandCoreAction.SURVEY_BORDER);
    }

    private void initMilitaryTab() {
        int row = 0;
        this.recruitButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.recruit", ImperialCommandCoreAction.RECRUIT_GUARDSMAN);
        this.cycleSpecialistButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.cycle_specialist", ImperialCommandCoreAction.CYCLE_SPECIALIST);
        this.promoteSpecialistButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.promote_specialist", ImperialCommandCoreAction.PROMOTE_SPECIALIST);
    }

    private void initDefenseTab() {
        int row = 0;
        this.repairButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.repair_core", ImperialCommandCoreAction.REPAIR_CORE);
        this.reinforcementButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.reinforcements", ImperialCommandCoreAction.CALL_REINFORCEMENTS);
        this.rallyButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.rally", ImperialCommandCoreAction.RALLY_DEFENDERS);
        this.fortifyButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.fortify", ImperialCommandCoreAction.FORTIFY_DEFENDERS);
        this.forceRaidButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.force_raid", ImperialCommandCoreAction.FORCE_RAID_TEST);
    }

    private void initResourcesTab() {
        int row = 0;
        this.depositButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.deposit_all", ImperialCommandCoreAction.DEPOSIT_RESOURCES);
        this.withdrawIronButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.withdraw_iron", ImperialCommandCoreAction.WITHDRAW_IRON);
        this.withdrawCoalButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.withdraw_coal", ImperialCommandCoreAction.WITHDRAW_COAL);
        this.withdrawScrapButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.withdraw_scrap", ImperialCommandCoreAction.WITHDRAW_SCRAP);
        this.withdrawGoldButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.withdraw_gold", ImperialCommandCoreAction.WITHDRAW_GOLD);
        this.withdrawEmeraldButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.withdraw_emerald", ImperialCommandCoreAction.WITHDRAW_EMERALD);
        this.withdrawCrusadiumButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.withdraw_crusadium", ImperialCommandCoreAction.WITHDRAW_CRUSADIUM);
        this.withdrawFoodButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.withdraw_food", ImperialCommandCoreAction.WITHDRAW_FOOD);
    }

    private Button addActionButton(int x, int y, int width, int height, String langKey, ImperialCommandCoreAction action) {
        return this.addRenderableWidget(
                Button.builder(
                        Component.translatable(langKey),
                        button -> FirstCrusadeNetwork.CHANNEL.sendToServer(
                                new ImperialCommandCoreActionPacket(this.menu.getCommandCorePos(), action)
                        )
                ).bounds(x, y, width, height).build()
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonStates();
    }

    private void updateButtonStates() {
        applyButton(this.upgradeButton, this.menu.getCityLevel() < 5,
                Component.translatable("gui.firstcrusade.tip.upgrade.title"), getUpgradeCostText(),
                Component.translatable("gui.firstcrusade.reason.max_level"));

        // Delegation is only meaningful for a city the player owns; an unclaimed city is always run
        // by its Governor. The label flips between appointing and recalling.
        if (this.governanceButton != null) {
            this.governanceButton.setMessage(Component.translatable(this.menu.getGovernanceState() == 2
                    ? "gui.firstcrusade.button.recall_governor"
                    : "gui.firstcrusade.button.appoint_governor"));
        }

        applyButton(this.governanceButton, this.menu.getGovernanceState() != 0,
                Component.translatable("gui.firstcrusade.tip.governor.title"),
                Component.translatable("gui.firstcrusade.tip.governor.desc"),
                Component.translatable("gui.firstcrusade.reason.unclaimed_governor"));

        applyButton(this.surveyBorderButton, true,
                Component.translatable("gui.firstcrusade.tip.survey.title"),
                Component.translatable("gui.firstcrusade.tip.survey.desc", this.menu.getBuildBorderRadius()),
                null);

        applyButton(this.recruitButton, canTrainRecruit(),
                Component.translatable("gui.firstcrusade.tip.recruit.title"), Component.translatable("gui.firstcrusade.tip.recruit.desc"),
                getRecruitBlockReason());

        applyButton(this.cycleSpecialistButton, true,
                Component.translatable("gui.firstcrusade.tip.cycle_specialist.title"),
                Component.translatable("gui.firstcrusade.tip.cycle_specialist.desc", this.menu.getSelectedSpecialization().getDisplayName()),
                null);

        applyButton(this.promoteSpecialistButton, this.menu.getCityLevel() >= 2,
                Component.translatable("gui.firstcrusade.tip.promote_specialist.title", this.menu.getSelectedSpecialization().getDisplayName()),
                Component.translatable("gui.firstcrusade.tip.promote_specialist.desc"),
                Component.translatable("gui.firstcrusade.reason.req_level_2"));

        applyButton(this.repairButton, this.menu.getCityIntegrity() < 100,
                Component.translatable("gui.firstcrusade.tip.repair.title"), Component.translatable("gui.firstcrusade.tip.repair.desc"),
                Component.translatable("gui.firstcrusade.reason.repair_full"));

        applyButton(this.reinforcementButton,
                this.menu.hasActiveRaid() && this.menu.getReinforcementCooldownSeconds() <= 0 && hasRecruitCapacity(),
                Component.translatable("gui.firstcrusade.tip.reinforcements.title"),
                Component.translatable("gui.firstcrusade.tip.reinforcements.desc", reinforcementWarSupportCost()),
                getReinforcementBlockReason());

        applyButton(this.rallyButton, this.menu.hasActiveRaid(),
                Component.translatable("gui.firstcrusade.tip.rally.title"), Component.translatable("gui.firstcrusade.tip.rally.desc"),
                Component.translatable("gui.firstcrusade.reason.only_during_raid"));

        applyButton(this.fortifyButton, this.menu.hasActiveRaid(),
                Component.translatable("gui.firstcrusade.tip.fortify.title"),
                Component.translatable("gui.firstcrusade.tip.fortify.desc", fortifyWarSupportCost()),
                Component.translatable("gui.firstcrusade.reason.only_during_raid"));

        applyButton(this.forceRaidButton, !this.menu.hasActiveRaid(),
                Component.translatable("gui.firstcrusade.tip.force_raid.title"), Component.translatable("gui.firstcrusade.tip.force_raid.desc"),
                Component.translatable("gui.firstcrusade.reason.raid_active"));

        applyButton(this.depositButton, true,
                Component.translatable("gui.firstcrusade.tip.deposit.title"), Component.translatable("gui.firstcrusade.tip.deposit.desc"),
                null);

        applyWithdrawButton(this.withdrawIronButton, "gui.firstcrusade.res.iron", this.menu.getIron());
        applyWithdrawButton(this.withdrawCoalButton, "gui.firstcrusade.res.coal", this.menu.getCoal());
        applyWithdrawButton(this.withdrawScrapButton, "gui.firstcrusade.res.scrap", this.menu.getScrapMetal());
        applyWithdrawButton(this.withdrawGoldButton, "gui.firstcrusade.res.gold", this.menu.getGold());
        applyWithdrawButton(this.withdrawEmeraldButton, "gui.firstcrusade.res.emerald", this.menu.getEmerald());
        applyWithdrawButton(this.withdrawCrusadiumButton, "gui.firstcrusade.res.crusadium", this.menu.getCrusadium());
        applyWithdrawButton(this.withdrawFoodButton, "gui.firstcrusade.res.food", this.menu.getFood());
    }

    private void applyButton(Button button, boolean active, Component title, @Nullable Component desc, @Nullable Component reason) {
        if (button == null) {
            return;
        }

        button.active = active;

        MutableComponent text = Component.empty().append(title);

        if (desc != null) {
            text.append("\n").append(desc);
        }

        if (!active && reason != null) {
            text.append(Component.literal("\n").append(reason).withStyle(ChatFormatting.RED));
        }

        button.setTooltip(Tooltip.create(text));
    }

    private void applyWithdrawButton(Button button, String resourceKey, int stored) {
        Component resourceName = Component.translatable(resourceKey);

        applyButton(
                button,
                stored > 0,
                Component.translatable("gui.firstcrusade.tip.withdraw.title", resourceName),
                Component.translatable("gui.firstcrusade.tip.withdraw.desc", resourceName, stored),
                Component.translatable("gui.firstcrusade.reason.withdraw_empty", resourceName)
        );
    }

    private boolean hasRecruitCapacity() {
        return this.menu.getRecruitedGuardsmen() < this.menu.getMilitaryCapacity();
    }

    private boolean canTrainRecruit() {
        return militaryHasRoomForRecruit()
                && availableBarracks() > 0
                && this.menu.getUnemployedCitizenCount() > 0;
    }

    private boolean militaryHasRoomForRecruit() {
        return this.menu.getRecruitedGuardsmen() + this.menu.getRecruitsInTraining() < this.menu.getMilitaryCapacity();
    }

    private int availableBarracks() {
        return this.menu.getBarracksCount() - this.menu.getRecruitsInTraining();
    }

    @Nullable
    private Component getRecruitBlockReason() {
        if (this.menu.getBarracksCount() <= 0) {
            return Component.translatable("gui.firstcrusade.reason.need_barracks");
        }

        if (!militaryHasRoomForRecruit()) {
            return Component.translatable("gui.firstcrusade.reason.military_cap");
        }

        if (availableBarracks() <= 0) {
            return Component.translatable("gui.firstcrusade.reason.barracks_busy");
        }

        if (this.menu.getUnemployedCitizenCount() <= 0) {
            return Component.translatable("gui.firstcrusade.reason.no_unemployed");
        }

        return null;
    }



    @Nullable
    private Component getReinforcementBlockReason() {
        if (!this.menu.hasActiveRaid()) {
            return Component.translatable("gui.firstcrusade.reason.only_during_raid");
        }

        if (this.menu.getReinforcementCooldownSeconds() > 0) {
            return Component.translatable("gui.firstcrusade.reason.reinf_cooldown", this.menu.getReinforcementCooldownSeconds());
        }

        if (!hasRecruitCapacity()) {
            return Component.translatable("gui.firstcrusade.reason.military_cap_short");
        }

        return null;
    }

    private Component getUpgradeCostText() {
        int level = this.menu.getCityLevel();

        if (level >= 5) {
            return Component.translatable("gui.firstcrusade.tip.upgrade.max");
        }

        return Component.translatable("gui.firstcrusade.tip.upgrade.desc",
                ImperialCityLevelStats.upgradeIronCost(level),
                ImperialCityLevelStats.upgradeScrapCost(level),
                ImperialCityLevelStats.upgradeCoalCost(level),
                ImperialCityLevelStats.upgradePlateCost(level));
    }

    private int reinforcementWarSupportCost() {
        return ImperialCityLevelStats.reinforcementWarSupportCost(this.menu.getCityLevel());
    }

    private int fortifyWarSupportCost() {
        return ImperialCityLevelStats.fortifyWarSupportCost(this.menu.getCityLevel());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xEE0B0B0B);
        guiGraphics.fill(x, y, x + this.imageWidth, y + 16, 0xEE3A2A12);

        // Content panels. The split into a narrow info column and a wide action column only earns
        // its keep on a tab that has actions: the Crusade tabs are read-only, and squeezing their
        // prose into 146px was what pushed "Status: Unavailable (needs Core level 3)" out through
        // the divider. Those get the whole width instead.
        if (isReadOnlyTab(this.activeTab)) {
            guiGraphics.fill(x + 6, y + 40, x + this.imageWidth - 6, y + this.imageHeight - 6, 0xAA202020);
        } else {
            guiGraphics.fill(x + 6, y + 40, x + 152, y + this.imageHeight - 6, 0xAA202020);
            guiGraphics.fill(x + 156, y + 40, x + this.imageWidth - 6, y + this.imageHeight - 6, 0xAA181818);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawHeader(guiGraphics, "block.firstcrusade.imperial_command_core", 8, 5, 0xFFD6B85A);

        switch (this.activeTab) {
            case TAB_GARRISON -> renderGarrisonInfo(guiGraphics);
            case TAB_MILITARY -> renderMilitaryInfo(guiGraphics);
            case TAB_DEFENSE -> renderDefenseInfo(guiGraphics);
            case TAB_APOTHECARION -> renderApothecarionInfo(guiGraphics);
            case TAB_RECORD -> renderRecordInfo(guiGraphics);
            case TAB_RESOURCES -> renderResourcesInfo(guiGraphics);
            case TAB_WAR -> renderWarInfo(guiGraphics);
            default -> renderCityInfo(guiGraphics);
        }
    }

    // The War Table: a left status column plus an Age-of-Empires-style tactical minimap and a
    // dominion bar on the right (city centred; blue = Imperial units, red = Orks/camp).
    private void renderWarInfo(GuiGraphics g) {
        int y = 46;
        drawHeader(g, "gui.firstcrusade.tab.war", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(g, Component.translatable("gui.firstcrusade.war.dominion", this.menu.getWarDominion()), 12, y, dominionColor());
        y += 11;
        drawLine(g, Component.translatable("gui.firstcrusade.info.threat", getThreatText()), 12, y, getThreatColor());
        y += 11;
        drawLine(g, Component.translatable("gui.firstcrusade.info.crusade", this.menu.getCrusadeTier()), 12, y, 0xFFD6B85A);
        y += 11;
        drawLine(g, Component.translatable("gui.firstcrusade.war.waaagh", this.menu.getWaaaghTier()), 12, y, 0xFF7CCB5A);
        y += 11;
        drawLine(g, Component.translatable("gui.firstcrusade.info.territory", this.menu.getTerritoryRadius()), 12, y, 0xFF9AD0FF);
        y += 11;
        // The map auto-zooms to fit every settlement, so show the span the player is looking at.
        drawLine(g, Component.translatable("gui.firstcrusade.war.map_range", this.menu.getMapRange()), 12, y, 0xFF9AD0FF);

        int mx0 = 158;
        int my0 = 46;
        int size = 148;
        int mx1 = mx0 + size;
        int my1 = my0 + size;
        int cx = mx0 + size / 2;
        int cy = my0 + size / 2;
        int half = size / 2 - 2;
        int range = this.menu.getMapRange();

        g.fill(mx0, my0, mx1, my1, 0xFF0E140E);
        g.fill(mx0, my0, mx1, my0 + 1, 0xFF3A2A12);
        g.fill(mx0, my1 - 1, mx1, my1, 0xFF3A2A12);
        g.fill(mx0, my0, mx0 + 1, my1, 0xFF3A2A12);
        g.fill(mx1 - 1, my0, mx1, my1, 0xFF3A2A12);

        int rPix = Math.min(half, this.menu.getTerritoryRadius() * half / range);
        drawCircle(g, cx, cy, rPix, 0x553A7AFF);

        // Strategic blips: every Imperial city (blue) and Ork camp (green) in the world.
        for (int i = 0; i < this.menu.getBlipCount(); i++) {
            int px = cx + this.menu.getBlipDx(i) * half / range;
            int pz = cy + this.menu.getBlipDz(i) * half / range;
            switch (this.menu.getBlipKind(i)) {
                case 2 -> g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF4CA02C);
                default -> g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF3A7AFF);
            }
        }

        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF3A7AFF);

        int by0 = my1 + 4;
        int by1 = by0 + 8;
        g.fill(mx0, by0, mx1, by1, 0xFF202020);
        int split = mx0 + (this.menu.getWarDominion() + 100) * size / 200;
        g.fill(mx0, by0, split, by1, 0xFF3A7AFF);
        g.fill(split, by0, mx1, by1, 0xFF4CA02C);
        g.fill(split - 1, by0 - 1, split + 1, by1 + 1, 0xFFFFFFFF);
    }

    private void drawCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) {
            return;
        }
        for (int a = 0; a < 360; a += 8) {
            double rad = Math.toRadians(a);
            int px = cx + (int) Math.round(Math.cos(rad) * r);
            int py = cy + (int) Math.round(Math.sin(rad) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private int dominionColor() {
        int d = this.menu.getWarDominion();
        return d >= 15 ? 0xFF6699FF : (d <= -15 ? 0xFF7CCB5A : 0xFFCCCCCC);
    }

    private void renderCityInfo(GuiGraphics guiGraphics) {
        int y = 46;
        drawLine(guiGraphics, this.menu.getCityType().getDisplayName(), 12, y, 0xFFE0C070);
        y += 13;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.level", this.menu.getCityLevel()), 12, y, 0xFFFFFFFF);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.integrity", this.menu.getCityIntegrity()), 12, y, getIntegrityColor());
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.morale", this.menu.getCityMorale(), ImperialCityMoraleManager.getMoraleLabel(this.menu.getCityMorale())), 12, y, getMoraleColor());
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.citizens", this.menu.getCitizenCount(), getCitizenCapacityText()), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.unemployed", this.menu.getUnemployedCitizenCount()), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.soldiers", this.menu.getRecruitedGuardsmen(), this.menu.getMilitaryCapacity()), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.threat", getThreatText()), 12, y, getThreatColor());
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.raid", getRaidStatusText()), 12, y, getRaidStatusColor());
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.crusade", this.menu.getCrusadeTier()), 12, y, 0xFFD6B85A);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.territory", this.menu.getTerritoryRadius()), 12, y, 0xFF9AD0FF);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.governor", this.menu.getGovernorName(), this.menu.getGovernorPersonality().getDisplayName()), 12, y, 0xFFE0C070);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.governance", governanceText()), 12, y, governanceColor());
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.build_border", this.menu.getBuildBorderRadius()), 12, y, 0xFF9AD0FF);
    }

    // Label/colour for the city's current governance: ruled by the Governor (autonomous), run by the
    // player owner, or delegated by the owner to the Governor.
    private Component governanceText() {
        return switch (this.menu.getGovernanceState()) {
            case 1 -> Component.translatable("gui.firstcrusade.governance.manual");
            case 2 -> Component.translatable("gui.firstcrusade.governance.delegated");
            default -> Component.translatable("gui.firstcrusade.governance.autonomous");
        };
    }

    private int governanceColor() {
        return switch (this.menu.getGovernanceState()) {
            case 1 -> 0xFFBBD7FF;
            case 2 -> 0xFF9BE07A;
            default -> 0xFFE0C070;
        };
    }

    private void renderMilitaryInfo(GuiGraphics guiGraphics) {
        int y = 46;
        drawHeader(guiGraphics, "gui.firstcrusade.section.military", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.soldiers", this.menu.getRecruitedGuardsmen(), this.menu.getMilitaryCapacity()), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.recruits_training", this.menu.getRecruitsInTraining()), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.specialist", this.menu.getSelectedSpecialization().getDisplayName()), 12, y, 0xFF9AD0FF);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.gene", this.menu.getEmperorGeneSeed(), this.menu.getEmperorGeneSeedCapacity(), this.menu.getDailyGeneProduction()), 12, y, 0xFFB066FF);
        y += 11;
        drawLine(guiGraphics, getSpaceMarineText(), 12, y, 0xFFFFD27D);
    }

    private void renderDefenseInfo(GuiGraphics guiGraphics) {
        int y = 46;
        drawHeader(guiGraphics, "gui.firstcrusade.section.defense", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.integrity", this.menu.getCityIntegrity()), 12, y, getIntegrityColor());
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.threat", getThreatText()), 12, y, getThreatColor());
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.raid", getRaidStatusText()), 12, y, getRaidStatusColor());
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.raids_victories", this.menu.getOrkRaidCount(), this.menu.getOrkRaidVictories()), 12, y, 0xFFFFBBBB);
        y += 11;
        drawLine(guiGraphics, getReinforcementText(), 12, y, 0xFFBBD7FF);
    }

    private void renderResourcesInfo(GuiGraphics guiGraphics) {
        int y = 46;
        int cap = this.menu.getStorageCapacity();
        drawHeader(guiGraphics, "gui.firstcrusade.section.resources", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.iron", this.menu.getIron(), cap), 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.scrap", this.menu.getScrapMetal(), cap), 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.coal", this.menu.getCoal(), cap), 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.gold", this.menu.getGold(), cap), 12, y, 0xFFFFD700);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.emerald", this.menu.getEmerald(), cap), 12, y, 0xFF2ECC71);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.crusadium", this.menu.getCrusadium(), cap), 12, y, 0xFFB0C4DE);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.food", this.menu.getFood(), cap), 12, y, 0xFFE6D27A);
    }

    // ==================================================================== the Crusade panels

    /**
     * The garrison, as people rather than as a number.
     *
     * <p>The roster is not in the menu's ContainerData — that carries integers, and this carries
     * names. It is pulled on demand by {@link com.example.examplemod.crusade.client.CrusadeClientView},
     * so opening this tab costs one packet and never opening it costs nothing.
     */
    private void renderGarrisonInfo(GuiGraphics g) {
        int y = 46;

        var panel = com.example.examplemod.crusade.client.CrusadeClientView.panelFor(
                this.menu.getCommandCorePos(), gameTime());

        if (panel == null) {
            // "Asking" and "empty" must not look the same, or a slow answer reads as a dead base.
            drawLine(g, Component.translatable("gui.firstcrusade.crusade.awaiting"), 12, y, 0xFF888888);
            return;
        }

        drawLine(g, panel.regiment().displayName(), 12, y, 0xFFE0C070);
        y += 13;
        drawLine(g, Component.translatable("gui.firstcrusade.crusade.strength",
                panel.serving(), this.menu.getMilitaryCapacity()), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(g, Component.translatable("gui.firstcrusade.crusade.losses",
                panel.totalFallen()), 12, y, 0xFFB05050);
        y += 14;

        for (var row : panel.roster()) {
            if (y > this.imageHeight - 24) {
                break;
            }

            drawLine(g, soldierTitle(row), 12, y, gradeColour(row.grade()));
            drawLine(g, Component.literal(row.orkKills() + " Orks  ·  " + row.raids() + " raids  ·  "
                    + row.serviceDays() + "d"), 162, y, 0xFF9A9A9A);
            y += 11;
        }
    }

    /**
     * The Apothecarion: infrastructure, not a building.
     *
     * <p>Everything here is already synced as integers by the Core menu, so this panel is pure
     * presentation — it renames a resource counter into a medical readout and says whether the
     * facility is operational at this Core level.
     */
    private void renderApothecarionInfo(GuiGraphics g) {
        int y = 46;
        boolean operational = this.menu.getCityLevel() >= APOTHECARION_CORE_LEVEL;

        drawHeader(g, "gui.firstcrusade.apothecarion.header", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(g, Component.translatable(operational
                        ? "gui.firstcrusade.apothecarion.operational"
                        : "gui.firstcrusade.apothecarion.unavailable"),
                12, y, operational ? 0xFF7FD87F : 0xFFB05050);
        y += 13;
        drawLine(g, Component.translatable("gui.firstcrusade.apothecarion.stock",
                this.menu.getEmperorGeneSeed(), this.menu.getEmperorGeneSeedCapacity()),
                12, y, 0xFFD8C0FF);
        y += 11;
        drawLine(g, Component.translatable("gui.firstcrusade.apothecarion.production",
                this.menu.getDailyGeneProduction()), 12, y, 0xFFBBD7FF);
        y += 14;
        drawLine(g, Component.translatable("gui.firstcrusade.apothecarion.hint"), 12, y, 0xFF888888);
    }

    /** What the campaign has cost: the totals, then the roll of the dead. */
    private void renderRecordInfo(GuiGraphics g) {
        int y = 46;

        var panel = com.example.examplemod.crusade.client.CrusadeClientView.panelFor(
                this.menu.getCommandCorePos(), gameTime());

        drawHeader(g, "gui.firstcrusade.crusade.header", 12, y, 0xFFFFD27D);
        y += 13;

        if (panel == null) {
            drawLine(g, Component.translatable("gui.firstcrusade.crusade.awaiting"), 12, y, 0xFF888888);
            return;
        }

        drawLine(g, Component.translatable("gui.firstcrusade.crusade.name",
                panel.crusadeName().isEmpty()
                        ? Component.translatable("gui.firstcrusade.crusade.unnamed")
                        : Component.literal(panel.crusadeName())),
                12, y, 0xFFE0C070);
        y += 11;
        drawLine(g, Component.translatable("gui.firstcrusade.crusade.raids_won",
                this.menu.getOrkRaidVictories(), this.menu.getOrkRaidCount()), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(g, Component.translatable("gui.firstcrusade.crusade.serving",
                panel.serving()), 12, y, 0xFFBBD7FF);
        y += 14;

        drawHeader(g, "gui.firstcrusade.crusade.memorial", 12, y, 0xFFB05050);
        y += 13;

        for (var row : panel.fallen()) {
            if (y > this.imageHeight - 24) {
                break;
            }

            drawLine(g, soldierTitle(row), 12, y, 0xFF8A8A8A);
            drawLine(g, Component.translatable(row.fate()), 162, y, 0xFF6A6A6A);
            y += 11;
        }

        if (panel.fallen().isEmpty()) {
            drawLine(g, Component.translatable("gui.firstcrusade.crusade.no_dead"), 12, y, 0xFF7FD87F);
        }
    }

    /** The Core level at which the Apothecarion is declared operational. */
    private static final int APOTHECARION_CORE_LEVEL = 3;

    private Component soldierTitle(com.example.examplemod.crusade.CrusadePanelPacket.Row row) {
        return Component.translatable("soldier.firstcrusade.full_name",
                Component.translatable("soldier.firstcrusade.title."
                        + row.grade().toLowerCase(java.util.Locale.ROOT)),
                row.name());
    }

    private static int gradeColour(String grade) {
        return switch (grade) {
            case "SERGEANT" -> 0xFFE0C070;
            case "VETERAN" -> 0xFFD08050;
            default -> 0xFFDDDDDD;
        };
    }

    /** Client game time, used only to rate-limit the panel request. */
    private long gameTime() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    private void drawLine(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    private void drawLine(GuiGraphics guiGraphics, Component text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    private void drawHeader(GuiGraphics guiGraphics, String langKey, int x, int y, int color) {
        drawLine(guiGraphics, Component.translatable(langKey), x, y, color);
    }

    private String getCitizenCapacityText() {
        return switch (this.menu.getCityLevel()) {
            case 1 -> "3";
            case 2 -> "6";
            case 3 -> "10";
            case 4 -> "15";
            default -> "25";
        };
    }

    private int getMoraleColor() {
        int morale = this.menu.getCityMorale();

        if (morale >= 60) {
            return 0xFF77FF77;
        }

        if (morale >= 40) {
            return 0xFFFFFF77;
        }

        return 0xFFFF7777;
    }

    private int getIntegrityColor() {
        if (this.menu.getCityIntegrity() >= 70) {
            return 0xFF77FF77;
        }

        if (this.menu.getCityIntegrity() >= 35) {
            return 0xFFFFFF77;
        }

        return 0xFFFF7777;
    }

    private String getThreatText() {
        int score = this.menu.getThreatScore();
        int level = ThreatAssessmentManager.threatLevel(score);
        return ThreatAssessmentManager.threatLevelName(level) + " (" + score + ")";
    }

    private int getThreatColor() {
        int level = ThreatAssessmentManager.threatLevel(this.menu.getThreatScore());

        return switch (level) {
            case ThreatAssessmentManager.LEVEL_CRITICAL -> 0xFFFF3333;
            case ThreatAssessmentManager.LEVEL_SIEGE -> 0xFFFF7777;
            case ThreatAssessmentManager.LEVEL_ALERT -> 0xFFFFCC55;
            case ThreatAssessmentManager.LEVEL_VIGILANT -> 0xFFFFFF99;
            default -> 0xFF99FF99;
        };
    }

    private String getRaidStatusText() {
        if (this.menu.hasActiveRaid()) {
            return Component.translatable("gui.firstcrusade.status.raid_active", this.menu.getActiveRaidSeconds()).getString();
        }

        return Component.translatable("gui.firstcrusade.status.raid_safe").getString();
    }

    private int getRaidStatusColor() {
        if (this.menu.hasActiveRaid()) {
            return 0xFFFF5555;
        }

        return 0xFF77FF77;
    }

    private String getReinforcementText() {
        if (this.menu.getReinforcementCooldownSeconds() > 0) {
            return Component.translatable("gui.firstcrusade.status.reinf_cooldown", this.menu.getReinforcementCooldownSeconds()).getString();
        }

        return Component.translatable("gui.firstcrusade.status.reinf_ready").getString();
    }

    private String getSpaceMarineText() {
        if (this.menu.hasPendingSpaceMarineCandidate()) {
            return Component.translatable("gui.firstcrusade.status.sm_candidate").getString();
        }

        if (this.menu.getSpaceMarineCooldownSeconds() > 0) {
            return Component.translatable("gui.firstcrusade.status.sm_cooldown", this.menu.getSpaceMarineCooldownSeconds()).getString();
        }

        return Component.translatable("gui.firstcrusade.status.sm_ready").getString();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
