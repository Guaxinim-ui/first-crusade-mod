package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ImperialCommandCoreActionPacket {
    private final BlockPos commandCorePos;
    private final ImperialCommandCoreAction action;

    public ImperialCommandCoreActionPacket(BlockPos commandCorePos, ImperialCommandCoreAction action) {
        this.commandCorePos = commandCorePos;
        this.action = action;
    }

    public static void encode(ImperialCommandCoreActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.commandCorePos);
        buffer.writeEnum(packet.action);
    }

    public static ImperialCommandCoreActionPacket decode(FriendlyByteBuf buffer) {
        BlockPos commandCorePos = buffer.readBlockPos();
        ImperialCommandCoreAction action = buffer.readEnum(ImperialCommandCoreAction.class);

        return new ImperialCommandCoreActionPacket(commandCorePos, action);
    }

    public static void handle(ImperialCommandCoreActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            ServerLevel serverLevel = player.serverLevel();

            if (!isPlayerCloseEnough(player, packet.commandCorePos)) {
                player.displayClientMessage(Component.literal("You are too far away from the Imperial Command Core."), true);
                return;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(packet.commandCorePos);

            if (!(blockEntity instanceof ImperialCommandCoreBlockEntity commandCore)) {
                player.displayClientMessage(Component.literal("Imperial Command Core not found."), true);
                return;
            }

            executeAction(player, commandCore, packet.action);
        });

        context.setPacketHandled(true);
    }

    private static boolean isPlayerCloseEnough(ServerPlayer player, BlockPos commandCorePos) {
        double distance = player.distanceToSqr(
                commandCorePos.getX() + 0.5D,
                commandCorePos.getY() + 0.5D,
                commandCorePos.getZ() + 0.5D
        );

        return distance <= 64.0D;
    }

    private static void executeAction(ServerPlayer player, ImperialCommandCoreBlockEntity commandCore, ImperialCommandCoreAction action) {
        switch (action) {
            case DEPOSIT_RESOURCES -> commandCore.depositAllResources(player);

            case WITHDRAW_IRON -> commandCore.withdrawResource(player, ImperialResourceType.IRON, 64);
            case WITHDRAW_COAL -> commandCore.withdrawResource(player, ImperialResourceType.COAL, 64);
            case WITHDRAW_SCRAP -> commandCore.withdrawResource(player, ImperialResourceType.SCRAP, 64);
            case WITHDRAW_GOLD -> commandCore.withdrawResource(player, ImperialResourceType.GOLD, 64);
            case WITHDRAW_EMERALD -> commandCore.withdrawResource(player, ImperialResourceType.EMERALD, 64);
            case WITHDRAW_CRUSADIUM -> commandCore.withdrawResource(player, ImperialResourceType.CRUSADIUM, 64);
            case WITHDRAW_FOOD -> commandCore.withdrawFood(player, 64);

            case BUILD_IMPERIAL_MINE -> commandCore.tryBuildImperialMine(player);

            case BUILD_GOLD_MINE -> commandCore.tryBuildImperialGoldMine(player);

            case BUILD_SCRAP_YARD -> commandCore.tryBuildScrapYard(player);

            case BUILD_IMPERIAL_FORGE -> commandCore.tryBuildImperialForge(player);

            case BUILD_PROMETHIUM_REFINERY -> commandCore.tryBuildPromethiumRefinery(player);

            case BUILD_FARM -> commandCore.tryBuildImperialFarm(player);

            case BUILD_EMERALD_TRADE_DEPOT -> commandCore.tryBuildEmeraldTradeDepot(player);

            case BUILD_BARRACKS -> commandCore.tryBuildBarracks(player);

            case GIVE_BUILDER_TOOL -> commandCore.giveBuilderTool(player);

            case RECRUIT_GUARDSMAN -> commandCore.tryRecruitGuardsman(player);

            case CYCLE_SPECIALIST -> commandCore.cycleSelectedSpecialist(player);

            case PROMOTE_SPECIALIST -> commandCore.promoteSpecialist(player);

            case UPGRADE_CITY -> {
                ItemStack plateStack = findItemStack(player, FCRegistry.CRUSADIUM_PLATE.get());

                if (plateStack.isEmpty()) {
                    player.displayClientMessage(Component.literal("You need Crusadium Plate in your inventory to upgrade the city."), true);
                    return;
                }

                commandCore.tryUpgradeCity(player, plateStack);
            }

            case REPAIR_CORE -> {
                ItemStack plateStack = findItemStack(player, FCRegistry.CRUSADIUM_PLATE.get());

                if (plateStack.isEmpty()) {
                    player.displayClientMessage(Component.literal("You need Crusadium Plate in your inventory to repair the Core."), true);
                    return;
                }

                commandCore.repairCity(player, plateStack);
            }

            case CALL_REINFORCEMENTS -> commandCore.callImperialReinforcements(player);
            case RALLY_DEFENDERS -> commandCore.rallyDefenders(player);
            case FORTIFY_DEFENDERS -> commandCore.fortifyDefenders(player);
            case FORCE_RAID_TEST -> commandCore.forceOrkRaid(player);

            case TOGGLE_GOVERNANCE -> commandCore.toggleGovernanceDelegation(player);
            case SURVEY_BORDER -> commandCore.surveyBuildBorder(player);
        }
    }

    private static ItemStack findItemStack(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);

            if (!stack.isEmpty() && stack.is(item)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }
}