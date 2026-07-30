package com.example.examplemod.hive;

import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Hive City construction set — pure building materials.
 *
 * 14 material families, each with the full vanilla-style progression:
 * full block, stairs, slab and wall. 56 blocks total. Every block here is
 * plain and decorative: NO BlockEntities, NO ticking.
 *
 * Kept separate from {@link HiveBlocks} so the construction palette can grow
 * without touching the (already large) foundation registry.
 */
public final class HiveConstructionBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MODID);

    // ---------------------------------------------------------------------------------------
    // hive_ashcrete
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> HIVE_ASHCRETE = registerBlock("hive_ashcrete",
            () -> new Block(ashcrete()));

    public static final RegistryObject<Block> HIVE_ASHCRETE_STAIRS = registerBlock("hive_ashcrete_stairs",
            () -> new StairBlock(() -> HIVE_ASHCRETE.get().defaultBlockState(), ashcrete()));

    public static final RegistryObject<Block> HIVE_ASHCRETE_SLAB = registerBlock("hive_ashcrete_slab",
            () -> new SlabBlock(ashcrete()));

    public static final RegistryObject<Block> HIVE_ASHCRETE_WALL = registerBlock("hive_ashcrete_wall",
            () -> new WallBlock(ashcrete()));

    // ---------------------------------------------------------------------------------------
    // hive_ashcrete_brick
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> HIVE_ASHCRETE_BRICK = registerBlock("hive_ashcrete_brick",
            () -> new Block(ashcrete()));

    public static final RegistryObject<Block> HIVE_ASHCRETE_BRICK_STAIRS = registerBlock("hive_ashcrete_brick_stairs",
            () -> new StairBlock(() -> HIVE_ASHCRETE_BRICK.get().defaultBlockState(), ashcrete()));

    public static final RegistryObject<Block> HIVE_ASHCRETE_BRICK_SLAB = registerBlock("hive_ashcrete_brick_slab",
            () -> new SlabBlock(ashcrete()));

    public static final RegistryObject<Block> HIVE_ASHCRETE_BRICK_WALL = registerBlock("hive_ashcrete_brick_wall",
            () -> new WallBlock(ashcrete()));

    // ---------------------------------------------------------------------------------------
    // hive_granite
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> HIVE_GRANITE = registerBlock("hive_granite",
            () -> new Block(granite()));

    public static final RegistryObject<Block> HIVE_GRANITE_STAIRS = registerBlock("hive_granite_stairs",
            () -> new StairBlock(() -> HIVE_GRANITE.get().defaultBlockState(), granite()));

    public static final RegistryObject<Block> HIVE_GRANITE_SLAB = registerBlock("hive_granite_slab",
            () -> new SlabBlock(granite()));

    public static final RegistryObject<Block> HIVE_GRANITE_WALL = registerBlock("hive_granite_wall",
            () -> new WallBlock(granite()));

    // ---------------------------------------------------------------------------------------
    // hive_granite_brick
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> HIVE_GRANITE_BRICK = registerBlock("hive_granite_brick",
            () -> new Block(granite()));

    public static final RegistryObject<Block> HIVE_GRANITE_BRICK_STAIRS = registerBlock("hive_granite_brick_stairs",
            () -> new StairBlock(() -> HIVE_GRANITE_BRICK.get().defaultBlockState(), granite()));

    public static final RegistryObject<Block> HIVE_GRANITE_BRICK_SLAB = registerBlock("hive_granite_brick_slab",
            () -> new SlabBlock(granite()));

    public static final RegistryObject<Block> HIVE_GRANITE_BRICK_WALL = registerBlock("hive_granite_brick_wall",
            () -> new WallBlock(granite()));

    // ---------------------------------------------------------------------------------------
    // plasteel_plating
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> PLASTEEL_PLATING = registerBlock("plasteel_plating",
            () -> new Block(plasteel()));

    public static final RegistryObject<Block> PLASTEEL_PLATING_STAIRS = registerBlock("plasteel_plating_stairs",
            () -> new StairBlock(() -> PLASTEEL_PLATING.get().defaultBlockState(), plasteel()));

    public static final RegistryObject<Block> PLASTEEL_PLATING_SLAB = registerBlock("plasteel_plating_slab",
            () -> new SlabBlock(plasteel()));

    public static final RegistryObject<Block> PLASTEEL_PLATING_WALL = registerBlock("plasteel_plating_wall",
            () -> new WallBlock(plasteel()));

    // ---------------------------------------------------------------------------------------
    // rusted_plasteel
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> RUSTED_PLASTEEL = registerBlock("rusted_plasteel",
            () -> new Block(plasteel()));

    public static final RegistryObject<Block> RUSTED_PLASTEEL_STAIRS = registerBlock("rusted_plasteel_stairs",
            () -> new StairBlock(() -> RUSTED_PLASTEEL.get().defaultBlockState(), plasteel()));

    public static final RegistryObject<Block> RUSTED_PLASTEEL_SLAB = registerBlock("rusted_plasteel_slab",
            () -> new SlabBlock(plasteel()));

    public static final RegistryObject<Block> RUSTED_PLASTEEL_WALL = registerBlock("rusted_plasteel_wall",
            () -> new WallBlock(plasteel()));

    // ---------------------------------------------------------------------------------------
    // gothic_basalt
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> GOTHIC_BASALT = registerBlock("gothic_basalt",
            () -> new Block(basalt()));

    public static final RegistryObject<Block> GOTHIC_BASALT_STAIRS = registerBlock("gothic_basalt_stairs",
            () -> new StairBlock(() -> GOTHIC_BASALT.get().defaultBlockState(), basalt()));

    public static final RegistryObject<Block> GOTHIC_BASALT_SLAB = registerBlock("gothic_basalt_slab",
            () -> new SlabBlock(basalt()));

    public static final RegistryObject<Block> GOTHIC_BASALT_WALL = registerBlock("gothic_basalt_wall",
            () -> new WallBlock(basalt()));

    // ---------------------------------------------------------------------------------------
    // gothic_basalt_brick
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> GOTHIC_BASALT_BRICK = registerBlock("gothic_basalt_brick",
            () -> new Block(basalt()));

    public static final RegistryObject<Block> GOTHIC_BASALT_BRICK_STAIRS = registerBlock("gothic_basalt_brick_stairs",
            () -> new StairBlock(() -> GOTHIC_BASALT_BRICK.get().defaultBlockState(), basalt()));

    public static final RegistryObject<Block> GOTHIC_BASALT_BRICK_SLAB = registerBlock("gothic_basalt_brick_slab",
            () -> new SlabBlock(basalt()));

    public static final RegistryObject<Block> GOTHIC_BASALT_BRICK_WALL = registerBlock("gothic_basalt_brick_wall",
            () -> new WallBlock(basalt()));

    // ---------------------------------------------------------------------------------------
    // promethium_brick
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> PROMETHIUM_BRICK = registerBlock("promethium_brick",
            () -> new Block(fired()));

    public static final RegistryObject<Block> PROMETHIUM_BRICK_STAIRS = registerBlock("promethium_brick_stairs",
            () -> new StairBlock(() -> PROMETHIUM_BRICK.get().defaultBlockState(), fired()));

    public static final RegistryObject<Block> PROMETHIUM_BRICK_SLAB = registerBlock("promethium_brick_slab",
            () -> new SlabBlock(fired()));

    public static final RegistryObject<Block> PROMETHIUM_BRICK_WALL = registerBlock("promethium_brick_wall",
            () -> new WallBlock(fired()));

    // ---------------------------------------------------------------------------------------
    // sanctum_marble
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> SANCTUM_MARBLE = registerBlock("sanctum_marble",
            () -> new Block(marble()));

    public static final RegistryObject<Block> SANCTUM_MARBLE_STAIRS = registerBlock("sanctum_marble_stairs",
            () -> new StairBlock(() -> SANCTUM_MARBLE.get().defaultBlockState(), marble()));

    public static final RegistryObject<Block> SANCTUM_MARBLE_SLAB = registerBlock("sanctum_marble_slab",
            () -> new SlabBlock(marble()));

    public static final RegistryObject<Block> SANCTUM_MARBLE_WALL = registerBlock("sanctum_marble_wall",
            () -> new WallBlock(marble()));

    // ---------------------------------------------------------------------------------------
    // sanctum_marble_brick
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> SANCTUM_MARBLE_BRICK = registerBlock("sanctum_marble_brick",
            () -> new Block(marble()));

    public static final RegistryObject<Block> SANCTUM_MARBLE_BRICK_STAIRS = registerBlock("sanctum_marble_brick_stairs",
            () -> new StairBlock(() -> SANCTUM_MARBLE_BRICK.get().defaultBlockState(), marble()));

    public static final RegistryObject<Block> SANCTUM_MARBLE_BRICK_SLAB = registerBlock("sanctum_marble_brick_slab",
            () -> new SlabBlock(marble()));

    public static final RegistryObject<Block> SANCTUM_MARBLE_BRICK_WALL = registerBlock("sanctum_marble_brick_wall",
            () -> new WallBlock(marble()));

    // ---------------------------------------------------------------------------------------
    // corroded_iron
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> CORRODED_IRON = registerBlock("corroded_iron",
            () -> new Block(plasteel()));

    public static final RegistryObject<Block> CORRODED_IRON_STAIRS = registerBlock("corroded_iron_stairs",
            () -> new StairBlock(() -> CORRODED_IRON.get().defaultBlockState(), plasteel()));

    public static final RegistryObject<Block> CORRODED_IRON_SLAB = registerBlock("corroded_iron_slab",
            () -> new SlabBlock(plasteel()));

    public static final RegistryObject<Block> CORRODED_IRON_WALL = registerBlock("corroded_iron_wall",
            () -> new WallBlock(plasteel()));

    // ---------------------------------------------------------------------------------------
    // hazard_plating
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> HAZARD_PLATING = registerBlock("hazard_plating",
            () -> new Block(plasteel()));

    public static final RegistryObject<Block> HAZARD_PLATING_STAIRS = registerBlock("hazard_plating_stairs",
            () -> new StairBlock(() -> HAZARD_PLATING.get().defaultBlockState(), plasteel()));

    public static final RegistryObject<Block> HAZARD_PLATING_SLAB = registerBlock("hazard_plating_slab",
            () -> new SlabBlock(plasteel()));

    public static final RegistryObject<Block> HAZARD_PLATING_WALL = registerBlock("hazard_plating_wall",
            () -> new WallBlock(plasteel()));

    // ---------------------------------------------------------------------------------------
    // imperial_tile
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> IMPERIAL_TILE = registerBlock("imperial_tile",
            () -> new Block(marble()));

    public static final RegistryObject<Block> IMPERIAL_TILE_STAIRS = registerBlock("imperial_tile_stairs",
            () -> new StairBlock(() -> IMPERIAL_TILE.get().defaultBlockState(), marble()));

    public static final RegistryObject<Block> IMPERIAL_TILE_SLAB = registerBlock("imperial_tile_slab",
            () -> new SlabBlock(marble()));

    public static final RegistryObject<Block> IMPERIAL_TILE_WALL = registerBlock("imperial_tile_wall",
            () -> new WallBlock(marble()));

    // =========================================================================================
    // Creative tab
    // =========================================================================================

    public static final RegistryObject<CreativeModeTab> CONSTRUCTION_TAB =
            CREATIVE_MODE_TABS.register("hive_construction_tab", () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.firstcrusade.hive_construction_tab"))
                    .icon(() -> HIVE_ASHCRETE_BRICK.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(HIVE_ASHCRETE.get());
                        output.accept(HIVE_ASHCRETE_STAIRS.get());
                        output.accept(HIVE_ASHCRETE_SLAB.get());
                        output.accept(HIVE_ASHCRETE_WALL.get());
                        output.accept(HIVE_ASHCRETE_BRICK.get());
                        output.accept(HIVE_ASHCRETE_BRICK_STAIRS.get());
                        output.accept(HIVE_ASHCRETE_BRICK_SLAB.get());
                        output.accept(HIVE_ASHCRETE_BRICK_WALL.get());
                        output.accept(HIVE_GRANITE.get());
                        output.accept(HIVE_GRANITE_STAIRS.get());
                        output.accept(HIVE_GRANITE_SLAB.get());
                        output.accept(HIVE_GRANITE_WALL.get());
                        output.accept(HIVE_GRANITE_BRICK.get());
                        output.accept(HIVE_GRANITE_BRICK_STAIRS.get());
                        output.accept(HIVE_GRANITE_BRICK_SLAB.get());
                        output.accept(HIVE_GRANITE_BRICK_WALL.get());
                        output.accept(PLASTEEL_PLATING.get());
                        output.accept(PLASTEEL_PLATING_STAIRS.get());
                        output.accept(PLASTEEL_PLATING_SLAB.get());
                        output.accept(PLASTEEL_PLATING_WALL.get());
                        output.accept(RUSTED_PLASTEEL.get());
                        output.accept(RUSTED_PLASTEEL_STAIRS.get());
                        output.accept(RUSTED_PLASTEEL_SLAB.get());
                        output.accept(RUSTED_PLASTEEL_WALL.get());
                        output.accept(GOTHIC_BASALT.get());
                        output.accept(GOTHIC_BASALT_STAIRS.get());
                        output.accept(GOTHIC_BASALT_SLAB.get());
                        output.accept(GOTHIC_BASALT_WALL.get());
                        output.accept(GOTHIC_BASALT_BRICK.get());
                        output.accept(GOTHIC_BASALT_BRICK_STAIRS.get());
                        output.accept(GOTHIC_BASALT_BRICK_SLAB.get());
                        output.accept(GOTHIC_BASALT_BRICK_WALL.get());
                        output.accept(PROMETHIUM_BRICK.get());
                        output.accept(PROMETHIUM_BRICK_STAIRS.get());
                        output.accept(PROMETHIUM_BRICK_SLAB.get());
                        output.accept(PROMETHIUM_BRICK_WALL.get());
                        output.accept(SANCTUM_MARBLE.get());
                        output.accept(SANCTUM_MARBLE_STAIRS.get());
                        output.accept(SANCTUM_MARBLE_SLAB.get());
                        output.accept(SANCTUM_MARBLE_WALL.get());
                        output.accept(SANCTUM_MARBLE_BRICK.get());
                        output.accept(SANCTUM_MARBLE_BRICK_STAIRS.get());
                        output.accept(SANCTUM_MARBLE_BRICK_SLAB.get());
                        output.accept(SANCTUM_MARBLE_BRICK_WALL.get());
                        output.accept(CORRODED_IRON.get());
                        output.accept(CORRODED_IRON_STAIRS.get());
                        output.accept(CORRODED_IRON_SLAB.get());
                        output.accept(CORRODED_IRON_WALL.get());
                        output.accept(HAZARD_PLATING.get());
                        output.accept(HAZARD_PLATING_STAIRS.get());
                        output.accept(HAZARD_PLATING_SLAB.get());
                        output.accept(HAZARD_PLATING_WALL.get());
                        output.accept(IMPERIAL_TILE.get());
                        output.accept(IMPERIAL_TILE_STAIRS.get());
                        output.accept(IMPERIAL_TILE_SLAB.get());
                        output.accept(IMPERIAL_TILE_WALL.get());
                    })
                    .build());

    // =========================================================================================
    // Material properties
    // =========================================================================================

    private static BlockBehaviour.Properties ashcrete() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(4.0F, 12.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS);
    }

    private static BlockBehaviour.Properties granite() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(4.5F, 14.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE);
    }

    private static BlockBehaviour.Properties basalt() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(4.5F, 14.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.BASALT);
    }

    private static BlockBehaviour.Properties plasteel() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 18.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.NETHERITE_BLOCK);
    }

    private static BlockBehaviour.Properties fired() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(3.5F, 10.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.NETHER_BRICKS);
    }

    private static BlockBehaviour.Properties marble() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ)
                .strength(3.5F, 10.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.CALCITE);
    }

    // =========================================================================================

    private static RegistryObject<Block> registerBlock(String name, Supplier<Block> blockSupplier) {
        RegistryObject<Block> block = BLOCKS.register(name, blockSupplier);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    /** Called once from the ExampleMod constructor. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    private HiveConstructionBlocks() {
    }
}
