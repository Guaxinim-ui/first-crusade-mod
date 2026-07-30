package com.example.examplemod.flora;

import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.flora.block.FloraLichenBlock;
import com.example.examplemod.flora.block.FloraPlantBlock;
import com.example.examplemod.flora.block.FloraTallPlantBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Phase 2 of the First Crusade vegetation system: the base flora set.
 *
 * <p>A self-contained registry in the same shape as {@code HiveEssentialBlocks} — its own
 * DeferredRegisters, its own creative tab, one {@link #register(IEventBus)} call from
 * {@code ExampleMod}. Nothing here touches an existing registry, so no existing ID moves.
 *
 * <p>Every block in this class is inert: no block entity, no ticking, no random tick, no
 * spreading. A plant is a model, a ground rule and a loot table. All behaviour — where these
 * appear, how thick, and how a region changes over the course of the war — belongs to the
 * placement and settlement layers built in the later phases.
 *
 * <p>The blocks are {@code replaceable()} on purpose. That single property is what makes
 * {@code WorldGenPlacement.clearVegetation} sweep them out of a building footprint and lets a
 * city grow straight through a meadow instead of leaving grass poking out of a wall.
 */
public final class FCFlora {
    private FCFlora() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MODID);

    // ====================================================================================
    // Imperial fringe — the controlled, tended, half-dead green of Imperial ground
    // ====================================================================================

    public static final RegistryObject<Block> IMPERIAL_GRASS = register("imperial_grass",
            () -> new FloraPlantBlock(plant(MapColor.PLANT, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 12));

    public static final RegistryObject<Block> WITHERED_SCRUB = register("withered_scrub",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_BROWN, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 12));

    public static final RegistryObject<Block> DARK_FERN = register("dark_fern",
            () -> new FloraPlantBlock(plant(MapColor.PLANT, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 13));

    public static final RegistryObject<Block> MEMORIAL_BLOOM = register("memorial_bloom",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_WHITE, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 10));

    public static final RegistryObject<Block> AQUILA_BLOOM = register("aquila_bloom",
            () -> new FloraPlantBlock(plant(MapColor.GOLD, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 10));

    public static final RegistryObject<Block> OSSUARY_LILY = register("ossuary_lily",
            () -> new FloraPlantBlock(plant(MapColor.QUARTZ, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 10));

    public static final RegistryObject<Block> ROADSIDE_THISTLE = register("roadside_thistle",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_BROWN, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 12));

    // ====================================================================================
    // Forge world — soot, chemical run-off and things that grow on metal
    // ====================================================================================

    public static final RegistryObject<Block> ASH_GRASS = register("ash_grass",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_GRAY, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 12));

    public static final RegistryObject<Block> SOOT_GRASS = register("soot_grass",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_BLACK, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 12));

    public static final RegistryObject<Block> BURNT_STUBBLE = register("burnt_stubble",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_BLACK, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 5));

    public static final RegistryObject<Block> PROMETHIUM_WEED = register("promethium_weed",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_YELLOW, SoundType.GRASS, 0), FloraTags.GROUND_INDUSTRIAL, 12));

    public static final RegistryObject<Block> CHEM_BLOOM = register("chem_bloom",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_LIGHT_GREEN, SoundType.GRASS, 4), FloraTags.GROUND_INDUSTRIAL, 10));

    public static final RegistryObject<Block> SLAG_LICHEN = register("slag_lichen",
            () -> new FloraLichenBlock(lichen(MapColor.COLOR_GRAY, SoundType.MOSS, 0)));

    public static final RegistryObject<Block> RUST_MOSS = register("rust_moss",
            () -> new FloraLichenBlock(lichen(MapColor.COLOR_ORANGE, SoundType.MOSS, 0)));

    // ====================================================================================
    // Hive — cracks, vents, sumps and the bioluminescence of the Underhive
    // ====================================================================================

    public static final RegistryObject<Block> CRACK_WEED = register("crack_weed",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_GRAY, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 12));

    public static final RegistryObject<Block> VENT_GRASS = register("vent_grass",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_BROWN, SoundType.GRASS, 0), FloraTags.GROUND_INDUSTRIAL, 5));

    public static final RegistryObject<Block> PALLID_FUNGUS = register("pallid_fungus",
            () -> new FloraPlantBlock(plant(MapColor.QUARTZ, SoundType.FUNGUS, 0), FloraTags.GROUND_ANY, 8));

    public static final RegistryObject<Block> GLOW_CAP = register("glow_cap",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_CYAN, SoundType.FUNGUS, 7), FloraTags.GROUND_ANY, 8));

    public static final RegistryObject<Block> SUMP_MOSS = register("sump_moss",
            () -> new FloraLichenBlock(lichen(MapColor.TERRACOTTA_BROWN, SoundType.MOSS, 0)));

    public static final RegistryObject<Block> GUTTER_LICHEN = register("gutter_lichen",
            () -> new FloraLichenBlock(lichen(MapColor.COLOR_GRAY, SoundType.MOSS, 0)));

    public static final RegistryObject<Block> HAB_FERN = register("hab_fern",
            () -> new FloraPlantBlock(plant(MapColor.PLANT, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 13));

    public static final RegistryObject<Block> SLUDGE_ALGAE = register("sludge_algae",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_BROWN, SoundType.WET_GRASS, 0), FloraTags.GROUND_ANY, 5));

    // ====================================================================================
    // Ork — the ecology the green tide drags along with it
    // ====================================================================================

    public static final RegistryObject<Block> ORK_FUNGUS = register("ork_fungus",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_GREEN, SoundType.FUNGUS, 0), FloraTags.GROUND_ANY, 8));

    /**
     * The Orkoid life cycle: four stages, one per quarter-day, ending in a Gretchin.
     * Behaviour lives in {@link com.example.examplemod.flora.block.OrkSporePodBlock}.
     */
    public static final RegistryObject<Block> ORK_SPORE_POD = register("ork_spore_pod",
            () -> new com.example.examplemod.flora.block.OrkSporePodBlock(
                    plant(MapColor.COLOR_GREEN, SoundType.FUNGUS, 0).randomTicks()));

    public static final RegistryObject<Block> ORK_SPORE_CAP = register("ork_spore_cap",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_GREEN, SoundType.FUNGUS, 3), FloraTags.GROUND_ANY, 8));

    public static final RegistryObject<Block> SQUIG_GRASS = register("squig_grass",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_GREEN, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 12));

    public static final RegistryObject<Block> TRAMPLED_GRASS = register("trampled_grass",
            () -> new FloraPlantBlock(plant(MapColor.PLANT, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 5));

    public static final RegistryObject<Block> OIL_STAINED_GRASS = register("oil_stained_grass",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_BLACK, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 12));

    public static final RegistryObject<Block> GOB_MOSS = register("gob_moss",
            () -> new FloraLichenBlock(lichen(MapColor.COLOR_GREEN, SoundType.MOSS, 0)));

    // ====================================================================================
    // Chaos — vegetation that has stopped obeying its own biology
    // ====================================================================================

    public static final RegistryObject<Block> THORNWEED = register("thornweed",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_RED, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 12));

    public static final RegistryObject<Block> WRITHING_GRASS = register("writhing_grass",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_PURPLE, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 12));

    public static final RegistryObject<Block> CORRUPTED_BLOOM = register("corrupted_bloom",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_RED, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 10));

    public static final RegistryObject<Block> PULSING_ROOT = register("pulsing_root",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_PURPLE, SoundType.GRASS, 2), FloraTags.GROUND_ANY, 6));

    public static final RegistryObject<Block> CHAOS_LICHEN = register("chaos_lichen",
            () -> new FloraLichenBlock(lichen(MapColor.COLOR_PURPLE, SoundType.MOSS, 0)));

    // ====================================================================================
    // Death world — everything here is armed
    // ====================================================================================

    public static final RegistryObject<Block> VENOM_FROND = register("venom_frond",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_GREEN, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 13));

    public static final RegistryObject<Block> SPINE_BUSH = register("spine_bush",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_PURPLE, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 12));

    public static final RegistryObject<Block> TOXIC_BLOOM = register("toxic_bloom",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_GREEN, SoundType.GRASS, 3), FloraTags.GROUND_NATURAL, 10));

    public static final RegistryObject<Block> FANGED_SPROUT = register("fanged_sprout",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_PURPLE, SoundType.FUNGUS, 0), FloraTags.GROUND_NATURAL, 8));

    public static final RegistryObject<Block> MIRE_REED = register("mire_reed",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_BROWN, SoundType.WET_GRASS, 0), FloraTags.GROUND_NATURAL, 15));

    // ====================================================================================
    // Agri world — the only genuinely healthy plants in the mod
    // ====================================================================================

    public static final RegistryObject<Block> FIELD_GRASS = register("field_grass",
            () -> new FloraPlantBlock(plant(MapColor.PLANT, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 12));

    public static final RegistryObject<Block> PALE_FIELD_FLOWER = register("pale_field_flower",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_YELLOW, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 10));

    public static final RegistryObject<Block> AGRI_CLOVER = register("agri_clover",
            () -> new FloraPlantBlock(plant(MapColor.PLANT, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 12));

    public static final RegistryObject<Block> IRRIGATION_REED = register("irrigation_reed",
            () -> new FloraPlantBlock(plant(MapColor.PLANT, SoundType.WET_GRASS, 0), FloraTags.GROUND_NATURAL, 15));

    // ====================================================================================
    // Ironwood forest — cold, closed canopy, and everything under it lives on resin and rot
    // ====================================================================================

    public static final RegistryObject<Block> IRON_FERN = register("iron_fern",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_BLUE, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL, 13));

    /** Hardened sap that keeps growing after it has run: it clings to bark, so it is a lichen. */
    public static final RegistryObject<Block> RESIN_MOSS = register("resin_moss",
            () -> new FloraLichenBlock(lichen(MapColor.COLOR_ORANGE, SoundType.MOSS, 0)));

    public static final RegistryObject<Block> SHELF_FUNGUS = register("shelf_fungus",
            () -> new FloraLichenBlock(lichen(MapColor.TERRACOTTA_WHITE, SoundType.FUNGUS, 0)));

    public static final RegistryObject<Block> NEEDLE_LITTER = register("needle_litter",
            () -> new CarpetBlock(cover(MapColor.COLOR_BROWN, SoundType.GRASS)));

    // ====================================================================================
    // Sump marsh — standing water, gas, and things that feed on both
    // ====================================================================================

    public static final RegistryObject<Block> MARSH_GRASS = register("marsh_grass",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_GREEN, SoundType.WET_GRASS, 0), FloraTags.GROUND_NATURAL, 12));

    public static final RegistryObject<Block> BOG_FUNGUS = register("bog_fungus",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_YELLOW, SoundType.FUNGUS, 0), FloraTags.GROUND_ANY, 8));

    /**
     * A skin of marsh gas trapped under algae. It glows faintly — the only light the marsh floor
     * gets — and it is deliberately just a block: a gas mechanic would need a ticker per plant,
     * which the performance rules rule out.
     */
    public static final RegistryObject<Block> GAS_BLADDER = register("gas_bladder",
            () -> new FloraPlantBlock(plant(MapColor.COLOR_LIGHT_GREEN, SoundType.SLIME_BLOCK, 4), FloraTags.GROUND_ANY, 5));

    public static final RegistryObject<Block> MUD_LICHEN = register("mud_lichen",
            () -> new FloraLichenBlock(lichen(MapColor.TERRACOTTA_BROWN, SoundType.MOSS, 0)));

    // ====================================================================================
    // Ossuary tundra — frozen ground over old wars
    // ====================================================================================

    public static final RegistryObject<Block> FROST_LICHEN = register("frost_lichen",
            () -> new FloraLichenBlock(lichen(MapColor.ICE, SoundType.MOSS, 0)));

    public static final RegistryObject<Block> SNOW_SCRUB = register("snow_scrub",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_WHITE, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 10));

    public static final RegistryObject<Block> TUNDRA_MOSS = register("tundra_moss",
            () -> new CarpetBlock(cover(MapColor.TERRACOTTA_GREEN, SoundType.MOSS)));

    // ====================================================================================
    // Salt waste — a crust, a horizon, and almost nothing between them
    // ====================================================================================

    public static final RegistryObject<Block> BRINE_GRASS = register("brine_grass",
            () -> new FloraPlantBlock(plant(MapColor.SAND, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 12));

    public static final RegistryObject<Block> BRINE_THISTLE = register("brine_thistle",
            () -> new FloraPlantBlock(plant(MapColor.TERRACOTTA_WHITE, SoundType.GRASS, 0), FloraTags.GROUND_ANY, 10));

    public static final RegistryObject<Block> SALT_FLAKE = register("salt_flake",
            () -> new FloraPlantBlock(flat(MapColor.SNOW, SoundType.SAND), FloraTags.GROUND_ANY, 1));

    // ====================================================================================
    // Two-block-tall vegetation
    // ====================================================================================

    public static final RegistryObject<Block> TALL_IMPERIAL_GRASS = register("tall_imperial_grass",
            () -> new FloraTallPlantBlock(plant(MapColor.PLANT, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL));

    public static final RegistryObject<Block> TALL_ASH_GRASS = register("tall_ash_grass",
            () -> new FloraTallPlantBlock(plant(MapColor.COLOR_GRAY, SoundType.GRASS, 0), FloraTags.GROUND_ANY));

    public static final RegistryObject<Block> TALL_SQUIG_GRASS = register("tall_squig_grass",
            () -> new FloraTallPlantBlock(plant(MapColor.COLOR_GREEN, SoundType.GRASS, 0), FloraTags.GROUND_NATURAL));

    public static final RegistryObject<Block> TALL_MIRE_REED = register("tall_mire_reed",
            () -> new FloraTallPlantBlock(plant(MapColor.TERRACOTTA_BROWN, SoundType.WET_GRASS, 0), FloraTags.GROUND_NATURAL));

    public static final RegistryObject<Block> TALL_MARSH_REED = register("tall_marsh_reed",
            () -> new FloraTallPlantBlock(plant(MapColor.TERRACOTTA_GREEN, SoundType.WET_GRASS, 0), FloraTags.GROUND_NATURAL));

    // ====================================================================================
    // Ground cover and detritus — the layer that makes terrain stop looking swept
    // ====================================================================================

    public static final RegistryObject<Block> FALLEN_LEAVES = register("fallen_leaves",
            () -> new CarpetBlock(cover(MapColor.TERRACOTTA_BROWN, SoundType.GRASS)));

    public static final RegistryObject<Block> ASH_LAYER = register("ash_layer",
            () -> new CarpetBlock(cover(MapColor.COLOR_GRAY, SoundType.WET_GRASS)));

    public static final RegistryObject<Block> SCATTERED_TWIGS = register("scattered_twigs",
            () -> new FloraPlantBlock(flat(MapColor.TERRACOTTA_BROWN, SoundType.GRASS), FloraTags.GROUND_ANY, 1));

    public static final RegistryObject<Block> SMALL_ROOTS = register("small_roots",
            () -> new FloraPlantBlock(flat(MapColor.TERRACOTTA_BROWN, SoundType.GRASS), FloraTags.GROUND_ANY, 1));

    public static final RegistryObject<Block> RUBBLE_PEBBLES = register("rubble_pebbles",
            () -> new FloraPlantBlock(flat(MapColor.STONE, SoundType.STONE), FloraTags.GROUND_ANY, 1));

    public static final RegistryObject<Block> BONE_FRAGMENTS = register("bone_fragments",
            () -> new FloraPlantBlock(flat(MapColor.TERRACOTTA_WHITE, SoundType.STONE), FloraTags.GROUND_ANY, 1));

    // ====================================================================================
    // Creative tab
    // ====================================================================================

    public static final RegistryObject<CreativeModeTab> FLORA_TAB =
            CREATIVE_MODE_TABS.register("flora_tab", () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.firstcrusade.flora_tab"))
                    .icon(() -> ORK_FUNGUS.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(IMPERIAL_GRASS.get());
                        output.accept(WITHERED_SCRUB.get());
                        output.accept(DARK_FERN.get());
                        output.accept(MEMORIAL_BLOOM.get());
                        output.accept(AQUILA_BLOOM.get());
                        output.accept(OSSUARY_LILY.get());
                        output.accept(ROADSIDE_THISTLE.get());
                        output.accept(ASH_GRASS.get());
                        output.accept(SOOT_GRASS.get());
                        output.accept(BURNT_STUBBLE.get());
                        output.accept(PROMETHIUM_WEED.get());
                        output.accept(CHEM_BLOOM.get());
                        output.accept(SLAG_LICHEN.get());
                        output.accept(RUST_MOSS.get());
                        output.accept(CRACK_WEED.get());
                        output.accept(VENT_GRASS.get());
                        output.accept(PALLID_FUNGUS.get());
                        output.accept(GLOW_CAP.get());
                        output.accept(SUMP_MOSS.get());
                        output.accept(GUTTER_LICHEN.get());
                        output.accept(HAB_FERN.get());
                        output.accept(SLUDGE_ALGAE.get());
                        output.accept(ORK_FUNGUS.get());
                        output.accept(ORK_SPORE_CAP.get());
                        output.accept(ORK_SPORE_POD.get());
                        output.accept(SQUIG_GRASS.get());
                        output.accept(TRAMPLED_GRASS.get());
                        output.accept(OIL_STAINED_GRASS.get());
                        output.accept(GOB_MOSS.get());
                        output.accept(THORNWEED.get());
                        output.accept(WRITHING_GRASS.get());
                        output.accept(CORRUPTED_BLOOM.get());
                        output.accept(PULSING_ROOT.get());
                        output.accept(CHAOS_LICHEN.get());
                        output.accept(VENOM_FROND.get());
                        output.accept(SPINE_BUSH.get());
                        output.accept(TOXIC_BLOOM.get());
                        output.accept(FANGED_SPROUT.get());
                        output.accept(MIRE_REED.get());
                        output.accept(IRON_FERN.get());
                        output.accept(RESIN_MOSS.get());
                        output.accept(SHELF_FUNGUS.get());
                        output.accept(NEEDLE_LITTER.get());
                        output.accept(MARSH_GRASS.get());
                        output.accept(BOG_FUNGUS.get());
                        output.accept(GAS_BLADDER.get());
                        output.accept(MUD_LICHEN.get());
                        output.accept(FROST_LICHEN.get());
                        output.accept(SNOW_SCRUB.get());
                        output.accept(TUNDRA_MOSS.get());
                        output.accept(BRINE_GRASS.get());
                        output.accept(BRINE_THISTLE.get());
                        output.accept(SALT_FLAKE.get());
                        output.accept(FIELD_GRASS.get());
                        output.accept(PALE_FIELD_FLOWER.get());
                        output.accept(AGRI_CLOVER.get());
                        output.accept(IRRIGATION_REED.get());
                        output.accept(TALL_IMPERIAL_GRASS.get());
                        output.accept(TALL_ASH_GRASS.get());
                        output.accept(TALL_SQUIG_GRASS.get());
                        output.accept(TALL_MIRE_REED.get());
                        output.accept(TALL_MARSH_REED.get());
                        output.accept(FALLEN_LEAVES.get());
                        output.accept(ASH_LAYER.get());
                        output.accept(SCATTERED_TWIGS.get());
                        output.accept(SMALL_ROOTS.get());
                        output.accept(RUBBLE_PEBBLES.get());
                        output.accept(BONE_FRAGMENTS.get());
                        com.example.examplemod.flora.fruit.FCFruits.addToCreativeTab(output);
                    })
                    .build());

    // ====================================================================================
    // Block property presets
    // ====================================================================================

    /** Standard small plant: walk-through, insta-break, replaceable, randomly offset. */
    private static BlockBehaviour.Properties plant(MapColor color, SoundType sound, int light) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(color)
                .replaceable()
                .noCollission()
                .instabreak()
                .sound(sound)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY)
                .ignitedByLava();
        return light > 0 ? properties.lightLevel(state -> light) : properties;
    }

    /**
     * Flat ground detail (twigs, pebbles, bone). No XZ offset here on purpose: the model is a
     * full-tile quad, and offsetting it would expose the ground at the tile seams.
     */
    private static BlockBehaviour.Properties flat(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .replaceable()
                .noCollission()
                .instabreak()
                .sound(sound)
                .pushReaction(PushReaction.DESTROY);
    }

    /** Wall-clinging moss/lichen. Not replaceable — it is meant to survive on a structure. */
    private static BlockBehaviour.Properties lichen(MapColor color, SoundType sound, int light) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(color)
                .noCollission()
                .instabreak()
                .sound(sound)
                .pushReaction(PushReaction.DESTROY)
                .ignitedByLava();
        return light > 0 ? properties.lightLevel(state -> light) : properties;
    }

    /** Leaf litter / ash layer: a thin carpet you can walk on but any block overwrites. */
    private static BlockBehaviour.Properties cover(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .replaceable()
                .strength(0.1F)
                .sound(sound)
                .pushReaction(PushReaction.DESTROY)
                .ignitedByLava();
    }

    // ====================================================================================

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> registered = BLOCKS.register(name, block);
        ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    /** Called once from the ExampleMod constructor. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
