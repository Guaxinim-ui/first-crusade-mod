package com.example.examplemod.campaign.wartable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.examplemod.StrategicResourceType;
import com.example.examplemod.WarDominionData;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.CampaignFront;
import com.example.examplemod.campaign.CampaignFronts;
import com.example.examplemod.campaign.operation.Operation;
import com.example.examplemod.campaign.operation.OperationState;
import com.example.examplemod.campaign.operation.OperationType;
import com.example.examplemod.campaign.planet.PlanetCampaignState;
import com.example.examplemod.campaign.planet.PlanetWarState;
import com.example.examplemod.campaign.sector.SectorType;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.supply.SupplyNetwork;
import com.example.examplemod.campaign.supply.SupplyRoute;
import com.example.examplemod.campaign.supply.SupplyState;
import com.example.examplemod.campaign.war.CrusadeScore;
import com.example.examplemod.campaign.war.WarFaction;
import com.example.examplemod.campaign.war.WarIntensity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The whole war, as one message.
 *
 * <h2>A picture the server took, not a question the client asks</h2>
 *
 * The screen renders this and nothing else. It never reads {@link CampaignData}, never asks whether a
 * sector is Imperial, never computes a percentage. That is the rule the brief states outright and it
 * is what makes the War Table safe to have: a client that can only draw a snapshot cannot award
 * itself a planet, and one that is lying about what it drew has changed nothing on the server.
 *
 * <h2>Why everything travels at once</h2>
 *
 * Nine fronts, their sectors, their orders and a dozen lanes is a few kilobytes, sent when a player
 * right-clicks a block. The alternative — the screen requesting each front as it is selected — costs
 * a round trip per click for a table whose whole purpose is comparing worlds side by side.
 *
 * <p>Everything here is a record of plain values. No {@link CampaignFront} or {@link StrategicSector}
 * crosses the wire, so nothing on the client holds a reference to server state that has since moved.
 */
