package com.example.examplemod.planet;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * The table of destinations the navigation terminal offers.
 *
 * <h2>Why a Java table and not a datapack, yet</h2>
 *
 * Every field of {@link PlanetDefinition} is already data, and {@link #register} is the only way in —
 * so a datapack loader is a reader that calls {@code register} for each JSON file it finds, and
 * nothing else in the mod has to change. That loader is deliberately not written yet: a datapack
 * that can define a planet has to answer what happens when a pack is removed while a player is
 * standing on that planet, and that question deserves its own pass rather than a half-answer now.
 *
 * <p>What this class does guarantee is that the answer stays cheap: the screen, the commands, the
 * unlock store and the travel manager all address planets by {@link ResourceLocation} and never by
 * a Java constant.
 *
 * <h2>Which worlds are real</h2>
 *
 * Nine of the ten have a generated dimension ({@link FCPlanets}). Each one declares a fixed biome
 * composition rather than inventing new biomes — that is what the composition architecture is for,
 * and a new biome would mean textures, flora tables and a {@code FloraRegionResolver} entry.
 *
 * <p>The tenth, {@code hive_world}, is deliberately left with no destination. It does have a
 * dimension, but a <b>flat</b> one: a single bedrock layer under {@code hive_floor}, which is where
 * the Hive City is built. Sending a traveller there would drop them onto a bedrock plane, so the
 * terminal shows it as {@link PlanetTravelState#DISCOVERED_NOT_DEPLOYABLE} — visible on the
 * roadmap, button dark, and nobody teleported anywhere they cannot stand.
 */
public final class PlanetRegistry {
    private PlanetRegistry() {
    }

    private static final Map<ResourceLocation, PlanetDefinition> PLANETS = new LinkedHashMap<>();

    /** Cached, sorted view. Rebuilt on registration, never per frame. */
    private static List<PlanetDefinition> sorted = List.of();

    // ====================================================================================
    // The table
    // ====================================================================================

    static {
        // ---- Available from the first Spaceport -------------------------------------------
        //
        // Macragge is the mod's Imperial capital, so the capital entry points at it rather than
        // inventing a parallel world with the same job.
        register(PlanetDefinition.builder("imperial_capital")
                .type(PlanetWorldType.IMPERIAL_CAPITAL)
                .faction(PlanetFaction.IMPERIUM)
                .danger(PlanetDangerLevel.LOW)
                .military(PlanetMilitaryStatus.GARRISONED)
                .atmosphere(0xFF3E5C8A)
                .moons(2)
                .dimension(FCPlanets.MACRAGGE)
                .sortOrder(0)
                .build());

        register(PlanetDefinition.builder("agri_world")
                .type(PlanetWorldType.AGRI_WORLD)
                .faction(PlanetFaction.IMPERIUM)
                .danger(PlanetDangerLevel.LOW)
                .military(PlanetMilitaryStatus.PEACEFUL)
                .atmosphere(0xFF4E8A4E)
                .moons(1)
                .dimension(FCPlanets.AGRI_WORLD)
                .sortOrder(10)
                .build());

        register(PlanetDefinition.builder("hive_world")
                .type(PlanetWorldType.HIVE_WORLD)
                .faction(PlanetFaction.IMPERIUM)
                .danger(PlanetDangerLevel.MODERATE)
                .military(PlanetMilitaryStatus.GARRISONED)
                .atmosphere(0xFF8A7A3C)
                .moons(1)
                .requires(PlanetUnlockRequirement.TRIGGER_FIRST_LAUNCH,
                        "planet.firstcrusade.requirement.first_launch")
                .sortOrder(20)
                .build());

        // ---- Earned ------------------------------------------------------------------------

        register(PlanetDefinition.builder("armageddon")
                .type(PlanetWorldType.INDUSTRIAL_WAR_WORLD)
                .faction(PlanetFaction.CONTESTED)
                .danger(PlanetDangerLevel.EXTREME)
                .military(PlanetMilitaryStatus.ACTIVE_WARZONE)
                .atmosphere(0xFFB4562A)
                .moons(1)
                .dimension(FCPlanets.ARMAGEDDON)
                .requires(PlanetUnlockRequirement.TRIGGER_ORK_CHAMPION_SLAIN,
                        "planet.firstcrusade.requirement.ork_champion")
                .cost(FCRegistry.CRUSADIUM_PLATE, 2)
                .sortOrder(30)
                .build());

        register(PlanetDefinition.builder("cadia")
                .type(PlanetWorldType.FORTRESS_WORLD)
                .faction(PlanetFaction.IMPERIUM)
                .danger(PlanetDangerLevel.EXTREME)
                .military(PlanetMilitaryStatus.ACTIVE_WARZONE)
                .atmosphere(0xFF5C7A6A)
                .moons(1)
                .dimension(FCPlanets.CADIA)
                .requires(PlanetUnlockRequirement.TRIGGER_CAMPAIGN,
                        "planet.firstcrusade.requirement.campaign_cadia")
                .cost(FCRegistry.CRUSADIUM_PLATE, 4)
                .sortOrder(40)
                .build());

        register(PlanetDefinition.builder("forge_world")
                .type(PlanetWorldType.FORGE_WORLD)
                .faction(PlanetFaction.IMPERIUM)
                .danger(PlanetDangerLevel.MODERATE)
                .military(PlanetMilitaryStatus.GARRISONED)
                .atmosphere(0xFF8A3C3C)
                .moons(3)
                .dimension(FCPlanets.FORGE_WORLD)
                .requires(PlanetUnlockRequirement.TRIGGER_CRUSADIUM_FORGED,
                        "planet.firstcrusade.requirement.crusadium")
                .cost(FCRegistry.CRUSADIUM_PLATE, 4)
                .sortOrder(50)
                .build());

        register(PlanetDefinition.builder("ork_world")
                .type(PlanetWorldType.ORK_WORLD)
                .faction(PlanetFaction.ORKS)
                .danger(PlanetDangerLevel.EXTREME)
                .military(PlanetMilitaryStatus.OVERRUN)
                .atmosphere(0xFF5A6E2A)
                .moons(0)
                .dimension(FCPlanets.ORK_WORLD)
                .requires(PlanetUnlockRequirement.TRIGGER_ORK_CHAMPION_SLAIN,
                        "planet.firstcrusade.requirement.ork_champion")
                .requires(PlanetUnlockRequirement.TRIGGER_CAMPAIGN,
                        "planet.firstcrusade.requirement.ork_coordinates")
                .cost(FCRegistry.CRUSADIUM_PLATE, 6)
                .sortOrder(60)
                .build());

        register(PlanetDefinition.builder("necron_tomb_world")
                .type(PlanetWorldType.NECRON_TOMB_WORLD)
                .faction(PlanetFaction.NECRONS)
                .danger(PlanetDangerLevel.EXTREME)
                .military(PlanetMilitaryStatus.UNKNOWN)
                .atmosphere(0xFF2A6A4A)
                .moons(2)
                .dimension(FCPlanets.NECRON_TOMB_WORLD)
                .requires(PlanetUnlockRequirement.TRIGGER_CAMPAIGN,
                        "planet.firstcrusade.requirement.necron_artefact")
                .cost(FCRegistry.CRUSADIUM_PLATE, 8)
                .sortOrder(70)
                .build());

        // ---- The mod's other two worlds ----------------------------------------------------
        //
        // Catachan and Valhalla already generate; leaving them out of the terminal would mean the
        // only way to reach two finished worlds is a block that no longer offers a choice.

        register(PlanetDefinition.builder("catachan")
                .type(PlanetWorldType.DEATH_WORLD)
                .faction(PlanetFaction.CONTESTED)
                .danger(PlanetDangerLevel.HIGH)
                .military(PlanetMilitaryStatus.CONTESTED)
                .atmosphere(0xFF2E6E3A)
                .moons(1)
                .dimension(FCPlanets.CATACHAN)
                .requires(PlanetUnlockRequirement.TRIGGER_FIRST_LAUNCH,
                        "planet.firstcrusade.requirement.first_launch")
                .cost(FCRegistry.CRUSADIUM_PLATE, 2)
                .sortOrder(80)
                .build());

        register(PlanetDefinition.builder("valhalla")
                .type(PlanetWorldType.ICE_WORLD)
                .faction(PlanetFaction.IMPERIUM)
                .danger(PlanetDangerLevel.HIGH)
                .military(PlanetMilitaryStatus.GARRISONED)
                .atmosphere(0xFF7A9AB4)
                .moons(1)
                .dimension(FCPlanets.VALHALLA)
                .requires(PlanetUnlockRequirement.TRIGGER_FIRST_LAUNCH,
                        "planet.firstcrusade.requirement.first_launch")
                .cost(FCRegistry.CRUSADIUM_PLATE, 2)
                .sortOrder(90)
                .build());
    }

    // ====================================================================================
    // Access
    // ====================================================================================

    /**
     * Adds a destination. Later registrations of the same id replace the earlier one, which is what
     * lets a future datapack loader override a built-in planet instead of duplicating it.
     */
    public static void register(PlanetDefinition planet) {
        PLANETS.put(planet.id(), planet);
        rebuild();
    }

    private static void rebuild() {
        sorted = PLANETS.values().stream()
                .filter(PlanetDefinition::enabled)
                .sorted(Comparator.comparingInt(PlanetDefinition::sortOrder)
                        .thenComparing(planet -> planet.id().toString()))
                .toList();
    }

    /** Every enabled destination, in display order. Cached — safe to call from a render pass. */
    public static List<PlanetDefinition> all() {
        return sorted;
    }

    public static Collection<PlanetDefinition> allIncludingDisabled() {
        return PLANETS.values();
    }

    public static Optional<PlanetDefinition> get(@Nullable ResourceLocation id) {
        return id == null ? Optional.empty() : Optional.ofNullable(PLANETS.get(id));
    }

    /**
     * Looks a planet up from a string a player or a packet supplied.
     *
     * <p>Never throws on malformed input: a bad id from the network is a thing that happens, and the
     * caller's job is to answer "no such destination", not to crash the connection.
     */
    public static Optional<PlanetDefinition> parse(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(
                raw.indexOf(':') >= 0 ? raw : ExampleMod.MODID + ":" + raw);
        return get(id);
    }

    /** The planet whose dimension is the one given, if any — used for "you are here". */
    public static Optional<PlanetDefinition> byDimension(
            @Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        if (dimension == null) {
            return Optional.empty();
        }

        return sorted.stream()
                .filter(planet -> dimension.equals(planet.destinationDimension()))
                .findFirst();
    }

    /** Ids of every planet that is unlocked from the start — the grant a new player receives. */
    public static List<ResourceLocation> defaultUnlocked() {
        return sorted.stream()
                .filter(PlanetDefinition::isUnlockedByDefault)
                .map(PlanetDefinition::id)
                .toList();
    }
}
