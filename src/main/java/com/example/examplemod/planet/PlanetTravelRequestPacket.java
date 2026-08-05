package com.example.examplemod.planet;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server: "launch me to this planet", or "take me back".
 *
 * <h2>The only thing the client is trusted for</h2>
 *
 * This packet carries an id and a position and nothing else — no unlock flag, no cost, no distance,
 * no permission. Every one of those is looked up again on the server by
 * {@link PlanetTravelManager#beginTravel}, because the packet is the one part of the system an
 * unfriendly client controls completely.
 *
 * <p>The id is <b>resolved against the registry</b> rather than used: an unknown or malformed id
 * finds no planet and the request is dropped, so no string from the network ever reaches a
 * dimension lookup. The terminal position is checked for range and for actually being a terminal.
 *
 * <h2>Rate limiting</h2>
 *
 * A player already mid-launch is refused by {@code checkTerminal} (as {@code IN_TRANSIT}), which is
 * also what stops a client from spamming launches: the first one puts them in transit, and every
 * packet that arrives afterwards is answered with the same refusal until it finishes.
 */
public class PlanetTravelRequestPacket {

    /** Empty id means the return leg — "back where I came from" needs no destination. */
    private static final String RETURN = "";

    private final String planetId;
    private final BlockPos terminalPos;

    public PlanetTravelRequestPacket(ResourceLocation planetId, BlockPos terminalPos) {
        this.planetId = planetId == null ? RETURN : planetId.toString();
        this.terminalPos = terminalPos;
    }

    private PlanetTravelRequestPacket(String planetId, BlockPos terminalPos) {
        this.planetId = planetId;
        this.terminalPos = terminalPos;
    }

    /** The request that sends a traveller home. */
    public static PlanetTravelRequestPacket returnHome(BlockPos terminalPos) {
        return new PlanetTravelRequestPacket(RETURN, terminalPos);
    }

    public static void encode(PlanetTravelRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.planetId, 128);
        buffer.writeBlockPos(packet.terminalPos);
    }

    public static PlanetTravelRequestPacket decode(FriendlyByteBuf buffer) {
        return new PlanetTravelRequestPacket(buffer.readUtf(128), buffer.readBlockPos());
    }

    public static void handle(PlanetTravelRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            PlanetTravelManager.Blocker blocker;

            if (packet.planetId.isEmpty()) {
                blocker = PlanetTravelManager.beginReturn(player, packet.terminalPos);
            } else {
                Optional<PlanetDefinition> planet = PlanetRegistry.get(
                        ResourceLocation.tryParse(packet.planetId));

                // An id nothing matches is not an error to report to the player: it is a client
                // sending something the server never offered. Drop it quietly and log nothing —
                // a malformed packet must not be a way to fill somebody's console.
                if (planet.isEmpty()) {
                    return;
                }

                blocker = PlanetTravelManager.beginTravel(player, planet.get(), packet.terminalPos);
            }

            if (!blocker.ok()) {
                player.displayClientMessage(blocker.message(), true);
                return;
            }

            // The launch has begun; the screen has no further job.
            player.closeContainer();
        });

        context.setPacketHandled(true);
    }
}
