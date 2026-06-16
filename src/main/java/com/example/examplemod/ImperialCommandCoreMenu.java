package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class ImperialCommandCoreMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 40;

    private final ContainerData data;
    private final BlockPos commandCorePos;

    public ImperialCommandCoreMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(ExampleMod.IMPERIAL_COMMAND_CORE_MENU.get(), containerId);

        this.commandCorePos = extraData.readBlockPos();
        this.data = new SimpleContainerData(DATA_COUNT);

        addDataSlots(this.data);
    }

    public ImperialCommandCoreMenu(int containerId, Inventory playerInventory, ImperialCommandCoreBlockEntity commandCore) {
        super(ExampleMod.IMPERIAL_COMMAND_CORE_MENU.get(), containerId);

        this.commandCorePos = commandCore.getBlockPos();
        this.data = createServerData(commandCore);

        addDataSlots(this.data);
    }

    private ContainerData createServerData(ImperialCommandCoreBlockEntity commandCore) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> commandCore.getCityLevel();
                    case 1 -> commandCore.getCityIntegrityValue();
                    case 2 -> commandCore.getIron();
                    case 3 -> commandCore.getScrapMetal();
                    case 4 -> commandCore.getCoal();
                    case 5 -> commandCore.getStorageCapacity();
                    case 6 -> commandCore.getRecruitedGuardsmen();
                    case 7 -> commandCore.getMilitaryCapacity();
                    case 8 -> commandCore.getEmperorGeneSeed();
                    case 9 -> commandCore.getEmperorGeneSeedCapacity();
                    case 10 -> commandCore.getDailyIronProduction();
                    case 11 -> commandCore.getDailyScrapProduction();
                    case 12 -> commandCore.getDailyCoalProduction();
                    case 13 -> commandCore.getDailyEmperorGeneProduction();
                    case 14 -> commandCore.getImperialWarSupportValue();
                    case 15 -> commandCore.hasActiveOrkRaid() ? 1 : 0;
                    case 16 -> commandCore.getOrkRaidCountValue();
                    case 17 -> commandCore.getOrkRaidVictoriesValue();
                    case 18 -> commandCore.getReinforcementCooldownSeconds();
                    case 19 -> commandCore.getSpaceMarinePromotionCooldownSeconds();
                    case 20 -> commandCore.hasPendingSpaceMarineCandidate() ? 1 : 0;
                    case 21 -> commandCore.getActiveOrkRaidSeconds();
                    case 22 -> getCitizenCount(commandCore);
                    case 23 -> getUnemployedCitizenCount(commandCore);
                    case 24 -> getMineCount(commandCore);
                    case 25 -> commandCore.getImperialMineCapacity();
                    case 26 -> getScrapYardCount(commandCore);
                    case 27 -> commandCore.getScrapYardCapacity();
                    case 28 -> getForgeCount(commandCore);
                    case 29 -> commandCore.getImperialForgeCapacity();
                    case 30 -> getCitizenJobCount(commandCore, ImperialCitizenJob.MINER);
                    case 31 -> getCitizenJobCount(commandCore, ImperialCitizenJob.SCRAPPER);
                    case 32 -> getCitizenJobCount(commandCore, ImperialCitizenJob.SMITH);
                    case 33 -> getRefineryCount(commandCore);
                    case 34 -> commandCore.getPromethiumRefineryCapacity();
                    case 35 -> getCitizenJobCount(commandCore, ImperialCitizenJob.STOKER);
                    case 36 -> getBarracksCount(commandCore);
                    case 37 -> commandCore.getBarracksCapacity();
                    case 38 -> getCitizenJobCount(commandCore, ImperialCitizenJob.RECRUIT);
                    case 39 -> commandCore.getSelectedSpecialistOrdinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private int getCitizenCount(ImperialCommandCoreBlockEntity commandCore) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return ImperialPopulationManager.countAssignedCitizens(serverLevel, commandCore);
    }

    private int getUnemployedCitizenCount(ImperialCommandCoreBlockEntity commandCore) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return ImperialPopulationManager.countUnemployedCitizens(serverLevel, commandCore);
    }

    private int getMineCount(ImperialCommandCoreBlockEntity commandCore) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return ImperialWorkSiteManager.countImperialMines(serverLevel, commandCore, 128);
    }

    private int getScrapYardCount(ImperialCommandCoreBlockEntity commandCore) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return ImperialScrapYardManager.countScrapYards(serverLevel, commandCore, 128);
    }

    private int getForgeCount(ImperialCommandCoreBlockEntity commandCore) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return ImperialForgeManager.countForges(serverLevel, commandCore, 128);
    }

    private int getRefineryCount(ImperialCommandCoreBlockEntity commandCore) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return ImperialPromethiumRefineryManager.countRefineries(serverLevel, commandCore, 128);
    }

    private int getBarracksCount(ImperialCommandCoreBlockEntity commandCore) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return ImperialBarracksManager.countBarracks(serverLevel, commandCore, 128);
    }

    private int getCitizenJobCount(ImperialCommandCoreBlockEntity commandCore, ImperialCitizenJob job) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return ImperialPopulationManager.countCitizensWithJob(serverLevel, commandCore, job);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public BlockPos getCommandCorePos() {
        return this.commandCorePos;
    }

    public int getCityLevel() {
        return this.data.get(0);
    }

    public int getCityIntegrity() {
        return this.data.get(1);
    }

    public int getIron() {
        return this.data.get(2);
    }

    public int getScrapMetal() {
        return this.data.get(3);
    }

    public int getCoal() {
        return this.data.get(4);
    }

    public int getStorageCapacity() {
        return this.data.get(5);
    }

    public int getRecruitedGuardsmen() {
        return this.data.get(6);
    }

    public int getMilitaryCapacity() {
        return this.data.get(7);
    }

    public int getEmperorGeneSeed() {
        return this.data.get(8);
    }

    public int getEmperorGeneSeedCapacity() {
        return this.data.get(9);
    }

    public int getDailyIronProduction() {
        return this.data.get(10);
    }

    public int getDailyScrapProduction() {
        return this.data.get(11);
    }

    public int getDailyCoalProduction() {
        return this.data.get(12);
    }

    public int getDailyGeneProduction() {
        return this.data.get(13);
    }

    public int getImperialWarSupport() {
        return this.data.get(14);
    }

    public boolean hasActiveRaid() {
        return this.data.get(15) == 1;
    }

    public int getOrkRaidCount() {
        return this.data.get(16);
    }

    public int getOrkRaidVictories() {
        return this.data.get(17);
    }

    public int getReinforcementCooldownSeconds() {
        return this.data.get(18);
    }

    public int getSpaceMarineCooldownSeconds() {
        return this.data.get(19);
    }

    public boolean hasPendingSpaceMarineCandidate() {
        return this.data.get(20) == 1;
    }

    public int getActiveRaidSeconds() {
        return this.data.get(21);
    }

    public int getCitizenCount() {
        return this.data.get(22);
    }

    public int getUnemployedCitizenCount() {
        return this.data.get(23);
    }

    public int getMineCount() {
        return this.data.get(24);
    }

    public int getMineCapacity() {
        return this.data.get(25);
    }

    public int getScrapYardCount() {
        return this.data.get(26);
    }

    public int getScrapYardCapacity() {
        return this.data.get(27);
    }

    public int getForgeCount() {
        return this.data.get(28);
    }

    public int getForgeCapacity() {
        return this.data.get(29);
    }

    public int getMinerCount() {
        return this.data.get(30);
    }

    public int getScrapperCount() {
        return this.data.get(31);
    }

    public int getSmithCount() {
        return this.data.get(32);
    }

    public int getRefineryCount() {
        return this.data.get(33);
    }

    public int getRefineryCapacity() {
        return this.data.get(34);
    }

    public int getStokerCount() {
        return this.data.get(35);
    }

    public int getBarracksCount() {
        return this.data.get(36);
    }

    public int getBarracksCapacity() {
        return this.data.get(37);
    }

    public int getRecruitsInTraining() {
        return this.data.get(38);
    }

    public GuardsmanSpecialization getSelectedSpecialization() {
        return GuardsmanSpecialization.fromOrdinal(this.data.get(39));
    }
}