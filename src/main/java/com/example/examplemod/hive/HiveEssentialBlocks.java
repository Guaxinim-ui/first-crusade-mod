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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Hive City essentials — glass, grates and lumens.
 *
 * Companion to {@link HiveConstructionBlocks}: the same palette, but the pieces you
 * cannot make out of stairs and slabs. Everything uses the correct vanilla base class
 * ({@code IronBarsBlock} for panes and grates, so they connect and cull properly), and
 * every block is non-rotating — no orientable models, no surprise facing states.
 */
public final class HiveEssentialBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MODID);

    public static final RegistryObject<Block> HIVE_GLASS = registerBlock("hive_glass",
            () -> new Block(glass()));

    public static final RegistryObject<Block> HIVE_GLASS_PANE = registerBlock("hive_glass_pane",
            () -> new IronBarsBlock(glass()));

    public static final RegistryObject<Block> STAINED_SANCTUM_GLASS = registerBlock("stained_sanctum_glass",
            () -> new Block(glass()));

    public static final RegistryObject<Block> STAINED_SANCTUM_GLASS_PANE = registerBlock("stained_sanctum_glass_pane",
            () -> new IronBarsBlock(glass()));

    public static final RegistryObject<Block> HIVE_GRATE = registerBlock("hive_grate",
            () -> new IronBarsBlock(bars()));

    public static final RegistryObject<Block> HIVE_BARS = registerBlock("hive_bars",
            () -> new IronBarsBlock(bars()));

    public static final RegistryObject<Block> HIVE_CATWALK_GRATE = registerBlock("hive_catwalk_grate",
            () -> new Block(bars()));

    public static final RegistryObject<Block> HIVE_LUMEN_WHITE = registerBlock("hive_lumen_white",
            () -> new Block(lumen15()));

    public static final RegistryObject<Block> HIVE_LUMEN_AMBER = registerBlock("hive_lumen_amber",
            () -> new Block(lumen15()));

    public static final RegistryObject<Block> HIVE_LUMEN_RED = registerBlock("hive_lumen_red",
            () -> new Block(lumen12()));

    public static final RegistryObject<Block> HIVE_LUMEN_GREEN = registerBlock("hive_lumen_green",
            () -> new Block(lumen12()));

    public static final RegistryObject<Block> HIVE_FLOOR_LUMEN = registerBlock("hive_floor_lumen",
            () -> new Block(lumen15()));

    public static final RegistryObject<Block> SANCTUM_CANDLE_LUMEN = registerBlock("sanctum_candle_lumen",
            () -> new Block(lumen12()));

    /** Decorative solid sewage — looks like the fluid but is a full translucent block, no damage. */
    public static final RegistryObject<Block> SOLID_SEWAGE = registerBlock("solid_sewage",
            () -> new Block(sewage()));

    // =========================================================================================
    // Creative tab
    // =========================================================================================

    public static final RegistryObject<CreativeModeTab> ESSENTIALS_TAB =
            CREATIVE_MODE_TABS.register("hive_essentials_tab", () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.firstcrusade.hive_essentials_tab"))
                    .icon(() -> HIVE_LUMEN_AMBER.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(HIVE_GLASS.get());
                        output.accept(HIVE_GLASS_PANE.get());
                        output.accept(STAINED_SANCTUM_GLASS.get());
                        output.accept(STAINED_SANCTUM_GLASS_PANE.get());
                        output.accept(HIVE_GRATE.get());
                        output.accept(HIVE_BARS.get());
                        output.accept(HIVE_CATWALK_GRATE.get());
                        output.accept(HIVE_LUMEN_WHITE.get());
                        output.accept(HIVE_LUMEN_AMBER.get());
                        output.accept(HIVE_LUMEN_RED.get());
                        output.accept(HIVE_LUMEN_GREEN.get());
                        output.accept(HIVE_FLOOR_LUMEN.get());
                        output.accept(SANCTUM_CANDLE_LUMEN.get());
                        output.accept(SOLID_SEWAGE.get());
                        output.accept(HiveBlocks.TOXIC_SLUDGE_BUCKET.get());
                    })
                    .build());

    // =========================================================================================
    // Material properties
    // =========================================================================================

    private static BlockBehaviour.Properties glass() {
        return BlockBehaviour.Properties.copy(Blocks.GLASS)
                .strength(1.5F, 3.0F)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    private static BlockBehaviour.Properties bars() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 12.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties lumen15() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .lightLevel(state -> 15);
    }

    private static BlockBehaviour.Properties sewage() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GREEN)
                .strength(1.0F, 2.0F)
                .sound(SoundType.SLIME_BLOCK)
                .noOcclusion()
                .lightLevel(state -> 3);
    }

    private static BlockBehaviour.Properties lumen12() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .lightLevel(state -> 12);
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

    private HiveEssentialBlocks() {
    }
}
