package com.example.examplemod;

import net.minecraft.client.Minecraft;

/**
 * Client-only entry point for opening the faction selection screen. Kept in its own class so the
 * dedicated server never touches client code: {@link OpenFactionSelectPacket} reaches it only via
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)}.
 */
public final class FactionSelectClient {
    private FactionSelectClient() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new FactionSelectScreen());
    }
}
