package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server request from the Strategium GUI to start researching the next Imperium Age. The
 * server re-validates everything (distance, bench present, cost, no research running) in
 * {@link StrategiumBlockEntity#tryStartResearch}.
 */
public class StrategiumActionPacket {
    private final BlockPos benchPos;

    public StrategiumActionPacket(BlockPos benchPos) {
        this.benchPos = benchPos;
    }

    public static void encode(StrategiumActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.benchPos);
    }

    public static StrategiumActionPacket decode(FriendlyByteBuf buffer) {
        return new StrategiumActionPacket(buffer.readBlockPos());
    }

    public static void handle(StrategiumActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            ServerLevel serverLevel = player.serverLevel();

            double distance = player.distanceToSqr(
                    packet.benchPos.getX() + 0.5D, packet.benchPos.getY() + 0.5D, packet.benchPos.getZ() + 0.5D);
            if (distance > 64.0D) {
                player.displayClientMessage(Component.translatable("msg.firstcrusade.strategium.too_far"), true);
                return;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(packet.benchPos);
            if (blockEntity instanceof StrategiumBlockEntity bench) {
                bench.tryStartResearch(player);
            }
        });

        context.setPacketHandled(true);
    }
}
