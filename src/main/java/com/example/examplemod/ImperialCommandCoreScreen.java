package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ImperialCommandCoreScreen extends AbstractContainerScreen<ImperialCommandCoreMenu> {
    private Button depositButton;
    private Button buildMineButton;
    private Button buildScrapYardButton;
    private Button buildForgeButton;
    private Button buildRefineryButton;
    private Button buildBarracksButton;
    private Button recruitButton;
    private Button cycleSpecialistButton;
    private Button promoteSpecialistButton;
    private Button upgradeButton;
    private Button repairButton;
    private Button reinforcementButton;
    private Button rallyButton;
    private Button fortifyButton;
    private Button forceRaidButton;

    public ImperialCommandCoreScreen(ImperialCommandCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        this.imageWidth = 400;
        this.imageHeight = 344;
    }

    @Override
    protected void init() {
        super.init();

        int buttonX = this.leftPos + 255;
        int buttonY = this.topPos + 34;
        int buttonWidth = 130;
        int buttonHeight = 18;
        int gap = 20;
        int row = 0;

        this.depositButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Deposit Resources", ImperialCommandCoreAction.DEPOSIT_RESOURCES);

        this.buildMineButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Build Mine", ImperialCommandCoreAction.BUILD_IMPERIAL_MINE);

        this.buildScrapYardButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Build Scrap", ImperialCommandCoreAction.BUILD_SCRAP_YARD);

        this.buildForgeButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Build Forge", ImperialCommandCoreAction.BUILD_IMPERIAL_FORGE);

        this.buildRefineryButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Build Refinery", ImperialCommandCoreAction.BUILD_PROMETHIUM_REFINERY);

        this.buildBarracksButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Build Barracks", ImperialCommandCoreAction.BUILD_BARRACKS);

        this.recruitButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Recruit", ImperialCommandCoreAction.RECRUIT_GUARDSMAN);

        this.cycleSpecialistButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Cycle Specialist", ImperialCommandCoreAction.CYCLE_SPECIALIST);

        this.promoteSpecialistButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Promote Specialist", ImperialCommandCoreAction.PROMOTE_SPECIALIST);

        this.upgradeButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Upgrade City", ImperialCommandCoreAction.UPGRADE_CITY);

        this.repairButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Repair Core", ImperialCommandCoreAction.REPAIR_CORE);

        this.reinforcementButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Reinforcements", ImperialCommandCoreAction.CALL_REINFORCEMENTS);

        this.rallyButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Rally", ImperialCommandCoreAction.RALLY_DEFENDERS);

        this.fortifyButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Fortify", ImperialCommandCoreAction.FORTIFY_DEFENDERS);

        this.forceRaidButton = addActionButton(buttonX, buttonY + gap * row++, buttonWidth, buttonHeight,
                "Force Raid", ImperialCommandCoreAction.FORCE_RAID_TEST);

        updateButtonStates();
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
        applyButton(
                this.depositButton,
                true,
                "Deposit Resources",
                "Moves Iron, Coal and Scrap Metal from your inventory into the city.",
                null
        );

        applyButton(
                this.buildMineButton,
                this.menu.getMineCount() < this.menu.getMineCapacity(),
                "Build Imperial Mine",
                "Cost: 20 Iron, 10 Scrap, 5 Coal. Assigns a Miner who produces Iron.",
                "Mine capacity reached. Upgrade the city to build more."
        );

        applyButton(
                this.buildScrapYardButton,
                this.menu.getScrapYardCount() < this.menu.getScrapYardCapacity(),
                "Build Scrap Yard",
                "Cost: 15 Iron, 5 Coal. Assigns a Scrapper who produces Scrap Metal.",
                "Scrap Yard capacity reached. Upgrade the city to build more."
        );

        applyButton(
                this.buildForgeButton,
                this.menu.getForgeCount() < this.menu.getForgeCapacity(),
                "Build Imperial Forge",
                "Cost: 30 Iron, 20 Scrap, 10 Coal. Assigns a Smith who forges Crusadium Plate (4 Iron, 3 Scrap, 2 Coal each).",
                "Forge capacity reached. Upgrade the city to build more."
        );

        applyButton(
                this.buildRefineryButton,
                this.menu.getRefineryCount() < this.menu.getRefineryCapacity(),
                "Build Promethium Refinery",
                "Cost: 18 Iron, 8 Scrap. Assigns a Stoker who produces Coal.",
                "Refinery capacity reached. Upgrade the city to build more."
        );

        applyButton(
                this.buildBarracksButton,
                this.menu.getBarracksCount() < this.menu.getBarracksCapacity(),
                "Build Imperial Barracks",
                "Cost: 25 Iron, 15 Scrap, 5 Coal. Trains Recruits into Guardsmen.",
                "Barracks capacity reached. Upgrade the city to build more."
        );

        applyButton(
                this.recruitButton,
                canTrainRecruit(),
                "Recruit",
                "Assigns the nearest unemployed Citizen as a Recruit to train at a Barracks into a Guardsman.",
                getRecruitBlockReason()
        );

        applyButton(
                this.cycleSpecialistButton,
                true,
                "Cycle Specialist",
                "Selects the specialist type for Promote Specialist. Selected: " + this.menu.getSelectedSpecialization().getDisplayName() + ".",
                null
        );

        applyButton(
                this.promoteSpecialistButton,
                this.menu.getCityLevel() >= 2,
                "Promote Specialist: " + this.menu.getSelectedSpecialization().getDisplayName(),
                "Promotes the nearest Guardsman to the selected specialist role. Costs Iron, Scrap and War Support.",
                "Requires an Imperial settlement of Level 2 or higher."
        );

        applyButton(
                this.upgradeButton,
                this.menu.getCityLevel() < 5,
                "Upgrade City",
                getUpgradeCostText(),
                "The city is already at the maximum level."
        );

        applyButton(
                this.repairButton,
                this.menu.getCityIntegrity() < 100,
                "Repair Core",
                "Cost: 1 Crusadium Plate. Restores Core integrity.",
                "Core integrity is already full."
        );

        applyButton(
                this.reinforcementButton,
                this.menu.hasActiveRaid()
                        && this.menu.getReinforcementCooldownSeconds() <= 0
                        && hasRecruitCapacity(),
                "Call Reinforcements",
                "Cost: " + reinforcementWarSupportCost() + " War Support. Deploys emergency Guardsmen during a raid.",
                getReinforcementBlockReason()
        );

        applyButton(
                this.rallyButton,
                this.menu.hasActiveRaid(),
                "Rally Defenders",
                "Repositions assigned Guardsmen back to defend the Core.",
                "Only usable during an active Ork raid."
        );

        applyButton(
                this.fortifyButton,
                this.menu.hasActiveRaid(),
                "Fortify Defenders",
                "Cost: " + fortifyWarSupportCost() + " War Support. Buffs assigned defenders.",
                "Only usable during an active Ork raid."
        );

        applyButton(
                this.forceRaidButton,
                !this.menu.hasActiveRaid(),
                "Force Raid (Test)",
                "Immediately triggers an Ork raid for testing.",
                "A raid is already active."
        );
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
        return switch (this.menu.getCityLevel()) {
            case 2 -> 10;
            case 3 -> 18;
            case 4 -> 30;
            case 5 -> 50;
            default -> 5;
        };
    }

    private int fortifyWarSupportCost() {
        return switch (this.menu.getCityLevel()) {
            case 2 -> 10;
            case 3 -> 18;
            case 4 -> 30;
            case 5 -> 45;
            default -> 5;
        };
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xEE0B0B0B);
        guiGraphics.fill(x, y, x + this.imageWidth, y + 22, 0xEE3A2A12);

        guiGraphics.fill(x + 8, y + 30, x + 248, y + 158, 0xAA202020);
        guiGraphics.fill(x + 8, y + 164, x + 248, y + 216, 0xAA202020);
        guiGraphics.fill(x + 8, y + 222, x + 248, y + 334, 0xAA202020);

        guiGraphics.fill(x + 253, y + 30, x + 392, y + 334, 0xAA202020);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = 7;

        drawLine(guiGraphics, "Imperial Command Core", 8, y, 0xFFD6B85A);

        y = 34;
        drawLine(guiGraphics, "Settlement", 12, y, 0xFFFFD27D);
        drawLine(guiGraphics, this.menu.getCityType().getDisplayName(), 130, y, 0xFFE0C070);
        y += 13;
        drawLine(guiGraphics, "Level: " + this.menu.getCityLevel(), 12, y, 0xFFFFFFFF);
        y += 11;
        drawLine(guiGraphics, "Integrity: " + this.menu.getCityIntegrity() + "/100", 12, y, getIntegrityColor());
        drawLine(guiGraphics, "Morale: " + this.menu.getCityMorale() + " (" + ImperialCityMoraleManager.getMoraleLabel(this.menu.getCityMorale()) + ")", 130, y, getMoraleColor());
        y += 11;
        drawLine(guiGraphics, "Citizens: " + this.menu.getCitizenCount() + "/" + getCitizenCapacityText(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "Unemployed: " + this.menu.getUnemployedCitizenCount(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "Soldiers: " + this.menu.getRecruitedGuardsmen() + "/" + this.menu.getMilitaryCapacity(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "Mines: " + this.menu.getMineCount() + "/" + this.menu.getMineCapacity() + "  (Miners: " + this.menu.getMinerCount() + ")", 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, "Scrap Yards: " + this.menu.getScrapYardCount() + "/" + this.menu.getScrapYardCapacity() + "  (Scrappers: " + this.menu.getScrapperCount() + ")", 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, "Forges: " + this.menu.getForgeCount() + "/" + this.menu.getForgeCapacity() + "  (Smiths: " + this.menu.getSmithCount() + ")", 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, "Refineries: " + this.menu.getRefineryCount() + "/" + this.menu.getRefineryCapacity() + "  (Stokers: " + this.menu.getStokerCount() + ")", 12, y, 0xFFFFDD77);
        y += 11;
        drawLine(guiGraphics, "Barracks: " + this.menu.getBarracksCount() + "/" + this.menu.getBarracksCapacity() + "  (Training: " + this.menu.getRecruitsInTraining() + ")", 12, y, 0xFFFFDD77);

        y = 168;
        drawLine(guiGraphics, "Resources", 12, y, 0xFFFFD27D);
        int cap = this.menu.getStorageCapacity();
        int col2 = 130;
        y += 13;
        drawLine(guiGraphics, "Iron: " + this.menu.getIron() + "/" + cap, 12, y, 0xFFD8D8D8);
        drawLine(guiGraphics, "Gold: " + this.menu.getGold() + "/" + cap, col2, y, 0xFFFFD700);
        y += 11;
        drawLine(guiGraphics, "Scrap: " + this.menu.getScrapMetal() + "/" + cap, 12, y, 0xFFD8D8D8);
        drawLine(guiGraphics, "Emerald: " + this.menu.getEmerald() + "/" + cap, col2, y, 0xFF2ECC71);
        y += 11;
        drawLine(guiGraphics, "Coal: " + this.menu.getCoal() + "/" + cap, 12, y, 0xFFD8D8D8);
        drawLine(guiGraphics, "Crusadium: " + this.menu.getCrusadium() + "/" + cap, col2, y, 0xFFB0C4DE);

        y = 226;
        drawLine(guiGraphics, "Production", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, "Specialist (selected): " + this.menu.getSelectedSpecialization().getDisplayName(), 12, y, 0xFF9AD0FF);
        y += 11;
        drawLine(guiGraphics, "Gene " + this.menu.getEmperorGeneSeed() + "/" + this.menu.getEmperorGeneSeedCapacity() + " +" + this.menu.getDailyGeneProduction() + "/day", 12, y, 0xFFB066FF);
        y += 11;
        drawLine(guiGraphics, getSpaceMarineText(), 12, y, 0xFFFFD27D);
        y += 11;
        drawLine(guiGraphics, "Threat: " + getThreatText(), 12, y, getThreatColor());
        y += 11;
        drawLine(guiGraphics, "Raid: " + getRaidStatusText(), 12, y, getRaidStatusColor());
        y += 11;
        drawLine(guiGraphics, "Raids/Victories: " + this.menu.getOrkRaidCount() + "/" + this.menu.getOrkRaidVictories(), 12, y, 0xFFFFBBBB);
        y += 11;
        drawLine(guiGraphics, getReinforcementText(), 12, y, 0xFFBBD7FF);
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