package com.example.examplemod.hive;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.item.Items;
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
 * FASE 2 — Hive City foundation block set (29 blocks).
 *
 * Self-contained registry for everything Hive-related, so the (already huge) ExampleMod class
 * only needs a single {@code HiveBlocks.register(modEventBus)} call. All blocks here are plain
 * decorative/structural blocks: NO BlockEntities, NO ticking — performance rule #1 of the Hive
 * City spec (§15). Behaviour-carrying blocks (working machines, marker blocks) come in later
 * phases as separate registries.
 *
 * Naming: english snake_case ids (spec §9). Every block gets a BlockItem and appears in the
 * dedicated "Hive City" creative tab.
 */
public final class HiveBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MODID);

    // =========================================================================================
    // 9.1 STRUCTURAL
    // =========================================================================================

    public static final RegistryObject<Block> REINFORCED_ASHCRETE = registerBlock("reinforced_ashcrete",
            () -> new Block(ashcrete()));

    public static final RegistryObject<Block> CRACKED_REINFORCED_ASHCRETE = registerBlock("cracked_reinforced_ashcrete",
            () -> new Block(ashcrete()));

    public static final RegistryObject<Block> REINFORCED_ASHCRETE_STAIRS = registerBlock("reinforced_ashcrete_stairs",
            () -> new StairBlock(() -> REINFORCED_ASHCRETE.get().defaultBlockState(), ashcrete()));

    public static final RegistryObject<Block> REINFORCED_ASHCRETE_SLAB = registerBlock("reinforced_ashcrete_slab",
            () -> new SlabBlock(ashcrete()));

    public static final RegistryObject<Block> REINFORCED_ASHCRETE_WALL = registerBlock("reinforced_ashcrete_wall",
            () -> new WallBlock(ashcrete()));

    public static final RegistryObject<Block> RIVETED_STEEL_BLOCK = registerBlock("riveted_steel_block",
            () -> new Block(steel()));

    public static final RegistryObject<Block> RUSTED_RIVETED_STEEL = registerBlock("rusted_riveted_steel",
            () -> new Block(steel().mapColor(MapColor.TERRACOTTA_ORANGE)));

    public static final RegistryObject<Block> RIVETED_STEEL_STAIRS = registerBlock("riveted_steel_stairs",
            () -> new StairBlock(() -> RIVETED_STEEL_BLOCK.get().defaultBlockState(), steel()));

    public static final RegistryObject<Block> RIVETED_STEEL_SLAB = registerBlock("riveted_steel_slab",
            () -> new SlabBlock(steel()));

    public static final RegistryObject<Block> ARMORED_HIVE_PLATING = registerBlock("armored_hive_plating",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(8.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)));

    // =========================================================================================
    // 9.2 FLOORS & CATWALKS
    // =========================================================================================

    public static final RegistryObject<Block> INDUSTRIAL_GRATING = registerBlock("industrial_grating",
            () -> new HiveGratingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.CHAIN)
                    .noOcclusion()));

    public static final RegistryObject<Block> INDUSTRIAL_CATWALK = registerBlock("industrial_catwalk",
            () -> new HiveCatwalkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.CHAIN)
                    .noOcclusion()));

    public static final RegistryObject<Block> INDUSTRIAL_RAILING = registerBlock("industrial_railing",
            () -> new IronBarsBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.CHAIN)
                    .noOcclusion()));

    // =========================================================================================
    // 9.3 PIPES
    // =========================================================================================

    /** 10px-thick main pipe. Apothem 0.3125 = 5/16 half-width. */
    public static final RegistryObject<Block> LARGE_HIVE_PIPE = registerBlock("large_hive_pipe",
            () -> new HivePipeBlock(0.3125F, pipe()));

    /** 12px bolted junction collar; arms auto-connect exactly like the pipe. */
    public static final RegistryObject<Block> PIPE_JUNCTION = registerBlock("pipe_junction",
            () -> new HivePipeBlock(0.375F, pipe()));

    /** Axis-aligned valve segment; pipes connect to it via the pipe_connectable tag. */
    public static final RegistryObject<Block> PRESSURE_VALVE = registerBlock("pressure_valve",
            () -> new RotatedPillarBlock(pipe()));

    // =========================================================================================
    // 9.4 MACHINES (decorative shells — functional machines come in later phases)
    // =========================================================================================

    public static final RegistryObject<Block> MACHINE_CASING = registerBlock("machine_casing",
            () -> new Block(steel().strength(4.5F, 8.0F)));

    public static final RegistryObject<Block> INDUSTRIAL_VENT = registerBlock("industrial_vent",
            () -> new HiveHorizontalBlock(steel().strength(3.5F, 6.0F)));

    // =========================================================================================
    // 9.5 IMPERIAL GOTHIC ARCHITECTURE
    // =========================================================================================

    public static final RegistryObject<Block> GOTHIC_ARCH = registerBlock("gothic_arch",
            () -> new Block(cathedral()));

    public static final RegistryObject<Block> IMPERIAL_COLUMN = registerBlock("imperial_column",
            () -> new RotatedPillarBlock(cathedral().strength(4.0F, 10.0F)));

    public static final RegistryObject<Block> CATHEDRAL_WALL = registerBlock("cathedral_wall",
            () -> new Block(cathedral()));

    public static final RegistryObject<Block> SKULL_WALL_RELIEF = registerBlock("skull_wall_relief",
            () -> new Block(cathedral()));

    public static final RegistryObject<Block> AQUILA_WALL_RELIEF = registerBlock("aquila_wall_relief",
            () -> new Block(cathedral()));

    // =========================================================================================
    // 9.6 LIGHTING (plain light-emitting blocks — no BlockEntities, no dynamic lights)
    // =========================================================================================

    public static final RegistryObject<Block> HIVE_LUMEN_STRIP = registerBlock("hive_lumen_strip",
            () -> new RotatedPillarBlock(lumen(15)));

    public static final RegistryObject<Block> YELLOW_INDUSTRIAL_LUMEN = registerBlock("yellow_industrial_lumen",
            () -> new Block(lumen(15).mapColor(MapColor.TERRACOTTA_YELLOW)));

    public static final RegistryObject<Block> GREEN_INDUSTRIAL_LUMEN = registerBlock("green_industrial_lumen",
            () -> new Block(lumen(13).mapColor(MapColor.TERRACOTTA_GREEN)));

    public static final RegistryObject<Block> RED_EMERGENCY_LUMEN = registerBlock("red_emergency_lumen",
            () -> new Block(lumen(10).mapColor(MapColor.TERRACOTTA_RED)));

    // =========================================================================================
    // 9.7 DECORATION
    // =========================================================================================

    public static final RegistryObject<Block> HAZARD_STRIPE_PANEL = registerBlock("hazard_stripe_panel",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final RegistryObject<Block> CARGO_CONTAINER = registerBlock("cargo_container",
            () -> new HiveHorizontalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_GREEN)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    // =========================================================================================
    // FASE 6.5 — PACOTE DE DETALHAMENTO (móveis, tapetes, sludge tóxico, luzes fortes).
    // =========================================================================================

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
            () -> new net.minecraft.world.item.BucketItem(HiveFluids.TOXIC_SLUDGE,
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));

    /** Sludge sólido decorativo (parece líquido mas é bloco cheio translúcido, sem dano). */
    public static final RegistryObject<Block> SOLID_TOXIC_SLUDGE = registerBlock("solid_toxic_sludge",
            () -> new HiveGratingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(1.0F, 2.0F)
                    .sound(SoundType.HONEY_BLOCK)
                    .lightLevel(state -> 7)
                    .noOcclusion()));

    // ---- Móveis ----
    public static final RegistryObject<Block> HIVE_TABLE = registerBlock("hive_table",
            () -> new HiveShapeBlocks.Table(steel().strength(2.5F, 4.0F)));

    public static final RegistryObject<Block> HIVE_CHAIR = registerBlock("hive_chair",
            () -> new HiveChairBlock(0.35D, steel().strength(2.0F, 3.0F).noOcclusion()));

    public static final RegistryObject<Block> HIVE_BENCH = registerBlock("hive_bench",
            () -> new HiveChairBlock(0.30D, steel().strength(2.5F, 4.0F).noOcclusion()));

    public static final RegistryObject<Block> HIVE_RUG = registerBlock("hive_rug",
            () -> new HiveShapeBlocks.Rug(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    public static final RegistryObject<Block> SHELF_UNIT = registerBlock("shelf_unit",
            () -> new HiveHorizontalBlock(steel().strength(2.5F, 4.0F).noOcclusion()));

    /**
     * Baú de suprimentos — decorativo por ora. Um contêiner FUNCIONAL exige BlockEntity +
     * MenuType próprios (o BarrelBlock vanilla depende do BlockEntityType.BARREL, que não
     * podemos reusar com segurança). Fica como bloco cheio com face; a versão com inventário
     * é um follow-up natural para o Claude Code (registrar HiveContainerBlockEntity + menu).
     */
    public static final RegistryObject<Block> SUPPLY_CRATE = registerBlock("supply_crate",
            () -> new HiveHorizontalBlock(steel().strength(3.0F, 5.0F).sound(SoundType.WOOD)));

    // ---- Luzes fortes (nível 15, cores e formatos variados) ----
    public static final RegistryObject<Block> INDUSTRIAL_FLOODLIGHT = registerBlock("industrial_floodlight",
            () -> new HiveHorizontalBlock(lumen(15).sound(SoundType.LANTERN)));

    public static final RegistryObject<Block> HANGING_HIVE_LAMP = registerBlock("hanging_hive_lamp",
            () -> new Block(lumen(15).mapColor(MapColor.TERRACOTTA_YELLOW).noOcclusion()));

    public static final RegistryObject<Block> CATHEDRAL_BRAZIER = registerBlock("cathedral_brazier",
            () -> new Block(lumen(15).mapColor(MapColor.COLOR_ORANGE).noOcclusion().sound(SoundType.LANTERN)));

    public static final RegistryObject<Block> WARNING_BEACON = registerBlock("warning_beacon",
            () -> new Block(lumen(14).mapColor(MapColor.COLOR_RED).noOcclusion().sound(SoundType.LANTERN)));

    // ---- Canos maiores (12px e 14px de diâmetro) ----
    /** Cano grosso 12px — mesma lógica de conexão do large_hive_pipe. */
    public static final RegistryObject<Block> HUGE_HIVE_PIPE = registerBlock("huge_hive_pipe",
            () -> new HivePipeBlock(0.375F, pipe()));

    /** Coletor 14px — quase cheio, para troncos principais. */
    public static final RegistryObject<Block> MAIN_PIPE_TRUNK = registerBlock("main_pipe_trunk",
            () -> new HivePipeBlock(0.4375F, pipe()));

    // ---- Detalhes finos ----
    public static final RegistryObject<Block> INDUSTRIAL_CHAIN = registerBlock("industrial_chain",
            () -> new RotatedPillarBlock(pipe().strength(3.0F, 6.0F)));

    public static final RegistryObject<Block> CABLE_BUNDLE = registerBlock("cable_bundle",
            () -> new RotatedPillarBlock(steel().strength(2.0F, 4.0F).noOcclusion()));

    public static final RegistryObject<Block> WALL_TERMINAL = registerBlock("wall_terminal",
            () -> new HiveHorizontalBlock(steel().strength(2.5F, 4.0F).lightLevel(state -> 6)));

    public static final RegistryObject<Block> SECTOR_NUMBER_PANEL = registerBlock("sector_number_panel",
            () -> new HiveHorizontalBlock(steel().strength(2.5F, 4.0F)));

    // ---- Estátuas (FASE 6.5 Parte B) ----
    /** Estátua pequena de 1 bloco (busto de santo) — com face. */
    public static final RegistryObject<Block> SAINT_BUST = registerBlock("saint_bust",
            () -> new HiveHorizontalBlock(cathedral().strength(3.0F, 8.0F).noOcclusion()));

    /** Estátua pequena (águia em pedestal) — com face. */
    public static final RegistryObject<Block> AQUILA_STATUE = registerBlock("aquila_statue",
            () -> new HiveHorizontalBlock(cathedral().strength(3.0F, 8.0F).noOcclusion()));

    /** Estátua grande (santo guerreiro) — 3 blocos de altura, modelo alto. */
    public static final RegistryObject<Block> SAINT_STATUE = registerBlock("saint_statue",
            () -> new HiveStatueBlock(3, cathedral().strength(4.0F, 10.0F).noOcclusion()));

    /** Estátua grande (guardião imperial com lança) — 3 blocos de altura. */
    public static final RegistryObject<Block> IMPERIAL_GUARDIAN_STATUE = registerBlock("imperial_guardian_statue",
            () -> new HiveStatueBlock(3, cathedral().strength(4.0F, 10.0F).noOcclusion()));

    /** Estandarte da águia (2 blocos de altura) — para salões e naves. */
    public static final RegistryObject<Block> AQUILA_BANNER = registerBlock("aquila_banner",
            () -> new HiveStatueBlock(2, cathedral().strength(2.5F, 5.0F).noOcclusion()));

    // ---- Underhive (FASE 9) ----
    /** Escombros/entulho — bloco cheio irregular, base da Underhive. */
    public static final RegistryObject<Block> RUBBLE = registerBlock("rubble",
            () -> new Block(ashcrete().strength(2.0F, 4.0F).sound(SoundType.GRAVEL)));

    /** Concreto rachado da Underhive — mais degradado que o cracked_reinforced. */
    public static final RegistryObject<Block> UNDERHIVE_CONCRETE = registerBlock("underhive_concrete",
            () -> new Block(ashcrete().strength(3.0F, 6.0F)));

    /** Pilha de sucata — metal retorcido, com face. */
    public static final RegistryObject<Block> SCRAP_PILE = registerBlock("scrap_pile",
            () -> new HiveHorizontalBlock(steel().strength(2.0F, 4.0F).sound(SoundType.METAL).noOcclusion()));

    /** Fungo luminoso — cresce nas paredes úmidas, luz esverdeada 8. */
    public static final RegistryObject<Block> GLOW_FUNGUS = registerBlock("glow_fungus",
            () -> new HiveGratingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .instabreak()
                    .noCollission()
                    .sound(SoundType.WART_BLOCK)
                    .lightLevel(state -> 8)
                    .noOcclusion()));

    /** Barril tóxico — vazando, com face; decorativo (poças de sludge ao redor colocadas à mão). */
    public static final RegistryObject<Block> TOXIC_BARREL = registerBlock("toxic_barrel",
            () -> new HiveHorizontalBlock(steel().strength(2.5F, 4.0F).sound(SoundType.METAL)
                    .lightLevel(state -> 3).noOcclusion()));

    /** Parede corrugada de sucata — barricadas de gangue. */
    public static final RegistryObject<Block> CORRUGATED_WALL = registerBlock("corrugated_wall",
            () -> new RotatedPillarBlock(steel().strength(2.5F, 4.0F).sound(SoundType.METAL)));

    /** Fogueira de gangue — luz laranja 15, para acampamentos. */
    public static final RegistryObject<Block> GANG_FIRE = registerBlock("gang_fire",
            () -> new Block(lumen(15).mapColor(MapColor.COLOR_ORANGE).noOcclusion().sound(SoundType.WOOD)));

    /** Grafite/marca de gangue — decoração de parede plana. */
    public static final RegistryObject<Block> GANG_MARKING = registerBlock("gang_marking",
            () -> new HiveHorizontalBlock(ashcrete().strength(1.5F, 3.0F)));

    // =========================================================================================
    // FASE 6 — MANUFACTORUM (indústria pesada). Decorativos sem tick, exceto onde a luz/
    // partícula justifica lightLevel fixo. Nada de BlockEntity (spec §9.4/§15).
    // =========================================================================================

    /** Fornalha industrial acesa — face frontal, emite luz 13 (janela de fogo). */
    public static final RegistryObject<Block> FORGE_FURNACE = registerBlock("forge_furnace",
            () -> new HiveHorizontalBlock(steel().strength(5.0F, 10.0F).lightLevel(state -> 13)));

    /** Boca de fundição com metal derretido — luz 14, laranja. */
    public static final RegistryObject<Block> SMELTER_CRUCIBLE = registerBlock("smelter_crucible",
            () -> new Block(steel().strength(5.0F, 10.0F)
                    .mapColor(MapColor.COLOR_ORANGE).lightLevel(state -> 14)));

    /** Esteira transportadora — pilar (eixo) para correr em X ou Z. */
    public static final RegistryObject<Block> CONVEYOR_BELT = registerBlock("conveyor_belt",
            () -> new RotatedPillarBlock(steel().strength(3.5F, 6.0F).sound(SoundType.NETHERITE_BLOCK)));

    /** Turbina/rotor industrial — pilar, para dutos e geradores. */
    public static final RegistryObject<Block> INDUSTRIAL_TURBINE = registerBlock("industrial_turbine",
            () -> new RotatedPillarBlock(steel().strength(5.0F, 9.0F)));

    /** Caldeira pressurizada — casca cilíndrica; conecta canos (tag). */
    public static final RegistryObject<Block> BOILER_TANK = registerBlock("boiler_tank",
            () -> new RotatedPillarBlock(steel().strength(4.5F, 8.0F).noOcclusion()));

    /** Chaminé industrial — pilar oco, tijolo escuro fuliginoso. */
    public static final RegistryObject<Block> SMOKE_STACK = registerBlock("smoke_stack",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(4.0F, 10.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS)));

    /** Console cogitador — face frontal, telas esverdeadas, luz 7. */
    public static final RegistryObject<Block> COGITATOR_CONSOLE = registerBlock("cogitator_console",
            () -> new HiveHorizontalBlock(steel().strength(3.0F, 6.0F)
                    .mapColor(MapColor.COLOR_GREEN).lightLevel(state -> 7)));

    /** Painel de controle — face frontal, botões e mostradores. */
    public static final RegistryObject<Block> CONTROL_PANEL = registerBlock("control_panel",
            () -> new HiveHorizontalBlock(steel().strength(3.0F, 6.0F).lightLevel(state -> 4)));

    /** Duto de ventilação — pilar, para redes de exaustão. */
    public static final RegistryObject<Block> VENTILATION_DUCT = registerBlock("ventilation_duct",
            () -> new RotatedPillarBlock(steel().strength(2.5F, 5.0F).noOcclusion()));

    /** Prensa/martelo industrial — bloco cheio pesado. */
    public static final RegistryObject<Block> INDUSTRIAL_PRESS = registerBlock("industrial_press",
            () -> new Block(steel().strength(6.0F, 12.0F).sound(SoundType.ANVIL)));

    /** Tanque de refrigerante — vidro industrial cheio de líquido verde, luz 6, translúcido. */
    public static final RegistryObject<Block> COOLANT_TANK = registerBlock("coolant_tank",
            () -> new HiveGratingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(2.0F, 4.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 6)
                    .noOcclusion()));

    /** Placa de propaganda imperial — decoração de parede plana. */
    public static final RegistryObject<Block> IMPERIAL_PROPAGANDA_PANEL = registerBlock("imperial_propaganda_panel",
            () -> new HiveHorizontalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0F, 4.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    // =========================================================================================
    // MARCADORES DE ESTRUTURA (spec §13) — só existem na autoria; o HiveMarkerProcessor
    // os converte em ar e captura as posições na colocação do módulo.
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
    // CREATIVE TAB
    // =========================================================================================

    public static final RegistryObject<CreativeModeTab> HIVE_TAB =
            CREATIVE_MODE_TABS.register("hive_tab", () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.firstcrusade.hive_tab"))
                    .icon(() -> SKULL_WALL_RELIEF.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Structural
                        output.accept(REINFORCED_ASHCRETE.get());
                        output.accept(CRACKED_REINFORCED_ASHCRETE.get());
                        output.accept(REINFORCED_ASHCRETE_STAIRS.get());
                        output.accept(REINFORCED_ASHCRETE_SLAB.get());
                        output.accept(REINFORCED_ASHCRETE_WALL.get());
                        output.accept(RIVETED_STEEL_BLOCK.get());
                        output.accept(RUSTED_RIVETED_STEEL.get());
                        output.accept(RIVETED_STEEL_STAIRS.get());
                        output.accept(RIVETED_STEEL_SLAB.get());
                        output.accept(ARMORED_HIVE_PLATING.get());
                        // Floors & catwalks
                        output.accept(INDUSTRIAL_GRATING.get());
                        output.accept(INDUSTRIAL_CATWALK.get());
                        output.accept(INDUSTRIAL_RAILING.get());
                        // Pipes
                        output.accept(LARGE_HIVE_PIPE.get());
                        output.accept(PIPE_JUNCTION.get());
                        output.accept(PRESSURE_VALVE.get());
                        // Machines
                        output.accept(MACHINE_CASING.get());
                        output.accept(INDUSTRIAL_VENT.get());
                        // Gothic
                        output.accept(GOTHIC_ARCH.get());
                        output.accept(IMPERIAL_COLUMN.get());
                        output.accept(CATHEDRAL_WALL.get());
                        output.accept(SKULL_WALL_RELIEF.get());
                        output.accept(AQUILA_WALL_RELIEF.get());
                        // Lighting
                        output.accept(HIVE_LUMEN_STRIP.get());
                        output.accept(YELLOW_INDUSTRIAL_LUMEN.get());
                        output.accept(GREEN_INDUSTRIAL_LUMEN.get());
                        output.accept(RED_EMERGENCY_LUMEN.get());
                        // Decoration
                        output.accept(HAZARD_STRIPE_PANEL.get());
                        output.accept(CARGO_CONTAINER.get());
                        // Manufactorum (FASE 6)
                        output.accept(FORGE_FURNACE.get());
                        output.accept(SMELTER_CRUCIBLE.get());
                        output.accept(CONVEYOR_BELT.get());
                        output.accept(INDUSTRIAL_TURBINE.get());
                        output.accept(BOILER_TANK.get());
                        output.accept(SMOKE_STACK.get());
                        output.accept(COGITATOR_CONSOLE.get());
                        output.accept(CONTROL_PANEL.get());
                        output.accept(VENTILATION_DUCT.get());
                        output.accept(INDUSTRIAL_PRESS.get());
                        output.accept(COOLANT_TANK.get());
                        output.accept(IMPERIAL_PROPAGANDA_PANEL.get());
                        // Detailing pack (FASE 6.5)
                        output.accept(TOXIC_SLUDGE_BUCKET.get());
                        output.accept(SOLID_TOXIC_SLUDGE.get());
                        output.accept(HIVE_TABLE.get());
                        output.accept(HIVE_CHAIR.get());
                        output.accept(HIVE_BENCH.get());
                        output.accept(HIVE_RUG.get());
                        output.accept(SHELF_UNIT.get());
                        output.accept(SUPPLY_CRATE.get());
                        output.accept(INDUSTRIAL_FLOODLIGHT.get());
                        output.accept(HANGING_HIVE_LAMP.get());
                        output.accept(CATHEDRAL_BRAZIER.get());
                        output.accept(WARNING_BEACON.get());
                        output.accept(HUGE_HIVE_PIPE.get());
                        output.accept(MAIN_PIPE_TRUNK.get());
                        output.accept(INDUSTRIAL_CHAIN.get());
                        output.accept(CABLE_BUNDLE.get());
                        output.accept(WALL_TERMINAL.get());
                        output.accept(SECTOR_NUMBER_PANEL.get());
                        // Estátuas (FASE 6.5 Parte B)
                        output.accept(SAINT_BUST.get());
                        output.accept(AQUILA_STATUE.get());
                        output.accept(SAINT_STATUE.get());
                        output.accept(IMPERIAL_GUARDIAN_STATUE.get());
                        output.accept(AQUILA_BANNER.get());
                        // Underhive (FASE 9)
                        output.accept(RUBBLE.get());
                        output.accept(UNDERHIVE_CONCRETE.get());
                        output.accept(SCRAP_PILE.get());
                        output.accept(GLOW_FUNGUS.get());
                        output.accept(TOXIC_BARREL.get());
                        output.accept(CORRUGATED_WALL.get());
                        output.accept(GANG_FIRE.get());
                        output.accept(GANG_MARKING.get());
                        // New concept-sheet Hive City blocks
                        com.example.examplemod.hive.HiveCityConceptBlocks.addToCreativeTab(output);
                        // Structure markers (dev)
                        for (HiveMarkers.MarkerType type : HiveMarkers.MarkerType.values()) {
                            output.accept(MARKERS.get(type).get());
                        }
                    })
                    .build());

    // =========================================================================================
    // Property templates (each call returns a FRESH builder — never share instances)
    // =========================================================================================

    private static BlockBehaviour.Properties ashcrete() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(4.0F, 12.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS);
    }

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(5.0F, 10.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties pipe() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties cathedral() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.DEEPSLATE)
                .strength(3.5F, 9.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS);
    }

    private static BlockBehaviour.Properties lumen(int light) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F, 4.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.LANTERN)
                .lightLevel(state -> light);
    }

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
