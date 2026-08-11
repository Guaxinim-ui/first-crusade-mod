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

    /**
     * A greenskin victory big enough for the whole world to feel.
     *
     * <p>Added here rather than in a second SavedData of the WAAAGH's own: there is one global tide
     * and it lives in {@link WaaaghOverlordData}. A player-side copy would be a second number with
     * the same name, and this mod already has three of those.
     *
     * <p>Only called for events on the scale of a city falling. A kill or a blow must never reach
     * this — the tide would stop meaning "how the war is going" and start meaning "how long somebody
     * has been playing".
     */
    public static void contributeFromGreenskinVictory(ServerLevel level, int amount) {
        if (amount <= 0) {
            return;
        }

        WaaaghOverlordData data = WaaaghOverlordData.get(level);
        data.add(amount);

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
