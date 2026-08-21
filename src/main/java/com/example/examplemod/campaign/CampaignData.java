package com.example.examplemod.campaign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.example.examplemod.StrategicResourceType;
import com.example.examplemod.campaign.force.StrategicDeployment;
import com.example.examplemod.campaign.operation.Operation;
import com.example.examplemod.campaign.planet.PlanetWarState;
import com.example.examplemod.campaign.sector.PlanetSectorBlueprints;
import com.example.examplemod.campaign.sector.SectorBlueprint;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.supply.SupplyRoute;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Everything the Crusade knows about every front, in one place.
 *
 * <h2>One store, deliberately global</h2>
 *
 * Kept on the overworld's data storage, like the war map and for the same reason: the War Table has
 * to draw Armageddon while the player is standing on Macragge, and a per-level store cannot be read
 * without loading the level. That would turn opening a menu into loading nine dimensions.
 *
 * <p>The thing that made "store it on the overworld" a bug everywhere else was storing it there
 * <i>and</i> keying it by position alone. Everything here is keyed by front id, and every sector
 * carries a {@link StrategicLocation}, so a global store is exactly right.
 *
 * <h2>Sectors are laid out once</h2>
 *
 * A front's sectors are materialised from {@link PlanetSectorBlueprints} the first time it is
 * activated, and persisted from then on. They are not regenerated on load: a sector that changed
 * hands must stay changed, and rebuilding from the blueprint would quietly hand every world back to
 * whoever the table says starts with it.
 *
 * <p>New blueprints added to an existing save <i>are</i> picked up — {@link #activateFront} adds any
 * sector id the front does not already have, without touching the ones it does. That is what lets a
 * planet gain a sector in a later build without a world reset.
 */
public class CampaignData extends SavedData {
    private static final String NAME = "firstcrusade_campaign";

    private static final int FORMAT_VERSION = 1;

    private final Map<ResourceLocation, PlanetWarState> fronts = new LinkedHashMap<>();
    private final Map<String, StrategicSector> sectors = new LinkedHashMap<>();
    private final Map<String, Operation> operations = new LinkedHashMap<>();
    private final Map<String, SupplyRoute> routes = new LinkedHashMap<>();
    private final Map<String, StrategicDeployment> deployments = new LinkedHashMap<>();

    public CampaignData() {
    }

    public static CampaignData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(CampaignData::load, CampaignData::new, NAME);
    }

    // ====================================================================================
    // Fronts
    // ====================================================================================

    /** This front's state, created at its campaign start if it has none yet. */
    public PlanetWarState stateOf(CampaignFront front) {
        return this.fronts.computeIfAbsent(front.id(), id -> {
            setDirty();
            return new PlanetWarState(id);
        });
    }

    @Nullable
    public PlanetWarState existingState(ResourceLocation frontId) {
        return this.fronts.get(frontId);
    }

    public Collection<PlanetWarState> states() {
        return this.fronts.values();
    }

    /**
     * Lays out a front's sectors, if they are not already laid out.
     *
     * <p>Idempotent by sector id: calling it again after a build added blueprints creates only the
     * new ones. Nothing is written to the world — a sector is a strategic marker, not a structure.
     *
     * @param anchor where the layout is centred, normally the front's spawn
     * @return how many sectors were created
     */
    public int activateFront(CampaignFront front, BlockPos anchor) {
        List<SectorBlueprint> blueprints = PlanetSectorBlueprints.forFront(front.dimension());

        if (blueprints.isEmpty()) {
            return 0;
        }

        PlanetWarState state = stateOf(front);

        if (state.anchor().equals(BlockPos.ZERO)) {
            state.setAnchor(anchor);
        }

        // Always lay out around the anchor the front was FIRST activated with, not the one passed
        // now. Otherwise a second traveller landing a thousand blocks away would scatter any new
        // sector far from the ones already on the map.
        BlockPos origin = state.anchor();
        int created = 0;

        for (SectorBlueprint blueprint : blueprints) {
            String id = blueprint.idOn(front.dimension());

            if (this.sectors.containsKey(id)) {
                continue;
            }

            this.sectors.put(id, blueprint.materialise(front.dimension(), origin));
            created++;
        }

        if (created > 0 || !state.sectorsLaidOut()) {
            state.markSectorsLaidOut();
            setDirty();
        }

        if (created > 0) {
            CampaignLog.war("{} front activated: {} sector(s) laid out around {}",
                    front.path(), created, origin);
        }

        return created;
    }

    // ====================================================================================
    // Sectors
    // ====================================================================================

    @Nullable
    public StrategicSector sector(String id) {
        return this.sectors.get(id);
    }

    /**
     * Every sector on one front.
     *
     * <p>A linear filter over a map whose size is measured in dozens across the whole save. An index
     * per front would be a second structure to keep in step with this one, and the pass that reads it
     * runs every ten seconds, not every tick.
     */
    public List<StrategicSector> sectorsOn(ResourceKey<Level> dimension) {
        List<StrategicSector> found = new ArrayList<>();

        for (StrategicSector sector : this.sectors.values()) {
            if (sector.dimension().equals(dimension)) {
                found.add(sector);
            }
        }

        return found;
    }

    public Collection<StrategicSector> allSectors() {
        return this.sectors.values();
    }

    public int sectorCount() {
        return this.sectors.size();
    }

    /**
     * The sector nearest a position <b>on the same world</b>, within a radius.
     *
     * <p>The dimension is carried by the location rather than passed alongside it, which is the
     * whole point of {@link StrategicLocation}: there is no way to write this method such that it
     * accidentally answers with a sector on another planet.
     */
    @Nullable
    public StrategicSector nearestSector(StrategicLocation from, double maxDistance) {
        StrategicSector best = null;
        double bestDistance = maxDistance * maxDistance;

        for (StrategicSector sector : this.sectors.values()) {
            double distance = sector.location().distanceSqrTo(from);

            if (distance <= bestDistance) {
                bestDistance = distance;
                best = sector;
            }
        }

        return best;
    }

    // ====================================================================================
    // Operations
    // ====================================================================================

    /**
     * How long a finished order stays on the books before it is swept away. Long enough that a
     * player who was mid-fight when it completed still finds it on the War Table; short enough that
     * the list is a picture of the war rather than a ledger of it.
     */
    private static final long FINISHED_OPERATION_GRACE_TICKS = 6000L;

    public void addOperation(Operation operation) {
        this.operations.put(operation.id(), operation);
        setDirty();
    }

    @Nullable
    public Operation operation(String id) {
        return this.operations.get(id);
    }

    public Collection<Operation> allOperations() {
        return this.operations.values();
    }

    /**
     * The orders currently standing on one front.
     *
     * <p>Returns a copy rather than a view: callers advance and complete operations while iterating,
     * and completing one can issue another. Iterating the live map through that is a
     * {@link java.util.ConcurrentModificationException} waiting for the first player who finishes
     * two objectives with one kill.
     */
    public List<Operation> activeOperationsOn(ResourceLocation frontId) {
        List<Operation> found = new ArrayList<>();

        for (Operation operation : this.operations.values()) {
            if (operation.isActive() && operation.frontId().equals(frontId)) {
                found.add(operation);
            }
        }

        return found;
    }

    /** Every order on one front, finished ones included, for the War Table and the commands. */
    public List<Operation> operationsOn(ResourceLocation frontId) {
        List<Operation> found = new ArrayList<>();

        for (Operation operation : this.operations.values()) {
            if (operation.frontId().equals(frontId)) {
                found.add(operation);
            }
        }

        return found;
    }

    /** Drops finished orders whose grace period has run out. */
    public void retireFinishedOperations(ResourceLocation frontId, long gameTime) {
        boolean removed = this.operations.values().removeIf(operation ->
                operation.frontId().equals(frontId)
                        && operation.state().isFinished()
                        && gameTime - operation.finishedAt() > FINISHED_OPERATION_GRACE_TICKS);

        if (removed) {
            setDirty();
        }
    }

    // ====================================================================================
    // Deployments
    // ====================================================================================

    /** How long a spent deployment stays visible on the War Table before it is swept away. */
    private static final long SPENT_DEPLOYMENT_GRACE_TICKS = 2400L;

    public void addDeployment(StrategicDeployment deployment) {
        this.deployments.put(deployment.id(), deployment);
        setDirty();
    }

    @Nullable
    public StrategicDeployment deployment(String id) {
        return this.deployments.get(id);
    }

    public Collection<StrategicDeployment> allDeployments() {
        return this.deployments.values();
    }

    /**
     * Live deployments on one front, as a copy.
     *
     * <p>A copy for the same reason {@link #activeOperationsOn} returns one: the caller advances and
     * spends deployments while iterating, and a spent one can end a fight, which can raise another.
     */
    public List<StrategicDeployment> activeDeploymentsOn(ResourceLocation frontId) {
        List<StrategicDeployment> found = new ArrayList<>();

        for (StrategicDeployment deployment : this.deployments.values()) {
            if (deployment.isActive() && deployment.frontId().equals(frontId)) {
                found.add(deployment);
            }
        }

        return found;
    }

    /** Every deployment on one front, spent ones included, for the War Table. */
    public List<StrategicDeployment> deploymentsOn(ResourceLocation frontId) {
        List<StrategicDeployment> found = new ArrayList<>();

        for (StrategicDeployment deployment : this.deployments.values()) {
            if (deployment.frontId().equals(frontId)) {
                found.add(deployment);
            }
        }

        return found;
    }

    /** How many live deployments one faction already has on a front — the cap orders respect. */
    public int countActiveDeployments(ResourceLocation frontId,
                                      com.example.examplemod.campaign.war.WarFaction faction) {
        int count = 0;

        for (StrategicDeployment deployment : this.deployments.values()) {
            if (deployment.isActive() && deployment.frontId().equals(frontId)
                    && deployment.faction() == faction) {
                count++;
            }
        }

        return count;
    }

    public void retireSpentDeployments(ResourceLocation frontId, long gameTime) {
        boolean removed = this.deployments.values().removeIf(deployment ->
                deployment.frontId().equals(frontId)
                        && !deployment.isActive()
                        && gameTime - deployment.issuedAt() > SPENT_DEPLOYMENT_GRACE_TICKS);

        if (removed) {
            setDirty();
        }
    }

    // ====================================================================================
    // Supply
    // ====================================================================================

    /**
     * The lane for this origin/destination/resource, created empty if the save has never had one.
     *
     * <p>Created on demand rather than seeded at world start, so a build that adds a lane to
     * {@link com.example.examplemod.campaign.supply.SupplyNetwork}'s table picks it up on an existing
     * save without a reset — and a lane removed from the table simply stops being recomputed, keeping
     * its last state until {@link #reset} clears it.
     */
    public SupplyRoute supplyRoute(ResourceLocation origin, ResourceLocation destination,
                                   StrategicResourceType resource) {
        return this.routes.computeIfAbsent(SupplyRoute.idOf(origin, destination, resource), id -> {
            setDirty();
            return new SupplyRoute(origin, destination, resource);
        });
    }

    public Collection<SupplyRoute> supplyRoutes() {
        return this.routes.values();
    }

    @Nullable
    public SupplyRoute supplyRoute(String id) {
        return this.routes.get(id);
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public static CampaignData load(CompoundTag tag) {
        CampaignData data = new CampaignData();

        ListTag frontList = tag.getList("Fronts", Tag.TAG_COMPOUND);

        for (int i = 0; i < frontList.size(); i++) {
            PlanetWarState state = PlanetWarState.load(frontList.getCompound(i));

            if (state == null) {
                // A front whose id no longer parses: an installation that dropped a dimension. Drop
                // the record rather than crashing the save it is in.
                CampaignLog.warn("dropped an unreadable front record while loading the campaign");
                continue;
            }

            data.fronts.put(state.frontId(), state);
        }

        ListTag sectorList = tag.getList("Sectors", Tag.TAG_COMPOUND);

        for (int i = 0; i < sectorList.size(); i++) {
            StrategicSector sector = StrategicSector.load(sectorList.getCompound(i));

            if (sector.id().isEmpty()) {
                continue;
            }

            data.sectors.put(sector.id(), sector);
        }

        ListTag operationList = tag.getList("Operations", Tag.TAG_COMPOUND);

        for (int i = 0; i < operationList.size(); i++) {
            Operation operation = Operation.load(operationList.getCompound(i));

            if (operation == null || operation.id().isEmpty()) {
                continue;
            }

            data.operations.put(operation.id(), operation);
        }

        ListTag deploymentList = tag.getList("Deployments", Tag.TAG_COMPOUND);

        for (int i = 0; i < deploymentList.size(); i++) {
            StrategicDeployment deployment = StrategicDeployment.load(deploymentList.getCompound(i));

            if (deployment == null || deployment.id().isEmpty()) {
                continue;
            }

            data.deployments.put(deployment.id(), deployment);
        }

        ListTag routeList = tag.getList("Routes", Tag.TAG_COMPOUND);

        for (int i = 0; i < routeList.size(); i++) {
            SupplyRoute route = SupplyRoute.load(routeList.getCompound(i));

            if (route == null) {
                // A lane naming a front or a resource this installation no longer has. Dropping it
                // is safe: the network recreates every lane in its table on the next pass.
                continue;
            }

            data.routes.put(route.id(), route);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", FORMAT_VERSION);

        ListTag frontList = new ListTag();

        for (PlanetWarState state : this.fronts.values()) {
            frontList.add(state.save());
        }

        tag.put("Fronts", frontList);

        ListTag sectorList = new ListTag();

        for (StrategicSector sector : this.sectors.values()) {
            sectorList.add(sector.save());
        }

        tag.put("Sectors", sectorList);

        ListTag operationList = new ListTag();

        for (Operation operation : this.operations.values()) {
            operationList.add(operation.save());
        }

        tag.put("Operations", operationList);

        ListTag deploymentList = new ListTag();

        for (StrategicDeployment deployment : this.deployments.values()) {
            deploymentList.add(deployment.save());
        }

        tag.put("Deployments", deploymentList);

        ListTag routeList = new ListTag();

        for (SupplyRoute route : this.routes.values()) {
            routeList.add(route.save());
        }

        tag.put("Routes", routeList);

        return tag;
    }

    /** Wipes the campaign back to nothing. For {@code /fcstrategy war reset}. */
    public void reset() {
        this.fronts.clear();
        this.sectors.clear();
        this.operations.clear();
        this.routes.clear();
        this.deployments.clear();
        setDirty();
    }

    /** Wipes one front, so the next activation lays it out again. */
    public boolean resetFront(CampaignFront front) {
        boolean removed = this.fronts.remove(front.id()) != null;
        removed |= this.sectors.keySet().removeIf(id -> id.startsWith(front.path() + "."));
        removed |= this.operations.values().removeIf(op -> op.frontId().equals(front.id()));
        removed |= this.deployments.values().removeIf(d -> d.frontId().equals(front.id()));

        if (removed) {
            setDirty();
        }

        return removed;
    }
}
