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

/**
 * Menu for the Strategium bench: a slotless container that syncs the Imperium's research state to the
 * client GUI ({@link StrategiumScreen}). All economy happens through {@link StrategiumActionPacket};
 * the data slots are read-only mirrors of the global research/Age state and the bench's stockpile.
 */
public class StrategiumMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 12;

    private final ContainerData data;
    private final BlockPos benchPos;

    // Client-side constructor (reads the synced bench position from the open-screen buffer).
    public StrategiumMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(ExampleMod.STRATEGIUM_MENU.get(), containerId);
        this.benchPos = extraData.readBlockPos();
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(this.data);
    }

    // Server-side constructor (live view of the bench + global research state).
    public StrategiumMenu(int containerId, Inventory playerInventory, StrategiumBlockEntity bench) {
        super(ExampleMod.STRATEGIUM_MENU.get(), containerId);
        this.benchPos = bench.getBlockPos();
        this.data = createServerData(bench);
        addDataSlots(this.data);
    }

    private ContainerData createServerData(StrategiumBlockEntity bench) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (!(bench.getLevel() instanceof ServerLevel serverLevel)) {
                    return 0;
                }

                ImperiumOverlordData crusade = ImperiumOverlordData.get(serverLevel);
                WaaaghOverlordData waaagh = WaaaghOverlordData.get(serverLevel);
                FactionResearchData research = FactionResearchData.get(serverLevel);

                int tier = crusade.getTier();

                return switch (index) {
                    case 0 -> tier;
                    case 1 -> Math.round(crusade.getProgressToNextTier() * 1000.0F);
                    case 2 -> research.isActive() ? 1 : 0;
                    case 3 -> research.getTicksRemaining();
                    case 4 -> research.getTotalTicks();
                    case 5 -> bench.getStoredIron();
                    case 6 -> bench.getStoredScrap();
                    case 7 -> FactionResearchManager.researchIronCost(tier);
                    case 8 -> FactionResearchManager.researchScrapCost(tier);
                    case 9 -> waaagh.getTier();
                    case 10 -> Math.round(waaagh.getProgressToNextTier() * 1000.0F);
                    case 11 -> tier >= FactionResearchManager.MAX_TIER ? 1 : 0;
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

    public BlockPos getBenchPos() {
        return this.benchPos;
    }

    public int getImperiumAge() {
        return this.data.get(0);
    }

    public float getImperiumProgress() {
        return this.data.get(1) / 1000.0F;
    }

    public boolean isResearching() {
        return this.data.get(2) == 1;
    }

    public int getResearchTicksRemaining() {
        return this.data.get(3);
    }

    public int getResearchTotalTicks() {
        return this.data.get(4);
    }

    public int getStoredIron() {
        return this.data.get(5);
    }

    public int getStoredScrap() {
        return this.data.get(6);
    }

    public int getResearchIronCost() {
        return this.data.get(7);
    }

    public int getResearchScrapCost() {
        return this.data.get(8);
    }

    public int getWaaaghAge() {
        return this.data.get(9);
    }

    public float getWaaaghProgress() {
        return this.data.get(10) / 1000.0F;
    }

    public boolean isImperiumMaxAge() {
        return this.data.get(11) == 1;
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
