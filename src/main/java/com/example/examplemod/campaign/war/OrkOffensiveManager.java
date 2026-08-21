package com.example.examplemod.campaign.war;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.campaign.CampaignConfig;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.CampaignFront;
import com.example.examplemod.campaign.CampaignLog;
import com.example.examplemod.campaign.StrategicLocation;
import com.example.examplemod.campaign.force.DeploymentManager;
import com.example.examplemod.campaign.planet.PlanetWarState;
import com.example.examplemod.campaign.sector.StrategicSector;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The WAAAGH!, rebuilt as a strategic thing.
 *
 * <h2>Why the old raid system was not simply switched back on</h2>
 *
 * Ork waves already existed and are disabled ({@code ExampleMod.ORK_WAVES_ENABLED}, default false)
 * because of what they cost: the old offensive spawned twenty-odd mobs at a camp and gave every one
 * of them a navigation target four hundred blocks away. Twenty simultaneous long-range paths through
 * chunks nobody has loaded is the single most expensive thing the mod ever did, and it happened
 * whether or not a player was anywhere near either end.
 *
 * <p>So the pressure is the same and the mechanism is not. A planet's camps build WAAAGH! toward a
 * threshold; crossing it raises one {@link com.example.examplemod.campaign.force.StrategicDeployment}
 * — a number with a timer — aimed at the most valuable Imperial sector. It musters, it marches, it
 * makes contact, and it presses. Bodies appear only if a player is close enough to see the fight, and
 * only a capped handful of them.
 *
 * <p>{@code ORK_WAVES_ENABLED} still gates the <b>old</b> path in {@code StrategicWarAIManager} and
 * is deliberately left alone: this is a different system, with its own config, and turning one on
 * must not silently turn the other on too.
 *
 * <h2>Build-up is per planet, not per camp</h2>
 *
 * Each camp contributes, but the pool and the threshold belong to the front. A planet with eight
 * camps launches sooner and harder than one with two, which is the behaviour the fiction wants, and
 * it means the whole system is one integer per front rather than a state machine per camp.
 */
public final class OrkOffensiveManager {

    private OrkOffensiveManager() {
    }

    /** Strength of the force a launch raises, per point of threshold crossed. */
    private static final double STRENGTH_PER_THRESHOLD = 0.22D;

    /** No offensive is raised below this strength — a raid of two is not a raid. */
    private static final int MINIMUM_STRENGTH = 6;

    /**
     * One front's build-up: add what its camps produced, and launch if the pool is full.
     *
     * <p>Called from the campaign's strategic pass. Costs a walk over the front's camps (already
     * bucketed by dimension) and nothing else.
     */
    public static void tickFront(CampaignData campaign, WorldWarMapData warMap, CampaignFront front,
                                 @Nullable ServerLevel level, List<StrategicSector> sectors,
                                 long gameTime) {
        if (!CampaignConfig.enabled()) {
            return;
        }

        PlanetWarState state = campaign.stateOf(front);

        if (!state.state().isEngaged()) {
            return;
        }

        int camps = warMap.countCamps(front.dimension());

        if (camps <= 0) {
            return;
        }

        // A hotter planet builds faster. Same multiplier the operations layer reads, so "this world
        // is dangerous" means one thing across the whole campaign.
        int gained = (int) Math.round(camps * CampaignConfig.orkRaidBuildPerCamp()
                * state.intensity().raidRate() * CampaignConfig.warSpeed());

        if (gained <= 0) {
            return;
        }

        int threshold = CampaignConfig.orkRaidThreshold();
        int pool = state.addWaaaghBuildUp(gained, threshold * 2);

        if (pool < threshold) {
            // Warn once as the pool crosses three quarters. A raid that arrives with no warning is
            // an ambush, and the brief asks for a preparation phase a player can act on.
            if (state.notePreparationWarning(pool >= threshold * 3 / 4)) {
                announce(level, Component.translatable("msg.firstcrusade.raid.preparing",
                        front.displayName()).withStyle(ChatFormatting.RED));

                CampaignLog.raid("{} WAAAGH! building ({}/{})", front.path(), pool, threshold);
            }

            return;
        }

        launch(campaign, warMap, front, state, sectors, level, gameTime, pool, threshold);
    }

