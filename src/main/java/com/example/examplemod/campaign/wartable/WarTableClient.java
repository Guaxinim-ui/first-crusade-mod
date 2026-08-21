package com.example.examplemod.campaign.wartable;

import com.example.examplemod.campaign.wartable.client.WarTableScreen;

import net.minecraft.client.Minecraft;

/**
 * The client half of the War Table, kept in its own class so the server never loads a screen.
 *
 * <p>{@link WarTableSnapshotPacket} reaches this only through {@code DistExecutor}, which is what
 * stops a dedicated server from class-loading {@link WarTableScreen} and everything in
 * {@code net.minecraft.client} behind it.
 */
public final class WarTableClient {

    private WarTableClient() {
    }

    /**
     * Shows the war.
     *
     * <p>A snapshot arriving while the table is already open <b>updates</b> it in place rather than
     * opening a second screen: that is the refresh path, and reopening would throw away which front
     * the player had selected and scroll them back to the top.
     */
    public static void show(WarTableSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof WarTableScreen open) {
            open.refresh(snapshot);
            return;
        }

        minecraft.setScreen(new WarTableScreen(snapshot));
    }
}
