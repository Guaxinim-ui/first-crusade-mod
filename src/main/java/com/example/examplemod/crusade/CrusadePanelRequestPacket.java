package com.example.examplemod.crusade;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Client → server: "I am looking at this base's people, send them."
 *
 * <h2>Pull, not push</h2>
 *
 * The roster could have ridden along with the Core menu at open time, and then every player opening
 * a Core for any reason would pay for a list most of them never scroll to. Asking means the traffic
 * happens when a tab is actually opened, which for most visits is never.
 *
 * <h2>What the server refuses</h2>
 *
 * The position comes from the client, so it is checked rather than trusted: the chunk must be
 * loaded, the block must really be a Command Core, and the player must be close enough to have the
 * screen open at all. Without the range check this packet would be a way to read any base on the
 * map from anywhere — which in multiplayer is another player's garrison, and none of this client's
 * business.
 */
public class CrusadePanelRequestPacket {

    /** Squared blocks. Comfortably past the vanilla container reach, and nowhere near a map scan. */
    private static final double MAX_RANGE_SQR = 64.0D * 64.0D;

    private final BlockPos corePos;

    public CrusadePanelRequestPacket(BlockPos corePos) {
        this.corePos = corePos;
    }

    public static void encode(CrusadePanelRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.corePos);
    }

    public static CrusadePanelRequestPacket decode(FriendlyByteBuf buffer) {
        return new CrusadePanelRequestPacket(buffer.readBlockPos());
    }

    public static void handle(CrusadePanelRequestPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }

            if (player.distanceToSqr(packet.corePos.getX() + 0.5D, packet.corePos.getY() + 0.5D,
                    packet.corePos.getZ() + 0.5D) > MAX_RANGE_SQR) {
                return;
            }

            // An unloaded chunk is not force-loaded to answer a GUI. If the base is not in memory
            // the screen simply shows nothing, which is honest and free.
            if (!level.isLoaded(packet.corePos)
                    || !(level.getBlockEntity(packet.corePos)
                            instanceof com.example.examplemod.ImperialCommandCoreBlockEntity)) {
                return;
            }

            ImperialCrusadeData data = ImperialCrusadeData.get(level);
            ImperialSoldierRoster roster = data.peek(packet.corePos);
            if (roster == null) {
                return;
            }

            com.example.examplemod.FirstCrusadeNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    CrusadePanelPacket.of(packet.corePos, roster, data.crusadeName(),
                            level.getGameTime()));
        });

        context.setPacketHandled(true);
    }
}
