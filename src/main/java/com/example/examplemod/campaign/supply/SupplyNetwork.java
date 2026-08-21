package com.example.examplemod.campaign.supply;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.example.examplemod.StrategicResourceType;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.CampaignFront;
import com.example.examplemod.campaign.CampaignFronts;
import com.example.examplemod.campaign.CampaignLog;
import com.example.examplemod.campaign.sector.SectorType;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.war.WarFaction;
import com.example.examplemod.planet.FCPlanets;

import net.minecraft.resources.ResourceLocation;

/**
 * The Crusade's logistics: who produces what, where it goes, and what stops it.
 *
 * <h2>Production is the sectors, and nothing else</h2>
 *
 * A front produces exactly what its Imperial-held sectors produce — {@link SectorType#output()} and
 * {@link SectorType#outputAmount()}, summed. Nothing else contributes. That is what makes losing a
 * refinery mean something across the whole campaign rather than only on the planet it stood on:
 * Armageddon's promethium is the refinery, so the refinery falling is felt on every world the lane
 * fed.
 *
 * <h2>The lanes are the fiction, written down</h2>
 *
 * {@link #LANES} is a fixed table because the Crusade's dependencies are a design statement, not an
 * emergent one: Verdanis feeds the war, the Forge World arms it, the Hive supplies its bodies. A
 * route that generated itself from whoever happened to have a surplus would produce a supply map
 * with no shape, and no reason for any particular planet to be defended.
 *
 * <h2>What blocks a lane</h2>
 *
 * A Spaceport is how anything leaves or reaches a world, so an enemy holding either end's Spaceport
 * {@link SupplyState#BLOCKED}s the lane outright. A front merely under heavy attack
 * {@link SupplyState#DISRUPTED}s it to half. This is the mechanism the brief asks for by name, and
 * it is deliberately the <i>only</i> thing that can cut a lane: one rule the player can learn and
 * act on beats five they have to discover.
 *
 * <h2>Cost</h2>
 *
 * One pass is a walk over the fronts' sectors — the same dozens the campaign already walks — plus a
 * walk over a fixed table of a dozen lanes. No world access, no entities.
 */
public final class SupplyNetwork {

    private SupplyNetwork() {
    }

    /**
     * One entry of the Crusade's supply map: a producer, a consumer, and what flows.
     *
     * @param nominal the lane's rated throughput before the origin's actual production caps it
     */
    private record Lane(ResourceLocation origin, ResourceLocation destination,
                        StrategicResourceType resource, int nominal) {
    }

    private static final List<Lane> LANES = buildLanes();

    private static List<Lane> buildLanes() {
        List<Lane> lanes = new ArrayList<>();

        ResourceLocation macragge = FCPlanets.MACRAGGE.location();
        ResourceLocation verdanis = FCPlanets.AGRI_WORLD.location();
        ResourceLocation armageddon = FCPlanets.ARMAGEDDON.location();
        ResourceLocation cadia = FCPlanets.CADIA.location();
        ResourceLocation forge = FCPlanets.FORGE_WORLD.location();
        ResourceLocation catachan = FCPlanets.CATACHAN.location();
        ResourceLocation valhalla = FCPlanets.VALHALLA.location();
        ResourceLocation hive = com.example.examplemod.hive.city.HiveWorld.LEVEL.location();

        // Verdanis feeds the war. Four lanes, because a granary with one customer is not a granary.
        lanes.add(new Lane(verdanis, armageddon, StrategicResourceType.FOOD, 40));
        lanes.add(new Lane(verdanis, cadia, StrategicResourceType.FOOD, 30));
        lanes.add(new Lane(verdanis, valhalla, StrategicResourceType.FOOD, 30));
        lanes.add(new Lane(verdanis, macragge, StrategicResourceType.FOOD, 20));

        // The Forge World arms it.
        lanes.add(new Lane(forge, armageddon, StrategicResourceType.PLASTEEL, 30));
        lanes.add(new Lane(forge, cadia, StrategicResourceType.PLASTEEL, 25));
        lanes.add(new Lane(forge, macragge, StrategicResourceType.CERAMITE, 15));

        // Armageddon's refineries fuel it — including, pointedly, the Forge World that arms it.
        lanes.add(new Lane(armageddon, forge, StrategicResourceType.PROMETHIUM, 30));
        lanes.add(new Lane(armageddon, valhalla, StrategicResourceType.PROMETHIUM, 20));

        // The Hive supplies the bodies.
        lanes.add(new Lane(hive, armageddon, StrategicResourceType.MANPOWER, 30));
        lanes.add(new Lane(hive, cadia, StrategicResourceType.MANPOWER, 25));
        lanes.add(new Lane(hive, catachan, StrategicResourceType.MANPOWER, 10));

        // Macragge sends iron out to the fronts it commands.
        lanes.add(new Lane(macragge, armageddon, StrategicResourceType.IRON, 25));
        lanes.add(new Lane(macragge, cadia, StrategicResourceType.IRON, 20));

        return List.copyOf(lanes);
    }

