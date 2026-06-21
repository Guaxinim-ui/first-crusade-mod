package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * The Imperial Crusade Overlord — the Imperium's strategic war mind, mirror of
 * {@link WaaaghOverlordManager}. Imperial prosperity feeds a global Crusade tier (0-4); the higher
 * the tier, the heavier the reinforcements the Crusade dispatches to threatened cities. The Command
 * Core reads {@link #getTier} when calling reinforcements. See {@link ImperiumOverlordData}.
 */
public final class ImperiumOverlordManager {
    private static final Component[] TIER_CRIES = {
            Component.empty(),
            Component.translatable("msg.firstcrusade.crusade.tier1"),
            Component.translatable("msg.firstcrusade.crusade.tier2"),
            Component.translatable("msg.firstcrusade.crusade.tier3"),
            Component.translatable("msg.firstcrusade.crusade.tier4")
    };

    private ImperiumOverlordManager() {
    }

    public static int getTier(ServerLevel level) {
        return ImperiumOverlordData.get(level).getTier();
    }

    public static int getCrusade(ServerLevel level) {
        return ImperiumOverlordData.get(level).getCrusade();
    }

    // Each prosperous Imperial city expands the Crusade; the mightier the Imperium, the heavier the
    // reinforcements it can muster. Announces when the world crosses into a new Crusade tier.
    public static void contributeFromCity(ServerLevel level, ImperialCommandCoreBlockEntity core) {
        ImperiumOverlordData data = ImperiumOverlordData.get(level);
        data.add(core.getCityLevel());

        int tier = data.getTier();

        if (tier > data.getLastAnnouncedTier()) {
            data.setLastAnnouncedTier(tier);
            announce(level, tier);
        }
    }

    private static void announce(ServerLevel level, int tier) {
        if (tier <= 0 || tier >= TIER_CRIES.length) {
            return;
        }

        level.getServer().getPlayerList().broadcastSystemMessage(TIER_CRIES[tier], false);
        WarDominionManager.shift(level, 10);
    }
}
