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
    private static final int DATA_COUNT = 20;

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
        this.data = createServerData(camp, playerInventory.player);
        addDataSlots(this.data);
    }

    /**
     * The panel's read-only view of the camp — plus, for the viewer only, whether they are Imperial.
     *
     * <p>The client cannot work its own allegiance out: {@code PlayerFactionData} lives on the
     * server. Rather than teach the client about factions for one button, the server answers the
     * question once, here, when the menu opens. The button it draws is still only a courtesy —
     * {@link ImperialCommandCoreActionPacket}'s Ork counterpart re-checks the faction on arrival.
     */
    private ContainerData createServerData(OrkCampBlockEntity camp,
                                           net.minecraft.world.entity.player.Player viewer) {
        boolean imperial = FirstCrusadeFactionManager.getFaction(viewer)
                == FirstCrusadeFaction.IMPERIUM;

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
                    case 8 -> imperial ? 1 : 0;
                    case 9 -> camp.isUnderAssault() ? 1 : 0;
                    case 10 -> camp.getCachedNobz();
                    case 11 -> camp.getGarrisonCap();
                    case 12 -> camp.getNobCap();
                    // -1 when no target is chosen, which the panel reads as "none" rather than as a
                    // distance of zero — a camp aimed at itself and a camp aimed at nothing must not
                    // look the same.
                    case 13 -> camp.getTargetDistance();
                    case 14 -> camp.getBoyLootCost();
                    case 15 -> camp.getNobLootCost();
                    case 16 -> camp.hasSquigPen() ? 1 : 0;
                    case 17 -> camp.hasMekWorkshop() ? 1 : 0;
                    case 18 -> camp.getSquigPenCost();
                    case 19 -> camp.getMekWorkshopCost();
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

    /** Whether the player looking at this panel fights for the Imperium. */
    public boolean isViewerImperial() {
        return this.data.get(8) != 0;
    }

    public boolean isUnderAssault() {
        return this.data.get(9) != 0;
    }

    public int getNobz() {
        return this.data.get(10);
    }

    public int getGarrisonCap() {
        return this.data.get(11);
    }

    public int getNobCap() {
        return this.data.get(12);
    }

    /** Blocks to the chosen target, or -1 when the camp is not pointed at anything. */
    public int getTargetDistance() {
        return this.data.get(13);
    }

    public boolean hasTarget() {
        return getTargetDistance() >= 0;
    }

    public int getBoyCost() {
        return this.data.get(14);
    }

    public int getNobCost() {
        return this.data.get(15);
    }

    public boolean hasSquigPen() {
        return this.data.get(16) != 0;
    }

    public boolean hasMekWorkshop() {
        return this.data.get(17) != 0;
    }

    public int getSquigPenCost() {
        return this.data.get(18);
    }

    public int getMekWorkshopCost() {
        return this.data.get(19);
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
