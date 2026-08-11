package com.example.examplemod.progression.ork;

import java.util.function.Supplier;

import com.example.examplemod.progression.PlayerProgressionClientView;
import com.example.examplemod.progression.PlayerProgressionProfile;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: the Fury bar, and nothing else.
 *
 * <h2>Why it is not the profile packet</h2>
 *
 * Fury moves on every blow an Ork lands and every blow he takes. The Fury gain used to end in
 * {@code PlayerProgressionNetwork.sync}, which sends the <b>whole</b> progression profile — every
 * rank of both trees, every tally, the klan, the surgery state — plus a body broadcast to every
 * client that can see the player. Per swing. In a fight with a handful of Orks that is a packet storm
 * to move a number between 0 and 100.
 *
 * <p>This is that number: two fields, sent to one player, and throttled on top of that by
 * {@link PlayerOrkProgressionBalance#FURY_SYNC_INTERVAL_TICKS}.
 *
 * <h2>The timestamp travels with it</h2>
 *
 * Fury decays by arithmetic on "what it was, and when" rather than by ticking — so sending only the
 * value would leave the client with a bar that never drains between packets, or one that drains from
 * the wrong instant. Sending the pair means the client runs the identical
 * {@link PlayerOrkProgressionProfile#fury} it already has, over the identical inputs, and the two
 * sides cannot disagree. The DA GREENEST floor comes out right for free, because the client reads it
 * from its own copy of the ranks.
 *
 * <h2>It is not authority</h2>
 *
 * The value is written into the client's copy of the profile, which the client only ever draws with.
 * Every decision that Fury feeds — whether the shout may be used, what it costs — is taken on the
 * server against the server's own profile.
 */
public class SyncOrkFuryPacket {

    private final int fury;
    private final long gameTime;

    public SyncOrkFuryPacket(int fury, long gameTime) {
        this.fury = fury;
        this.gameTime = gameTime;
    }

    public static void encode(SyncOrkFuryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.fury);
        buffer.writeLong(packet.gameTime);
    }

    public static SyncOrkFuryPacket decode(FriendlyByteBuf buffer) {
        return new SyncOrkFuryPacket(buffer.readVarInt(), buffer.readLong());
    }

    public static void handle(SyncOrkFuryPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    PlayerProgressionProfile self = PlayerProgressionClientView.self();
                    if (self == null) {
                        // The full profile has not arrived yet. Dropping this is correct: the sync
                        // that follows carries the same Fury anyway.
                        return;
                    }

                    // setFury clamps to 0..FURY_MAX, so a malformed packet moves a bar and nothing
                    // else — which is the whole reason this packet is allowed to be small.
                    self.ork().setFury(packet.fury, packet.gameTime);
                }));

        context.setPacketHandled(true);
    }
}
