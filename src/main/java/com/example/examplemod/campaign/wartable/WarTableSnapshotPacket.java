package com.example.examplemod.campaign.wartable;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: the state of the whole war, and the signal to show it.
 *
 * <p>Client-only opening is routed through {@code WarTableClient} by {@link DistExecutor} so the
 * server never class-loads the screen — the same guard {@code OpenFactionSelectPacket} uses, and for
 * the same reason: a dedicated server has no rendering classes to load.
 */
public class WarTableSnapshotPacket {

    private final WarTableSnapshot snapshot;

    public WarTableSnapshotPacket(WarTableSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public WarTableSnapshot snapshot() {
        return this.snapshot;
    }

    public static void encode(WarTableSnapshotPacket packet, FriendlyByteBuf buffer) {
        packet.snapshot.write(buffer);
    }

    public static WarTableSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new WarTableSnapshotPacket(WarTableSnapshot.read(buffer));
    }

    public static void handle(WarTableSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> WarTableClient.show(packet.snapshot)));

        context.setPacketHandled(true);
    }
}
