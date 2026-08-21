package com.example.examplemod.campaign.operation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.example.examplemod.ImperialCommandCoreBlockEntity;
import com.example.examplemod.ImperialResourceType;
import com.example.examplemod.WarDominionManager;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.campaign.CampaignConfig;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.CampaignFront;
import com.example.examplemod.campaign.CampaignLog;
import com.example.examplemod.campaign.PlanetCampaignManager;
import com.example.examplemod.campaign.planet.PlanetWarState;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.war.WarFaction;
import com.example.examplemod.campaign.war.WarIntensity;
import com.example.examplemod.progression.PlayerProgressionManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Issues the Crusade's orders, watches for them being carried out, and pays for them.
 *
 * <h2>Orders come out of the war, not out of a bag</h2>
 *
 * {@link #maybeIssue} reads the front it is called for — who holds the most valuable enemy sector,
 * whether anything is disputed, how hard it is being fought — and picks the order that situation
 * calls for. A front the Imperium is losing gets DEFEND on the sector about to fall; one with an
 * intact Warboss gets ASSASSINATION; a quiet held world gets RECON. Nothing is random except the
 * tie-break between equally appropriate orders.
 *
 * <h2>Watching costs nothing when nothing is being watched</h2>
 *
 * The expensive-looking hook is {@link #onEnemyKilled}, which runs from {@code LivingDeathEvent} —
 * every death of every mob on the server. It answers in one hash lookup against
 * {@link #frontsWatchingKills}, a set that is <b>empty</b> unless a kill-driven order is actually
 * standing somewhere. No SavedData is touched, no list is walked, and the common case is a miss on
 * an empty set.
 *
 * <p>The set is rebuilt on each strategic pass, which is also the only place it can go stale — by at
 * most one pass, and in the harmless direction: a kill counted a few seconds after an order ended,
 * or one missed a few seconds after it began.
 */
public final class OperationManager {

    private OperationManager() {
    }

    /**
     * Fronts with a live kill-driven order. See the class note: this exists so the death handler
     * can decline in one lookup.
     */
    private static final Set<ResourceLocation> frontsWatchingKills = new HashSet<>();

    // ====================================================================================
    // The strategic pass
    // ====================================================================================

    /**
     * One front's worth of operation upkeep: resolve what the world has already done, retire what
     * has run out of time, and issue a new order if there is room for one.
     *
     * <p>Called from {@link PlanetCampaignManager}'s pass, so it inherits its interval and never
     * runs on its own clock.
     */
    public static void tickFront(ServerLevel overworld, CampaignData campaign, CampaignFront front,
                                 @Nullable ServerLevel level, List<StrategicSector> sectors,
                                 long gameTime) {
        List<Operation> active = campaign.activeOperationsOn(front.id());

        for (Operation operation : active) {
            resolvePolled(overworld, campaign, front, operation, level, gameTime);
        }

        // Re-read: resolvePolled may have finished some.
        active = campaign.activeOperationsOn(front.id());

        for (Operation operation : active) {
            if (!operation.isExpired(gameTime)) {
                continue;
            }

            // DEFEND is the one order the clock COMPLETES rather than kills: holding until the
            // deadline is the whole objective. Everything else that reaches its deadline unfinished
            // has failed.
            if (operation.trigger() == OperationTrigger.SECTOR_HELD) {
                if (stillHeld(campaign, operation)) {
                    // complete() FIRST. Without it the order stays ACTIVE while past its deadline,
                    // so the next pass finds it here again, holds the sector again, and pays again —
                    // every ten seconds, for as long as the sector is held.
                    operation.complete(gameTime);
                    finish(overworld, campaign, front, operation, level, gameTime);
                } else {
                    // The sector was lost. That is a defeat, not an order nobody took up.
                    operation.fail(gameTime);
                    reportUnfinished(level, front, operation);
                }

                continue;
            }

            operation.expire(gameTime);
            reportUnfinished(level, front, operation);
        }

        campaign.retireFinishedOperations(front.id(), gameTime);
        maybeIssue(overworld, campaign, front, sectors, gameTime);
    }

    private static void reportUnfinished(@Nullable ServerLevel level, CampaignFront front,
                                         Operation operation) {
        announce(level, Component.translatable("msg.firstcrusade.operation.expired",
                operation.type().displayName()).withStyle(ChatFormatting.GRAY));

        CampaignLog.war("{} operation {} ended unfinished ({})",
                front.path(), operation.type().name(), operation.state().name());
    }

    /** Advances the orders whose progress is a question about the world rather than an event. */
    private static void resolvePolled(ServerLevel overworld, CampaignData campaign,
                                      CampaignFront front, Operation operation,
                                      @Nullable ServerLevel level, long gameTime) {
        if (!operation.trigger().isPolled()) {
            return;
        }

        switch (operation.trigger()) {
            case SECTOR_TAKEN -> {
                StrategicSector target = campaign.sector(operation.targetSectorId());

                if (target != null && target.owner() == WarFaction.IMPERIUM
                        && operation.complete(gameTime)) {
                    finish(overworld, campaign, front, operation, level, gameTime);
                }
            }

            case VISIT_SECTOR -> {
                StrategicSector target = campaign.sector(operation.targetSectorId());

                if (level != null && target != null && anyPlayerNear(level, target.pos(), 64.0D)
                        && operation.complete(gameTime)) {
                    finish(overworld, campaign, front, operation, level, gameTime);
                }
            }

            case TIME_ON_FRONT -> {
                // One tick of credit per pass with somebody actually standing on the front. A player
                // who logs off mid-order simply stops earning it rather than failing it.
                if (level != null && !level.players().isEmpty() && operation.advance(1, gameTime)) {
                    finish(overworld, campaign, front, operation, level, gameTime);
                }
            }

            default -> {
                // Not polled; handled by an event.
            }
        }
    }

    private static boolean stillHeld(CampaignData campaign, Operation operation) {
        StrategicSector target = campaign.sector(operation.targetSectorId());
        return target != null && target.owner() == WarFaction.IMPERIUM;
    }

    // ====================================================================================
    // Issuing
    // ====================================================================================

    /**
     * Issues one order for this front if it has room for another and the war gives it something to
     * ask for.
     */
    private static void maybeIssue(ServerLevel overworld, CampaignData campaign, CampaignFront front,
                                   List<StrategicSector> sectors, long gameTime) {
        if (sectors.isEmpty()) {
            return;
        }

        PlanetWarState state = campaign.stateOf(front);

        if (!state.state().isEngaged()) {
            return;
        }

        if (campaign.activeOperationsOn(front.id()).size() >= CampaignConfig.maxOperationsPerFront()) {
            return;
        }

        RandomSource random = overworld.random;

        StrategicSector target = chooseTarget(sectors, state, random);
        OperationType type = chooseType(state, sectors, target, random);

        if (type == null) {
            return;
        }

        int weight = target == null ? 2 : target.importance();

        // A hotter front asks for more and pays more. The intensity multiplier is the same number the
        // raid layer reads, so "this planet is dangerous" means one thing across the campaign.
        double heat = state.intensity().raidRate();

        int required = Math.max(1, (int) Math.round(type.baseRequired() * heat));
        long expires = gameTime + Math.round(type.baseDurationTicks() * CampaignConfig.operationTimeScale());

        String id = front.path() + ".op." + gameTime + "." + random.nextInt(1000);

        Operation operation = new Operation(
                id,
                type,
                front.id(),
                type.needsTargetSector() && target != null ? target.id() : "",
                target == null ? null : target.type(),
                required,
                OperationReward.scaled(weight, type.rewardScale() * heat),
                gameTime,
                expires);

        campaign.addOperation(operation);

        CampaignLog.war("{} operation issued: {} x{} ({})",
                front.path(), type.name(), required,
                operation.hasTarget() ? operation.targetSectorId() : "front-wide");

        ServerLevel level = overworld.getServer().getLevel(front.dimension());

        announce(level, Component.translatable("msg.firstcrusade.operation.issued",
                type.displayName(), operation.describe()).withStyle(ChatFormatting.GOLD));
    }

    /**
     * What the order is about: the most valuable sector an enemy still holds, or — when the front is
     * clean — an Imperial one under pressure, or nothing.
     */
    @Nullable
    private static StrategicSector chooseTarget(List<StrategicSector> sectors, PlanetWarState state,
                                                RandomSource random) {
        StrategicSector enemyHeld = PlanetWarState.primaryTarget(sectors);

        if (enemyHeld != null) {
            return enemyHeld;
        }

        List<StrategicSector> threatened = new ArrayList<>();

        for (StrategicSector sector : sectors) {
            if (sector.owner() == WarFaction.IMPERIUM && sector.isDisputed()) {
                threatened.add(sector);
            }
        }

        if (!threatened.isEmpty()) {
            return threatened.get(random.nextInt(threatened.size()));
        }

        // Nothing contested at all: pick any sector, so a quiet world can still offer RECON.
        return sectors.isEmpty() ? null : sectors.get(random.nextInt(sectors.size()));
    }

    /** The order that this front's situation actually calls for. */
    @Nullable
    private static OperationType chooseType(PlanetWarState state, List<StrategicSector> sectors,
                                            @Nullable StrategicSector target, RandomSource random) {
        if (target == null) {
            return null;
        }

        boolean enemyHolds = target.owner().isEnemyOfImperium();

        // The seat of enemy power is worth naming as its own kind of order.
        if (enemyHolds && target.type().isEnemySeat()) {
            return random.nextBoolean() ? OperationType.ASSASSINATION : OperationType.CAPTURE;
        }

        if (enemyHolds) {
            // A front with camps on it can be told to raze them; one without can only be told to
            // take ground or break the enemy in the field.
            return switch (random.nextInt(3)) {
                case 0 -> OperationType.CAPTURE;
                case 1 -> OperationType.DESTROY;
                default -> OperationType.ASSAULT;
            };
        }

        if (target.isDisputed()) {
            return OperationType.DEFEND;
        }

        // Held and quiet. A dangerous world still asks the garrison to survive it; a safe one asks
        // for a patrol.
        if (state.intensity() == WarIntensity.TOTAL_WAR
                || state.intensity() == WarIntensity.HEAVY_FIGHTING) {
            return OperationType.SURVIVE;
        }

        return sectors.size() > 1 ? OperationType.RECON : null;
    }

    // ====================================================================================
    // Event hooks
    // ====================================================================================

    /**
     * An enemy died on a front. Advances ASSAULT and, for a leader, ASSASSINATION.
     *
     * <p>The set test comes first and is the only cost on the overwhelming majority of deaths — see
     * the class note.
     */
    public static void onEnemyKilled(ServerLevel level, boolean leader) {
        ResourceLocation frontId = level.dimension().location();

        if (!frontsWatchingKills.contains(frontId)) {
            return;
        }

        CampaignData campaign = CampaignData.get(level);
        ServerLevel overworld = level.getServer().overworld();
        long gameTime = level.getGameTime();

        for (Operation operation : campaign.activeOperationsOn(frontId)) {
            OperationTrigger trigger = operation.trigger();

            if (trigger == OperationTrigger.KILL_ENEMY
                    || (trigger == OperationTrigger.KILL_LEADER && leader)) {

                if (operation.advance(1, gameTime)) {
                    com.example.examplemod.campaign.CampaignFronts.byId(frontId).ifPresent(front ->
                            finish(overworld, campaign, front, operation, level, gameTime));
                }
            }
        }

        campaign.setDirty();
    }

    /** An Ork camp was razed on a front. Advances DESTROY. */
    public static void onCampRazed(ServerLevel level) {
        CampaignData campaign = CampaignData.get(level);
        ResourceLocation frontId = level.dimension().location();
        ServerLevel overworld = level.getServer().overworld();
        long gameTime = level.getGameTime();

        for (Operation operation : campaign.activeOperationsOn(frontId)) {
            if (operation.trigger() == OperationTrigger.RAZE_CAMP && operation.advance(1, gameTime)) {
                com.example.examplemod.campaign.CampaignFronts.byId(frontId).ifPresent(front ->
                        finish(overworld, campaign, front, operation, level, gameTime));
            }
        }

        campaign.setDirty();
    }

    /**
     * Rebuilds the death-handler's fast-path set from the orders currently standing.
     *
     * <p>Called once at the end of a strategic pass, not once per front: it walks every order in the
     * save, so calling it per front would repeat that walk ten times to reach the same answer.
     */
    public static void refreshWatch(CampaignData campaign) {
        refreshKillWatch(campaign);
    }

    private static void refreshKillWatch(CampaignData campaign) {
        frontsWatchingKills.clear();

        for (Operation operation : campaign.allOperations()) {
            if (operation.isActive() && operation.trigger().isDeathDriven()) {
                frontsWatchingKills.add(operation.frontId());
            }
        }
    }

    /** Clears the watch set when a server stops, so a second world in the same JVM starts clean. */
    public static void clearWatch() {
        frontsWatchingKills.clear();
    }

    // ====================================================================================
    // Payment
    // ====================================================================================

    /**
     * Completes an order and pays for it.
     *
     * <p>Every payment lands in something that already existed — see {@link OperationReward}. The
     * sector half is the one that matters most: an order that captures its target does so through
     * {@link PlanetCampaignManager#captureSector}, the same path the debug command and the war use,
     * so there is exactly one way ground changes hands.
     */
    private static void finish(ServerLevel overworld, CampaignData campaign, CampaignFront front,
                               Operation operation, @Nullable ServerLevel level, long gameTime) {
        OperationReward reward = operation.reward();

        CampaignLog.war("{} operation {} completed ({})",
                front.path(), operation.type().name(), reward.shortText());

        // Ground first: the reward for taking a sector is the sector.
        if (operation.type().capturesOnSuccess() && operation.hasTarget() && level != null) {
            StrategicSector target = campaign.sector(operation.targetSectorId());

            if (target != null) {
                PlanetCampaignManager.captureSector(level, target, WarFaction.IMPERIUM);
            }
        }

        if (reward.dominion() > 0) {
            WarDominionManager.shift(overworld, reward.dominion());
        }

        // Through the manager, never straight into the data: countDown deactivates a research it
        // finishes, and only the manager knows to grant the Age that finishing it was for.
        com.example.examplemod.FactionResearchManager.accelerate(overworld, reward.researchTicks());

        payLocal(level, operation, reward);

        campaign.stateOf(front).recordEvent(
                "Operação " + operation.type().name() + " concluída", gameTime);

        announce(level, Component.translatable("msg.firstcrusade.operation.completed",
                operation.type().displayName()).withStyle(ChatFormatting.GREEN));
    }

    /**
     * The half of the payout that needs somewhere on the front to land: resources and War Support
     * into the nearest Command Core, experience to whoever is actually there.
     *
     * <p>A front with no loaded level and no Core simply does not pay that half. The order still
     * completes and still pays the global half — dominion and research — because the war was still
     * won; what is skipped is a delivery with no address.
     */
    private static void payLocal(@Nullable ServerLevel level, Operation operation,
                                 OperationReward reward) {
        if (level == null) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (reward.xp() > 0) {
                PlayerProgressionManager.awardXp(player, reward.xp());
            }
        }

        ImperialCommandCoreBlockEntity core = nearestLoadedCore(level, operation);

        if (core == null) {
            return;
        }

        if (reward.iron() > 0) {
            core.receiveProducedResource(ImperialResourceType.IRON, reward.iron());
        }

        if (reward.scrap() > 0) {
            core.receiveProducedResource(ImperialResourceType.SCRAP, reward.scrap());
        }

        if (reward.warSupport() > 0) {
            core.addWarSupport(reward.warSupport());
        }
    }

    /**
     * The nearest Imperial Core to the order's target that is actually loaded.
     *
     * <p>Reads the war map for candidates — which is now bucketed by dimension, so this cannot pick
     * a Core on another planet — and only touches the world for ones whose chunk is already loaded.
     */
    @Nullable
    private static ImperialCommandCoreBlockEntity nearestLoadedCore(ServerLevel level,
                                                                    Operation operation) {
        CampaignData campaign = CampaignData.get(level);
        StrategicSector target = operation.hasTarget()
                ? campaign.sector(operation.targetSectorId())
                : null;

        BlockPos from = target != null ? target.pos() : level.getSharedSpawnPos();

        ImperialCommandCoreBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (long packed : WorldWarMapData.get(level).getCities(level)) {
            BlockPos pos = BlockPos.of(packed);

            if (!level.isLoaded(pos)) {
                continue;
            }

            double distance = pos.distSqr(from);

            if (distance >= bestDistance) {
                continue;
            }

            if (level.getBlockEntity(pos) instanceof ImperialCommandCoreBlockEntity core) {
                bestDistance = distance;
                best = core;
            }
        }

        return best;
    }

    // ====================================================================================
    // Shared
    // ====================================================================================

    private static boolean anyPlayerNear(ServerLevel level, BlockPos pos, double radius) {
        double radiusSqr = radius * radius;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                    <= radiusSqr) {
                return true;
            }
        }

        return false;
    }

    private static void announce(@Nullable ServerLevel level, Component message) {
        if (level == null) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    // ====================================================================================
    // By hand
    // ====================================================================================

    /**
     * Issues an order of a named type, ignoring both the per-front cap and whether the war calls for
     * it. For {@code /fcstrategy operation create}.
     *
     * @return the order, or null when the front has no sectors to aim it at, or the type has no
     *         wired trigger and so could never be finished
     */
    @Nullable
    public static Operation issueByHand(ServerLevel level, CampaignFront front, OperationType type) {
        if (!type.trigger().isWired()) {
            return null;
        }

        CampaignData campaign = CampaignData.get(level);
        List<StrategicSector> sectors = campaign.sectorsOn(front.dimension());

        if (sectors.isEmpty()) {
            return null;
        }

        PlanetWarState state = campaign.stateOf(front);
        long gameTime = level.getGameTime();

        StrategicSector target = chooseTarget(sectors, state, level.random);
        int weight = target == null ? 2 : target.importance();
        double heat = state.intensity().raidRate();

        Operation operation = new Operation(
                front.path() + ".op." + gameTime + ".manual",
                type,
                front.id(),
                type.needsTargetSector() && target != null ? target.id() : "",
                target == null ? null : target.type(),
                Math.max(1, (int) Math.round(type.baseRequired() * heat)),
                OperationReward.scaled(weight, type.rewardScale() * heat),
                gameTime,
                gameTime + Math.round(type.baseDurationTicks() * CampaignConfig.operationTimeScale()));

        campaign.addOperation(operation);
        refreshKillWatch(campaign);

        CampaignLog.war("{} operation issued by hand: {}", front.path(), operation.shortText());

        return operation;
    }

    /**
     * Completes an order outright and pays it, whatever its progress. For
     * {@code /fcstrategy operation complete}.
     *
     * @return false when there is no such active order
     */
    public static boolean completeByHand(ServerLevel level, String id) {
        CampaignData campaign = CampaignData.get(level);
        Operation operation = campaign.operation(id);
        long gameTime = level.getGameTime();

        if (operation == null || !operation.complete(gameTime)) {
            return false;
        }

        ServerLevel overworld = level.getServer().overworld();

        com.example.examplemod.campaign.CampaignFronts.byId(operation.frontId()).ifPresent(front ->
                finish(overworld, campaign, front, operation,
                        level.getServer().getLevel(front.dimension()), gameTime));

        campaign.setDirty();
        return true;
    }
}
