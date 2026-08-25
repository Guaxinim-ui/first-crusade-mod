package com.example.examplemod.hive;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Hive fluids + structure markers registry.
 *
 * <p>The old decorative/structural Hive block set (concept sheet + FASE 2/6/9 — ~119 blocks) was
 * removed and is being replaced by a new kit. This class deliberately keeps ONLY:</p>
 * <ul>
 *   <li>the toxic-sludge liquid block + bucket — referenced directly by {@link HiveFluids}, so
 *       moving them elsewhere would cause circular initialisation; and</li>
 *   <li>the 12 structure-marker blocks — these are <em>not</em> decorative: they are embedded in
 *       the city NBT templates and consumed by {@link HiveMarkerProcessor} and the persistent
 *       marker / population system.</li>
 * </ul>
 *
 * <p>{@link #register(IEventBus)} still wires {@link HiveProcessors}, {@link HiveFluids} and
 * {@link HiveEntities} so the fluid system and the seat entity keep working.</p>
 */
public final class HiveBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MODID);

    /** Água/lodo tóxico colocado — LiquidBlock com dano de contato (ver ToxicSludgeBlock). */
    public static final RegistryObject<LiquidBlock> TOXIC_SLUDGE_BLOCK = BLOCKS.register("toxic_sludge",
            () -> new ToxicSludgeBlock(HiveFluids.TOXIC_SLUDGE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    .noLootTable()
                    .liquid()
                    .lightLevel(state -> 4)));

    /** Balde de lodo tóxico (registrado aqui para o ForgeFlowingFluid.Properties.bucket). */
    public static final RegistryObject<Item> TOXIC_SLUDGE_BUCKET = ITEMS.register("toxic_sludge_bucket",
            () -> new BucketItem(HiveFluids.TOXIC_SLUDGE,
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));

    /**
     * The Hive's vertical circulation (spec §18) — see {@link HiveTransitLiftBlock}.
     *
     * <p>Registered here rather than in {@link HiveEssentialBlocks} because that class is glass,
     * grates and lumens: things you look at. This one has behaviour, and this class is where the
     * Hive's functional blocks already live.
     */
    public static final RegistryObject<Block> HIVE_TRANSIT_LIFT = registerBlock("hive_transit_lift",
            () -> new HiveTransitLiftBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)));

    // =========================================================================================
    // STRUCTURE MARKERS (spec §13) — dev-only blocks the HiveMarkerProcessor converts to air on
    // placement, capturing their positions. Kept because the NBT templates + persistent-marker /
    // population system depend on them.
    // =========================================================================================

    public static final Map<HiveMarkers.MarkerType, RegistryObject<Block>> MARKERS =
            new EnumMap<>(HiveMarkers.MarkerType.class);

    static {
        for (HiveMarkers.MarkerType type : HiveMarkers.MarkerType.values()) {
            MARKERS.put(type, registerBlock(type.id(),
                    () -> new HiveMarkerBlock(type, BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_MAGENTA)
                            .instabreak()
                            .noCollission()
                            .noOcclusion()
                            .sound(SoundType.LANTERN))));
        }
    }

    // =========================================================================================
    // CREATIVE TAB — minimal: only the surviving toxic-sludge bucket and the dev markers, so the
    // markers stay placeable for structure authoring. The new decorative kit brings its own tab.
    // =========================================================================================

    public static final RegistryObject<CreativeModeTab> HIVE_TAB =
            CREATIVE_MODE_TABS.register("hive_tab", () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.firstcrusade.hive_tab"))
                    .icon(() -> TOXIC_SLUDGE_BUCKET.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(TOXIC_SLUDGE_BUCKET.get());
                        output.accept(HIVE_TRANSIT_LIFT.get());
                        for (HiveMarkers.MarkerType type : HiveMarkers.MarkerType.values()) {
                            output.accept(MARKERS.get(type).get());
                        }
                    })
                    .build());

    // =========================================================================================
    // Plumbing
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
        HiveProcessors.register(modEventBus);
        HiveFluids.register(modEventBus);
        HiveEntities.register(modEventBus);
    }

    private HiveBlocks() {
    }
}
