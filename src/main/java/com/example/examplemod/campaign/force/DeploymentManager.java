package com.example.examplemod.campaign.force;

import java.util.List;

import javax.annotation.Nullable;

import com.example.examplemod.FCRegistry;
import com.example.examplemod.campaign.CampaignConfig;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.CampaignFront;
import com.example.examplemod.campaign.CampaignLog;
import com.example.examplemod.campaign.PlanetCampaignManager;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.war.WarFaction;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Moves strategic forces, and decides when a number has to become soldiers.
 *
 * <h2>The three distances, in one place</h2>
 *
 * The brief asks for a war that is arithmetic far away and physical up close. That rule lives here
 * and nowhere else:
 *
 * <ul>
 *   <li><b>Far.</b> A committed deployment adds {@link StrategicDeployment#pressure()} to its target
 *       sector every strategic pass and loses strength to attrition. No entity is created, no chunk
 *       is touched, and it works perfectly well on a planet with nobody on it.</li>
 *   <li><b>Near.</b> When a player is within the performance layer's own materialise distance, a
 *       <i>capped</i> slice of the deployment is spawned as real units. The cap is the point: an
 *       assault of forty is not forty mobs, it is a dozen mobs and the rest still arithmetic.</li>
 *   <li><b>Back to far.</b> Nothing here has to undo it. The units are spawned as ordinary mobs, so
 *       {@code FCStrategicBattleData}'s absorption sweep folds them back into a strategic battle the
 *       moment the player leaves — that system already exists and already does this correctly.</li>
 * </ul>
 *
 * <h2>What this deliberately does not do</h2>
 *
 * It does not pathfind. A deployment does not walk; it waits out a travel time and arrives. A war
 * party navigating four hundred blocks through unloaded chunks is the single most expensive thing
 * the old raid system did, and it bought nothing a timer does not — nobody watches an empty field
 * to see whether the Orks took the scenic route.
 */
public final class DeploymentManager {

    private DeploymentManager() {
    }

    /** Strength lost per pass in contact, as a fraction — a fight costs the attacker. */
    private static final double ATTRITION = 0.18D;

    /** One point of strength is worth this much raw contest before the sector's defence divides it. */
    private static final double PRESSURE_PER_STRENGTH = 1.6D;

    // ====================================================================================
    // The pass
    // ====================================================================================

    /**
     * One front's deployments: advance their clocks, press on their targets, materialise what a
     * player can see.
     *
     * <p>Called from the campaign's strategic pass, so it inherits that interval.
     */
    public static void tickFront(CampaignData campaign, CampaignFront front,
                                 @Nullable ServerLevel level, long gameTime) {
        List<StrategicDeployment> deployments = campaign.activeDeploymentsOn(front.id());

        if (deployments.isEmpty()) {
            return;
        }

        long moveTicks = CampaignConfig.deploymentTravelTicks();

        for (StrategicDeployment deployment : deployments) {
            DeploymentState changed = deployment.advanceIfDue(gameTime, moveTicks);

            if (changed == DeploymentState.COMMITTED) {
                CampaignLog.raid("{} {} deployment of {} reached {}",
                        front.path(), deployment.faction().name(), deployment.strength(),
                        deployment.targetSectorId());
            }

            if (deployment.state() != DeploymentState.COMMITTED) {
                continue;
            }

            resolveCommitted(campaign, front, deployment, level, gameTime);
        }

        campaign.retireSpentDeployments(front.id(), gameTime);
    }

    /**
     * A deployment in contact: press the sector, pay the attrition, and put bodies on the ground if
     * anyone is close enough to see it.
     */
    private static void resolveCommitted(CampaignData campaign, CampaignFront front,
                                         StrategicDeployment deployment,
                                         @Nullable ServerLevel level, long gameTime) {
        StrategicSector target = campaign.sector(deployment.targetSectorId());

        if (target == null) {
            // The sector it was sent at no longer exists. Nothing to press; end the deployment
            // rather than leaving a force pushing at a hole in the map forever.
            deployment.markSpent();
            return;
        }

        // The order is already done: the ground is ours (or theirs) without further fighting.
        if (target.owner() == deployment.faction()) {
            deployment.markSpent();

            CampaignLog.raid("{} {} deployment stood down: {} already held",
                    front.path(), deployment.faction().name(), target.type().name());
            return;
        }

        int signed = deployment.faction() == WarFaction.IMPERIUM ? 1 : -1;
        int pressure = (int) Math.round(deployment.pressure() * PRESSURE_PER_STRENGTH
                * CampaignConfig.warSpeed()) * signed;

        WarFaction previous = target.applyPressure(pressure, gameTime);

        if (previous != null) {
            CampaignLog.war("{} sector {} changed {} -> {} (deployment)",
                    front.path(), target.type().name(), previous.name(), target.owner().name());

            PlanetCampaignManager.refreshFront(level, target.dimension(), campaign);
        }

        // Contact costs. A deployment that never wore down would sit on a fortress pressing forever,
        // and the front line would stop being something either side has to keep feeding.
        deployment.spend(Math.max(1, (int) Math.round(deployment.strength() * ATTRITION)));

        maybeMaterialise(deployment, target, level);
    }

    // ====================================================================================
    // Materialisation
    // ====================================================================================

    /**
     * Turns part of a deployment into real units when a player is near its target.
     *
     * <p>The distance is the performance layer's own {@code strategicMaterialiseDistance}, read
     * rather than redefined: a battle that materialises at 160 blocks and a deployment that spawns at
     * 300 would produce a field where reinforcements appear out of nothing well before the fight
     * they are joining does.
     */
    private static void maybeMaterialise(StrategicDeployment deployment, StrategicSector target,
                                         @Nullable ServerLevel level) {
        if (level == null || deployment.strength() <= 0) {
            return;
        }

        int cap = CampaignConfig.deploymentMaterialiseCap();

        if (cap <= 0 || deployment.materialisedStrength() >= cap) {
            return;
        }

        double range = FirstCrusadePerformanceConfig.strategicMaterialiseDistance();

        if (!anyPlayerNear(level, target.pos(), range)) {
            return;
        }

        int wanted = Math.min(cap - deployment.materialisedStrength(), deployment.strength());
        int taken = deployment.materialise(wanted);

        if (taken <= 0) {
            return;
        }

        int spawned = spawn(level, deployment.faction(), target, taken);

        if (spawned <= 0) {
            // Nowhere to put them — the ground is walled in, or the chunks are not ready. Hand the
            // strength back rather than deleting it, and try again on a later pass.
            deployment.demateralise();
            return;
        }

        CampaignLog.raid("{} materialised {} {} unit(s) at {}",
                deployment.frontId().getPath(), spawned, deployment.faction().name(),
                target.type().name());
    }

    /**
     * Spawns a deployment's slice as mobs around a sector.
     *
     * <p>The composition is deliberately plain — line troops, with one leader per five. Anything
     * richer belongs to whatever raised the force, and a raid that arrived with a bespoke order of
     * battle would be a second, competing definition of what an Ork war party is.
     *
     * @return how many were actually placed
     */
    private static int spawn(ServerLevel level, WarFaction faction, StrategicSector target, int count) {
        BlockPos centre = target.pos();
        int placed = 0;

        for (int i = 0; i < count; i++) {
            boolean leader = i > 0 && i % 5 == 0;
            EntityType<? extends Mob> type = typeFor(faction, leader);

            if (type == null) {
                return placed;
            }

            BlockPos spot = scatter(level, centre);

            Mob mob = type.create(level);

            if (mob == null) {
                continue;
            }

            mob.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);

            // Persistent, because these are an army and not ambient spawns: a deployment whose
            // troops despawned when the player looked away would quietly delete itself.
            mob.setPersistenceRequired();
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                    MobSpawnType.EVENT, null, null);

            if (!level.addFreshEntity(mob)) {
                continue;
            }

            placed++;

            // The strategic order becomes a squad order. This is the seam the squad system was
            // written for and never had a writer: FCSquadOrder has existed for a while, and until
            // now nothing set it, so every squad in the mod was permanently on FOLLOW.
            //
            // Only the leader is told. Followers take their positions from the leader through
            // FCFormationGoal, so ordering the leader orders the squad.
            if (mob instanceof com.example.examplemod.ai.formation.FCSquadLeader squadLeader) {
                squadLeader.getSquad().setOrder(orderFor(faction, target), centre);
            }
        }

        return placed;
    }

    /**
     * The squad order that matches why this force was sent.
     *
     * <p>Derived from who holds the ground rather than carried on the deployment: a force arriving
     * at a sector its own side already holds is a garrison, and one arriving at the enemy's is an
     * assault. Reading it from the sector means the order stays right if the ground changed hands
     * while the force was on the march — which is exactly when it matters.
     */
    private static com.example.examplemod.ai.formation.FCSquadOrder orderFor(WarFaction faction,
                                                                             StrategicSector target) {
        return target.owner() == faction
                ? com.example.examplemod.ai.formation.FCSquadOrder.DEFEND
                : com.example.examplemod.ai.formation.FCSquadOrder.ATTACK;
    }

    @Nullable
    private static EntityType<? extends Mob> typeFor(WarFaction faction, boolean leader) {
        if (faction == WarFaction.IMPERIUM) {
            return leader ? FCRegistry.GUARDSMAN_SERGEANT.get() : FCRegistry.GUARDSMAN_RIFLEMAN.get();
        }

        if (faction == WarFaction.ORKS) {
            return leader ? FCRegistry.ORK_NOB.get() : FCRegistry.ORK_BOY.get();
        }

        // Necrons have no entities yet. Returning null keeps their strength abstract rather than
        // spawning a stand-in that would be wrong in every way that matters.
        return null;
    }

    /** A ground spot a few blocks off the sector centre, so a squad does not stack in one column. */
    private static BlockPos scatter(ServerLevel level, BlockPos centre) {
        int x = centre.getX() + level.random.nextInt(17) - 8;
        int z = centre.getZ() + level.random.nextInt(17) - 8;

        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

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

    // ====================================================================================
    // Creating
    // ====================================================================================

    /**
     * Raises a deployment.
     *
     * <p>The single constructor for both sides — a War Table order and an Ork offensive both come
     * through here, so neither can acquire rules the other does not have.
     */
    public static StrategicDeployment raise(CampaignData campaign, CampaignFront front,
                                            WarFaction faction,
                                            com.example.examplemod.campaign.StrategicLocation origin,
                                            String targetSectorId, int strength, long gameTime,
                                            boolean playerOrdered) {
        String id = front.path() + ".dep." + gameTime + "." + Math.abs(origin.packed() % 1000);

        StrategicDeployment deployment = new StrategicDeployment(
                id, front.id(), faction, origin, targetSectorId, strength, gameTime,
                gameTime + CampaignConfig.deploymentMusterTicks(), playerOrdered);

        campaign.addDeployment(deployment);

        CampaignLog.raid("{} {} deployment raised: {} -> {}",
                front.path(), faction.name(), strength, targetSectorId);

        return deployment;
    }
}
