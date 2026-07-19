package com.example.examplemod.hive;
import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 48 production blocks based on the First Crusade Hive City concept sheets. */
public final class HiveCityConceptBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final RegistryObject<Block> ARMORED_BULKHEAD_WALL = registerBlock("armored_bulkhead_wall", () -> new HiveHorizontalBlock(metal()));
    public static final RegistryObject<Block> RECESSED_STEEL_WALL_PANEL = registerBlock("recessed_steel_wall_panel", () -> new HiveHorizontalBlock(metal()));
    public static final RegistryObject<Block> GOTHIC_ARCH_WALL = registerBlock("gothic_arch_wall", () -> new HiveHorizontalBlock(stone()));
    public static final RegistryObject<Block> TALL_RIBBED_PILLAR = registerBlock("tall_ribbed_pillar", () -> new Block(stone()));
    public static final RegistryObject<Block> BUTTRESS_COLUMN = registerBlock("buttress_column", () -> new HiveHorizontalBlock(stone().noOcclusion()));
    public static final RegistryObject<Block> CATHEDRAL_CORNICE = registerBlock("cathedral_cornice", () -> new HiveHorizontalBlock(stone().noOcclusion()));
    public static final RegistryObject<Block> LOWER_WALL_MOLDING = registerBlock("lower_wall_molding", () -> new HiveHorizontalBlock(stone().noOcclusion()));
    public static final RegistryObject<Block> SPIRE_CAP_BLOCK = registerBlock("spire_cap_block", () -> new Block(stone().noOcclusion()));
    public static final RegistryObject<Block> BALCONY_EDGE_TRIM = registerBlock("balcony_edge_trim", () -> new HiveHorizontalBlock(stone().noOcclusion()));
    public static final RegistryObject<Block> BRIDGE_SUPPORT_BLOCK = registerBlock("bridge_support_block", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> GIANT_DOOR_SEGMENT = registerBlock("giant_door_segment", () -> new HiveHorizontalBlock(metal()));
    public static final RegistryObject<Block> NARROW_LANCET_RECESS = registerBlock("narrow_lancet_recess", () -> new HiveHorizontalBlock(stone()));
    public static final RegistryObject<Block> TRIANGULAR_RELIEF_PANEL = registerBlock("triangular_relief_panel", () -> new HiveHorizontalBlock(stone()));
    public static final RegistryObject<Block> WINDOW_SLOT_FRAME = registerBlock("window_slot_frame", () -> new HiveHorizontalBlock(stone().lightLevel(state -> 12)));
    public static final RegistryObject<Block> HEAVY_STRUCTURAL_FRAME = registerBlock("heavy_structural_frame", () -> new Block(metal().noOcclusion()));
    public static final RegistryObject<Block> VERTICAL_SEAM_STRIP = registerBlock("vertical_seam_strip", () -> new HiveHorizontalBlock(metal()));
    public static final RegistryObject<Block> STRAIGHT_PIPE = registerBlock("straight_pipe", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> ELBOW_PIPE = registerBlock("elbow_pipe", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> T_PIPE_JUNCTION = registerBlock("t_pipe_junction", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> CROSS_PIPE_JUNCTION = registerBlock("cross_pipe_junction", () -> new Block(metal().noOcclusion()));
    public static final RegistryObject<Block> PIPE_SUPPORT_CLAMP = registerBlock("pipe_support_clamp", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> VERTICAL_SERVICE_CONDUIT = registerBlock("vertical_service_conduit", () -> new Block(metal().noOcclusion()));
    public static final RegistryObject<Block> CABLE_BUNDLE_BLOCK = registerBlock("cable_bundle_block", () -> new HiveHorizontalBlock(metal()));
    public static final RegistryObject<Block> VENT_OUTLET = registerBlock("vent_outlet", () -> new HiveHorizontalBlock(metal()));
    public static final RegistryObject<Block> FLOOR_VENT = registerBlock("floor_vent", () -> new Block(metal().noOcclusion()));
    public static final RegistryObject<Block> LIFT_RAIL = registerBlock("lift_rail", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> GANTRY_BEAM = registerBlock("gantry_beam", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> SUSPENDED_TRACK_ANCHOR = registerBlock("suspended_track_anchor", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> MAINTENANCE_HATCH = registerBlock("maintenance_hatch", () -> new HiveHorizontalBlock(metal()));
    public static final RegistryObject<Block> MACHINE_CASING_BLOCK = registerBlock("machine_casing_block", () -> new HiveHorizontalBlock(metal().lightLevel(state -> 7)));
    public static final RegistryObject<Block> HAZARD_GRATED_FLOOR = registerBlock("hazard_grated_floor", () -> new Block(metal().noOcclusion()));
    public static final RegistryObject<Block> REINFORCED_PLATFORM_EDGE = registerBlock("reinforced_platform_edge", () -> new HiveHorizontalBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> GLOWING_SHRINE_WINDOW = registerBlock("glowing_shrine_window", () -> new HiveHorizontalBlock(stone().lightLevel(state -> 15)));
    public static final RegistryObject<Block> STAINED_WINDOW_VARIANT = registerBlock("stained_window_variant", () -> new HiveHorizontalBlock(stone().lightLevel(state -> 9)));
    public static final RegistryObject<Block> CANDLE_ALCOVE = registerBlock("candle_alcove", () -> new HiveHorizontalBlock(stone().lightLevel(state -> 14)));
    public static final RegistryObject<Block> WALL_SCONCE = registerBlock("wall_sconce", () -> new HiveHorizontalBlock(stone().lightLevel(state -> 15)));
    public static final RegistryObject<Block> SHRINE_RECESS = registerBlock("shrine_recess", () -> new HiveHorizontalBlock(stone()));
    public static final RegistryObject<Block> BLOODSTAINED_FLOOR_TILE = registerBlock("bloodstained_floor_tile", () -> new Block(stone()));
    public static final RegistryObject<Block> CATHEDRAL_FLOOR_TILE = registerBlock("cathedral_floor_tile", () -> new Block(stone()));
    public static final RegistryObject<Block> METAL_FLOOR_PLATE = registerBlock("metal_floor_plate", () -> new Block(metal()));
    public static final RegistryObject<Block> FLOOR_GRATE = registerBlock("floor_grate", () -> new Block(metal().noOcclusion()));
    public static final RegistryObject<Block> CATHEDRAL_STAIR_BLOCK = registerBlock("cathedral_stair_block", () -> new HiveHorizontalBlock(stone().noOcclusion()));
    public static final RegistryObject<Block> LANDING_SLAB = registerBlock("landing_slab", () -> new Block(stone()));
    public static final RegistryObject<Block> BALUSTRADE_RAILING = registerBlock("balustrade_railing", () -> new HiveHorizontalBlock(stone().noOcclusion()));
    public static final RegistryObject<Block> SKULL_RELIEF_PANEL = registerBlock("skull_relief_panel", () -> new HiveHorizontalBlock(stone()));
    public static final RegistryObject<Block> GARGOYLE_PEDESTAL = registerBlock("gargoyle_pedestal", () -> new HiveHorizontalBlock(stone().noOcclusion()));
    public static final RegistryObject<Block> INDUSTRIAL_CRATE = registerBlock("industrial_crate", () -> new HiveHorizontalBlock(wood()));
    public static final RegistryObject<Block> BRAZIER_BLOCK = registerBlock("brazier_block", () -> new Block(stone().noOcclusion().lightLevel(state -> 15)));

    public static void addToCreativeTab(CreativeModeTab.Output output) {
        output.accept(ARMORED_BULKHEAD_WALL.get());
        output.accept(RECESSED_STEEL_WALL_PANEL.get());
        output.accept(GOTHIC_ARCH_WALL.get());
        output.accept(TALL_RIBBED_PILLAR.get());
        output.accept(BUTTRESS_COLUMN.get());
        output.accept(CATHEDRAL_CORNICE.get());
        output.accept(LOWER_WALL_MOLDING.get());
        output.accept(SPIRE_CAP_BLOCK.get());
        output.accept(BALCONY_EDGE_TRIM.get());
        output.accept(BRIDGE_SUPPORT_BLOCK.get());
        output.accept(GIANT_DOOR_SEGMENT.get());
        output.accept(NARROW_LANCET_RECESS.get());
        output.accept(TRIANGULAR_RELIEF_PANEL.get());
        output.accept(WINDOW_SLOT_FRAME.get());
        output.accept(HEAVY_STRUCTURAL_FRAME.get());
        output.accept(VERTICAL_SEAM_STRIP.get());
        output.accept(STRAIGHT_PIPE.get());
        output.accept(ELBOW_PIPE.get());
        output.accept(T_PIPE_JUNCTION.get());
        output.accept(CROSS_PIPE_JUNCTION.get());
        output.accept(PIPE_SUPPORT_CLAMP.get());
        output.accept(VERTICAL_SERVICE_CONDUIT.get());
        output.accept(CABLE_BUNDLE_BLOCK.get());
        output.accept(VENT_OUTLET.get());
        output.accept(FLOOR_VENT.get());
        output.accept(LIFT_RAIL.get());
        output.accept(GANTRY_BEAM.get());
        output.accept(SUSPENDED_TRACK_ANCHOR.get());
        output.accept(MAINTENANCE_HATCH.get());
        output.accept(MACHINE_CASING_BLOCK.get());
        output.accept(HAZARD_GRATED_FLOOR.get());
        output.accept(REINFORCED_PLATFORM_EDGE.get());
        output.accept(GLOWING_SHRINE_WINDOW.get());
        output.accept(STAINED_WINDOW_VARIANT.get());
        output.accept(CANDLE_ALCOVE.get());
        output.accept(WALL_SCONCE.get());
        output.accept(SHRINE_RECESS.get());
        output.accept(BLOODSTAINED_FLOOR_TILE.get());
        output.accept(CATHEDRAL_FLOOR_TILE.get());
        output.accept(METAL_FLOOR_PLATE.get());
        output.accept(FLOOR_GRATE.get());
        output.accept(CATHEDRAL_STAIR_BLOCK.get());
        output.accept(LANDING_SLAB.get());
        output.accept(BALUSTRADE_RAILING.get());
        output.accept(SKULL_RELIEF_PANEL.get());
        output.accept(GARGOYLE_PEDESTAL.get());
        output.accept(INDUSTRIAL_CRATE.get());
        output.accept(BRAZIER_BLOCK.get());
    }

    private static BlockBehaviour.Properties metal() { return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0F, 10.0F).requiresCorrectToolForDrops().sound(SoundType.METAL); }
    private static BlockBehaviour.Properties stone() { return BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.0F, 10.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS); }
    private static BlockBehaviour.Properties wood() { return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0F, 5.0F).sound(SoundType.WOOD); }

    private static RegistryObject<Block> registerBlock(String name, Supplier<Block> supplier) {
        RegistryObject<Block> block = BLOCKS.register(name, supplier);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
    private HiveCityConceptBlocks() {}
}
