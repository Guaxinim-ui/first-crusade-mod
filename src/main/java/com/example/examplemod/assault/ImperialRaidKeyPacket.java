package com.example.examplemod.assault;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * "I pressed the raid key." That is the entire payload.
 *
 * <h2>No arguments on purpose</h2>
 *
 * The key does not name a camp, a base, a squad size or a drop point — the client has no business
 * choosing any of them, and a packet that carried them would be a packet somebody could forge.
 * Which camp is the nearest one is a question the server answers from its own war map, and every
 * rule that guards {@link ImperialAssaultManager#startRaid} guards this too, because this <i>is</i>
 * that call with the camp looked up first.
 *
 * <h2>Why a key at all</h2>
 *
 * The Ork panel's button needs the player to right-click the camp block, which means walking up to
 * a block in the middle of a camp full of Orks. A key is the same order given from wherever the
 * commander is standing when they decide to give it.
 */
public class ImperialRaidKeyPacket {

    public ImperialRaidKeyPacket() {
    }

    public static void encode(ImperialRaidKeyPacket packet, FriendlyByteBuf buffer) {
        // Nothing to write: the press is the whole message.
    }

    public static ImperialRaidKeyPacket decode(FriendlyByteBuf buffer) {
        return new ImperialRaidKeyPacket();
    }

    public static void handle(ImperialRaidKeyPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            Component reply = ImperialAssaultManager.declareNearestRaid(player);

            if (!reply.getString().isEmpty()) {
                player.displayClientMessage(reply, true);
            }
        });

        context.setPacketHandled(true);
    }
}
