package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class ImperialCommandCoreBlock extends BaseEntityBlock {
    public ImperialCommandCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ImperialCommandCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }

        return createTickerHelper(
                blockEntityType,
                ExampleMod.IMPERIAL_COMMAND_CORE_BLOCK_ENTITY.get(),
                ImperialCommandCoreBlockEntity::serverTick
        );
    }

    // When the Core is destroyed the city falls — drop it from the world war map.
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WorldWarMapData.get(serverLevel).removeCity(pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (!(blockEntity instanceof ImperialCommandCoreBlockEntity commandCore)) {
            return InteractionResult.PASS;
        }

        if (!commandCore.hasOwner()) {
            commandCore.setOwner(player);
            player.displayClientMessage(Component.literal("Imperial Command Core claimed."), false);
            return InteractionResult.SUCCESS;
        }

        if (heldItem.is(Items.ROTTEN_FLESH)) {
            commandCore.forceOrkRaid(player);
            return InteractionResult.SUCCESS;
        }

        if (heldItem.is(Items.EMERALD)) {
            commandCore.callImperialReinforcements(player);
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown() && heldItem.isEmpty()) {
            commandCore.tryRecruitGuardsman(player);
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown() && heldItem.is(ExampleMod.CRUSADIUM_PLATE.get())) {
            commandCore.tryUpgradeCity(player, heldItem);
            return InteractionResult.SUCCESS;
        }

        if (!player.isShiftKeyDown() && heldItem.is(ExampleMod.CRUSADIUM_PLATE.get())) {
            commandCore.repairCity(player, heldItem);
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown() && heldItem.is(Items.BELL)) {
            player.displayClientMessage(Component.literal("Village scan is disabled in this build."), false);
            player.displayClientMessage(Component.literal("The Ork raid system is active."), false);
            return InteractionResult.SUCCESS;
        }

        if (heldItem.is(Items.IRON_INGOT)) {
            commandCore.depositIron(player, heldItem);
            return InteractionResult.SUCCESS;
        }

        if (heldItem.is(Items.COAL)) {
            commandCore.depositCoal(player, heldItem);
            return InteractionResult.SUCCESS;
        }

        if (heldItem.is(ExampleMod.SCRAP_METAL.get())) {
            commandCore.depositScrapMetal(player, heldItem);
            return InteractionResult.SUCCESS;
        }

        commandCore.openCommandInterface(player);
        return InteractionResult.SUCCESS;
    }
}