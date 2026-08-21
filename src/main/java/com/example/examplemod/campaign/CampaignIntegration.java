package com.example.examplemod.campaign;

import com.example.examplemod.campaign.operation.OperationManager;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.war.WarFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Where the physical war reaches the strategic one.
 *
 * <h2>One door, not a dozen</h2>
 *
 * The campaign has to hear about four things: a camp razed, a city lost, a settlement founded, and
 * an enemy killed. Each of those already happens somewhere in the mod, in code that has nothing to
 * do with the campaign and should not grow to know about it. So every one of them calls a single
 * method here, and this class is the only file that knows both halves.
 *
 * <h2>Why a razed camp is worth more than a strategic pass</h2>
 *
 * {@link PlanetCampaignManager}'s settlement pressure already moves a sector's contest score toward
 * whoever has bases near it, so razing a camp would eventually shift the map on its own — over
 * several minutes, as the camp's absence stops counting. That is correct for the slow war and wrong
 * for the moment: a player who has just fought through a camp should see the map move for it.
 *
 * <p>So a razing applies an immediate, one-off shove to the nearest sector on top of the ongoing
 * pressure. It is a shove and not a capture: a single camp does not hand over a Manufactorum, and
 * making it do so would mean the map could be won by clearing the easiest ground repeatedly.
 */
public final class CampaignIntegration {

    /** How far a razed camp or lost city reaches to find the sector it mattered to. */
    private static final double EVENT_REACH = 500.0D;

    /** One-off contest points a razing is worth, before the sector's defence divides it down. */
    private static final int RAZE_SHOVE = 45;

    /** One-off contest points a lost Imperial city is worth to the Orks. */
    private static final int CITY_LOSS_SHOVE = 60;

    private CampaignIntegration() {
    }

    // ====================================================================================
    // Settlements
    // ====================================================================================

    /**
     * An Ork camp has been destroyed. Shoves the nearest sector toward the Imperium and advances any
     * DESTROY order standing on this front.
     */
    public static void onCampRazed(ServerLevel level, BlockPos pos) {
        if (!CampaignConfig.enabled()) {
            return;
        }

        shove(level, pos, RAZE_SHOVE, "camp razed");
        OperationManager.onCampRazed(level);
    }

    /**
     * An Imperial city has fallen. Shoves the nearest sector toward the Orks.
     *
     * <p>No operation hook: losing a city does not advance an order, it is the thing orders exist to
     * prevent.
     */
    public static void onCityLost(ServerLevel level, BlockPos pos) {
        if (!CampaignConfig.enabled()) {
            return;
        }

        shove(level, pos, -CITY_LOSS_SHOVE, "city lost");
    }

    /**
     * An enemy died on a front. Advances ASSAULT, and ASSASSINATION when it was a leader.
     *
     * <p>Runs for every death on the server, so it must be cheap to decline —
     * {@link OperationManager#onEnemyKilled} answers on one lookup against a set that is empty
     * unless a kill-driven order is actually standing.
     */
    public static void onEnemyKilled(ServerLevel level, boolean leader) {
        if (!CampaignConfig.enabled()) {
            return;
        }

        OperationManager.onEnemyKilled(level, leader);
    }

    // ====================================================================================
    // Shared
    // ====================================================================================

    /**
     * Applies a one-off contest shove to the sector nearest a position.
     *
     * <p>Nothing happens when there is no sector within {@link #EVENT_REACH} — a camp razed in the
     * middle of nowhere is a camp razed in the middle of nowhere, and inventing a sector for it to
     * have mattered to would make the map lie.
     */
    private static void shove(ServerLevel level, BlockPos pos, int pressure, String because) {
        CampaignData campaign = CampaignData.get(level);

        StrategicSector sector = campaign.nearestSector(
                StrategicLocation.of(level, pos), EVENT_REACH);

        if (sector == null) {
            return;
        }

        WarFaction previous = sector.applyPressure(pressure, level.getGameTime());
        campaign.setDirty();

        if (previous == null) {
            return;
        }

        // The shove was enough to take the sector. Route the aftermath through the manager so the
        // planet's control, state and objective are all recomputed the one way they ever are.
        announceThroughManager(level, sector, previous, because);
    }

    private static void announceThroughManager(ServerLevel level, StrategicSector sector,
                                               WarFaction previous, String because) {
        CampaignLog.war("{} sector {} changed {} -> {} ({})",
                sector.dimension().location().getPath(), sector.type().name(),
                previous.name(), sector.owner().name(), because);

        // captureSector is a no-op when the owner already matches — applyPressure has just set it —
        // so recompute the front directly instead of asking it to change something twice.
        PlanetCampaignManager.refreshFront(level, sector.dimension());
    }
}
