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
import net.minecraft.world.level.block.RotatedPillarBlock;
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
 * Hive City concrete set — six tones, four surface treatments each.
 *
 * Per tone: smooth, panel, ribbed and cracked full blocks, a rotatable pillar,
 * stairs and slabs for both the smooth and panel faces, and a wall. 60 blocks.
 *
 * Pillars use vanilla {@link RotatedPillarBlock} so the axis behaves exactly like
 * a log — place against any face and it orients predictably.
 */
public final class HiveConcreteBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MODID);

    // ---------------------------------------------------------------------------------------
    // pale_concrete
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> PALE_CONCRETE = registerBlock("pale_concrete",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> PALE_CONCRETE_PANEL = registerBlock("pale_concrete_panel",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> PALE_CONCRETE_RIBBED = registerBlock("pale_concrete_ribbed",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> PALE_CONCRETE_CRACKED = registerBlock("pale_concrete_cracked",
            () -> new Block(cracked()));

    public static final RegistryObject<Block> PALE_CONCRETE_PILLAR = registerBlock("pale_concrete_pillar",
            () -> new RotatedPillarBlock(concrete()));

    public static final RegistryObject<Block> PALE_CONCRETE_STAIRS = registerBlock("pale_concrete_stairs",
            () -> new StairBlock(() -> PALE_CONCRETE.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> PALE_CONCRETE_SLAB = registerBlock("pale_concrete_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> PALE_CONCRETE_PANEL_STAIRS = registerBlock("pale_concrete_panel_stairs",
            () -> new StairBlock(() -> PALE_CONCRETE_PANEL.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> PALE_CONCRETE_PANEL_SLAB = registerBlock("pale_concrete_panel_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> PALE_CONCRETE_WALL = registerBlock("pale_concrete_wall",
            () -> new WallBlock(concrete()));

    // ---------------------------------------------------------------------------------------
    // ash_concrete
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> ASH_CONCRETE = registerBlock("ash_concrete",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> ASH_CONCRETE_PANEL = registerBlock("ash_concrete_panel",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> ASH_CONCRETE_RIBBED = registerBlock("ash_concrete_ribbed",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> ASH_CONCRETE_CRACKED = registerBlock("ash_concrete_cracked",
            () -> new Block(cracked()));

    public static final RegistryObject<Block> ASH_CONCRETE_PILLAR = registerBlock("ash_concrete_pillar",
            () -> new RotatedPillarBlock(concrete()));

    public static final RegistryObject<Block> ASH_CONCRETE_STAIRS = registerBlock("ash_concrete_stairs",
            () -> new StairBlock(() -> ASH_CONCRETE.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> ASH_CONCRETE_SLAB = registerBlock("ash_concrete_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> ASH_CONCRETE_PANEL_STAIRS = registerBlock("ash_concrete_panel_stairs",
            () -> new StairBlock(() -> ASH_CONCRETE_PANEL.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> ASH_CONCRETE_PANEL_SLAB = registerBlock("ash_concrete_panel_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> ASH_CONCRETE_WALL = registerBlock("ash_concrete_wall",
            () -> new WallBlock(concrete()));

    // ---------------------------------------------------------------------------------------
    // dark_concrete
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> DARK_CONCRETE = registerBlock("dark_concrete",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> DARK_CONCRETE_PANEL = registerBlock("dark_concrete_panel",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> DARK_CONCRETE_RIBBED = registerBlock("dark_concrete_ribbed",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> DARK_CONCRETE_CRACKED = registerBlock("dark_concrete_cracked",
            () -> new Block(cracked()));

    public static final RegistryObject<Block> DARK_CONCRETE_PILLAR = registerBlock("dark_concrete_pillar",
            () -> new RotatedPillarBlock(concrete()));

    public static final RegistryObject<Block> DARK_CONCRETE_STAIRS = registerBlock("dark_concrete_stairs",
            () -> new StairBlock(() -> DARK_CONCRETE.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> DARK_CONCRETE_SLAB = registerBlock("dark_concrete_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> DARK_CONCRETE_PANEL_STAIRS = registerBlock("dark_concrete_panel_stairs",
            () -> new StairBlock(() -> DARK_CONCRETE_PANEL.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> DARK_CONCRETE_PANEL_SLAB = registerBlock("dark_concrete_panel_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> DARK_CONCRETE_WALL = registerBlock("dark_concrete_wall",
            () -> new WallBlock(concrete()));

    // ---------------------------------------------------------------------------------------
    // rust_concrete
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> RUST_CONCRETE = registerBlock("rust_concrete",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> RUST_CONCRETE_PANEL = registerBlock("rust_concrete_panel",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> RUST_CONCRETE_RIBBED = registerBlock("rust_concrete_ribbed",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> RUST_CONCRETE_CRACKED = registerBlock("rust_concrete_cracked",
            () -> new Block(cracked()));

    public static final RegistryObject<Block> RUST_CONCRETE_PILLAR = registerBlock("rust_concrete_pillar",
            () -> new RotatedPillarBlock(concrete()));

    public static final RegistryObject<Block> RUST_CONCRETE_STAIRS = registerBlock("rust_concrete_stairs",
            () -> new StairBlock(() -> RUST_CONCRETE.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> RUST_CONCRETE_SLAB = registerBlock("rust_concrete_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> RUST_CONCRETE_PANEL_STAIRS = registerBlock("rust_concrete_panel_stairs",
            () -> new StairBlock(() -> RUST_CONCRETE_PANEL.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> RUST_CONCRETE_PANEL_SLAB = registerBlock("rust_concrete_panel_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> RUST_CONCRETE_WALL = registerBlock("rust_concrete_wall",
            () -> new WallBlock(concrete()));

    // ---------------------------------------------------------------------------------------
    // ochre_concrete
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> OCHRE_CONCRETE = registerBlock("ochre_concrete",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_PANEL = registerBlock("ochre_concrete_panel",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_RIBBED = registerBlock("ochre_concrete_ribbed",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_CRACKED = registerBlock("ochre_concrete_cracked",
            () -> new Block(cracked()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_PILLAR = registerBlock("ochre_concrete_pillar",
            () -> new RotatedPillarBlock(concrete()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_STAIRS = registerBlock("ochre_concrete_stairs",
            () -> new StairBlock(() -> OCHRE_CONCRETE.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_SLAB = registerBlock("ochre_concrete_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_PANEL_STAIRS = registerBlock("ochre_concrete_panel_stairs",
            () -> new StairBlock(() -> OCHRE_CONCRETE_PANEL.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_PANEL_SLAB = registerBlock("ochre_concrete_panel_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> OCHRE_CONCRETE_WALL = registerBlock("ochre_concrete_wall",
            () -> new WallBlock(concrete()));

    // ---------------------------------------------------------------------------------------
    // slate_concrete
    // ---------------------------------------------------------------------------------------

    public static final RegistryObject<Block> SLATE_CONCRETE = registerBlock("slate_concrete",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> SLATE_CONCRETE_PANEL = registerBlock("slate_concrete_panel",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> SLATE_CONCRETE_RIBBED = registerBlock("slate_concrete_ribbed",
            () -> new Block(concrete()));

    public static final RegistryObject<Block> SLATE_CONCRETE_CRACKED = registerBlock("slate_concrete_cracked",
            () -> new Block(cracked()));

    public static final RegistryObject<Block> SLATE_CONCRETE_PILLAR = registerBlock("slate_concrete_pillar",
            () -> new RotatedPillarBlock(concrete()));

    public static final RegistryObject<Block> SLATE_CONCRETE_STAIRS = registerBlock("slate_concrete_stairs",
            () -> new StairBlock(() -> SLATE_CONCRETE.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> SLATE_CONCRETE_SLAB = registerBlock("slate_concrete_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> SLATE_CONCRETE_PANEL_STAIRS = registerBlock("slate_concrete_panel_stairs",
            () -> new StairBlock(() -> SLATE_CONCRETE_PANEL.get().defaultBlockState(), concrete()));

    public static final RegistryObject<Block> SLATE_CONCRETE_PANEL_SLAB = registerBlock("slate_concrete_panel_slab",
            () -> new SlabBlock(concrete()));

    public static final RegistryObject<Block> SLATE_CONCRETE_WALL = registerBlock("slate_concrete_wall",
            () -> new WallBlock(concrete()));

    // =========================================================================================
    // Creative tab
    // =========================================================================================

    public static final RegistryObject<CreativeModeTab> CONCRETE_TAB =
            CREATIVE_MODE_TABS.register("hive_concrete_tab", () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.firstcrusade.hive_concrete_tab"))
                    .icon(() -> ASH_CONCRETE_PANEL.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(PALE_CONCRETE.get());
                        output.accept(PALE_CONCRETE_PANEL.get());
                        output.accept(PALE_CONCRETE_RIBBED.get());
                        output.accept(PALE_CONCRETE_CRACKED.get());
                        output.accept(PALE_CONCRETE_PILLAR.get());
                        output.accept(PALE_CONCRETE_STAIRS.get());
                        output.accept(PALE_CONCRETE_SLAB.get());
                        output.accept(PALE_CONCRETE_PANEL_STAIRS.get());
                        output.accept(PALE_CONCRETE_PANEL_SLAB.get());
                        output.accept(PALE_CONCRETE_WALL.get());
                        output.accept(ASH_CONCRETE.get());
                        output.accept(ASH_CONCRETE_PANEL.get());
                        output.accept(ASH_CONCRETE_RIBBED.get());
                        output.accept(ASH_CONCRETE_CRACKED.get());
                        output.accept(ASH_CONCRETE_PILLAR.get());
                        output.accept(ASH_CONCRETE_STAIRS.get());
                        output.accept(ASH_CONCRETE_SLAB.get());
                        output.accept(ASH_CONCRETE_PANEL_STAIRS.get());
                        output.accept(ASH_CONCRETE_PANEL_SLAB.get());
                        output.accept(ASH_CONCRETE_WALL.get());
                        output.accept(DARK_CONCRETE.get());
                        output.accept(DARK_CONCRETE_PANEL.get());
                        output.accept(DARK_CONCRETE_RIBBED.get());
                        output.accept(DARK_CONCRETE_CRACKED.get());
                        output.accept(DARK_CONCRETE_PILLAR.get());
                        output.accept(DARK_CONCRETE_STAIRS.get());
                        output.accept(DARK_CONCRETE_SLAB.get());
                        output.accept(DARK_CONCRETE_PANEL_STAIRS.get());
                        output.accept(DARK_CONCRETE_PANEL_SLAB.get());
                        output.accept(DARK_CONCRETE_WALL.get());
                        output.accept(RUST_CONCRETE.get());
                        output.accept(RUST_CONCRETE_PANEL.get());
                        output.accept(RUST_CONCRETE_RIBBED.get());
                        output.accept(RUST_CONCRETE_CRACKED.get());
                        output.accept(RUST_CONCRETE_PILLAR.get());
                        output.accept(RUST_CONCRETE_STAIRS.get());
                        output.accept(RUST_CONCRETE_SLAB.get());
                        output.accept(RUST_CONCRETE_PANEL_STAIRS.get());
                        output.accept(RUST_CONCRETE_PANEL_SLAB.get());
                        output.accept(RUST_CONCRETE_WALL.get());
                        output.accept(OCHRE_CONCRETE.get());
                        output.accept(OCHRE_CONCRETE_PANEL.get());
                        output.accept(OCHRE_CONCRETE_RIBBED.get());
                        output.accept(OCHRE_CONCRETE_CRACKED.get());
                        output.accept(OCHRE_CONCRETE_PILLAR.get());
                        output.accept(OCHRE_CONCRETE_STAIRS.get());
                        output.accept(OCHRE_CONCRETE_SLAB.get());
                        output.accept(OCHRE_CONCRETE_PANEL_STAIRS.get());
                        output.accept(OCHRE_CONCRETE_PANEL_SLAB.get());
                        output.accept(OCHRE_CONCRETE_WALL.get());
                        output.accept(SLATE_CONCRETE.get());
                        output.accept(SLATE_CONCRETE_PANEL.get());
                        output.accept(SLATE_CONCRETE_RIBBED.get());
                        output.accept(SLATE_CONCRETE_CRACKED.get());
                        output.accept(SLATE_CONCRETE_PILLAR.get());
                        output.accept(SLATE_CONCRETE_STAIRS.get());
                        output.accept(SLATE_CONCRETE_SLAB.get());
                        output.accept(SLATE_CONCRETE_PANEL_STAIRS.get());
                        output.accept(SLATE_CONCRETE_PANEL_SLAB.get());
                        output.accept(SLATE_CONCRETE_WALL.get());
                    })
                    .build());

    // =========================================================================================

    private static BlockBehaviour.Properties concrete() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(4.0F, 12.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS);
    }

    private static BlockBehaviour.Properties cracked() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(3.0F, 9.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE);
    }

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

    private HiveConcreteBlocks() {
    }
}
