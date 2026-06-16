package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ImperialCommandCoreScreen extends AbstractContainerScreen<ImperialCommandCoreMenu> {
    private Button depositButton;
    private Button buildMineButton;
    private Button recruitButton;
    private Button upgradeButton;
    private Button repairButton;
    private Button reinforcementButton;
    private Button rallyButton;
    private Button fortifyButton;
    private Button forceRaidButton;

    public ImperialCommandCoreScreen(ImperialCommandCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        this.imageWidth = 380;
        this.imageHeight = 285;
    }

    @Override
    protected void init() {
        super.init();

        int buttonX = this.leftPos + 240;
        int buttonY = this.topPos + 34;
        int buttonWidth = 125;
        int buttonHeight = 20;
        int gap = 23;

        this.depositButton = addActionButton(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                "Deposit Resources",
                ImperialCommandCoreAction.DEPOSIT_RESOURCES
        );

        this.buildMineButton = addActionButton(
                buttonX,
                buttonY + gap,
                buttonWidth,
                buttonHeight,
                "Build Mine",
                ImperialCommandCoreAction.BUILD_IMPERIAL_MINE
        );

        this.recruitButton = addActionButton(
                buttonX,
                buttonY + gap * 2,
                buttonWidth,
                buttonHeight,
                "Recruit",
                ImperialCommandCoreAction.RECRUIT_GUARDSMAN
        );

        this.upgradeButton = addActionButton(
                buttonX,
                buttonY + gap * 3,
                buttonWidth,
                buttonHeight,
                "Upgrade City",
                ImperialCommandCoreAction.UPGRADE_CITY
        );

        this.repairButton = addActionButton(
                buttonX,
                buttonY + gap * 4,
                buttonWidth,
                buttonHeight,
                "Repair Core",
                ImperialCommandCoreAction.REPAIR_CORE
        );

        this.reinforcementButton = addActionButton(
                buttonX,
                buttonY + gap * 5,
                buttonWidth,
                buttonHeight,
                "Reinforcements",
                ImperialCommandCoreAction.CALL_REINFORCEMENTS
        );

        this.rallyButton = addActionButton(
                buttonX,
                buttonY + gap * 6,
                buttonWidth,
                buttonHeight,
                "Rally",
                ImperialCommandCoreAction.RALLY_DEFENDERS
        );

        this.fortifyButton = addActionButton(
                buttonX,
                buttonY + gap * 7,
                buttonWidth,
                buttonHeight,
                "Fortify",
                ImperialCommandCoreAction.FORTIFY_DEFENDERS
        );

        this.forceRaidButton = addActionButton(
                buttonX,
                buttonY + gap * 8,
                buttonWidth,
                buttonHeight,
                "Force Raid",
                ImperialCommandCoreAction.FORCE_RAID_TEST
        );

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
        if (this.depositButton != null) {
            this.depositButton.active = true;
        }

        if (this.buildMineButton != null) {
            this.buildMineButton.active = true;
        }

        if (this.recruitButton != null) {
            this.recruitButton.active = hasRecruitCapacity();
        }

        if (this.upgradeButton != null) {
            this.upgradeButton.active = this.menu.getCityLevel() < 5;
        }

        if (this.repairButton != null) {
            this.repairButton.active = this.menu.getCityIntegrity() < 100;
        }

        if (this.reinforcementButton != null) {
            this.reinforcementButton.active = this.menu.hasActiveRaid()
                    && this.menu.getReinforcementCooldownSeconds() <= 0
                    && hasRecruitCapacity();
        }

        if (this.rallyButton != null) {
            this.rallyButton.active = this.menu.hasActiveRaid();
        }

        if (this.fortifyButton != null) {
            this.fortifyButton.active = this.menu.hasActiveRaid();
        }

        if (this.forceRaidButton != null) {
            this.forceRaidButton.active = !this.menu.hasActiveRaid();
        }
    }

    private boolean hasRecruitCapacity() {
        return this.menu.getRecruitedGuardsmen() < this.menu.getMilitaryCapacity();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xEE0B0B0B);
        guiGraphics.fill(x, y, x + this.imageWidth, y + 22, 0xEE3A2A12);

        guiGraphics.fill(x + 8, y + 30, x + 225, y + 96, 0xAA202020);
        guiGraphics.fill(x + 8, y + 102, x + 225, y + 154, 0xAA202020);
        guiGraphics.fill(x + 8, y + 160, x + 225, y + 275, 0xAA202020);

        guiGraphics.fill(x + 235, y + 30, x + 372, y + 245, 0xAA202020);
        guiGraphics.fill(x + 235, y + 250, x + 372, y + 275, 0xAA202020);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = 7;

        drawLine(guiGraphics, "Imperial Command Core", 8, y, 0xFFD6B85A);

        y = 34;
        drawLine(guiGraphics, "Settlement", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, "Level: " + this.menu.getCityLevel(), 12, y, 0xFFFFFFFF);
        y += 11;
        drawLine(guiGraphics, "Integrity: " + this.menu.getCityIntegrity() + "/100", 12, y, getIntegrityColor());
        y += 11;
        drawLine(guiGraphics, "Soldiers: " + this.menu.getRecruitedGuardsmen() + "/" + this.menu.getMilitaryCapacity(), 12, y, 0xFFBBD7FF);
        y += 11;
        drawLine(guiGraphics, "War Support: " + this.menu.getImperialWarSupport(), 12, y, 0xFFFFDD77);

        y = 106;
        drawLine(guiGraphics, "Resources", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, "Iron: " + this.menu.getIron() + "/" + this.menu.getStorageCapacity(), 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, "Scrap: " + this.menu.getScrapMetal() + "/" + this.menu.getStorageCapacity(), 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, "Coal: " + this.menu.getCoal() + "/" + this.menu.getStorageCapacity(), 12, y, 0xFFD8D8D8);

        y = 164;
        drawLine(guiGraphics, "Production", 12, y, 0xFFFFD27D);
        y += 13;
        drawLine(guiGraphics, "Iron +" + this.menu.getDailyIronProduction() + "/day", 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, "Scrap +" + this.menu.getDailyScrapProduction() + "/day", 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, "Coal +" + this.menu.getDailyCoalProduction() + "/day", 12, y, 0xFFD8D8D8);
        y += 11;
        drawLine(guiGraphics, "Gene " + this.menu.getEmperorGeneSeed() + "/" + this.menu.getEmperorGeneSeedCapacity() + " +" + this.menu.getDailyGeneProduction() + "/day", 12, y, 0xFFB066FF);
        y += 11;
        drawLine(guiGraphics, getSpaceMarineText(), 12, y, 0xFFFFD27D);

        y = 254;
        drawLine(guiGraphics, "Raid: " + getRaidStatusText(), 243, y, getRaidStatusColor());
        y += 11;
        drawLine(guiGraphics, "Raids/Victories: " + this.menu.getOrkRaidCount() + "/" + this.menu.getOrkRaidVictories(), 243, y, 0xFFFFBBBB);
    }

    private void drawLine(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x, y, color, false);
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