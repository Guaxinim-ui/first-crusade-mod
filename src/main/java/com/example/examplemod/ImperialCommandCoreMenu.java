package com.example.examplemod;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class ImperialCommandCoreMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 22;

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

    @Override
public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
}
}