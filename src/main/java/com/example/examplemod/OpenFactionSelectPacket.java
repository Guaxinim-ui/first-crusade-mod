package com.example.examplemod;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server -> client trigger that tells a freshly joined player (who has not picked a side yet) to
 * open the faction selection screen. It carries no data — it is purely the "choose your allegiance"
 * signal. The client-only opening is routed through {@link FactionSelectClient} so the server never
 * class-loads the screen.
 */
public class OpenFactionSelectPacket {
    public OpenFactionSelectPacket() {
    }

    public static void encode(OpenFactionSelectPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenFactionSelectPacket decode(FriendlyByteBuf buffer) {
        return new OpenFactionSelectPacket();
    }

    public static void handle(OpenFactionSelectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> FactionSelectClient::open));

        context.setPacketHandled(true);
    }
}
