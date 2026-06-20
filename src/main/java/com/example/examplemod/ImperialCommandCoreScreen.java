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
    private static final int TAB_CITY = 0;
    private static final int TAB_BUILD = 1;
    private static final int TAB_MILITARY = 2;
    private static final int TAB_DEFENSE = 3;
    private static final int TAB_RESOURCES = 4;

    private static final String[] TAB_KEYS = {
            "gui.firstcrusade.tab.city",
            "gui.firstcrusade.tab.build",
            "gui.firstcrusade.tab.military",
            "gui.firstcrusade.tab.defense",
            "gui.firstcrusade.tab.resources"
    };

    private int activeTab = TAB_CITY;

    // City tab
    private Button upgradeButton;
    // Build tab
    private Button buildMineButton;
    private Button buildGoldMineButton;
    private Button buildScrapYardButton;
    private Button buildForgeButton;
    private Button buildRefineryButton;
    private Button buildFarmButton;
    private Button buildTradeDepotButton;
    private Button buildBarracksButton;
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

        this.imageWidth = 320;
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        clearButtonReferences();
        addTabButtons();

        switch (this.activeTab) {
            case TAB_BUILD -> initBuildTab();
            case TAB_MILITARY -> initMilitaryTab();
            case TAB_DEFENSE -> initDefenseTab();
            case TAB_RESOURCES -> initResourcesTab();
            default -> initCityTab();
        }

        updateButtonStates();
    }

    private void clearButtonReferences() {
        this.upgradeButton = null;
        this.buildMineButton = null;
        this.buildGoldMineButton = null;
        this.buildScrapYardButton = null;
        this.buildForgeButton = null;
        this.buildRefineryButton = null;
        this.buildFarmButton = null;
        this.buildTradeDepotButton = null;
        this.buildBarracksButton = null;
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
        int tabWidth = 59;

        for (int i = 0; i < TAB_KEYS.length; i++) {
            int index = i;
            int tabX = this.leftPos + 8 + i * 61;

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
    }

    private void initBuildTab() {
        int row = 0;
        this.buildMineButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.build_mine", ImperialCommandCoreAction.BUILD_IMPERIAL_MINE);
        this.buildGoldMineButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.build_gold_mine", ImperialCommandCoreAction.BUILD_GOLD_MINE);
        this.buildScrapYardButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.build_scrap", ImperialCommandCoreAction.BUILD_SCRAP_YARD);
        this.buildForgeButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.build_forge", ImperialCommandCoreAction.BUILD_IMPERIAL_FORGE);
        this.buildRefineryButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.build_refinery", ImperialCommandCoreAction.BUILD_PROMETHIUM_REFINERY);
        this.buildFarmButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.build_farm", ImperialCommandCoreAction.BUILD_FARM);
        this.buildTradeDepotButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.build_trade_depot", ImperialCommandCoreAction.BUILD_EMERALD_TRADE_DEPOT);
        this.buildBarracksButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "gui.firstcrusade.button.build_barracks", ImperialCommandCoreAction.BUILD_BARRACKS);
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
                        button -> ExampleMod.NETWORK_CHANNEL.sendToServer(
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

        applyButton(this.buildMineButton, this.menu.getMineCount() < this.menu.getMineCapacity(),
                Component.translatable("gui.firstcrusade.tip.mine.title"), Component.translatable("gui.firstcrusade.tip.mine.desc"),
                Component.translatable("gui.firstcrusade.reason.mine_cap"));

        applyButton(this.buildGoldMineButton,
                this.menu.getCityLevel() >= 2 && this.menu.getGoldMineCount() < this.menu.getGoldMineCapacity(),
                Component.translatable("gui.firstcrusade.tip.gold_mine.title"), Component.translatable("gui.firstcrusade.tip.gold_mine.desc"),
                getGoldMineBlockReason());

        applyButton(this.buildScrapYardButton, this.menu.getScrapYardCount() < this.menu.getScrapYardCapacity(),
                Component.translatable("gui.firstcrusade.tip.scrap_yard.title"), Component.translatable("gui.firstcrusade.tip.scrap_yard.desc"),
                Component.translatable("gui.firstcrusade.reason.scrap_yard_cap"));

        applyButton(this.buildForgeButton, this.menu.getForgeCount() < this.menu.getForgeCapacity(),
                Component.translatable("gui.firstcrusade.tip.forge.title"), Component.translatable("gui.firstcrusade.tip.forge.desc"),
                Component.translatable("gui.firstcrusade.reason.forge_cap"));

        applyButton(this.buildRefineryButton, this.menu.getRefineryCount() < this.menu.getRefineryCapacity(),
                Component.translatable("gui.firstcrusade.tip.refinery.title"), Component.translatable("gui.firstcrusade.tip.refinery.desc"),
                Component.translatable("gui.firstcrusade.reason.refinery_cap"));

        applyButton(this.buildFarmButton, this.menu.getFarmCount() < this.menu.getFarmCapacity(),
                Component.translatable("gui.firstcrusade.tip.farm.title"), Component.translatable("gui.firstcrusade.tip.farm.desc"),
                Component.translatable("gui.firstcrusade.reason.farm_cap"));

        applyButton(this.buildTradeDepotButton,
                this.menu.getCityLevel() >= 3 && this.menu.getTradeDepotCount() < this.menu.getTradeDepotCapacity(),
                Component.translatable("gui.firstcrusade.tip.trade_depot.title"), Component.translatable("gui.firstcrusade.tip.trade_depot.desc"),
                getTradeDepotBlockReason());

        applyButton(this.buildBarracksButton, this.menu.getBarracksCount() < this.menu.getBarracksCapacity(),
                Component.translatable("gui.firstcrusade.tip.barracks.title"), Component.translatable("gui.firstcrusade.tip.barracks.desc"),
                Component.translatable("gui.firstcrusade.reason.barracks_cap"));

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

    private Component getGoldMineBlockReason() {
        if (this.menu.getCityLevel() < 2) {
            return Component.translatable("gui.firstcrusade.reason.req_level_2");
        }

        return Component.translatable("gui.firstcrusade.reason.gold_mine_cap");
    }

    private Component getTradeDepotBlockReason() {
        if (this.menu.getCityLevel() < 3) {
            return Component.translatable("gui.firstcrusade.reason.req_level_3");
        }

        return Component.translatable("gui.firstcrusade.reason.trade_depot_cap");
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

        // Content panels: left info column and right action column.
        guiGraphics.fill(x + 6, y + 40, x + 152, y + this.imageHeight - 6, 0xAA202020);
        guiGraphics.fill(x + 156, y + 40, x + this.imageWidth - 6, y + this.imageHeight - 6, 0xAA181818);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawHeader(guiGraphics, "block.firstcrusade.imperial_command_core", 8, 5, 0xFFD6B85A);

        switch (this.activeTab) {
            case TAB_BUILD -> renderBuildInfo(guiGraphics);
            case TAB_MILITARY -> renderMilitaryInfo(guiGraphics);
            case TAB_DEFENSE -> renderDefenseInfo(guiGraphics);
            case TAB_RESOURCES -> renderResourcesInfo(guiGraphics);
            default -> renderCityInfo(guiGraphics);
        }
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
    }

    private void renderBuildInfo(GuiGraphics guiGraphics) {
        int y = 46;
        drawHeader(guiGraphics, "gui.firstcrusade.section.structures", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.mines", this.menu.getMineCount(), this.menu.getMineCapacity(), this.menu.getMinerCount()), 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.gold_mines", this.menu.getGoldMineCount(), this.menu.getGoldMineCapacity(), this.menu.getGoldMinerCount()), 12, y, 0xFFFFE08A);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.scrap_yards", this.menu.getScrapYardCount(), this.menu.getScrapYardCapacity(), this.menu.getScrapperCount()), 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.forges", this.menu.getForgeCount(), this.menu.getForgeCapacity(), this.menu.getSmithCount()), 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.refineries", this.menu.getRefineryCount(), this.menu.getRefineryCapacity(), this.menu.getStokerCount()), 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.farms", this.menu.getFarmCount(), this.menu.getFarmCapacity(), this.menu.getFarmerCount()), 12, y, 0xFF9BE07A);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.trade_depots", this.menu.getTradeDepotCount(), this.menu.getTradeDepotCapacity(), this.menu.getTraderCount()), 12, y, 0xFF7AE0B0);
        y += 11;
        drawLine(guiGraphics, Component.translatable("gui.firstcrusade.info.barracks", this.menu.getBarracksCount(), this.menu.getBarracksCapacity(), this.menu.getRecruitsInTraining()), 12, y, 0xFFFFDD77);
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
