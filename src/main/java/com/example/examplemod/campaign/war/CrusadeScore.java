package com.example.examplemod.campaign.war;

import com.example.examplemod.WarDominionData;
import com.example.examplemod.WarDominionManager;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.planet.PlanetCampaignState;
import com.example.examplemod.campaign.planet.PlanetWarState;

import net.minecraft.server.level.ServerLevel;

/**
 * How the Crusade is doing across every front at once.
 *
 * <h2>The global score becomes a summary, not a source</h2>
 *
 * {@link WarDominionData} is a single number that every war event nudged: razing a camp pushed it
 * up, a war party pushed it down, and at ±50 a triumph was declared. That was the right idea while
 * there was one planet. Across nine it is meaningless — a defeat on Cadia and a victory on
 * Armageddon cancel to zero, and the number can only report a war it does not describe.
 *
 * <p>So the dominion is no longer the record of the war; it is a <b>reading</b> of it. This class
 * recomputes it from the fronts each strategic pass, weighted by how much of each front each side
 * holds. That keeps everything already reading {@code WarDominionData} working — the Core's GUI, the
 * triumph broadcast, the strategic status command — while making the number honest.
 *
 * <h2>What was deliberately left alone</h2>
 *
 * {@link WarDominionManager#shift} still exists and every existing caller still calls it. A shift is
 * now a <i>nudge</i> that the next recomputation smooths back toward the true reading, rather than
 * the permanent record it used to be — which is what an event like "a camp was razed" should have
 * been all along. Removing those calls would have meant touching a dozen files to no benefit, and
 * would have deleted the immediate feedback a player gets from winning a fight.
 */
public final class CrusadeScore {

    /** How fast the reading moves toward the true value per pass. */
    private static final double SMOOTHING = 0.25D;

    private CrusadeScore() {
    }

    /**
     * Recomputes the global dominion from the state of every front and eases the stored value toward
     * it.
     *
     * <p>Eased rather than assigned: a hard write would erase the nudge a player just earned by
     * razing a camp before they read it, and would make the number jump the instant a front is
     * activated. A quarter of the distance per pass reaches the true value in about a minute and a
     * half of game time while leaving events visible.
     */
    public static void recompute(ServerLevel overworld, CampaignData campaign) {
        // Until a front has actually been engaged, the campaign has no opinion and must not express
        // one. Without this guard a save where nobody has left Macragge yet would read a target of
        // zero and quietly drag the dominion there — erasing the points a player earned razing camps
        // before the campaign had anything to say about it.
        if (!hasEngagedFront(campaign)) {
            return;
        }

        int target = targetFor(campaign);

        WarDominionData data = WarDominionData.get(overworld);
        int current = data.getDominion();
        int delta = (int) Math.round((target - current) * SMOOTHING);

        if (delta == 0) {
            // Never let smoothing stall short of the target: move one point so a war that really has
            // been decided eventually says so.
            delta = Integer.signum(target - current);
        }

        if (delta != 0) {
            WarDominionManager.shift(overworld, delta);
        }
    }

    /**
     * The dominion the fronts add up to, in the same [-100, 100] the stored value uses.
     *
     * <p>Weighted by front: a planet the Crusade has conquered counts for more than one it has only
     * landed on, because a conquered world is a base and a contested one is a bill. Fronts nobody has
     * engaged with are skipped entirely rather than counted as neutral — an unvisited planet is not
     * evidence about how the war is going.
     */
    public static int targetFor(CampaignData campaign) {
        double weightedSum = 0.0D;
        double totalWeight = 0.0D;

        for (PlanetWarState state : campaign.states()) {
            if (!state.state().isEngaged()) {
                continue;
            }

            double weight = weightOf(state.state());

            // -100 (all enemy) to +100 (all Imperial); contested ground pulls both toward zero.
            double share = state.imperialControl() - state.enemyControl();

            weightedSum += share * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0D) {
            return 0;
        }

        int value = (int) Math.round(weightedSum / totalWeight);
        return Math.max(-100, Math.min(100, value));
    }

    /** True once at least one front has been laid out and is being fought over. */
    public static boolean hasEngagedFront(CampaignData campaign) {
        for (PlanetWarState state : campaign.states()) {
            if (state.state().isEngaged()) {
                return true;
            }
        }

        return false;
    }

    private static double weightOf(PlanetCampaignState state) {
        return switch (state) {
            case CONQUERED, LOST -> 1.5D;
            case IMPERIAL_CONTROL, ENEMY_CONTROL -> 1.25D;
            case CONTESTED -> 1.0D;
            default -> 0.75D;
        };
    }

    /** How many fronts the Crusade has actually taken. For the War Table's headline. */
    public static int conqueredFronts(CampaignData campaign) {
        int count = 0;

        for (PlanetWarState state : campaign.states()) {
            if (state.state() == PlanetCampaignState.CONQUERED) {
                count++;
            }
        }

        return count;
    }

    /** How many fronts have fallen. */
    public static int lostFronts(CampaignData campaign) {
        int count = 0;

        for (PlanetWarState state : campaign.states()) {
            if (state.state() == PlanetCampaignState.LOST) {
                count++;
            }
        }

        return count;
    }
}
