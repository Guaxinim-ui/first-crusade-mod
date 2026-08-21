package com.example.examplemod.campaign.sector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.examplemod.campaign.war.WarFaction;
import com.example.examplemod.hive.city.HiveWorld;
import com.example.examplemod.planet.FCPlanets;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * What each world is <i>for</i>, expressed as the ground it is fought over.
 *
 * <h2>This table is the planets' identity</h2>
 *
 * Terrain already differs from world to world. Terrain is not identity — a jungle with the same
 * eight sectors as a hive world is a reskin, which is exactly what the campaign is meant to stop
 * being. What makes Cadia a fortress world is that almost everything on it is a defensive work and
 * the Imperium starts holding all of it; what makes Ork World the seat of the WAAAGH! is that the
 * Imperium starts with a single beachhead and everything else is green.
 *
 * <p>So the differences here are real differences:
 *
 * <ul>
 *   <li><b>Macragge</b> — the Crusade's own capital. Entirely Imperial, no enemy holding at all,
 *       which is what makes it the safe place the brief asks for.</li>
 *   <li><b>Verdanis</b> — food. Four of its seven sectors produce {@code FOOD}, so losing the planet
 *       is felt everywhere else rather than only here.</li>
 *   <li><b>Armageddon</b> — total war. The only world that starts genuinely split, with an Ork
 *       stronghold as heavy as the Hive facing it.</li>
 *   <li><b>Cadia</b> — fortress world. Six defensive works, all Imperial, all with high
 *       {@link SectorType#defence()}: a planet that is hard to take and hard to retake.</li>
 *   <li><b>Forge World</b> — industry. The only world with a vehicle factory, and the reason
 *       Promethium matters.</li>
 *   <li><b>Ork World</b> — the WAAAGH!. One Imperial landing pad against a Warboss.</li>
 *   <li><b>Necron Tomb World</b> — nothing is awake. Every sector is Necron-owned but the planet's
 *       awakening level is what governs whether any of it acts. No entities exist for it yet, and
 *       nothing here spawns one.</li>
 *   <li><b>Catachan</b> — the planet itself is the enemy. Mostly neutral wilderness, small
 *       garrisons, no armies — danger without a front line.</li>
 *   <li><b>Valhalla</b> — a logistics war in the snow: depots and buried bunkers, and the sectors
 *       that matter are the ones that keep the line supplied.</li>
 *   <li><b>Hive World</b> — the vertical campaign, one sector per level of the Hive.</li>
 * </ul>
 *
 * <p>Angles are spread rather than even so a front does not read as a clock face, and distances are
 * inside the planets' 5000-block border with room to spare.
 */
public final class PlanetSectorBlueprints {

    private static final Map<ResourceLocation, List<SectorBlueprint>> BY_PLANET = new HashMap<>();

    private PlanetSectorBlueprints() {
    }

    static {
        // ---------------------------------------------------------------- Macragge: the capital
        put(FCPlanets.MACRAGGE, List.of(
                SectorBlueprint.of("strategium", SectorType.COMMAND_BASTION, 0, 220),
                SectorBlueprint.of("spaceport", SectorType.SPACEPORT, 55, 340),
                SectorBlueprint.of("fortress_gate", SectorType.FORTRESS, 130, 420),
                SectorBlueprint.of("armory", SectorType.ARMORY, 200, 300),
                SectorBlueprint.of("medicae", SectorType.MEDICAE_STATION, 255, 260),
                SectorBlueprint.of("relay", SectorType.COMMAND_RELAY, 310, 380),
                SectorBlueprint.of("depot", SectorType.SUPPLY_DEPOT, 165, 500)));

        // ---------------------------------------------------------------- Verdanis: the granary
        put(FCPlanets.AGRI_WORLD, List.of(
                SectorBlueprint.of("north_fields", SectorType.AGRI_FIELDS, 10, 300),
                SectorBlueprint.of("south_fields", SectorType.AGRI_FIELDS, 190, 340),
                SectorBlueprint.of("grox_ranch", SectorType.GROX_RANCH, 95, 420),
                SectorBlueprint.of("silo", SectorType.SILO, 145, 260),
                SectorBlueprint.of("processing", SectorType.PROCESSING_FACILITY, 240, 320),
                SectorBlueprint.of("spaceport", SectorType.SPACEPORT, 300, 380),
                SectorBlueprint.of("raider_camp", SectorType.SCRAP_FORT, WarFaction.ORKS, 65, 620)));

        // ---------------------------------------------------------------- Armageddon: total war
        put(FCPlanets.ARMAGEDDON, List.of(
                SectorBlueprint.of("hive", SectorType.HIVE, 0, 280),
                SectorBlueprint.of("manufactorum", SectorType.MANUFACTORUM, 45, 420),
                SectorBlueprint.of("refinery", SectorType.REFINERY, 85, 520),
                SectorBlueprint.of("spaceport", SectorType.SPACEPORT, 320, 360),
                SectorBlueprint.of("hemlock_line", SectorType.DEFENSIVE_LINE, 130, 460),
                SectorBlueprint.of("east_bunkers", SectorType.BUNKER_COMPLEX, 100, 380),
                SectorBlueprint.of("ash_trenches", SectorType.TRENCH_NETWORK, 155, 300),
                SectorBlueprint.of("ork_stronghold", SectorType.ORK_STRONGHOLD, 195, 640),
                SectorBlueprint.of("scrap_fort", SectorType.SCRAP_FORT, 225, 520),
                SectorBlueprint.of("mek_works", SectorType.MEK_WORKSHOP, 250, 600),
                SectorBlueprint.of("loot_pits", SectorType.LOOT_PIT, 215, 420)));

        // ---------------------------------------------------------------- Cadia: the fortress
        put(FCPlanets.CADIA, List.of(
                SectorBlueprint.of("kasr_bastion", SectorType.COMMAND_BASTION, 0, 240),
                SectorBlueprint.of("north_fortress", SectorType.FORTRESS, 30, 420),
                SectorBlueprint.of("south_fortress", SectorType.FORTRESS, 200, 440),
                SectorBlueprint.of("west_trenches", SectorType.TRENCH_NETWORK, 270, 340),
                SectorBlueprint.of("east_trenches", SectorType.TRENCH_NETWORK, 90, 340),
                SectorBlueprint.of("artillery_line", SectorType.ARTILLERY_POSITION, 150, 380),
                SectorBlueprint.of("outer_bunkers", SectorType.BUNKER_COMPLEX, 315, 480),
                SectorBlueprint.of("spaceport", SectorType.SPACEPORT, 235, 300),
                SectorBlueprint.of("siege_camp", SectorType.SCRAP_FORT, WarFaction.ORKS, 60, 700)));

        // ---------------------------------------------------------------- Forge World: industry
        put(FCPlanets.FORGE_WORLD, List.of(
                SectorBlueprint.of("manufactorum", SectorType.MANUFACTORUM, 0, 260),
                SectorBlueprint.of("vehicle_factory", SectorType.VEHICLE_FACTORY, 50, 400),
                SectorBlueprint.of("promethium_refinery", SectorType.REFINERY, 110, 460),
                SectorBlueprint.of("reactor", SectorType.REACTOR, 180, 340),
                SectorBlueprint.of("armory", SectorType.ARMORY, 225, 300),
                SectorBlueprint.of("spaceport", SectorType.SPACEPORT, 290, 380),
                SectorBlueprint.of("salvage_yards", SectorType.RUINS, 330, 520),
                SectorBlueprint.of("mek_raiders", SectorType.MEK_WORKSHOP, WarFaction.ORKS, 145, 660)));

        // ---------------------------------------------------------------- Ork World: the WAAAGH!
        put(FCPlanets.ORK_WORLD, List.of(
                SectorBlueprint.of("landing_zone", SectorType.LANDING_PAD, WarFaction.IMPERIUM, 0, 180),
                SectorBlueprint.of("warboss_stronghold", SectorType.WARBOSS_STRONGHOLD, 180, 620),
                SectorBlueprint.of("great_totem", SectorType.WAAAGH_TOTEM, 140, 460),
                SectorBlueprint.of("scrap_fort", SectorType.SCRAP_FORT, 220, 480),
                SectorBlueprint.of("mek_workshop", SectorType.MEK_WORKSHOP, 260, 400),
                SectorBlueprint.of("squig_pens", SectorType.SQUIG_PEN, 300, 320),
                SectorBlueprint.of("loot_pit_north", SectorType.LOOT_PIT, 40, 380),
                SectorBlueprint.of("loot_pit_east", SectorType.LOOT_PIT, 95, 500)));

        // ---------------------------------------------------------------- Tomb World: asleep
        put(FCPlanets.NECRON_TOMB_WORLD, List.of(
                SectorBlueprint.of("tomb_entrance", SectorType.TOMB_ENTRANCE, 0, 240),
                SectorBlueprint.of("outer_complex", SectorType.TOMB_COMPLEX, 70, 400),
                SectorBlueprint.of("inner_complex", SectorType.TOMB_COMPLEX, 150, 480),
                SectorBlueprint.of("resurrection_chamber", SectorType.RESURRECTION_CHAMBER, 215, 420),
                SectorBlueprint.of("power_core", SectorType.POWER_CORE, 280, 360),
                SectorBlueprint.of("overlord_chamber", SectorType.OVERLORD_CHAMBER, 180, 640),
                SectorBlueprint.of("landing_zone", SectorType.LANDING_PAD, WarFaction.IMPERIUM, 330, 200)));

        // ---------------------------------------------------------------- Catachan: the world itself
        put(FCPlanets.CATACHAN, List.of(
                SectorBlueprint.of("firebase", SectorType.COMMAND_BASTION, 0, 200),
                SectorBlueprint.of("landing_pad", SectorType.LANDING_PAD, 60, 300),
                SectorBlueprint.of("medicae", SectorType.MEDICAE_STATION, 120, 260),
                SectorBlueprint.of("deep_jungle", SectorType.WILDERNESS, 190, 460),
                SectorBlueprint.of("swamp_basin", SectorType.WILDERNESS, 250, 520),
                SectorBlueprint.of("lost_outpost", SectorType.RUINS, 305, 400),
                SectorBlueprint.of("green_incursion", SectorType.SCRAP_FORT, WarFaction.ORKS, 155, 620)));

        // ---------------------------------------------------------------- Valhalla: the supply war
        put(FCPlanets.VALHALLA, List.of(
                SectorBlueprint.of("command_bastion", SectorType.COMMAND_BASTION, 0, 220),
                SectorBlueprint.of("buried_bunkers", SectorType.BUNKER_COMPLEX, 55, 380),
                SectorBlueprint.of("ice_trenches", SectorType.TRENCH_NETWORK, 115, 340),
                SectorBlueprint.of("north_depot", SectorType.SUPPLY_DEPOT, 200, 300),
                SectorBlueprint.of("south_depot", SectorType.SUPPLY_DEPOT, 285, 420),
                SectorBlueprint.of("spaceport", SectorType.SPACEPORT, 330, 360),
                SectorBlueprint.of("artillery_ridge", SectorType.ARTILLERY_POSITION, 160, 480),
                SectorBlueprint.of("frozen_camp", SectorType.SCRAP_FORT, WarFaction.ORKS, 240, 660)));

        // ---------------------------------------------------------------- Hive World: vertical
        //
        // The spire is a HIVE and not a COMMAND_BASTION because this is the world the MANPOWER lanes
        // draw from: with no HIVE sector anywhere on it, Necromunda produced no bodies and the three
        // lanes it feeds could never come off DESTROYED. A hive world with no hive on its map was
        // the bug.
        put(HiveWorld.LEVEL, List.of(
                SectorBlueprint.of("upper_hive", SectorType.HIVE, 0, 120),
                SectorBlueprint.of("mid_hive", SectorType.MANUFACTORUM, 90, 180),
                SectorBlueprint.of("hive_spaceport", SectorType.SPACEPORT, 180, 160),
                SectorBlueprint.of("underhive", SectorType.RUINS, WarFaction.NEUTRAL, 270, 200),
                SectorBlueprint.of("deep_hive", SectorType.RUINS, WarFaction.NEUTRAL, 315, 260)));
    }

    private static void put(ResourceKey<Level> planet, List<SectorBlueprint> blueprints) {
        BY_PLANET.put(planet.location(), blueprints);
    }

    /**
     * The sectors this front is laid out with, or an empty list for a front nobody has written a
     * layout for — which is not an error, it is a front with no strategic ground yet.
     */
    public static List<SectorBlueprint> forFront(ResourceKey<Level> dimension) {
        return BY_PLANET.getOrDefault(dimension.location(), List.of());
    }

    public static boolean hasLayout(ResourceKey<Level> dimension) {
        return BY_PLANET.containsKey(dimension.location());
    }
}
