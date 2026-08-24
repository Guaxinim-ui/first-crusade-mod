package com.example.examplemod.campaign;

import java.util.List;

import javax.annotation.Nullable;

import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.campaign.operation.OperationManager;
import com.example.examplemod.campaign.planet.PlanetCampaignState;
import com.example.examplemod.campaign.planet.PlanetWarState;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.war.CrusadeScore;
import com.example.examplemod.campaign.war.WarFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The war that runs whether or not anyone is watching.
 *
 * <h2>What one pass costs</h2>
 *
 * A pass over one front is: a walk over that front's sectors (a dozen), and for each of them a walk
 * over that front's settlements from the war map (tens, and only the ones on that planet now that
 * the map is bucketed). No entity query, no chunk load, no pathfinding, no block placement. That is
 * why it can run for every activated front rather than only the one the player is standing on —
 * which is the whole point, because a war that only happens where the player is looking is not a war
 * they are a part of.
 *
 * <h2>Loaded fronts and unloaded ones</h2>
 *
 * {@link #tick} runs against the server, not a level, and iterates fronts rather than levels. A
 * front whose dimension is not loaded is still simulated: its settlements are on the war map, its
 * sectors are in {@link CampaignData}, and neither needs the level. What a loaded front gets extra
 * is activation (which needs a spawn position) and orphan pruning (which needs loaded chunks).
 *
 * <p>The one thing this class must never do is materialise anything. Absorbing and restoring
 * armies is {@code FCStrategicBattleData}'s job and it already does it properly; the campaign only
 * moves ownership around.
 */
public final class PlanetCampaignManager {

    private PlanetCampaignManager() {
    }

    private static int countdown;

    /**
     * The campaign's entire entry point. Called once per server tick; does nothing at all on the
     * vast majority of them.
     */
    public static void tick(MinecraftServer server) {
        if (!CampaignConfig.enabled()) {
            return;
        }

        if (--countdown > 0) {
            return;
        }

        countdown = CampaignConfig.strategicIntervalTicks();

        runStrategicPass(server);
    }

    /** One full pass over every front. Public so {@code /fcstrategy war tick} can step it by hand. */
    public static void runStrategicPass(MinecraftServer server) {
        ServerLevel overworld = server.overworld();

        CampaignData campaign = CampaignData.get(overworld);
        WorldWarMapData warMap = WorldWarMapData.get(overworld);

        long gameTime = overworld.getGameTime();

        for (CampaignFront front : CampaignFronts.all()) {
            ServerLevel level = server.getLevel(front.dimension());

            tickFront(overworld, campaign, warMap, front, level, gameTime);
        }

        // Logistics after the fronts: a lane's state is a question about who holds which spaceport,
        // so asking it before the fronts are up to date would answer about last pass's war.
        com.example.examplemod.campaign.supply.SupplyNetwork.recompute(campaign);

        // Convoys after the lanes, for the same reason one step further along: a convoy is dispatched
        // because a lane is cut, so it has to be asked after this pass decided which ones are.
        com.example.examplemod.campaign.convoy.ConvoyManager.tick(server, campaign, gameTime);

        // The galaxy-wide picture is a function of the fronts, so it is recomputed here — after
        // them, and only here.
        CrusadeScore.recompute(overworld, campaign);

        // One rebuild for the whole save, after every front has issued and retired its orders.
        OperationManager.refreshWatch(campaign);

        campaign.setDirty();
    }

    private static void tickFront(ServerLevel overworld, CampaignData campaign, WorldWarMapData warMap,
                                  CampaignFront front, @Nullable ServerLevel level, long gameTime) {
        PlanetWarState state = campaign.stateOf(front);

        // Activation needs a real level: the layout is centred on the front's spawn, and a front
        // nobody has ever visited has no reason to exist on the map yet.
        if (!state.sectorsLaidOut()) {
            if (level == null || !CampaignConfig.autoActivateFronts() || level.players().isEmpty()) {
                return;
            }

            campaign.activateFront(front, level.getSharedSpawnPos());
        }

        List<StrategicSector> sectors = campaign.sectorsOn(front.dimension());

        if (sectors.isEmpty()) {
            return;
        }

        applySettlementPressure(campaign, warMap, front, sectors, gameTime);

        if (level != null && !level.players().isEmpty()) {
            advanceNecronAwakening(front, state, gameTime);
        }

        // Applied whether or not anyone is standing there: once the tomb is awake it keeps taking
        // its planet back, and coming home to find the landing zone gone is the point.
        applyNecronAwakeningPressure(campaign, front, state, sectors, gameTime);

        PlanetCampaignState previous = state.recompute(sectors, front);
        state.refreshObjective(sectors);

        if (previous != null) {
            announceStateChange(front, state, previous, gameTime);
        }

        // Forces move before orders are issued, so an order is chosen against a front whose sectors
        // already reflect this pass's fighting rather than last pass's.
        com.example.examplemod.campaign.force.DeploymentManager.tickFront(
                campaign, front, level, gameTime);

        com.example.examplemod.campaign.war.OrkOffensiveManager.tickFront(
                campaign, warMap, front, level, sectors, gameTime);

        // Orders last, so they are issued against a front whose control has already been brought up
        // to date by everything above.
        OperationManager.tickFront(overworld, campaign, front, level, sectors, gameTime);
    }

    /**
     * Moves each sector's contest score by who has settlements near it.
     *
     * <p>This is what makes a planet's map move on its own. A city projects Imperial pressure, a camp
     * projects Ork pressure, both fall off with distance, and the sector's own
     * {@link com.example.examplemod.campaign.sector.SectorType#defence()} divides the result — so
     * a fortress under the same pressure as an artillery position takes six times as long to fall.
     *
     * <p>A sector with force from both sides on it is marked disputed, which is the "contested"
     * slice of the planet's control and the input the war intensity is read from.
     */
    private static void applySettlementPressure(CampaignData campaign, WorldWarMapData warMap,
                                                CampaignFront front, List<StrategicSector> sectors,
                                                long gameTime) {
        int radius = CampaignConfig.settlementInfluenceRadius();
        long radiusSqr = (long) radius * radius;
        int perSettlement = CampaignConfig.pressurePerSettlement();
        double speed = CampaignConfig.warSpeed();

        // Read once per front, not once per sector.
        var cities = warMap.getCities(front.dimension());
        var camps = warMap.getCamps(front.dimension());

        for (StrategicSector sector : sectors) {
            BlockPos at = sector.pos();

            double imperial = weighted(cities, at, radiusSqr, radius);
            double ork = weighted(camps, at, radiusSqr, radius);

            // A sector its own side already garrisons holds itself: without this a planet with no
            // enemy on it would slowly drift to neutral, and Macragge would decay for no reason.
            //
            // The enemy branch is written against isEnemyOfImperium rather than against ORKS so
            // that Necron-held ground defends itself too. With an ORKS-only test, every sector on
            // the tomb world had no holder at all as far as this pass was concerned, and a single
            // Imperial city in range would have walked the whole planet off the Necrons without a
            // shot — a faction that has no units yet losing a war it is not fighting.
            if (sector.owner() == WarFaction.IMPERIUM) {
                imperial += 0.35D;
            } else if (sector.owner().isEnemyOfImperium()) {
                ork += 0.35D;
            }

            sector.setDisputed(imperial > 0.0D && ork > 0.0D);

            int pressure = (int) Math.round((imperial - ork) * perSettlement * speed);
            WarFaction previousOwner = sector.applyPressure(pressure, gameTime);

            if (previousOwner != null) {
                announceCapture(campaign, front, sector, previousOwner, gameTime);
            }
        }
    }

    /**
     * Total influence of a set of settlements on one point, each falling off linearly to nothing at
     * the influence radius.
     *
     * <p>Linear rather than inverse-square on purpose: inverse-square makes a settlement one block
     * outside a sector infinitely more important than one two blocks outside it, which produces a
     * map where a single building decides a planet.
     */
    private static double weighted(Iterable<Long> packedPositions, BlockPos at, long radiusSqr, int radius) {
        double total = 0.0D;

        for (long packed : packedPositions) {
            BlockPos pos = BlockPos.of(packed);
            double distanceSqr = pos.distSqr(at);

            if (distanceSqr > radiusSqr) {
                continue;
            }

            total += 1.0D - Math.sqrt(distanceSqr) / radius;
        }

        return total;
    }

    /**
     * What the awakening actually does (§26).
     *
     * <h2>Necrons without a single model</h2>
     *
     * The awakening was a number from 0 to 100 with five named stages, and crossing one wrote a log
     * line. Nothing else read it. The entities it is eventually for do not exist and cannot be
     * invented here — but a faction does not need bodies to take ground, and this planet's map
     * already has {@link WarFaction#NECRONS} holding six of its seven sectors.
     *
     * <p>So a waking tomb pushes on its own planet exactly the way a settlement pushes: through
     * {@link StrategicSector#applyPressure}, the same call, divided by the same sector defence. The
     * Imperial landing zone stops being a safe corner the moment the tomb reaches WARRIORS, and a
     * player who leaves the tomb world alone long enough loses their foothold on it. Nothing spawns;
     * the planet simply stops being theirs.
     *
     * <p>{@code applySettlementPressure} was already written against {@code isEnemyOfImperium}
     * rather than against ORKS so that Necron ground would defend itself — the seam for this was
     * left open on purpose, and this is the other half of it.
     */
    private static void applyNecronAwakeningPressure(CampaignData campaign, CampaignFront front,
                                                     PlanetWarState state, List<StrategicSector> sectors,
                                                     long gameTime) {
        if (!front.dimension().equals(com.example.examplemod.planet.FCPlanets.NECRON_TOMB_WORLD)) {
            return;
        }

        PlanetWarState.NecronStage stage = state.necronStage();

        // Below WARRIORS the tomb is stirring, not fighting. Scarabs do not take a landing pad.
        if (stage.ordinal() < PlanetWarState.NecronStage.WARRIORS.ordinal()) {
            return;
        }

        // One step per stage above SCARABS, so the pressure grows as the tomb does rather than
        // arriving all at once at the top.
        int pressure = (stage.ordinal() - PlanetWarState.NecronStage.SCARABS.ordinal())
                * CampaignConfig.pressurePerSettlement();

        for (StrategicSector sector : sectors) {
            if (sector.owner() == WarFaction.NECRONS) {
                continue;
            }

            WarFaction previous = sector.applyPressure(-pressure, gameTime, WarFaction.NECRONS);

            if (previous != null) {
                announceCapture(campaign, front, sector, previous, gameTime);
            }
        }
    }

    private static void advanceNecronAwakening(CampaignFront front, PlanetWarState state, long gameTime) {
        if (!front.dimension().equals(com.example.examplemod.planet.FCPlanets.NECRON_TOMB_WORLD)) {
            return;
        }

        List<PlanetWarState.NecronStage> crossed =
                state.raiseNecronAwakening(CampaignConfig.necronAwakeningPerPass());

        for (PlanetWarState.NecronStage stage : crossed) {
            // Nothing is spawned. The Necrons have no entities yet; what this does is record the
            // stage so the systems that will read it already have a history to read.
            CampaignLog.war("{} necron awakening reached {} ({}%)",
                    front.path(), stage.name(), stage.threshold());

            state.recordEvent("Necron awakening: " + stage.name(), gameTime);
        }
    }

    // ====================================================================================
    // Capture
    // ====================================================================================

    /**
     * Hands a sector to a faction outright and reports it. For the debug command, for scripted
     * objectives, and for whatever later slice ties a razed camp to the ground it sat on.
     *
     * @return true when this actually changed the owner
     */
    public static boolean captureSector(ServerLevel level, StrategicSector sector, WarFaction faction) {
        CampaignData campaign = CampaignData.get(level);
        long gameTime = level.getGameTime();

        WarFaction previous = sector.setOwner(faction, gameTime);

        if (previous == null) {
            return false;
        }

        CampaignFronts.byDimension(sector.dimension())
                .ifPresent(front -> {
                    announceCapture(campaign, front, sector, previous, gameTime);

                    List<StrategicSector> sectors = campaign.sectorsOn(front.dimension());
                    PlanetWarState state = campaign.stateOf(front);

                    PlanetCampaignState before = state.recompute(sectors, front);
                    state.refreshObjective(sectors);

                    if (before != null) {
                        announceStateChange(front, state, before, gameTime);
                    }
                });

        campaign.setDirty();
        return true;
    }

    /**
     * Recomputes one front's control, state and objective right now.
     *
     * <p>For events that changed a sector outside the strategic pass — a camp razed, a city lost.
     * Without it the planet's percentages would be a pass out of date at exactly the moment a player
     * is looking at them to see whether what they just did mattered.
     */
    public static void refreshFront(ServerLevel level, ResourceKey<Level> dimension) {
        refreshFront(level, dimension, CampaignData.get(level));
    }

    /**
     * The same, for a caller that already has the campaign in hand.
     *
     * <p>The level may be null — a front whose dimension is not loaded still has to be recomputed,
     * because its war runs whether or not anyone is standing on it. Only the log timestamp needs a
     * level, and that falls back to the campaign's own clock.
     */
    public static void refreshFront(@Nullable ServerLevel level, ResourceKey<Level> dimension,
                                    CampaignData campaign) {
        CampaignFronts.byDimension(dimension).ifPresent(front -> {
            PlanetWarState state = campaign.stateOf(front);

            List<StrategicSector> sectors = campaign.sectorsOn(dimension);

            PlanetCampaignState previous = state.recompute(sectors, front);
            state.refreshObjective(sectors);

            if (previous != null) {
                announceStateChange(front, state, previous,
                        level == null ? state.lastEventTime() : level.getGameTime());
            }

            campaign.setDirty();
        });
    }

    private static void announceCapture(CampaignData campaign, CampaignFront front,
                                        StrategicSector sector, WarFaction previous, long gameTime) {
        CampaignLog.war("{} sector {} changed {} -> {}",
                front.path(), sector.type().name(), previous.name(), sector.owner().name());

        campaign.stateOf(front).recordEvent(
                sector.type().name() + ": " + previous.name() + " -> " + sector.owner().name(),
                gameTime);
    }

    private static void announceStateChange(CampaignFront front, PlanetWarState state,
                                            PlanetCampaignState previous, long gameTime) {
        CampaignLog.war("{} campaign state {} -> {} (imperial {}%, enemy {}%, contested {}%)",
                front.path(), previous.name(), state.state().name(),
                state.imperialControl(), state.enemyControl(), state.contestedControl());

        state.recordEvent(previous.name() + " -> " + state.state().name(), gameTime);
    }

    // ====================================================================================
    // Arrival
    // ====================================================================================

    /**
     * Called when a traveller lands on a front: lays its sectors out if this is the first arrival.
     *
     * <p>Separate from the tick because arrival is the moment a front becomes real, and waiting up
     * to ten seconds for the next pass would mean landing on a planet the War Table says nothing
     * about.
     */
    public static void onArrival(ServerLevel level, BlockPos landing) {
        if (!CampaignConfig.enabled()) {
            return;
        }

        CampaignFronts.byDimension(level.dimension()).ifPresent(front -> {
            CampaignData campaign = CampaignData.get(level);
            PlanetWarState state = campaign.stateOf(front);

            if (state.sectorsLaidOut()) {
                return;
            }

            // The front's own spawn, not the landing site: a second traveller landing far away must
            // not produce a layout centred somewhere else. The landing is only the fallback for a
            // world whose spawn has not been resolved.
            BlockPos anchor = level.getSharedSpawnPos();
            campaign.activateFront(front, anchor.equals(BlockPos.ZERO) ? landing : anchor);

            List<StrategicSector> sectors = campaign.sectorsOn(front.dimension());
            state.recompute(sectors, front);
            state.refreshObjective(sectors);

            campaign.setDirty();
        });
    }
}
