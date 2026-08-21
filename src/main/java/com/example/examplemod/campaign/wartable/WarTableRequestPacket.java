package com.example.examplemod.campaign.wartable;

import java.util.function.Supplier;

import com.example.examplemod.FirstCrusadeNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Client → server: "send me the war again".
 *
 * <h2>The only thing the screen may ask for</h2>
 *
 * The War Table's refresh button sends this and nothing else. It carries a block position, which the
 * server re-validates from scratch — the table must still be there and the player must still be
 * standing at it. Between opening the screen and pressing refresh, a player can walk away, and
 * another can break the block; a request that trusted the position it was handed would let a client
 * read the whole strategic picture from anywhere in the world.
 *
 * <p>There is deliberately no packet for changing anything. Commanding troops from the table is the
 * next slice, and when it arrives it will be a separate, individually validated action rather than a
 * flag on this one.
 */
public class WarTableRequestPacket {

    private final BlockPos tablePos;

    public WarTableRequestPacket(BlockPos tablePos) {
        this.tablePos = tablePos;
    }

    public static void encode(WarTableRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.tablePos);
    }

    public static WarTableRequestPacket decode(FriendlyByteBuf buffer) {
        return new WarTableRequestPacket(buffer.readBlockPos());
    }

    public static void handle(WarTableRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null || !WarTableBlock.canUse(player, packet.tablePos)) {
                return;
            }

            FirstCrusadeNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new WarTableSnapshotPacket(WarTableSnapshot.capture(player, packet.tablePos)));
        });

        context.setPacketHandled(true);
    }
}
