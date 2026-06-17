package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ImperialCommandCoreScreen extends AbstractContainerScreen<ImperialCommandCoreMenu> {
    private static final int TAB_CITY = 0;
    private static final int TAB_BUILD = 1;
    private static final int TAB_MILITARY = 2;
    private static final int TAB_DEFENSE = 3;
    private static final int TAB_RESOURCES = 4;

    private static final String[] TAB_NAMES = {"City", "Build", "Military", "Defense", "Resources"};

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
    }

    private void addTabButtons() {
        int tabY = this.topPos + 20;
        int tabWidth = 59;

        for (int i = 0; i < TAB_NAMES.length; i++) {
            int index = i;
            int tabX = this.leftPos + 8 + i * 61;

            Button tab = this.addRenderableWidget(
                    Button.builder(
                            Component.literal(TAB_NAMES[i]),
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
        this.upgradeButton = addActionButton(buttonX(), buttonY(0), 148, 18, "Upgrade City", ImperialCommandCoreAction.UPGRADE_CITY);
    }

    private void initBuildTab() {
        int row = 0;
        this.buildMineButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Build Mine", ImperialCommandCoreAction.BUILD_IMPERIAL_MINE);
        this.buildGoldMineButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Build Gold Mine", ImperialCommandCoreAction.BUILD_GOLD_MINE);
        this.buildScrapYardButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Build Scrap", ImperialCommandCoreAction.BUILD_SCRAP_YARD);
        this.buildForgeButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Build Forge", ImperialCommandCoreAction.BUILD_IMPERIAL_FORGE);
        this.buildRefineryButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Build Refinery", ImperialCommandCoreAction.BUILD_PROMETHIUM_REFINERY);
        this.buildFarmButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Build Farm", ImperialCommandCoreAction.BUILD_FARM);
        this.buildTradeDepotButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Build Trade Depot", ImperialCommandCoreAction.BUILD_EMERALD_TRADE_DEPOT);
        this.buildBarracksButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Build Barracks", ImperialCommandCoreAction.BUILD_BARRACKS);
    }

    private void initMilitaryTab() {
        int row = 0;
        this.recruitButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Recruit", ImperialCommandCoreAction.RECRUIT_GUARDSMAN);
        this.cycleSpecialistButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Cycle Specialist", ImperialCommandCoreAction.CYCLE_SPECIALIST);
        this.promoteSpecialistButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Promote Specialist", ImperialCommandCoreAction.PROMOTE_SPECIALIST);
    }

    private void initDefenseTab() {
        int row = 0;
        this.repairButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Repair Core", ImperialCommandCoreAction.REPAIR_CORE);
        this.reinforcementButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Reinforcements", ImperialCommandCoreAction.CALL_REINFORCEMENTS);
        this.rallyButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Rally", ImperialCommandCoreAction.RALLY_DEFENDERS);
        this.fortifyButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Fortify", ImperialCommandCoreAction.FORTIFY_DEFENDERS);
        this.forceRaidButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Force Raid", ImperialCommandCoreAction.FORCE_RAID_TEST);
    }

    private void initResourcesTab() {
        int row = 0;
        this.depositButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Deposit All", ImperialCommandCoreAction.DEPOSIT_RESOURCES);
        this.withdrawIronButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Withdraw Iron", ImperialCommandCoreAction.WITHDRAW_IRON);
        this.withdrawCoalButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Withdraw Coal", ImperialCommandCoreAction.WITHDRAW_COAL);
        this.withdrawScrapButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Withdraw Scrap", ImperialCommandCoreAction.WITHDRAW_SCRAP);
        this.withdrawGoldButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Withdraw Gold", ImperialCommandCoreAction.WITHDRAW_GOLD);
        this.withdrawEmeraldButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Withdraw Emerald", ImperialCommandCoreAction.WITHDRAW_EMERALD);
        this.withdrawCrusadiumButton = addActionButton(buttonX(), buttonY(row++), 148, 18, "Withdraw Crusadium", ImperialCommandCoreAction.WITHDRAW_CRUSADIUM);
    }

    private Button addActionButton(int x, int y, int width, int height, String text, ImperialCommandCoreAction action) {
        return this.addRenderableWidget(
                Button.builder(
                        Component.literal(text),
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
                "Upgrade City", getUpgradeCostText(), "The city is already at the maximum level.");

        applyButton(this.buildMineButton, this.menu.getMineCount() < this.menu.getMineCapacity(),
                "Build Imperial Mine", "Cost: 20 Iron, 10 Scrap, 5 Coal. Assigns a Miner who produces Iron.",
                "Mine capacity reached. Upgrade the city to build more.");

        applyButton(this.buildGoldMineButton,
                this.menu.getCityLevel() >= 2 && this.menu.getGoldMineCount() < this.menu.getGoldMineCapacity(),
                "Build Gold Mine", "Cost: 40 Iron, 25 Scrap, 15 Coal. Assigns a Gold Miner who produces Gold (slowly).",
                getGoldMineBlockReason());

        applyButton(this.buildScrapYardButton, this.menu.getScrapYardCount() < this.menu.getScrapYardCapacity(),
                "Build Scrap Yard", "Cost: 15 Iron, 5 Coal. Assigns a Scrapper who produces Scrap Metal.",
                "Scrap Yard capacity reached. Upgrade the city to build more.");

        applyButton(this.buildForgeButton, this.menu.getForgeCount() < this.menu.getForgeCapacity(),
                "Build Imperial Forge", "Cost: 30 Iron, 20 Scrap, 10 Coal. Assigns a Smith who forges Crusadium Plate (4 Iron, 3 Scrap, 2 Coal each).",
                "Forge capacity reached. Upgrade the city to build more.");

        applyButton(this.buildRefineryButton, this.menu.getRefineryCount() < this.menu.getRefineryCapacity(),
                "Build Promethium Refinery", "Cost: 18 Iron, 8 Scrap. Assigns a Stoker who produces Coal.",
                "Refinery capacity reached. Upgrade the city to build more.");

        applyButton(this.buildFarmButton, this.menu.getFarmCount() < this.menu.getFarmCapacity(),
                "Build Imperial Farm", "Cost: 15 Iron, 5 Scrap. Assigns a Farmer; staffed farms feed the city (morale + growth).",
                "Farm capacity reached. Upgrade the city to build more.");

        applyButton(this.buildTradeDepotButton,
                this.menu.getCityLevel() >= 3 && this.menu.getTradeDepotCount() < this.menu.getTradeDepotCapacity(),
                "Build Emerald Trade Depot", "Cost: 30 Iron, 15 Scrap, 10 Coal. Assigns a Trader who trades Gold for Emerald (4 Gold each).",
                getTradeDepotBlockReason());

        applyButton(this.buildBarracksButton, this.menu.getBarracksCount() < this.menu.getBarracksCapacity(),
                "Build Imperial Barracks", "Cost: 25 Iron, 15 Scrap, 5 Coal. Trains Recruits into Guardsmen.",
                "Barracks capacity reached. Upgrade the city to build more.");

        applyButton(this.recruitButton, canTrainRecruit(),
                "Recruit", "Assigns the nearest unemployed Citizen as a Recruit to train at a Barracks into a Guardsman.",
                getRecruitBlockReason());

        applyButton(this.cycleSpecialistButton, true,
                "Cycle Specialist", "Selects the specialist type for Promote Specialist. Selected: " + this.menu.getSelectedSpecialization().getDisplayName() + ".",
                null);

        applyButton(this.promoteSpecialistButton, this.menu.getCityLevel() >= 2,
                "Promote Specialist: " + this.menu.getSelectedSpecialization().getDisplayName(),
                "Promotes the nearest Guardsman to the selected specialist role. Costs Iron, Scrap and War Support.",
                "Requires an Imperial settlement of Level 2 or higher.");

        applyButton(this.repairButton, this.menu.getCityIntegrity() < 100,
                "Repair Core", "Cost: 1 Crusadium Plate. Restores Core integrity.",
                "Core integrity is already full.");

        applyButton(this.reinforcementButton,
                this.menu.hasActiveRaid() && this.menu.getReinforcementCooldownSeconds() <= 0 && hasRecruitCapacity(),
                "Call Reinforcements", "Cost: " + reinforcementWarSupportCost() + " War Support. Deploys emergency Guardsmen during a raid.",
                getReinforcementBlockReason());

        applyButton(this.rallyButton, this.menu.hasActiveRaid(),
                "Rally Defenders", "Repositions assigned Guardsmen back to defend the Core.",
                "Only usable during an active Ork raid.");

        applyButton(this.fortifyButton, this.menu.hasActiveRaid(),
                "Fortify Defenders", "Cost: " + fortifyWarSupportCost() + " War Support. Buffs assigned defenders.",
                "Only usable during an active Ork raid.");

        applyButton(this.forceRaidButton, !this.menu.hasActiveRaid(),
                "Force Raid (Test)", "Immediately triggers an Ork raid for testing.",
                "A raid is already active.");

        applyButton(this.depositButton, true,
                "Deposit All Resources", "Moves Iron, Coal, Scrap, Gold, Emerald and Crusadium from your inventory into the city.",
                null);

        applyWithdrawButton(this.withdrawIronButton, "Iron", this.menu.getIron());
        applyWithdrawButton(this.withdrawCoalButton, "Coal", this.menu.getCoal());
        applyWithdrawButton(this.withdrawScrapButton, "Scrap Metal", this.menu.getScrapMetal());
        applyWithdrawButton(this.withdrawGoldButton, "Gold", this.menu.getGold());
        applyWithdrawButton(this.withdrawEmeraldButton, "Emerald", this.menu.getEmerald());
        applyWithdrawButton(this.withdrawCrusadiumButton, "Crusadium", this.menu.getCrusadium());
    }

    private void applyButton(Button button, boolean active, String title, String cost, String reason) {
        if (button == null) {
            return;
        }

        button.active = active;

        StringBuilder text = new StringBuilder(title);

        if (cost != null && !cost.isEmpty()) {
            text.append("\n").append(cost);
        }

        if (!active && reason != null && !reason.isEmpty()) {
            text.append("\n§c").append(reason);
        }

        button.setTooltip(Tooltip.create(Component.literal(text.toString())));
    }

    private void applyWithdrawButton(Button button, String resourceName, int stored) {
        applyButton(
                button,
                stored > 0,
                "Withdraw " + resourceName,
                "Takes up to 64 " + resourceName + " into your inventory. Stored: " + stored + ".",
                "No " + resourceName + " stored to withdraw."
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

    private String getRecruitBlockReason() {
        if (this.menu.getBarracksCount() <= 0) {
            return "Build an Imperial Barracks first.";
        }

        if (!militaryHasRoomForRecruit()) {
            return "Military capacity reached. Upgrade the city.";
        }

        if (availableBarracks() <= 0) {
            return "All Barracks are busy training recruits.";
        }

        if (this.menu.getUnemployedCitizenCount() <= 0) {
            return "No unemployed Citizen available to train.";
        }

        return null;
    }

    private String getGoldMineBlockReason() {
        if (this.menu.getCityLevel() < 2) {
            return "Requires an Imperial settlement of Level 2 or higher.";
        }

        return "Gold Mine capacity reached. Upgrade the city to build more.";
    }

    private String getTradeDepotBlockReason() {
        if (this.menu.getCityLevel() < 3) {
            return "Requires an Imperial settlement of Level 3 or higher.";
        }

        return "Trade Depot capacity reached. Upgrade the city to build more.";
    }

    private String getReinforcementBlockReason() {
        if (!this.menu.hasActiveRaid()) {
            return "Only usable during an active Ork raid.";
        }

        if (this.menu.getReinforcementCooldownSeconds() > 0) {
            return "On cooldown: " + this.menu.getReinforcementCooldownSeconds() + "s.";
        }

        if (!hasRecruitCapacity()) {
            return "Military capacity reached.";
        }

        return null;
    }

    private String getUpgradeCostText() {
        return switch (this.menu.getCityLevel()) {
            case 1 -> "Cost: 100 Iron, 60 Scrap, 30 Coal, 2 Crusadium Plate.";
            case 2 -> "Cost: 500 Iron, 300 Scrap, 150 Coal, 6 Crusadium Plate.";
            case 3 -> "Cost: 2500 Iron, 1500 Scrap, 750 Coal, 18 Crusadium Plate.";
            case 4 -> "Cost: 12000 Iron, 7200 Scrap, 3600 Coal, 54 Crusadium Plate.";
            default -> "City is at the maximum level.";
        };
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
        drawLine(guiGraphics, "Imperial Command Core", 8, 5, 0xFFD6B85A);

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
        drawLine(guiGraphics, "Level: " + this.menu.getCityLevel(), 12, y, 0xFFFFFFFF);
        y += 11;
        drawLine(guiGraphics, "Integrity: " + this.menu.getCityIntegrity() + "/100", 12, y, getIntegrityColor());
        y += 11;
        drawLine(guiGraphics, "Morale: " + this.menu.getCityMorale() + " (" + ImperialCityMoraleManager.getMoraleLabel(this.menu.getCityMorale()) + ")", 12, y, getMoraleColor());
        y += 11;
        drawLine(guiGraphics, "Citizens: " + this.menu.getCitizenCount() + "/" + getCitizenCapacityText(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "Unemployed: " + this.menu.getUnemployedCitizenCount(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "Soldiers: " + this.menu.getRecruitedGuardsmen() + "/" + this.menu.getMilitaryCapacity(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "Threat: " + getThreatText(), 12, y, getThreatColor());
        y += 11;
        drawLine(guiGraphics, "Raid: " + getRaidStatusText(), 12, y, getRaidStatusColor());
    }

    private void renderBuildInfo(GuiGraphics guiGraphics) {
        int y = 46;
        drawLine(guiGraphics, "Structures", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, "Mines: " + this.menu.getMineCount() + "/" + this.menu.getMineCapacity() + " (" + this.menu.getMinerCount() + ")", 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, "Gold Mines: " + this.menu.getGoldMineCount() + "/" + this.menu.getGoldMineCapacity() + " (" + this.menu.getGoldMinerCount() + ")", 12, y, 0xFFFFE08A);
        y += 11;
        drawLine(guiGraphics, "Scrap Yards: " + this.menu.getScrapYardCount() + "/" + this.menu.getScrapYardCapacity() + " (" + this.menu.getScrapperCount() + ")", 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, "Forges: " + this.menu.getForgeCount() + "/" + this.menu.getForgeCapacity() + " (" + this.menu.getSmithCount() + ")", 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, "Refineries: " + this.menu.getRefineryCount() + "/" + this.menu.getRefineryCapacity() + " (" + this.menu.getStokerCount() + ")", 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, "Farms: " + this.menu.getFarmCount() + "/" + this.menu.getFarmCapacity() + " (" + this.menu.getFarmerCount() + ")", 12, y, 0xFF9BE07A);
        y += 11;
        drawLine(guiGraphics, "Trade Depots: " + this.menu.getTradeDepotCount() + "/" + this.menu.getTradeDepotCapacity() + " (" + this.menu.getTraderCount() + ")", 12, y, 0xFF7AE0B0);
        y += 11;
        drawLine(guiGraphics, "Barracks: " + this.menu.getBarracksCount() + "/" + this.menu.getBarracksCapacity() + " (" + this.menu.getRecruitsInTraining() + ")", 12, y, 0xFFFFDD77);
    }

    private void renderMilitaryInfo(GuiGraphics guiGraphics) {
        int y = 46;
        drawLine(guiGraphics, "Military", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, "Soldiers: " + this.menu.getRecruitedGuardsmen() + "/" + this.menu.getMilitaryCapacity(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "Recruits training: " + this.menu.getRecruitsInTraining(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "Specialist: " + this.menu.getSelectedSpecialization().getDisplayName(), 12, y, 0xFF9AD0FF);
        y += 11;
        drawLine(guiGraphics, "Gene " + this.menu.getEmperorGeneSeed() + "/" + this.menu.getEmperorGeneSeedCapacity() + " +" + this.menu.getDailyGeneProduction() + "/day", 12, y, 0xFFB066FF);
        y += 11;
        drawLine(guiGraphics, getSpaceMarineText(), 12, y, 0xFFFFD27D);
    }

    private void renderDefenseInfo(GuiGraphics guiGraphics) {
        int y = 46;
        drawLine(guiGraphics, "Defense", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, "Integrity: " + this.menu.getCityIntegrity() + "/100", 12, y, getIntegrityColor());
        y += 11;
        drawLine(guiGraphics, "Threat: " + getThreatText(), 12, y, getThreatColor());
        y += 11;
        drawLine(guiGraphics, "Raid: " + getRaidStatusText(), 12, y, getRaidStatusColor());
        y += 11;
        drawLine(guiGraphics, "Raids/Victories: " + this.menu.getOrkRaidCount() + "/" + this.menu.getOrkRaidVictories(), 12, y, 0xFFFFBBBB);
        y += 11;
        drawLine(guiGraphics, getReinforcementText(), 12, y, 0xFFBBD7FF);
    }

    private void renderResourcesInfo(GuiGraphics guiGraphics) {
        int y = 46;
        int cap = this.menu.getStorageCapacity();
        drawLine(guiGraphics, "Resources", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, "Iron: " + this.menu.getIron() + "/" + cap, 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, "Scrap: " + this.menu.getScrapMetal() + "/" + cap, 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, "Coal: " + this.menu.getCoal() + "/" + cap, 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, "Gold: " + this.menu.getGold() + "/" + cap, 12, y, 0xFFFFD700);
        y += 11;
        drawLine(guiGraphics, "Emerald: " + this.menu.getEmerald() + "/" + cap, 12, y, 0xFF2ECC71);
        y += 11;
        drawLine(guiGraphics, "Crusadium: " + this.menu.getCrusadium() + "/" + cap, 12, y, 0xFFB0C4DE);
    }

    private void drawLine(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x, y, color, false);
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
            return "ACTIVE " + this.menu.getActiveRaidSeconds() + "s";
        }

        return "Safe";
    }

    private int getRaidStatusColor() {
        if (this.menu.hasActiveRaid()) {
            return 0xFFFF5555;
        }

        return 0xFF77FF77;
    }

    private String getReinforcementText() {
        if (this.menu.getReinforcementCooldownSeconds() > 0) {
            return "Reinf: " + this.menu.getReinforcementCooldownSeconds() + "s";
        }

        return "Reinf: Ready";
    }

    private String getSpaceMarineText() {
        if (this.menu.hasPendingSpaceMarineCandidate()) {
            return "SM: Candidate moving to Core";
        }

        if (this.menu.getSpaceMarineCooldownSeconds() > 0) {
            return "SM Cooldown: " + this.menu.getSpaceMarineCooldownSeconds() + "s";
        }

        return "SM Ascension: Ready";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
