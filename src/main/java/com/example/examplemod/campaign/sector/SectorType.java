package com.example.examplemod.campaign.sector;

import javax.annotation.Nullable;

import com.example.examplemod.StrategicResourceType;
import com.example.examplemod.campaign.war.WarFaction;

import net.minecraft.network.chat.Component;

/**
 * What a strategic sector <i>is</i>, and therefore what taking it is worth.
 *
 * <h2>A sector is a place that matters, not a piece of map</h2>
 *
 * The campaign deliberately does not divide a planet into a grid. A chunk grid over nine worlds is
 * hundreds of thousands of cells to store, tick and draw, and none of them mean anything — nobody
 * fights for cell (43, 17). What people fight for is the refinery, the spaceport, the gate. So a
 * planet has a dozen sectors, each attached to something with a name, and holding one is worth
 * exactly what that thing produces.
 *
 * <h2>The three numbers</h2>
 *
 * <ul>
 *   <li><b>importance</b> — the sector's weight in its planet's control percentage. A Hive is worth
 *       several outposts, which is why taking one swings the map and taking a listening post does
 *       not.</li>
 *   <li><b>defence</b> — how much harder it is to take than to hold. A fortress is not merely
 *       important, it is <i>hard</i>, and those are different things: an artillery position is easy
 *       to overrun and expensive to lose.</li>
 *   <li><b>output</b> — what holding it supplies. Read by the logistics layer; it is the reason
 *       {@code SUPPLY_DEPOT} exists as a type rather than as flavour text.</li>
 * </ul>
 */
public enum SectorType {

    // ---------------------------------------------------------------- Imperial infrastructure
    //
    // A hive's tithe is people. It is the only thing in the table that produces MANPOWER, and the
    // three lanes the Hive World feeds (armageddon, cadia, catachan) are written against exactly
    // that — before this, MANPOWER had a resource type, three supply lanes and no producer at all,
    // so those lanes sat DESTROYED ("origem não produz Manpower") in every save ever loaded.
    HIVE("hive", 5, 4, StrategicResourceType.MANPOWER, 12),
    SPACEPORT("spaceport", 4, 2, StrategicResourceType.FERROCRETE, 6),
    MANUFACTORUM("manufactorum", 4, 3, StrategicResourceType.PLASTEEL, 10),
    REFINERY("refinery", 3, 2, StrategicResourceType.PROMETHIUM, 12),
    REACTOR("reactor", 4, 3, StrategicResourceType.COAL, 10),
    VEHICLE_FACTORY("vehicle_factory", 4, 3, StrategicResourceType.PLASTEEL, 8),
    SUPPLY_DEPOT("supply_depot", 2, 1, StrategicResourceType.IRON, 8),
    COMMAND_BASTION("command_bastion", 4, 4, null, 0),
    MEDICAE_STATION("medicae_station", 2, 1, null, 0),
    ARMORY("armory", 2, 2, StrategicResourceType.CERAMITE, 4),
    LANDING_PAD("landing_pad", 2, 1, null, 0),
    COMMAND_RELAY("command_relay", 2, 1, null, 0),

    // ---------------------------------------------------------------- defensive works
    DEFENSIVE_LINE("defensive_line", 3, 4, null, 0),
    TRENCH_NETWORK("trench_network", 2, 4, null, 0),
    BUNKER_COMPLEX("bunker_complex", 3, 5, null, 0),
    FORTRESS("fortress", 5, 6, null, 0),
    ARTILLERY_POSITION("artillery_position", 3, 1, null, 0),
    DEFENSE_TOWER("defense_tower", 1, 3, null, 0),

    // ---------------------------------------------------------------- agriculture
    AGRI_FIELDS("agri_fields", 3, 1, StrategicResourceType.FOOD, 14),
    GROX_RANCH("grox_ranch", 2, 1, StrategicResourceType.FOOD, 8),
    SILO("silo", 2, 1, StrategicResourceType.FOOD, 6),
    PROCESSING_FACILITY("processing_facility", 3, 2, StrategicResourceType.FOOD, 10),

