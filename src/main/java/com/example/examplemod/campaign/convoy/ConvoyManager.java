package com.example.examplemod.campaign.convoy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.example.examplemod.ImperialCommandCoreBlockEntity;
import com.example.examplemod.campaign.CampaignConfig;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.CampaignFront;
import com.example.examplemod.campaign.CampaignFronts;
import com.example.examplemod.campaign.CampaignIntegration;
import com.example.examplemod.campaign.CampaignLog;
import com.example.examplemod.campaign.operation.OperationManager;
import com.example.examplemod.campaign.planet.PlanetWarState;
import com.example.examplemod.campaign.supply.SupplyRoute;
import com.example.examplemod.campaign.supply.SupplyState;
import com.example.examplemod.campaign.war.WarIntensity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runs the Crusade's relief convoys: when one leaves, what the war does to it on the way, and what
 * lands.
 *
 * <h2>A convoy is what the Imperium does when the arithmetic fails</h2>
 *
 * {@link com.example.examplemod.campaign.supply.SupplyNetwork} already decides, every pass, that a
 * lane is BLOCKED or DISRUPTED. Until now that was the end of the sentence — the player watched a
 * number go to zero and the only lever was retaking a spaceport on another planet. A convoy is the
 * second lever: the lane that failed forces one shipment through anyway, carrying several passes'
 * worth in one run, and that run can be defended or lost.
 *
 * <p>Dispatch is therefore keyed to failure, never to plenty. A healthy lane never sends one, because
 * a convoy down a lane that is already delivering is a free reward with no stakes attached.
 *
 * <h2>DESTROYED is not a blockade, and gets no convoy</h2>
 *
 * {@link SupplyState#DESTROYED} means the origin produces nothing. There is no cargo to escort and no
 * enemy to blame, and dispatching into it would put an order on the board telling the player to go
 * defend an empty warehouse. The distinction between "cut" and "empty" is one the supply layer
 * already went to the trouble of making; this reads it rather than flattening it.
 *
 * <h2>What one pass costs</h2>
 *
 * A walk over the live convoys — at most {@link CampaignConfig#maxLiveConvoys()}, which is a
 * single digit — plus one walk over the lane table when looking for something to dispatch. No world
 * access except the two places that genuinely need it: asking whether anyone is standing on the
 * destination front, and finding a Core to pay into on arrival.
 */
public final class ConvoyManager {

    private ConvoyManager() {
    }

    /**
     * Fronts with a convoy in the air. Exists so the death hook can decline in one lookup — the same
     * device {@link OperationManager} uses for kill-driven orders, and for the same reason: this runs
     * from {@code LivingDeathEvent}, on every death of every mob on the server.
     */
    private static final Set<ResourceLocation> frontsAwaitingConvoy = new HashSet<>();

    // ====================================================================================
    // The strategic pass
    // ====================================================================================

    /**
     * One pass: move what is in the air, dispatch at most one new run, sweep what has aged out.
     *
     * <p>Called from the strategic pass <b>after</b> the supply network has recomputed. Dispatch is a
     * question about which lanes are cut, so asking it first would be asking about last pass's war —
     * the same ordering rule the network itself is called under.
     */
    public static void tick(MinecraftServer server, CampaignData campaign, long gameTime) {
        if (CampaignConfig.maxLiveConvoys() <= 0) {
            // Convoys off. Anything already in the air is left exactly as it is rather than being
            // destroyed: turning a feature off must not delete a player's in-progress escort.
            frontsAwaitingConvoy.clear();
            return;
        }

        for (Convoy convoy : campaign.inTransitConvoys()) {
            advance(server, campaign, convoy, gameTime);
        }

        maybeDispatch(server, campaign, gameTime);

        campaign.retireFinishedConvoys(gameTime);
        refreshWatch(campaign);
    }

    /**
     * One convoy's pass: the war bites, then the clock is checked.
     *
     * <p>Attrition before arrival, deliberately. A convoy whose last pass takes it to zero on the same
     * tick it would have landed is lost — the run was never safe until it was over, and resolving
     * arrival first would make the final stretch free.
     */
    private static void advance(MinecraftServer server, CampaignData campaign, Convoy convoy,
                                long gameTime) {
        ServerLevel level = levelOf(server, convoy.destination());

        // Asked once and used twice: it halves this pass's bite, and it is what decides whether the
        // ESCORT order was ever taken up by anybody. See Convoy#wasEscorted.
        boolean watched = level != null && !level.players().isEmpty();

        if (watched) {
            convoy.markEscorted();
        }

        if (convoy.damage(biteFor(campaign, convoy, watched))) {
            lose(server, campaign, convoy, level, gameTime);
            return;
        }

        if (gameTime >= convoy.arrivesAt()) {
            arrive(server, campaign, convoy, level, gameTime);
        }
    }

    /**
     * How much integrity the war takes from this convoy this pass.
     *
     * <p>Scaled by the destination front's intensity — the same {@link WarIntensity#raidRate()} the
     * raid layer and the order generator read, so "this planet is dangerous" keeps meaning one thing
     * across the campaign.
     *
     * <p>A player standing on the front halves it, because they <i>are</i> the escort: the fiction of
     * an escorted convoy is troops in the field between the cargo and the enemy, and the player being
     * there is the only version of that this layer can honestly represent. It is a halving and not an
     * immunity — presence alone must not be enough, or the order would complete itself by being
     * logged in.
     */
    private static int biteFor(CampaignData campaign, Convoy convoy, boolean watched) {
        PlanetWarState state = campaign.existingState(convoy.destination());

        double heat = state == null ? WarIntensity.DORMANT.raidRate() : state.intensity().raidRate();
        int bite = Math.max(1, (int) Math.round(CampaignConfig.convoyAttritionPerPass() * heat));

        if (watched) {
            bite = Math.max(1, bite / 2);
        }

        return bite;
    }

    // ====================================================================================
    // Endings
    // ====================================================================================

    private static void arrive(MinecraftServer server, CampaignData campaign, Convoy convoy,
                               @Nullable ServerLevel level, long gameTime) {
        int landed = convoy.deliverable();

        convoy.arrive(gameTime);
        campaign.setDirty();

        CampaignLog.war("convoy {} arrived on {} with {}% integrity: {} of {} {}",
                convoy.id(), convoy.destination().getPath(), convoy.integrity(),
                landed, convoy.cargo(), convoy.resource().name());

        payInto(level, convoy, landed);

        announce(level, Component.translatable("msg.firstcrusade.convoy.arrived",
                convoy.resource().getDisplayName(), landed, convoy.integrity())
                .withStyle(ChatFormatting.GREEN));

        OperationManager.onConvoyResolved(server, campaign, convoy, true, gameTime);
    }

    private static void lose(MinecraftServer server, CampaignData campaign, Convoy convoy,
                             @Nullable ServerLevel level, long gameTime) {
        convoy.lose(gameTime);
        campaign.setDirty();

        CampaignLog.war("convoy {} lost en route to {} ({} {} destroyed)",
                convoy.id(), convoy.destination().getPath(),
                convoy.cargo(), convoy.resource().name());

        announce(level, Component.translatable("msg.firstcrusade.convoy.lost",
                convoy.resource().getDisplayName()).withStyle(ChatFormatting.RED));

        OperationManager.onConvoyResolved(server, campaign, convoy, false, gameTime);
    }

    /**
     * Pays an arrived convoy into the nearest loaded Command Core on the destination front, as War
     * Support.
     *
     * <h2>Why War Support and not the cargo</h2>
     *
     * The lanes carry FOOD, MANPOWER, PLASTEEL, PROMETHIUM and CERAMITE, and a Command Core stores
     * IRON, COAL, SCRAP, GOLD, EMERALD and CRUSADIUM. The two lists overlap on almost nothing, so
     * "deliver the cargo" would mean inventing an exchange rate between promethium and iron that this
     * mod has never had, for the sole purpose of making a number land somewhere.
     *
     * <p>War Support is the one sink every cargo can honestly land in: it is what the front's
     * commander spends to act — the War Table's orders and the armour requisition both draw on it —
     * and "the convoy got through, so the Crusade can move here" is exactly what a supply run means.
     * One sink, no invented rates, and it feeds a currency that already existed.
     *
     * <p>A front with no loaded Core simply does not get paid, the same way an operation's local half
     * is skipped: the run still succeeded and still resolves its order, but a delivery with no address
     * cannot be made.
     */
    private static void payInto(@Nullable ServerLevel level, Convoy convoy, int landed) {
        if (level == null || landed <= 0) {
            return;
        }

        ImperialCommandCoreBlockEntity core = CampaignIntegration.nearestLoadedCore(
                level, level.getSharedSpawnPos());

        if (core == null) {
            return;
        }

        int support = Math.max(1,
                (int) Math.round(landed * CampaignConfig.convoyWarSupportPerCargo()));
        core.addWarSupport(support);

        CampaignLog.war("convoy {} paid {} war support into the core at {}",
                convoy.id(), support, core.getBlockPos());
    }

    // ====================================================================================
    // Dispatch
    // ====================================================================================

    /**
     * Sends one convoy, if any lane is cut and everything else allows it.
     *
     * <p>One per pass at most. The cap and the per-lane cooldown throttle it on their own, but a pass
     * that could dispatch three at once would empty every candidate lane into the air in one tick and
     * then go quiet for minutes.
     */
    private static void maybeDispatch(MinecraftServer server, CampaignData campaign, long gameTime) {
        if (campaign.countLiveConvoys() >= CampaignConfig.maxLiveConvoys()) {
            return;
        }

        for (SupplyRoute route : campaign.supplyRoutes()) {
            if (refuse(campaign, route) != null) {
                continue;
            }

            dispatch(server, campaign, route, gameTime);
            return;
        }
    }

    /**
     * Why this lane may not send a convoy right now, or null when it may.
     *
     * <p>Written as a refusal rather than as a boolean because the command has to say which check
     * failed. "Nothing happened" is what a player reports as a bug, and a dispatch that silently
     * declines for one of five reasons is the most reportable thing in this file.
     */
    @Nullable
    public static Component refuse(CampaignData campaign, SupplyRoute route) {
        if (CampaignConfig.maxLiveConvoys() <= 0) {
            return Component.translatable("msg.firstcrusade.convoy.refused.disabled");
        }

        if (route.state() == SupplyState.DESTROYED) {
            return Component.translatable("msg.firstcrusade.convoy.refused.no_cargo",
                    Component.translatable("planet." + com.example.examplemod.ExampleMod.MODID + "."
                            + route.origin().getPath()));
        }

        if (route.state().carries() && route.state() != SupplyState.DISRUPTED) {
            return Component.translatable("msg.firstcrusade.convoy.refused.lane_healthy");
        }

        if (route.amount() <= 0) {
            return Component.translatable("msg.firstcrusade.convoy.refused.no_cargo",
                    Component.translatable("planet." + com.example.examplemod.ExampleMod.MODID + "."
                            + route.origin().getPath()));
        }

        // The destination has to be a front the campaign has actually laid out, or the cargo is for
        // a planet with no sectors, no state and nowhere to land.
        PlanetWarState destination = campaign.existingState(route.destination());

        if (destination == null || !destination.sectorsLaidOut()) {
            return Component.translatable("msg.firstcrusade.convoy.refused.front_inactive",
                    Component.translatable("planet." + com.example.examplemod.ExampleMod.MODID + "."
                            + route.destination().getPath()));
        }

        if (campaign.hasRecentConvoyOn(route.id())) {
            return Component.translatable("msg.firstcrusade.convoy.refused.lane_busy");
        }

        if (campaign.countLiveConvoys() >= CampaignConfig.maxLiveConvoys()) {
            return Component.translatable("msg.firstcrusade.convoy.refused.at_cap",
                    CampaignConfig.maxLiveConvoys());
        }

        return null;
    }

    /**
     * Puts one convoy in the air down this lane and raises the ESCORT order for it.
     *
     * <p>Assumes {@link #refuse} has already passed. Public so {@code /fcstrategy convoy dispatch}
     * can force one without waiting for the pass to choose this lane.
     */
    public static Convoy dispatch(MinecraftServer server, CampaignData campaign, SupplyRoute route,
                                  long gameTime) {
        // The cargo is several passes of the lane's rated throughput in one run. That multiple is the
        // whole reason it is worth defending: a convoy carrying one pass's worth would be a rounding
        // error the player could rationally ignore.
        int cargo = Math.max(1, route.amount() * CampaignConfig.convoyCargoPasses());
        long travel = CampaignConfig.convoyTravelTicks();

        Convoy convoy = new Convoy(
                route.id() + "@" + gameTime,
                route.origin(),
                route.destination(),
                route.resource(),
                cargo,
                gameTime,
                gameTime + travel);

        campaign.addConvoy(convoy);

        CampaignLog.war("convoy {} dispatched: {} {} {} -> {} ({} ticks)",
                convoy.id(), cargo, route.resource().name(),
                route.origin().getPath(), route.destination().getPath(), travel);

        ServerLevel level = levelOf(server, route.destination());

        CampaignFronts.byId(route.destination()).ifPresent(front ->
                OperationManager.issueEscort(server, campaign, front, convoy, gameTime));

        announce(level, Component.translatable("msg.firstcrusade.convoy.dispatched",
                Component.translatable("planet." + com.example.examplemod.ExampleMod.MODID + "."
                        + route.origin().getPath()),
                convoy.resource().getDisplayName(), cargo).withStyle(ChatFormatting.GOLD));

        refreshWatch(campaign);
        return convoy;
    }

    // ====================================================================================
    // Event hooks
    // ====================================================================================

    /**
     * An enemy died on a front. Repairs any convoy heading there.
     *
     * <p>The set test comes first and is the only cost on the overwhelming majority of deaths. The
     * set is empty whenever nothing is in the air, which is most of the time.
     *
     * <p>This — not standing still — is what escorting actually is. Presence halves the bite; killing
     * what is doing the biting takes integrity back, and a player who works the front hard enough can
     * hold a convoy at full through a total war.
     */
    public static void onEnemyKilled(ServerLevel level) {
        ResourceLocation frontId = level.dimension().location();

        if (!frontsAwaitingConvoy.contains(frontId)) {
            return;
        }

        CampaignData campaign = CampaignData.get(level);
        int repair = CampaignConfig.convoyDefencePerKill();

        for (Convoy convoy : campaign.inTransitConvoys()) {
            if (convoy.destination().equals(frontId)) {
                convoy.defend(repair);
            }
        }

        campaign.setDirty();
    }

    /**
     * Finishes off a convoy whose integrity a caller has already taken to zero.
     *
     * <p>For {@code /fcstrategy convoy strike}. It routes through the same {@link #lose} the attrition
     * pass uses, so the order fails and the announcement goes out exactly as it would in play — a test
     * that reached the loss state by a private path would be testing a path no game ever takes.
     */
    public static void killByHand(MinecraftServer server, CampaignData campaign, Convoy convoy,
                                  long gameTime) {
        lose(server, campaign, convoy, levelOf(server, convoy.destination()), gameTime);
        refreshWatch(campaign);
    }

    /** Rebuilds the death hook's fast-path set. Called at the end of every pass and after a dispatch. */
    public static void refreshWatch(CampaignData campaign) {
        frontsAwaitingConvoy.clear();

        for (Convoy convoy : campaign.inTransitConvoys()) {
            frontsAwaitingConvoy.add(convoy.destination());
        }
    }

    /** Clears the watch set when a server stops, so a second world in the same JVM starts clean. */
    public static void clearWatch() {
        frontsAwaitingConvoy.clear();
    }

    // ====================================================================================
    // Shared
    // ====================================================================================

    @Nullable
    private static ServerLevel levelOf(MinecraftServer server, ResourceLocation frontId) {
        CampaignFront front = CampaignFronts.byId(frontId).orElse(null);
        return front == null ? null : server.getLevel(front.dimension());
    }

    private static void announce(@Nullable ServerLevel level, Component message) {
        if (level == null) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    /** Every convoy touching a front, either end, for the commands and the War Table. */
    public static List<Convoy> convoysTouching(CampaignData campaign, ResourceLocation frontId) {
        List<Convoy> found = new ArrayList<>();

        for (Convoy convoy : campaign.allConvoys()) {
            if (convoy.origin().equals(frontId) || convoy.destination().equals(frontId)) {
                found.add(convoy);
            }
        }

        return found;
    }
}
