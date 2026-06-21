package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Tracks the planet-wide balance of the war and declares a global triumph when one side dominates.
 * War events nudge the score: razing an Ork camp or the Crusade rising push toward the Imperium; war
 * parties and a rising WAAAGH! push toward the Orks. Crossing the threshold announces a triumph; the
 * war can swing back through contention, after which a fresh triumph can be declared.
 */
public final class WarDominionManager {
    private static final int TRIUMPH_THRESHOLD = 50;
    private static final int CONTESTED_RESET = 15;

    private WarDominionManager() {
    }

    public static void shift(ServerLevel level, int amount) {
        WarDominionData data = WarDominionData.get(level);
        data.add(amount);
        evaluate(level, data);
    }

    private static void evaluate(ServerLevel level, WarDominionData data) {
        int dominion = data.getDominion();
        int announced = data.getAnnouncedSide();

        if (dominion >= TRIUMPH_THRESHOLD && announced <= 0) {
            data.setAnnouncedSide(1);
            broadcast(level, Component.translatable("msg.firstcrusade.victory.imperium"));
        } else if (dominion <= -TRIUMPH_THRESHOLD && announced >= 0) {
            data.setAnnouncedSide(-1);
            broadcast(level, Component.translatable("msg.firstcrusade.victory.waaagh"));
        } else if (Math.abs(dominion) < CONTESTED_RESET && announced != 0) {
            // The war is in the balance again — the planet is contested, a new triumph can be earned.
            data.setAnnouncedSide(0);
            broadcast(level, Component.translatable("msg.firstcrusade.victory.contested"));
        }
    }

    private static void broadcast(ServerLevel level, Component message) {
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }
}