    // ---------------------------------------------------------------- Ork holdings
    SCRAP_FORT("scrap_fort", 3, 3, StrategicResourceType.ORK_SCRAP, 10),
    MEK_WORKSHOP("mek_workshop", 3, 2, StrategicResourceType.ORK_SCRAP, 8),
    SQUIG_PEN("squig_pen", 1, 1, StrategicResourceType.TEEF, 6),
    WAAAGH_TOTEM("waaagh_totem", 3, 2, StrategicResourceType.WAAAGH, 10),
    LOOT_PIT("loot_pit", 2, 1, StrategicResourceType.TEEF, 8),
    ORK_STRONGHOLD("ork_stronghold", 5, 5, StrategicResourceType.WAAAGH, 12),
    WARBOSS_STRONGHOLD("warboss_stronghold", 6, 6, StrategicResourceType.WAAAGH, 16),

    // ---------------------------------------------------------------- Necron holdings
    TOMB_ENTRANCE("tomb_entrance", 3, 3, null, 0),
    TOMB_COMPLEX("tomb_complex", 4, 5, null, 0),
    RESURRECTION_CHAMBER("resurrection_chamber", 4, 4, null, 0),
    POWER_CORE("power_core", 4, 4, StrategicResourceType.ADAMANTIUM, 6),
    OVERLORD_CHAMBER("overlord_chamber", 6, 6, null, 0),

    // ---------------------------------------------------------------- wilderness
    WILDERNESS("wilderness", 1, 1, null, 0),
    RUINS("ruins", 1, 1, StrategicResourceType.SCRAP, 4);

    private final String key;
    private final int importance;
    private final int defence;
    @Nullable
    private final StrategicResourceType output;
    private final int outputAmount;

    SectorType(String key, int importance, int defence,
               @Nullable StrategicResourceType output, int outputAmount) {
        this.key = key;
        this.importance = importance;
        this.defence = defence;
        this.output = output;
        this.outputAmount = outputAmount;
    }

    public String key() {
        return this.key;
    }

    /** Weight in the planet's control percentage. */
    public int importance() {
        return this.importance;
    }

    /** How much the holder's strength is multiplied when defending this ground. */
    public int defence() {
        return this.defence;
    }

    @Nullable
    public StrategicResourceType output() {
        return this.output;
    }

    public int outputAmount() {
        return this.outputAmount;
    }

    public boolean produces() {
        return this.output != null && this.outputAmount > 0;
    }

    public Component displayName() {
        return Component.translatable("sector.firstcrusade." + this.key);
    }

    /**
     * True for a sector that is the seat of an enemy power on its world.
     *
     * <p>This is what {@code CONQUERED} hangs on: a planet is not taken because a percentage crossed
     * a line, it is taken because the thing running the war there is gone. Holding 96% of Ork World
     * while the Warboss still sits in his stronghold is a stalemate, not a victory, and the campaign
     * says so.
     */
    public boolean isEnemySeat() {
        return this == WARBOSS_STRONGHOLD || this == ORK_STRONGHOLD || this == OVERLORD_CHAMBER;
    }

    /** Which power naturally builds this. Used as a sector's starting owner. */
    public WarFaction builder() {
        return switch (this) {
            case SCRAP_FORT, MEK_WORKSHOP, SQUIG_PEN, WAAAGH_TOTEM, LOOT_PIT,
                 ORK_STRONGHOLD, WARBOSS_STRONGHOLD -> WarFaction.ORKS;
            case TOMB_ENTRANCE, TOMB_COMPLEX, RESURRECTION_CHAMBER, POWER_CORE,
                 OVERLORD_CHAMBER -> WarFaction.NECRONS;
            case WILDERNESS, RUINS -> WarFaction.NEUTRAL;
            default -> WarFaction.IMPERIUM;
        };
    }

    /** Reads a persisted name, degrading an unknown one to {@link #WILDERNESS} instead of throwing. */
    public static SectorType fromName(String name) {
        if (name != null) {
            for (SectorType type : values()) {
                if (type.name().equalsIgnoreCase(name) || type.key.equalsIgnoreCase(name)) {
                    return type;
                }
            }
        }

        return WILDERNESS;
    }
}
