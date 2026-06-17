package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class ImperialCommandCoreMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 55;

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
                    case 22 -> commandCore.getCachedCitizenCount();
                    case 23 -> commandCore.getCachedUnemployedCount();
                    case 24 -> commandCore.getCachedMineCount();
                    case 25 -> commandCore.getImperialMineCapacity();
                    case 26 -> commandCore.getCachedScrapYardCount();
                    case 27 -> commandCore.getScrapYardCapacity();
                    case 28 -> commandCore.getCachedForgeCount();
                    case 29 -> commandCore.getImperialForgeCapacity();
                    case 30 -> commandCore.getCachedMinerCount();
                    case 31 -> commandCore.getCachedScrapperCount();
                    case 32 -> commandCore.getCachedSmithCount();
                    case 33 -> commandCore.getCachedRefineryCount();
                    case 34 -> commandCore.getPromethiumRefineryCapacity();
                    case 35 -> commandCore.getCachedStokerCount();
                    case 36 -> commandCore.getCachedBarracksCount();
                    case 37 -> commandCore.getBarracksCapacity();
                    case 38 -> commandCore.getCachedRecruitCount();
                    case 39 -> commandCore.getSelectedSpecialistOrdinal();
                    case 40 -> commandCore.getGold();
                    case 41 -> commandCore.getEmerald();
                    case 42 -> commandCore.getCrusadium();
                    case 43 -> commandCore.getCityMorale();
                    case 44 -> commandCore.getCityTypeOrdinal();
                    case 45 -> commandCore.getCachedThreatScore();
                    case 46 -> commandCore.getCachedGoldMineCount();
                    case 47 -> commandCore.getGoldMineCapacity();
                    case 48 -> commandCore.getCachedGoldMinerCount();
                    case 49 -> commandCore.getCachedFarmCount();
                    case 50 -> commandCore.getFarmCapacity();
                    case 51 -> commandCore.getCachedFarmerCount();
                    case 52 -> commandCore.getCachedTradeDepotCount();
                    case 53 -> commandCore.getTradeDepotCapacity();
                    case 54 -> commandCore.getCachedTraderCount();
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

    public int getGold() {
        return this.data.get(40);
    }

    public int getEmerald() {
        return this.data.get(41);
    }

    public int getCrusadium() {
        return this.data.get(42);
    }

    public int getCityMorale() {
        return this.data.get(43);
    }

    public ImperialCityType getCityType() {
        ImperialCityType[] values = ImperialCityType.values();
        int ordinal = this.data.get(44);

        if (ordinal < 0 || ordinal >= values.length) {
            return ImperialCityType.CIVILISED;
        }

        return values[ordinal];
    }

    public int getThreatScore() {
        return this.data.get(45);
    }

    public int getGoldMineCount() {
        return this.data.get(46);
    }

    public int getGoldMineCapacity() {
        return this.data.get(47);
    }

    public int getGoldMinerCount() {
        return this.data.get(48);
    }

    public int getFarmCount() {
        return this.data.get(49);
    }

    public int getFarmCapacity() {
        return this.data.get(50);
    }

    public int getFarmerCount() {
        return this.data.get(51);
    }

    public int getTradeDepotCount() {
        return this.data.get(52);
    }

    public int getTradeDepotCapacity() {
        return this.data.get(53);
    }

    public int getTraderCount() {
        return this.data.get(54);
    }
}