    private static void launch(CampaignData campaign, WorldWarMapData warMap, CampaignFront front,
                               PlanetWarState state, List<StrategicSector> sectors,
                               @Nullable ServerLevel level, long gameTime, int pool, int threshold) {
        if (campaign.countActiveDeployments(front.id(), WarFaction.ORKS)
                >= CampaignConfig.maxDeploymentsPerSide()) {
            // Already at the cap. The pool stays full, so the moment one resolves another launches —
            // pressure is deferred, never lost.
            return;
        }

        StrategicSector target = richestImperialSector(sectors);

        if (target == null) {
            // Nothing Imperial left to attack on this world. Hold the pool rather than spending it
            // on nothing.
            return;
        }

        BlockPos origin = nearestCamp(warMap, front, target.pos());

        if (origin == null) {
            return;
        }

        int strength = Math.max(MINIMUM_STRENGTH, (int) Math.round(pool * STRENGTH_PER_THRESHOLD));

        // The test sandbox caps how many warriors a faction may field. Honouring it here keeps the
        // fixed-world layout watchable instead of letting the strategic layer route around the cap.
        if (ExampleMod.TEST_FIXED_WORLD) {
            strength = Math.min(strength, ExampleMod.TEST_WARRIOR_CAP);
        }

        state.spendWaaaghBuildUp(threshold);

        DeploymentManager.raise(campaign, front, WarFaction.ORKS,
                new StrategicLocation(front.dimension(), origin), target.id(), strength,
                gameTime, false);

        state.recordEvent("WAAAGH! lançada contra " + target.type().name(), gameTime);

        CampaignLog.raid("{} WAAAGH! launched from {} at {} with strength {}",
                front.path(), origin, target.type().name(), strength);

        announce(level, Component.translatable("msg.firstcrusade.raid.launched",
                front.displayName(), target.type().displayName()).withStyle(ChatFormatting.DARK_RED));
    }

    /**
     * Why a launch on this front would not happen right now, or null when nothing is stopping one.
     *
     * <p>For {@code /fcstrategy raid start} and nothing else. That command fills the pool and runs a
     * pass, and every one of the conditions below makes the pass do nothing at all — so without this
     * the command reports "WAAAGH! enchida e passe executado" and the tester is left staring at an
     * empty {@code raid list} with no way to tell which of four different reasons applied. The most
     * common one on a fresh world is the plainest: nobody has built an Ork camp yet, so there is no
     * WAAAGH! to raise and no camp for it to set out from.
     *
     * <p>Deliberately not called from {@link #tickFront}: two of these questions are asked there
     * <i>after</i> the build-up is added, because a front at the deployment cap must keep filling its
     * pool rather than losing the pressure. Asking them early would change that behaviour, so this
     * re-asks them instead — using the same helpers, so the answers cannot drift from the real ones.
     */
    @Nullable
    public static String launchBlocker(CampaignData campaign, WorldWarMapData warMap,
                                       CampaignFront front, PlanetWarState state,
                                       List<StrategicSector> sectors) {
        if (!state.state().isEngaged()) {
            return "a frente está " + state.state().name() + ", que não é um estado engajado";
        }

        if (warMap.countCamps(front.dimension()) <= 0) {
            return "não há nenhum Ork Camp registrado neste planeta — sem camp não há WAAAGH! nem "
                    + "origem para a força sair";
        }

        if (campaign.countActiveDeployments(front.id(), WarFaction.ORKS)
                >= CampaignConfig.maxDeploymentsPerSide()) {
            return "já está no teto de " + CampaignConfig.maxDeploymentsPerSide()
                    + " força(s) Ork em movimento; o pool fica cheio e lança quando uma resolver";
        }

        if (richestImperialSector(sectors) == null) {
            return "não sobrou nenhum setor imperial neste planeta para atacar";
        }

        return null;
    }

    /** The most valuable sector the Imperium holds here — what a WAAAGH! would go for. */
    @Nullable
    private static StrategicSector richestImperialSector(List<StrategicSector> sectors) {
        StrategicSector best = null;

        for (StrategicSector sector : sectors) {
            if (sector.owner() != WarFaction.IMPERIUM) {
                continue;
            }

            if (best == null || sector.importance() > best.importance()) {
                best = sector;
            }
        }

        return best;
    }

    /**
     * The camp closest to the target, which is where the force sets out from.
     *
     * <p>Reads the war map rather than the world, so it works on a planet with no chunks loaded —
     * and it reads only <b>this planet's</b> camps, which is the whole point of the map being
     * bucketed by dimension.
     */
    @Nullable
    private static BlockPos nearestCamp(WorldWarMapData warMap, CampaignFront front, BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (long packed : warMap.getCamps(front.dimension())) {
            BlockPos pos = BlockPos.of(packed);
            double distance = pos.distSqr(target);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }

        return best;
    }

    private static void announce(@Nullable ServerLevel level, Component message) {
        if (level == null) {
            return;
        }

        List<ServerPlayer> players = new ArrayList<>(level.players());

        for (ServerPlayer player : players) {
            player.sendSystemMessage(message);
        }
    }
}
