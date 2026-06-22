package com.example.examplemod;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server message carrying the side a player picked on the faction screen. The server
 * records it in {@link PlayerFactionData} (once — a player cannot re-pick by resending) and confirms
 * the pledge in chat. What the allegiance changes in gameplay is left for later.
 */
public class SelectFactionPacket {
    private final PlayerFaction faction;

    public SelectFactionPacket(PlayerFaction faction) {
        this.faction = faction;
    }

    public static void encode(SelectFactionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.faction);
    }

    public static SelectFactionPacket decode(FriendlyByteBuf buffer) {
        return new SelectFactionPacket(buffer.readEnum(PlayerFaction.class));
    }

    public static void handle(SelectFactionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null || !packet.faction.isChosen()) {
                return;
            }

            ServerLevel overworld = player.serverLevel().getServer().overworld();
            PlayerFactionData data = PlayerFactionData.get(overworld);

            // One-time choice: ignore a resend once a side is already locked in.
            if (data.hasChosen(player.getUUID())) {
                return;
            }

            data.setFaction(player.getUUID(), packet.faction);
            player.displayClientMessage(
                    Component.translatable("msg.firstcrusade.faction.chosen", packet.faction.getDisplayName()), false);
        });

        context.setPacketHandled(true);
    }
}
