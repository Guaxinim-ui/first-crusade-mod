package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class StrategicCoreMessageBus {
    private StrategicCoreMessageBus() {
    }

    public static void sendToOpenCore(ServerLevel level, BlockPos corePos, Component message) {
        for (ServerPlayer player : level.players()) {
            if (!(player.containerMenu instanceof ImperialCommandCoreMenu menu)) {
                continue;
            }

            if (!menu.getCommandCorePos().equals(corePos)) {
                continue;
            }

            player.displayClientMessage(message, false);
        }
    }

    public static void sendToNearestOpenCore(ServerLevel level, BlockPos eventPos, Component message) {
        ServerPlayer bestPlayer = null;
        double bestDistance = Double.MAX_VALUE;

        for (ServerPlayer player : level.players()) {
            if (!(player.containerMenu instanceof ImperialCommandCoreMenu menu)) {
                continue;
            }

            double distance = menu.getCommandCorePos().distSqr(eventPos);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestPlayer = player;
            }
        }

        if (bestPlayer != null) {
            bestPlayer.displayClientMessage(message, false);
        }
    }
}