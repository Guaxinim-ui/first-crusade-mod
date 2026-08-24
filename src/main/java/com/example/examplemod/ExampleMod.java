package com.example.examplemod;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "firstcrusade";

    private static final Logger LOGGER = LogUtils.getLogger();

    // Networking lives in FirstCrusadeNetwork (channel + packet registration).


    public ExampleMod(FMLJavaModLoadingContext context) {
    IEventBus modEventBus = context.getModEventBus();

    modEventBus.addListener(this::commonSetup);

    // All registrations (blocks, items, entities, block entities, menus, creative tab, tiers,
    // armour) and entity attributes live in FCRegistry.
    FCRegistry.register(modEventBus);
    FCWeaponSounds.register(modEventBus);

    // Sons do terminal de navegacao planetaria (ids proprios; o audio vem do vanilla
    // via sounds.json ate haver gravacao propria).
    com.example.examplemod.planet.PlanetSounds.register(modEventBus);

    // Hive fluids + structure markers registry (com.example.examplemod.hive). The old decorative
    // block set was removed; the new decorative kit registers itself via the three classes below.
    com.example.examplemod.hive.HiveBlocks.register(modEventBus);

    // New Hive decorative kit (133 blocks across three self-contained registries + creative tabs).
    com.example.examplemod.hive.HiveEssentialBlocks.register(modEventBus);
    com.example.examplemod.hive.HiveConcreteBlocks.register(modEventBus);
    com.example.examplemod.hive.HiveConstructionBlocks.register(modEventBus);

    // Imperial Battle Tank vehicle pack — self-contained registry in .registry.
    com.example.examplemod.registry.ModVehicleEntities.register(modEventBus);
    com.example.examplemod.necron.FCNecrons.register(modEventBus);

    // Valkyrie Gunship + Sentinel Walker combat-vehicle mobs — self-contained registry in .registry.
    com.example.examplemod.registry.ModCombatVehicleContent.register(modEventBus);

    // Vegetation set (phase 2: base flora) — self-contained registry in .flora, no existing IDs moved.
    // The runtime, per-chunk decorator that actually places this flora lives in .flora.runtime and
    // needs no registration here: it is driven by Forge events (see FloraEvents).
    com.example.examplemod.flora.FCFlora.register(modEventBus);

    // Trees: log + canopy per species, placed both by worldgen features and by the decorator.
    com.example.examplemod.flora.tree.FCFloraTrees.register(modEventBus);

    // The two natural soils (sump mud, salt crust). Placed by the overworld surface rule, so they
    // exist the moment a chunk is carved rather than being painted on afterwards.
    com.example.examplemod.flora.FCFloraGround.register(modEventBus);

    // Phase D: fruit trees, their fruit, and the nodes it hangs from.
    com.example.examplemod.flora.fruit.FCFruits.register(modEventBus);

    // Fase E: a fauna — entity types, ovos, drops, atributos e as regras de onde ela pode nascer.
    // Registrado depois de FCFruits porque o Grox se alimenta da vagem da Fase D.
    com.example.examplemod.animal.FCAnimals.register(modEventBus);

    // A fauna dos modelos do Blockbench: as nove especies novas, os drops delas e os trofeus.
    // Registro proprio ao lado de FCAnimals, e nao dentro dele — ver o javadoc da classe para o
    // porque as seis da Fase E nao foram movidas.
    com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.register(modEventBus);

    // As estruturas da fauna: uma Feature so, treze sitios em datapack. Roda na geracao do chunk e
    // nunca mais — e por isso que nao existe manager nenhum verificando se ja gerou.
    com.example.examplemod.fauna.world.FaunaStructureRegistry.register(modEventBus);

    net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.SERVER, FirstCrusadeServerConfig.SPEC);

    net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.COMMON,
            com.example.examplemod.flora.FloraConfig.SPEC, "firstcrusade-flora-common.toml");

    // Fase E: fauna. Spec proprio — mexer nos animais nao e mexer no capim.
    net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.COMMON,
            com.example.examplemod.animal.FCAnimalConfig.SPEC, "firstcrusade-animals-common.toml");

    // Performance. SERVER porque tudo aqui muda simulacao — alcance de aquisicao, frequencia de
    // varredura, nivel de IA por distancia. Cliente nenhum pode decidir isso por si.
    net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.SERVER,
            com.example.examplemod.performance.config.FirstCrusadePerformanceConfig.SPEC,
            "firstcrusade-performance-server.toml");

    // Graficos. CLIENT porque e so aparencia — densidade de particula, tracer, distancia visual.
    // Preset padrao GRIMDARK. Este spec nunca e lido pela simulacao: dois clientes com presets
    // diferentes no mesmo servidor tomam exatamente o mesmo dano do mesmo tiro.
    net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
            com.example.examplemod.performance.config.FirstCrusadeClientConfig.SPEC,
            "firstcrusade-graphics-client.toml");

    // Forge game events live in FirstCrusadeForgeEvents (auto-registered via @EventBusSubscriber);
    // client wiring in FirstCrusadeClientEvents; networking in FirstCrusadeNetwork.
}

private void commonSetup(final FMLCommonSetupEvent event) {
    event.enqueueWork(FirstCrusadeNetwork::register);

    LOGGER.info("First Crusade loaded successfully.");
}


// Small, closed "planet": clamp the overworld to a compact playable area on every start. The
// world border only limits movement (no worldgen/chunk changes), so this is fully reversible.
// TEST MODE (temporary, owner request 2026-06-22): a clean sandbox to watch the two peoples
// develop. When true: no vanilla mobs spawn, the world is seeded with a fixed layout (5 Ork cities
// north, 5 Imperial cities south), cities don't seed their own extra camps/raids, and both factions
// are capped at 50 warriors. Flip to false to return to normal world behaviour.
// These are server config settings (see FirstCrusadeServerConfig), mirrored into these fields on
// config (re)load so the many call sites keep reading ExampleMod.* on hot paths. The values here are
// only the pre-config defaults.
public static boolean TEST_FIXED_WORLD = true;
public static int TEST_WARRIOR_CAP = 50;
public static boolean ORK_WAVES_ENABLED = false;
public static double WORLD_BORDER_SIZE = 5000.0D;
public static boolean SEAL_NETHER_AND_END = true;
public static boolean SEED_STARTING_SETTLEMENTS = true;
public static boolean PLANET_HAZARDS_ENABLED = true;

}