    // ====================================================================================
    // The pass
    // ====================================================================================

    /**
     * Recomputes production, then every lane's state and delivery.
     *
     * <p>Called once per strategic pass, after every front's control has been brought up to date —
     * a lane's state is a question about who holds what, so asking it before that is asking about
     * last pass's war.
     */
    public static void recompute(CampaignData campaign) {
        Map<ResourceLocation, Map<StrategicResourceType, Integer>> production = new java.util.HashMap<>();
        Map<ResourceLocation, Boolean> spaceportHeld = new java.util.HashMap<>();
        Map<ResourceLocation, Boolean> underAttack = new java.util.HashMap<>();

        for (CampaignFront front : CampaignFronts.all()) {
            List<StrategicSector> sectors = campaign.sectorsOn(front.dimension());

            production.put(front.id(), produceOn(sectors));
            spaceportHeld.put(front.id(), holdsSpaceport(sectors));
            underAttack.put(front.id(), isUnderAttack(campaign, front));
        }

        for (Lane lane : LANES) {
            SupplyRoute route = campaign.supplyRoute(lane.origin(), lane.destination(), lane.resource());

            int available = production
                    .getOrDefault(lane.origin(), Map.of())
                    .getOrDefault(lane.resource(), 0);

            // A lane carries what it is rated for or what the origin actually makes, whichever is
            // less. That is the whole reason losing a refinery is felt on another planet.
            int rated = Math.min(lane.nominal(), available);
            route.setAmount(rated);

            SupplyState state = evaluate(lane, spaceportHeld, underAttack, available);
            Reason reason = reasonFor(lane, spaceportHeld, underAttack, available);
            SupplyState previous = route.setState(state, reason.key(), reason.arg());

            route.setDelivered((int) Math.round(rated * state.throughput()));

            if (previous != null) {
                CampaignLog.war("supply {} -> {} [{}] {} -> {} ({} {})",
                        lane.origin().getPath(), lane.destination().getPath(),
                        lane.resource().name(), previous.name(), state.name(),
                        reason.key(), reason.arg());
            }
        }

        campaign.setDirty();
    }

    private static SupplyState evaluate(Lane lane, Map<ResourceLocation, Boolean> spaceportHeld,
                                        Map<ResourceLocation, Boolean> underAttack, int available) {
        // Nothing to send is not a blockade, it is an empty warehouse. Distinguishing the two is
        // what stops the War Table telling a player to go retake a spaceport that is already theirs.
        if (available <= 0) {
            return SupplyState.DESTROYED;
        }

        // Either end's spaceport in enemy hands stops the lane. Both ends, because a cargo needs
        // somewhere to leave from and somewhere to arrive at.
        if (!spaceportHeld.getOrDefault(lane.origin(), true)
                || !spaceportHeld.getOrDefault(lane.destination(), true)) {
            return SupplyState.BLOCKED;
        }

        if (underAttack.getOrDefault(lane.origin(), false)
                || underAttack.getOrDefault(lane.destination(), false)) {
            return SupplyState.DISRUPTED;
        }

        return SupplyState.ACTIVE;
    }

    /**
     * Why a lane is in the state it is: a translation key and the single key it substitutes.
     *
     * <p>Both halves are keys, never prose. The argument is a planet key for the lane reasons and a
     * bare resource name for the empty-warehouse one — the reader feeds both to
     * {@code Component.translatable}, and the resource, having no language entry, comes out as
     * itself. That is what lets one drawing path handle both without asking which it received.
     */
    private record Reason(String key, String arg) {

        private static final Reason NONE = new Reason("", "");

        static Reason of(String key, ResourceLocation planet) {
            return new Reason(key, "planet." + com.example.examplemod.ExampleMod.MODID + "."
                    + planet.getPath());
        }
    }

    private static final String REASON_ROOT = "supply." + com.example.examplemod.ExampleMod.MODID
            + ".reason.";

