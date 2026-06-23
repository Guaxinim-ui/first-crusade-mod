package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Strategium (War Table) bench. It stockpiles Iron and Scrap the player feeds it (right-click
 * with the material), and from its menu the player spends that stockpile to research the Imperium's
 * next Age — a timed, paid age-up (see {@link FactionResearchManager}). The research itself is global
 * ({@link FactionResearchData}); the bench is the place you fund and trigger it.
 */
public class StrategiumBlockEntity extends BlockEntity {
    private int storedIron = 0;
    private int storedScrap = 0;

    public StrategiumBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.STRATEGIUM_BLOCK_ENTITY.get(), pos, state);
    }

    public int getStoredIron() {
        return this.storedIron;
    }

    public int getStoredScrap() {
        return this.storedScrap;
    }

    public void depositIron(Player player, ItemStack stack) {
        this.storedIron += stack.getCount();
        stack.setCount(0);
        setChanged();
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.strategium.stocked_iron", this.storedIron), true);
    }

    public void depositScrap(Player player, ItemStack stack) {
        this.storedScrap += stack.getCount();
        stack.setCount(0);
        setChanged();
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.strategium.stocked_scrap", this.storedScrap), true);
    }

    // Spends the stockpile to begin researching the next Crusade Age. Validates that no research is
    // already running, the Imperium is not already at the top Age, and the bench holds enough resources.
    public void tryStartResearch(Player player) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        FactionResearchData research = FactionResearchData.get(serverLevel);

        if (research.isActive()) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.research.busy"), true);
            return;
        }

        int currentTier = ImperiumOverlordManager.getTier(serverLevel);

        if (currentTier >= FactionResearchManager.MAX_TIER) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.research.maxed"), true);
            return;
        }

        int ironCost = FactionResearchManager.researchIronCost(currentTier);
        int scrapCost = FactionResearchManager.researchScrapCost(currentTier);

        if (this.storedIron < ironCost || this.storedScrap < scrapCost) {
            player.displayClientMessage(
                    Component.translatable("msg.firstcrusade.research.too_poor", ironCost, scrapCost), true);
            return;
        }

        this.storedIron -= ironCost;
        this.storedScrap -= scrapCost;
        setChanged();

        int targetTier = currentTier + 1;
        research.start(targetTier, FactionResearchManager.researchDurationTicks(currentTier));

        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("msg.firstcrusade.research.started", targetTier), false);
    }

    public void openInterface(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        NetworkHooks.openScreen(
                serverPlayer,
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("block.firstcrusade.strategium");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                        return new StrategiumMenu(containerId, playerInventory, StrategiumBlockEntity.this);
                    }
                },
                this.worldPosition
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("StoredIron", this.storedIron);
        tag.putInt("StoredScrap", this.storedScrap);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.storedIron = tag.getInt("StoredIron");
        this.storedScrap = tag.getInt("StoredScrap");
    }
}
