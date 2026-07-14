package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Ork city's command panel: a slotless container that mirrors the Ork city's state to
 * the client GUI ({@link OrkCampScreen}). Read-only data slots (level, clan, populace, garrison,
 * loot and Loot Pits); building is requested through {@link OrkCampActionPacket}. Built to the same
 * simple-panel pattern as {@link StrategiumMenu}.
 */
public class OrkCampMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 8;

    private final ContainerData data;
    private final BlockPos campPos;

    public OrkCampMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(FCRegistry.ORK_CAMP_MENU.get(), containerId);
        this.campPos = extraData.readBlockPos();
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(this.data);
    }

    public OrkCampMenu(int containerId, Inventory playerInventory, OrkCampBlockEntity camp) {
        super(FCRegistry.ORK_CAMP_MENU.get(), containerId);
        this.campPos = camp.getBlockPos();
        this.data = createServerData(camp);
        addDataSlots(this.data);
    }

    private ContainerData createServerData(OrkCampBlockEntity camp) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> camp.getCampLevel();
                    case 1 -> camp.getClanOrdinal();
                    case 2 -> camp.getLootValue();
                    case 3 -> camp.getLootCapValue();
                    case 4 -> camp.getCachedGrots();
                    case 5 -> camp.getCachedBoyz();
                    case 6 -> camp.getCachedLootPits();
                    case 7 -> camp.getLootPitCost();
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

    public BlockPos getCampPos() {
        return this.campPos;
    }

    public int getCampLevel() {
        return this.data.get(0);
    }

    public OrkClan getClan() {
        OrkClan[] values = OrkClan.values();
        int ordinal = this.data.get(1);
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OrkClan.GOFFS;
    }

    public int getLoot() {
        return this.data.get(2);
    }

    public int getLootCap() {
        return this.data.get(3);
    }

    public int getGrots() {
        return this.data.get(4);
    }

    public int getBoyz() {
        return this.data.get(5);
    }

    public int getLootPits() {
        return this.data.get(6);
    }

    public int getLootPitCost() {
        return this.data.get(7);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
