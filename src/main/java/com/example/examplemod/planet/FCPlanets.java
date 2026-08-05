package com.example.examplemod.planet;

import java.util.List;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * The worlds the Crusade is fought on, and the single place that answers "is this one of ours?".
 *
 * <h2>Why this class exists</h2>
 *
 * The mod used to hijack {@code minecraft:overworld}: its own dimension type, noise settings and
 * biome source were written over the vanilla files, so <i>every</i> world anyone created — including
 * saves made before installing the mod — generated as a First Crusade planet. That is where the
 * floating oceans, the upside-down shipwrecks and the mismatched sea level all came from: vanilla
 * structures are placed relative to a sea level the hijacked generator no longer had.
 *
 * <p>Now the overworld is left alone. A player starts in an ordinary Minecraft world, with its
 * oceans, its structures and its Nether, and the Crusade begins when they take a ship. Everything
 * the mod generates lives in the dimensions below, where it cannot collide with anything.
 *
 * <p>The practical consequence is that no mod system may key off {@link Level#OVERWORLD} any more.
 * "The world the war happens in" is now a set, not a constant, and this class is that set.
 *
 * <h2>Each planet is a fixed composition</h2>
 *
 * A planet does not roll its biomes from one global climate field the way the old overworld did —
 * that is what produced a patchwork where the landscape changed every hundred blocks with no logic
 * behind it. Each planet instead declares which biomes it has and in what proportion, and only
 * those exist there. The proportions live in {@code tools/generate_planets.py}; the lore they come
 * from is cited there.
 */
public final class FCPlanets {
    private FCPlanets() {
    }

    /** Ultramar's capital: mountainous rock over a thin band of habitable lowland. */
    public static final ResourceKey<Level> MACRAGGE = key("macragge");

    /** Hive world under permanent industrial ash, with live volcanic highland. */
    public static final ResourceKey<Level> ARMAGEDDON = key("armageddon");

    /** Death world. Jungle and swamp, and nothing else, because nothing else survives. */
    public static final ResourceKey<Level> CATACHAN = key("catachan");

    /** Ice world: frozen tundra over the bones of old wars, with taiga in the lee. */
    public static final ResourceKey<Level> VALHALLA = key("valhalla");

    /** Agri world Verdanis: cultivated plain, windbreaks and irrigation flats. */
    public static final ResourceKey<Level> AGRI_WORLD = key("agri_world");

    /** Fortress world: bare rock and short steppe under a permanent wind. */
    public static final ResourceKey<Level> CADIA = key("cadia");

    /** Forge world: no surface water left, ash over volcanic rock and old salt beds. */
    public static final ResourceKey<Level> FORGE_WORLD = key("forge_world");

    /** Taken world: dark scrub and marsh the Ork spore has already rewritten. */
    public static final ResourceKey<Level> ORK_WORLD = key("ork_world");

    /** Tomb world: dead salt desert over stone mesas. What matters is underneath. */
    public static final ResourceKey<Level> NECRON_TOMB_WORLD = key("necron_tomb_world");

    /**
     * In travel order. The Spaceport walks this list, so the order is the order a player meets the
     * planets in — Macragge first because it is the mildest and the Imperium's own, and the rest in
     * the order the navigation terminal sorts them, so the two interfaces agree.
     *
     * <p>{@code firstcrusade:hive_world} is deliberately absent. It exists as a dimension, but it is
     * a <b>flat</b> one — a single bedrock layer under {@code hive_floor} — because it is where the
     * Hive City is built, not a world anyone should be dropped onto.
     */
    public static final List<ResourceKey<Level>> ALL = List.of(
            MACRAGGE, AGRI_WORLD, ARMAGEDDON, CADIA, FORGE_WORLD,
            ORK_WORLD, NECRON_TOMB_WORLD, CATACHAN, VALHALLA);

    /** The first destination a ship offers, and where a lost traveller is sent back to. */
    public static final ResourceKey<Level> DEFAULT = MACRAGGE;

    /**
     * True for a dimension the mod generates and fights over.
     *
     * <p>This is the test that replaced {@code dimension() == Level.OVERWORLD} throughout the mod.
     * Getting it wrong in the safe direction costs a settlement that never seeds; getting it wrong
     * in the other direction puts an Imperial city in somebody's vanilla survival world.
     */
    public static boolean isCrusadeWorld(LevelAccessor level) {
        return level instanceof Level actual && isCrusadeWorld(actual.dimension());
    }

    public static boolean isCrusadeWorld(ResourceKey<Level> dimension) {
        return ALL.contains(dimension);
    }

    /** The translation key for a planet's name, used by the Spaceport's messages. */
    public static String nameKey(ResourceKey<Level> planet) {
        return "planet." + ExampleMod.MODID + "." + planet.location().getPath();
    }

    /** The next planet in travel order, wrapping. Used to cycle a Spaceport's destination. */
    public static ResourceKey<Level> next(ResourceKey<Level> from) {
        int index = ALL.indexOf(from);
        return ALL.get(index < 0 ? 0 : (index + 1) % ALL.size());
    }

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ExampleMod.MODID, name));
    }
}
