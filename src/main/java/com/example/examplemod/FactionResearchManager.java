package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Drives the Imperium's paid age-up research. The player funds and starts a research at a
 * {@link StrategiumBlock} bench; this manager counts it down globally (so it progresses even while
 * the player roams) and, on completion, advances the Crusade Age — the player-driven counterpart to
 * the Crusade/WAAAGH! that otherwise creep up on their own from prosperity.
 *
 * "Age" here is the Crusade tier (0-4) from {@link ImperiumOverlordData}. The Orks (WAAAGH!) advance
 * automatically (no player to research for them); the bench shows their Age for reference.
 */
public final class FactionResearchManager {
    public static final int MAX_TIER = 4;
    private static final int TICK_INTERVAL = 20; // count down once per second

    private FactionResearchManager() {
    }

    // The resources a research costs to advance FROM the given current tier to the next.
    public static int researchIronCost(int currentTier) {
        return 200 + currentTier * 200; // 200 / 400 / 600 / 800
    }

    public static int researchScrapCost(int currentTier) {
        return 80 + currentTier * 80; // 80 / 160 / 240 / 320
    }

    // How long the research takes (ticks) — longer for the higher Ages.
    public static int researchDurationTicks(int currentTier) {
        return 2400 + currentTier * 1200; // 2 / 4 / 6 / 8 minutes
    }

    public static void tick(ServerLevel overworld) {
        if (overworld.getGameTime() % TICK_INTERVAL != 0) {
            return;
        }

        FactionResearchData research = FactionResearchData.get(overworld);
        if (!research.isActive()) {
            return;
        }

        if (research.countDown(TICK_INTERVAL)) {
            complete(overworld, research.getTargetTier());
        }
    }

    /**
     * Brings a running research forward by the given number of ticks, completing it properly if that
     * finishes it.
     *
     * <p>Exists so nothing outside this class calls {@link FactionResearchData#countDown} directly.
     * That method deactivates the research when it reaches zero and returns true to say so — a
     * caller that ignored the return would silently cancel the research instead of finishing it, and
     * the player would watch the bench go blank without ever gaining the Age they paid for.
     *
     * @return true when this completed the research
     */
    public static boolean accelerate(ServerLevel overworld, int ticks) {
        if (ticks <= 0) {
            return false;
        }

        FactionResearchData research = FactionResearchData.get(overworld);

        if (!research.isActive()) {
            return false;
        }

        int targetTier = research.getTargetTier();

        if (!research.countDown(ticks)) {
            return false;
        }

        complete(overworld, targetTier);
        return true;
    }

    private static void complete(ServerLevel overworld, int targetTier) {
        // The breakthrough lifts the Crusade Age to the researched tier.
        ImperiumOverlordData.get(overworld).ensureAtLeastTier(targetTier);

        overworld.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("msg.firstcrusade.research.complete", targetTier),
                false
        );
        WarDominionManager.shift(overworld, 8);
    }
}
