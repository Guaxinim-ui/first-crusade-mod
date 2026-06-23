package com.example.examplemod;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server request from the Ork city panel ({@link OrkCampScreen}). For now it carries a
 * single build action (raise a Loot Pit); the server re-validates distance and loot in
 * {@link OrkCampBlockEntity#buildLootPit}.
 */
public class OrkCampActionPacket {
    public enum Action {
        BUILD_LOOT_PIT
    }

    private final BlockPos campPos;
    private final Action action;

    public OrkCampActionPacket(BlockPos campPos, Action action) {
        this.campPos = campPos;
        this.action = action;
    }

    public static void encode(OrkCampActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.campPos);
        buffer.writeEnum(packet.action);
    }

    public static OrkCampActionPacket decode(FriendlyByteBuf buffer) {
        return new OrkCampActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class));
    }

    public static void handle(OrkCampActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            ServerLevel serverLevel = player.serverLevel();

            double distance = player.distanceToSqr(
                    packet.campPos.getX() + 0.5D, packet.campPos.getY() + 0.5D, packet.campPos.getZ() + 0.5D);
            if (distance > 64.0D) {
                player.displayClientMessage(Component.translatable("msg.firstcrusade.ork.too_far"), true);
                return;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(packet.campPos);
            if (blockEntity instanceof OrkCampBlockEntity camp) {
                switch (packet.action) {
                    case BUILD_LOOT_PIT -> camp.buildLootPit(player);
                }
            }
        });

        context.setPacketHandled(true);
    }
}
