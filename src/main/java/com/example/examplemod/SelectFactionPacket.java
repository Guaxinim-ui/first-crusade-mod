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

            // Picking a side changes the body: an Ork is 0.70 x 2.05 and a human is 0.60 x 1.80.
            // recalculate applies the right attribute pass, refreshes the dimensions, makes room if
            // the new body does not fit, and syncs both the profile and the body to every client
            // that can see him. Without it the choice only took effect on the next relog.
            com.example.examplemod.progression.PlayerProgressionManager.recalculate(player);
            player.displayClientMessage(
                    Component.translatable("msg.firstcrusade.faction.chosen", packet.faction.getDisplayName()), false);
        });

        context.setPacketHandled(true);
    }
}
