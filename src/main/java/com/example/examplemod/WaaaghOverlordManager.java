package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * The WAAAGH! Overlord — the strategic Ork mind. Imperial prosperity feeds a global WAAAGH! tier
 * (0-4); the higher the tier, the bigger and more frequent the Ork war parties everywhere become.
 * Ork Camps read {@link #getTier} to escalate their aggression. See {@link WaaaghOverlordData}.
 */
public final class WaaaghOverlordManager {
    private static final String[] TIER_CRIES = {
            "",
            "Distant drums echo: the Orks are gathering. (WAAAGH! Tier 1)",
            "The WAAAGH! is growing. Ork war parties swell. (Tier 2)",
            "A great WAAAGH! is rising across the land! (Tier 3)",
            "WAAAGH!!! The green tide engulfs the world! (Tier 4)"
    };

    private WaaaghOverlordManager() {
    }

    public static int getTier(ServerLevel level) {
        return WaaaghOverlordData.get(level).getTier();
    }

    public static int getWaaagh(ServerLevel level) {
        return WaaaghOverlordData.get(level).getWaaagh();
    }

    // Each prosperous Imperial city feeds the green tide; the mightier the Imperium, the bigger
    // the WAAAGH! grows in answer. Announces when the world crosses into a new tier.
    public static void contributeFromCity(ServerLevel level, ImperialCommandCoreBlockEntity core) {
        WaaaghOverlordData data = WaaaghOverlordData.get(level);
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

        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal(TIER_CRIES[tier]),
                false
        );
        WarDominionManager.shift(level, -10);
    }
}