    private static Reason reasonFor(Lane lane, Map<ResourceLocation, Boolean> spaceportHeld,
                                    Map<ResourceLocation, Boolean> underAttack, int available) {
        if (available <= 0) {
            return new Reason(REASON_ROOT + "no_output", lane.resource().getDisplayName());
        }

        if (!spaceportHeld.getOrDefault(lane.origin(), true)) {
            return Reason.of(REASON_ROOT + "spaceport_lost", lane.origin());
        }

        if (!spaceportHeld.getOrDefault(lane.destination(), true)) {
            return Reason.of(REASON_ROOT + "spaceport_lost", lane.destination());
        }

        if (underAttack.getOrDefault(lane.origin(), false)) {
            return Reason.of(REASON_ROOT + "under_attack", lane.origin());
        }

        if (underAttack.getOrDefault(lane.destination(), false)) {
            return Reason.of(REASON_ROOT + "under_attack", lane.destination());
        }

        return Reason.NONE;
    }

    // ====================================================================================
    // Front readings
    // ====================================================================================

    /** Everything this front's Imperial-held sectors produce, summed by resource. */
    public static Map<StrategicResourceType, Integer> produceOn(List<StrategicSector> sectors) {
        Map<StrategicResourceType, Integer> output = new EnumMap<>(StrategicResourceType.class);

        for (StrategicSector sector : sectors) {
            if (sector.owner() != WarFaction.IMPERIUM || !sector.type().produces()) {
                continue;
            }

            // A sector both sides have force on delivers at half. It is still ours; the lorries are
            // just being shot at.
            int amount = sector.isDisputed()
                    ? Math.max(1, sector.type().outputAmount() / 2)
                    : sector.type().outputAmount();

            output.merge(sector.type().output(), amount, Integer::sum);
        }

        return output;
    }

    /**
     * True when the Imperium holds a Spaceport here, or when the front has no Spaceport at all.
     *
     * <p>The second half matters: a world with no Spaceport sector in its layout must not be treated
     * as blockaded forever. Catachan has no Spaceport, and the Hive World's is a different kind of
     * thing entirely — neither should silently kill every lane it touches.
     */
    private static boolean holdsSpaceport(List<StrategicSector> sectors) {
        boolean found = false;

        for (StrategicSector sector : sectors) {
            if (sector.type() != SectorType.SPACEPORT) {
                continue;
            }

            found = true;

            if (sector.owner() == WarFaction.IMPERIUM) {
                return true;
            }
        }

        return !found;
    }

    private static boolean isUnderAttack(CampaignData campaign, CampaignFront front) {
        var state = campaign.existingState(front.id());

        if (state == null) {
            return false;
        }

        return switch (state.intensity()) {
            case HEAVY_FIGHTING, TOTAL_WAR -> true;
            default -> false;
        };
    }

    // ====================================================================================
    // Reading, for the War Table and the commands
    // ====================================================================================

    /** What this front actually receives per pass, summed across every lane into it. */
    public static Map<StrategicResourceType, Integer> incoming(CampaignData campaign,
                                                               ResourceLocation frontId) {
        Map<StrategicResourceType, Integer> total = new EnumMap<>(StrategicResourceType.class);

        for (SupplyRoute route : campaign.supplyRoutes()) {
            if (route.destination().equals(frontId) && route.delivered() > 0) {
                total.merge(route.resource(), route.delivered(), Integer::sum);
            }
        }

        return total;
    }

    /** Every lane touching this front, either end. */
    public static List<SupplyRoute> routesTouching(CampaignData campaign, ResourceLocation frontId) {
        List<SupplyRoute> found = new ArrayList<>();

        for (SupplyRoute route : campaign.supplyRoutes()) {
            if (route.origin().equals(frontId) || route.destination().equals(frontId)) {
                found.add(route);
            }
        }

        return found;
    }

    /** How many lanes are not delivering, across the whole network. The War Table's one-line warning. */
    public static int brokenLanes(CampaignData campaign) {
        return brokenLanes(campaign.supplyRoutes());
    }

    /**
     * The same count over a subset.
     *
     * <p>Exists because a caller that has already filtered the routes — {@code /fcstrategy supply
     * list <front>} shows one front's lanes — must count against what it printed. Pairing a filtered
     * list with the global total reads as "3 of these 4 are broken" and says so even when all four
     * on screen are ACTIVE.
     */
    public static int brokenLanes(java.util.Collection<SupplyRoute> routes) {
        int broken = 0;

        for (SupplyRoute route : routes) {
            if (!route.state().carries()) {
                broken++;
            }
        }

        return broken;
    }
}
