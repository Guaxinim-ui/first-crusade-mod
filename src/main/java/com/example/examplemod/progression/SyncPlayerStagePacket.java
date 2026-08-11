package com.example.examplemod.progression;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client: one player's evolution stage, and nothing else about them.
 *
 * <h2>Why the stage needs a packet of its own</h2>
 *
 * A player's stage decides their body — {@link PlayerProgressionSizeManager} reads it on every size
 * event, and the size event runs <b>on both sides</b>. The server knows the stage from its own saved
 * data; the client has to be told. Without this packet the client's copy of
 * {@link PlayerProgressionClientView#stageOf} answers {@code ASTRA_RECRUIT} for everybody forever,
 * so the client keeps a vanilla 0.60x1.80 hitbox while the server moves a 0.84x2.30 one. The two
 * simulations then disagree about where walls are: the server sees the player walk into blocks the
 * client thinks they cleared, rejects the movement, teleports them back, and — while it waits for
 * the client to acknowledge that teleport — silently drops every block placement and puts the
 * player's real position out of reach of whatever they are swinging at. The symptom that reaches
 * the player is "I cannot hit anything and I cannot place blocks".
 *
 * <h2>Why not fold it into the profile packet</h2>
 *
 * {@link SyncPlayerProgressionPacket} carries a whole profile and goes to its owner alone, because
 * a player's doctrine points are nobody else's business. The stage is the opposite: it is visible
 * on sight, and every client in render distance needs it. Two audiences, two packets — the split
 * {@link PlayerProgressionClientView} already documents.
 */
public class SyncPlayerStagePacket {

    private final UUID playerId;
    private final PlayerBody body;

    public SyncPlayerStagePacket(UUID playerId, PlayerBody body) {
        this.playerId = playerId;
        this.body = body;
    }

    public static void encode(SyncPlayerStagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId);
        buffer.writeFloat(packet.body.width());
        buffer.writeFloat(packet.body.height());
    }

    public static SyncPlayerStagePacket decode(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();

        // Clamped on arrival. These two floats become a collision box, and a box read straight off
        // the wire is a box a malformed packet gets to choose; four blocks is well past anything
        // either ladder can produce and nowhere near enough to be interesting to abuse.
        float width = Math.max(0.1F, Math.min(4.0F, buffer.readFloat()));
        float height = Math.max(0.1F, Math.min(4.0F, buffer.readFloat()));

        return new SyncPlayerStagePacket(id, new PlayerBody(width, height));
    }

    public static void handle(SyncPlayerStagePacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.examplemod.progression.client.ClientStageSync.accept(
                        packet.playerId, packet.body)));

        context.setPacketHandled(true);
    }
}