public record WarTableSnapshot(int crusadeScore, int dominion, int conquered, int lost,
                               int brokenLanes, String currentFront, BlockPos tablePos,
                               List<FrontEntry> fronts, List<RouteEntry> routes) {

    /** One world's line on the table. */
    public record FrontEntry(String frontId, String frontPath, PlanetCampaignState state,
                             WarIntensity intensity, int imperial, int ork, int necron,
                             int contested, int cities, int camps, int necronAwakening,
                             String objectiveKey, String objectiveTarget, String lastEvent,
                             List<SectorEntry> sectors, List<OperationEntry> operations,
                             List<ResourceEntry> income, List<DeploymentEntry> deployments) {
    }

    /** A force on the move, on either side. */
    public record DeploymentEntry(String id, com.example.examplemod.campaign.war.WarFaction faction,
                                  com.example.examplemod.campaign.force.DeploymentState state,
                                  int strength, int materialised, String targetSectorId,
                                  boolean playerOrdered) {
    }

    public record SectorEntry(String id, SectorType type, WarFaction owner, int contest,
                              boolean disputed, int x, int z) {
    }

    public record OperationEntry(String id, OperationType type, OperationState state,
                                 int progress, int required, long ticksLeft,
                                 String targetType) {
    }

    /**
     * @param reason    translation key for why the lane is cut, empty when it is not
     * @param reasonArg the one key {@code reason} substitutes — see {@link SupplyRoute#reasonArg()}
     */
    public record RouteEntry(String originPath, String destinationPath, StrategicResourceType resource,
                             int delivered, int amount, SupplyState state, String reason,
                             String reasonArg) {
    }

    public record ResourceEntry(StrategicResourceType resource, int amount) {
    }

    // ====================================================================================
    // Building (server only)
    // ====================================================================================

    /**
     * Reads the campaign into a snapshot.
     *
     * <p>Walks the fronts, their sectors and their orders once each — the same collections the
     * strategic pass walks — and touches no world. A front whose dimension is not loaded is included
     * exactly like one that is, which is the point: the table has to show Armageddon from Macragge.
     */
    public static WarTableSnapshot capture(ServerPlayer player, BlockPos tablePos) {
        ServerLevel level = player.serverLevel();
        CampaignData campaign = CampaignData.get(level);
        WorldWarMapData warMap = WorldWarMapData.get(level);

        List<FrontEntry> fronts = new ArrayList<>();

        for (CampaignFront front : CampaignFronts.all()) {
            PlanetWarState state = campaign.existingState(front.id());

            List<SectorEntry> sectors = new ArrayList<>();

            for (StrategicSector sector : campaign.sectorsOn(front.dimension())) {
                sectors.add(new SectorEntry(sector.id(), sector.type(), sector.owner(),
                        sector.contest(), sector.isDisputed(),
                        sector.pos().getX(), sector.pos().getZ()));
            }

            List<OperationEntry> operations = new ArrayList<>();
            long gameTime = level.getGameTime();

            for (Operation operation : campaign.operationsOn(front.id())) {
                operations.add(new OperationEntry(operation.id(), operation.type(),
                        operation.state(), operation.progress(), operation.required(),
                        operation.ticksRemaining(gameTime),
                        operation.targetType() == null ? "" : operation.targetType().name()));
            }

            List<ResourceEntry> income = new ArrayList<>();

            for (Map.Entry<StrategicResourceType, Integer> entry
                    : SupplyNetwork.incoming(campaign, front.id()).entrySet()) {
                income.add(new ResourceEntry(entry.getKey(), entry.getValue()));
            }

            List<DeploymentEntry> deployments = new ArrayList<>();

            for (com.example.examplemod.campaign.force.StrategicDeployment deployment
                    : campaign.deploymentsOn(front.id())) {
                deployments.add(new DeploymentEntry(deployment.id(), deployment.faction(),
                        deployment.state(), deployment.strength(), deployment.materialisedStrength(),
                        deployment.targetSectorId(), deployment.playerOrdered()));
            }

            fronts.add(new FrontEntry(
                    front.id().toString(),
                    front.path(),
                    state == null ? PlanetCampaignState.AVAILABLE : state.state(),
                    state == null ? WarIntensity.DORMANT : state.intensity(),
                    state == null ? 0 : state.imperialControl(),
                    state == null ? 0 : state.orkControl(),
                    state == null ? 0 : state.necronControl(),
                    state == null ? 100 : state.contestedControl(),
                    warMap.countCities(front.dimension()),
                    warMap.countCamps(front.dimension()),
                    state == null ? 0 : state.necronAwakening(),
                    state == null ? "" : state.objectiveKey(),
                    state == null || state.objectiveTarget() == null
                            ? "" : state.objectiveTarget().name(),
                    state == null ? "" : state.lastEvent(),
                    sectors, operations, income, deployments));
        }

        List<RouteEntry> routes = new ArrayList<>();

        for (SupplyRoute route : campaign.supplyRoutes()) {
            routes.add(new RouteEntry(route.origin().getPath(), route.destination().getPath(),
                    route.resource(), route.delivered(), route.amount(), route.state(),
                    route.reason(), route.reasonArg()));
        }

        return new WarTableSnapshot(
                CrusadeScore.targetFor(campaign),
                WarDominionData.get(level).getDominion(),
                CrusadeScore.conqueredFronts(campaign),
                CrusadeScore.lostFronts(campaign),
                SupplyNetwork.brokenLanes(campaign),
                level.dimension().location().toString(),
                tablePos,
                fronts,
                routes);
    }

    // ====================================================================================
    // Network
    // ====================================================================================

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.crusadeScore);
        buffer.writeVarInt(this.dominion + 100); // shifted: VarInt is inefficient for negatives
        buffer.writeVarInt(this.conquered);
        buffer.writeVarInt(this.lost);
        buffer.writeVarInt(this.brokenLanes);
        buffer.writeUtf(this.currentFront, 160);
        buffer.writeBlockPos(this.tablePos);

        buffer.writeVarInt(this.fronts.size());

        for (FrontEntry front : this.fronts) {
            buffer.writeUtf(front.frontId(), 160);
            buffer.writeUtf(front.frontPath(), 80);
            buffer.writeEnum(front.state());
            buffer.writeEnum(front.intensity());
            buffer.writeVarInt(front.imperial());
            buffer.writeVarInt(front.ork());
            buffer.writeVarInt(front.necron());
            buffer.writeVarInt(front.contested());
            buffer.writeVarInt(front.cities());
            buffer.writeVarInt(front.camps());
            buffer.writeVarInt(front.necronAwakening());
            buffer.writeUtf(front.objectiveKey(), 160);
            buffer.writeUtf(front.objectiveTarget(), 80);
            buffer.writeUtf(front.lastEvent(), 200);

            buffer.writeVarInt(front.sectors().size());
            for (SectorEntry sector : front.sectors()) {
                buffer.writeUtf(sector.id(), 160);
                buffer.writeEnum(sector.type());
                buffer.writeEnum(sector.owner());
                buffer.writeVarInt(sector.contest() + 100);
                buffer.writeBoolean(sector.disputed());
                buffer.writeInt(sector.x());
                buffer.writeInt(sector.z());
            }

            buffer.writeVarInt(front.operations().size());
            for (OperationEntry operation : front.operations()) {
                buffer.writeUtf(operation.id(), 160);
                buffer.writeEnum(operation.type());
                buffer.writeEnum(operation.state());
                buffer.writeVarInt(operation.progress());
                buffer.writeVarInt(operation.required());
                buffer.writeVarLong(Math.max(0L, operation.ticksLeft()));
                buffer.writeUtf(operation.targetType(), 80);
            }

            buffer.writeVarInt(front.income().size());
            for (ResourceEntry income : front.income()) {
                buffer.writeEnum(income.resource());
                buffer.writeVarInt(income.amount());
            }

            buffer.writeVarInt(front.deployments().size());
            for (DeploymentEntry deployment : front.deployments()) {
                buffer.writeUtf(deployment.id(), 160);
                buffer.writeEnum(deployment.faction());
                buffer.writeEnum(deployment.state());
                buffer.writeVarInt(deployment.strength());
                buffer.writeVarInt(deployment.materialised());
                buffer.writeUtf(deployment.targetSectorId(), 160);
                buffer.writeBoolean(deployment.playerOrdered());
            }
        }

        buffer.writeVarInt(this.routes.size());

        for (RouteEntry route : this.routes) {
            buffer.writeUtf(route.originPath(), 80);
            buffer.writeUtf(route.destinationPath(), 80);
            buffer.writeEnum(route.resource());
            buffer.writeVarInt(route.delivered());
            buffer.writeVarInt(route.amount());
            buffer.writeEnum(route.state());
            buffer.writeUtf(route.reason(), 200);
            buffer.writeUtf(route.reasonArg(), 200);
        }
    }

    /**
     * Reads a snapshot back.
     *
     * <p>Every field is read into its own local before the record is built. A buffer is a cursor:
     * putting two reads inside one constructor call makes their order depend on the language's
     * evaluation order, and that is how a decoder starts reading the wrong field with nothing
     * reporting an error.
     */
    public static WarTableSnapshot read(FriendlyByteBuf buffer) {
        int crusadeScore = buffer.readVarInt();
        int dominion = buffer.readVarInt() - 100;
        int conquered = buffer.readVarInt();
        int lost = buffer.readVarInt();
        int brokenLanes = buffer.readVarInt();
        String currentFront = buffer.readUtf(160);
        BlockPos tablePos = buffer.readBlockPos();

        int frontCount = buffer.readVarInt();
        List<FrontEntry> fronts = new ArrayList<>(frontCount);

        for (int i = 0; i < frontCount; i++) {
            String frontId = buffer.readUtf(160);
            String frontPath = buffer.readUtf(80);
            PlanetCampaignState state = buffer.readEnum(PlanetCampaignState.class);
            WarIntensity intensity = buffer.readEnum(WarIntensity.class);
            int imperial = buffer.readVarInt();
            int ork = buffer.readVarInt();
            int necron = buffer.readVarInt();
            int contested = buffer.readVarInt();
            int cities = buffer.readVarInt();
            int camps = buffer.readVarInt();
            int awakening = buffer.readVarInt();
            String objectiveKey = buffer.readUtf(160);
            String objectiveTarget = buffer.readUtf(80);
            String lastEvent = buffer.readUtf(200);

            int sectorCount = buffer.readVarInt();
            List<SectorEntry> sectors = new ArrayList<>(sectorCount);

            for (int s = 0; s < sectorCount; s++) {
                String id = buffer.readUtf(160);
                SectorType type = buffer.readEnum(SectorType.class);
                WarFaction owner = buffer.readEnum(WarFaction.class);
                int contest = buffer.readVarInt() - 100;
                boolean disputed = buffer.readBoolean();
                int x = buffer.readInt();
                int z = buffer.readInt();

                sectors.add(new SectorEntry(id, type, owner, contest, disputed, x, z));
            }

            int operationCount = buffer.readVarInt();
            List<OperationEntry> operations = new ArrayList<>(operationCount);

            for (int o = 0; o < operationCount; o++) {
                String id = buffer.readUtf(160);
                OperationType type = buffer.readEnum(OperationType.class);
                OperationState opState = buffer.readEnum(OperationState.class);
                int progress = buffer.readVarInt();
                int required = buffer.readVarInt();
                long ticksLeft = buffer.readVarLong();
                String targetType = buffer.readUtf(80);

                operations.add(new OperationEntry(id, type, opState, progress, required,
                        ticksLeft, targetType));
            }

            int incomeCount = buffer.readVarInt();
            List<ResourceEntry> income = new ArrayList<>(incomeCount);

            for (int r = 0; r < incomeCount; r++) {
                StrategicResourceType resource = buffer.readEnum(StrategicResourceType.class);
                int amount = buffer.readVarInt();

                income.add(new ResourceEntry(resource, amount));
            }

            int deploymentCount = buffer.readVarInt();
            List<DeploymentEntry> deployments = new ArrayList<>(deploymentCount);

            for (int d = 0; d < deploymentCount; d++) {
                String id = buffer.readUtf(160);
                com.example.examplemod.campaign.war.WarFaction faction =
                        buffer.readEnum(com.example.examplemod.campaign.war.WarFaction.class);
                com.example.examplemod.campaign.force.DeploymentState deploymentState =
                        buffer.readEnum(com.example.examplemod.campaign.force.DeploymentState.class);
                int strength = buffer.readVarInt();
                int materialised = buffer.readVarInt();
                String targetSectorId = buffer.readUtf(160);
                boolean playerOrdered = buffer.readBoolean();

                deployments.add(new DeploymentEntry(id, faction, deploymentState, strength,
                        materialised, targetSectorId, playerOrdered));
            }

            fronts.add(new FrontEntry(frontId, frontPath, state, intensity, imperial, ork, necron,
                    contested, cities, camps, awakening, objectiveKey, objectiveTarget, lastEvent,
                    sectors, operations, income, deployments));
        }

        int routeCount = buffer.readVarInt();
        List<RouteEntry> routes = new ArrayList<>(routeCount);

        for (int i = 0; i < routeCount; i++) {
            String origin = buffer.readUtf(80);
            String destination = buffer.readUtf(80);
            StrategicResourceType resource = buffer.readEnum(StrategicResourceType.class);
            int delivered = buffer.readVarInt();
            int amount = buffer.readVarInt();
            SupplyState routeState = buffer.readEnum(SupplyState.class);
            String reason = buffer.readUtf(200);
            String reasonArg = buffer.readUtf(200);

            routes.add(new RouteEntry(origin, destination, resource, delivered, amount,
                    routeState, reason, reasonArg));
        }

        return new WarTableSnapshot(crusadeScore, dominion, conquered, lost, brokenLanes,
                currentFront, tablePos, fronts, routes);
    }

    /** The index of the front the player is standing on, or 0 when they are not on one. */
    public int currentFrontIndex() {
        for (int i = 0; i < this.fronts.size(); i++) {
            if (this.fronts.get(i).frontId().equals(this.currentFront)) {
                return i;
            }
        }

        return 0;
    }
}
