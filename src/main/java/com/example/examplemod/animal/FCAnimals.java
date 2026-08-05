package com.example.examplemod.animal;

import java.util.List;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.flora.fruit.FCFruits;
import com.example.examplemod.planet.FCPlanets;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Phase E: the wildlife, its drops and the rules under which the world may create it.
 *
 * <p>Its own registry rather than another thousand lines in {@code FCRegistry}, for the same reason
 * {@code FCFlora} and {@code FCFruits} have theirs: an animal is a self-contained thing — entity
 * type, egg, what it drops, where it may stand — and keeping those four together is what makes
 * adding the next species a single file to read instead of five to hunt through.
 *
 * <h2>Where an animal may spawn, and the two ceilings on it</h2>
 *
 * Natural spawning has two separate paths in Minecraft, and only one of them can be capped from
 * here:
 *
 * <ul>
 *   <li><b>Chunk generation</b> — the herds that are already there when a chunk first exists. The
 *       ceiling is the biome's {@code creature_spawn_probability} and the group size in its
 *       spawners list, both datapack values (see {@code tools/generate_biomes.py}). The Java cap
 *       cannot help here: during generation the level is a {@code WorldGenRegion}, whose entity
 *       queries return an empty list by contract, so any count taken then would read zero. That is
 *       a fact about the engine, not an oversight — it is why the datapack numbers are the ones
 *       that were measured.</li>
 *   <li><b>Continuous spawning</b> — the slow trickle a running server adds. Here the level is a
 *       real {@code ServerLevel}, {@link FCAnimalEntity#tooCrowded} works, and the population limit
 *       from config is what stops a loaded pasture from filling up over a long session.</li>
 * </ul>
 *
 * <p>And on top of both: {@link FCPlanets#isCrusadeWorld}. The mod's biomes only exist on the four
 * planets, so in practice the check never fires — but a datapack that put {@code pale_steppe} into
 * somebody's vanilla overworld should not also put Grox in it.
 */
public final class FCAnimals {
    private FCAnimals() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    // ====================================================================================
    // Grox
    // ====================================================================================

    public static final RegistryObject<EntityType<GroxEntity>> GROX =
            ENTITY_TYPES.register("grox",
                    () -> EntityType.Builder.of(GroxEntity::new, MobCategory.CREATURE)
                            .sized(1.6F, 1.9F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":grox"));

    public static final RegistryObject<Item> GROX_SPAWN_EGG = ITEMS.register("grox_spawn_egg",
            () -> new ForgeSpawnEggItem(GROX, 0x6E5A44, 0x9A8A5A, new Item.Properties()));

    /**
     * Raw meat, and it is worth eating raw only if you are desperate — the fruit of Phase D is the
     * safe field ration, and this is the reason to build a fire.
     */
    public static final RegistryObject<Item> GROX_MEAT = ITEMS.register("grox_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(3).saturationMod(0.3F).meat().build())));

    /** The best food the mod has. A Grox feeds a squad, which is why the Imperium farms them. */
    public static final RegistryObject<Item> COOKED_GROX_MEAT = ITEMS.register("cooked_grox_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(9).saturationMod(0.9F).meat().build())));

    /** Thick enough to be armour material. A crafting item, deliberately not food. */
    public static final RegistryObject<Item> GROX_HIDE = ITEMS.register("grox_hide",
            () -> new Item(new Item.Properties()));

    /** The rare drop: one horn per adult, some of the time. */
    public static final RegistryObject<Item> GROX_HORN = ITEMS.register("grox_horn",
            () -> new Item(new Item.Properties()));

    // ====================================================================================
    // As outras cinco espécies
    // ====================================================================================

    /** Cão de guarda dos Arbites. Caça pele-verdes; não é domesticável (o mod não tem pets). */
    public static final RegistryObject<EntityType<CyberMastiffEntity>> CYBER_MASTIFF =
            ENTITY_TYPES.register("cyber_mastiff",
                    () -> EntityType.Builder.of(CyberMastiffEntity::new, MobCategory.CREATURE)
                            .sized(0.9F, 1.1F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":cyber_mastiff"));

    public static final RegistryObject<Item> CYBER_MASTIFF_SPAWN_EGG =
            ITEMS.register("cyber_mastiff_spawn_egg",
                    () -> new ForgeSpawnEggItem(CYBER_MASTIFF, 0x484442, 0xDC3C28,
                            new Item.Properties()));

    /** Bola de dentes dos Orks. Hostil — e mesmo assim fauna, porque fauna é quem tem teto. */
    public static final RegistryObject<EntityType<SquigEntity>> SQUIG =
            ENTITY_TYPES.register("squig",
                    () -> EntityType.Builder.of(SquigEntity::new, MobCategory.CREATURE)
                            .sized(0.8F, 1.0F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":squig"));

    public static final RegistryObject<Item> SQUIG_SPAWN_EGG = ITEMS.register("squig_spawn_egg",
            () -> new ForgeSpawnEggItem(SQUIG, 0x962C2E, 0xECE8D6, new Item.Properties()));

    /** O que vive na água que ninguém bebe. Sem ataque, só distância. */
    public static final RegistryObject<EntityType<SumpRatEntity>> SUMP_RAT =
            ENTITY_TYPES.register("sump_rat",
                    () -> EntityType.Builder.of(SumpRatEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 0.5F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":sump_rat"));

    public static final RegistryObject<Item> SUMP_RAT_SPAWN_EGG =
            ITEMS.register("sump_rat_spawn_egg",
                    () -> new ForgeSpawnEggItem(SUMP_RAT, 0x544C3E, 0x968C78,
                            new Item.Properties()));

    /** Pernalta dos ermos: a única silhueta do mod que se lê a duzentos metros. */
    public static final RegistryObject<EntityType<AshStriderEntity>> ASH_STRIDER =
            ENTITY_TYPES.register("ash_strider",
                    () -> EntityType.Builder.of(AshStriderEntity::new, MobCategory.CREATURE)
                            .sized(0.9F, 2.1F)
                            .clientTrackingRange(12)
                            .build(ExampleMod.MODID + ":ash_strider"));

    public static final RegistryObject<Item> ASH_STRIDER_SPAWN_EGG =
            ITEMS.register("ash_strider_spawn_egg",
                    () -> new ForgeSpawnEggItem(ASH_STRIDER, 0x7E7870, 0xB2ACA0,
                            new Item.Properties()));

    /** Escavador raro. O peso de spawn mais baixo do mod, e é isso que o faz valer a pena. */
    public static final RegistryObject<EntityType<AmbullEntity>> AMBULL =
            ENTITY_TYPES.register("ambull",
                    () -> EntityType.Builder.of(AmbullEntity::new, MobCategory.CREATURE)
                            .sized(1.4F, 2.4F)
                            .clientTrackingRange(12)
                            .build(ExampleMod.MODID + ":ambull"));

    public static final RegistryObject<Item> AMBULL_SPAWN_EGG = ITEMS.register("ambull_spawn_egg",
            () -> new ForgeSpawnEggItem(AMBULL, 0x605239, 0xF07828, new Item.Properties()));

    // ====================================================================================
    // Drops partilhados
    // ====================================================================================

    /**
     * Carne de bicho pequeno — rato, squig, o que estiver morto.
     *
     * <p>Um item para as duas espécies em vez de um por bicho: mecanicamente seriam idênticos, e
     * um inventário com quatro carnes ruins diferentes não é profundidade, é ruído. O nome diz o
     * que ela é ("catada"), e a fome que ela causa crua diz o resto.
     */
    public static final RegistryObject<Item> SCAVENGED_MEAT = ITEMS.register("scavenged_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2).saturationMod(0.2F).meat()
                    .effect(() -> new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.HUNGER, 120, 0), 0.6F)
                    .build())));

    public static final RegistryObject<Item> COOKED_SCAVENGED_MEAT =
            ITEMS.register("cooked_scavenged_meat",
                    () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(5).saturationMod(0.6F).meat().build())));

    /** Placa de quitina: o material que sai do que tem carapaça em vez de couro. */
    public static final RegistryObject<Item> CHITIN_PLATE = ITEMS.register("chitin_plate",
            () -> new Item(new Item.Properties()));

    /** The Phase D pod, addressed from here so the entity does not reach across packages twice. */
    public static Item groxFeed() {
        return FCFruits.GROX_FEED_POD.get();
    }

    // ====================================================================================
    // Wiring
    // ====================================================================================

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(FCAnimals::registerAttributes);
        modEventBus.addListener(FCAnimals::registerSpawnPlacements);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(GROX.get(), GroxEntity.createAttributes().build());
        event.put(CYBER_MASTIFF.get(), CyberMastiffEntity.createAttributes().build());
        event.put(SQUIG.get(), SquigEntity.createAttributes().build());
        event.put(SUMP_RAT.get(), SumpRatEntity.createAttributes().build());
        event.put(ASH_STRIDER.get(), AshStriderEntity.createAttributes().build());
        event.put(AMBULL.get(), AmbullEntity.createAttributes().build());
    }

    /**
     * Without this the biome's spawner entry is inert: the game has no idea what surface the animal
     * needs, and every natural spawn attempt fails silently.
     */
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        for (RegistryObject<? extends EntityType<? extends FCAnimalEntity>> type : SPECIES) {
            event.register(type.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FCAnimals::checkWildlifeSpawn,
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }

    /**
     * Toda a fauna, para os laços que precisam falar com "as espécies" em vez de com uma delas.
     *
     * <p>Esquecer uma espécie aqui não dá erro nenhum: ela só nunca aparece no mundo, exatamente
     * como o {@code rocky_highland} nunca via um Grox. Uma lista num lugar só é o que impede isso.
     */
    private static final List<RegistryObject<? extends EntityType<? extends FCAnimalEntity>>> SPECIES =
            List.of(GROX, CYBER_MASTIFF, SQUIG, SUMP_RAT, ASH_STRIDER, AMBULL);

    /**
     * The mod's own spawn test, wrapping {@link FCAnimalEntity#checkWildlifeSpawn} with the planet
     * check.
     *
     * <p>Note that this reaches the dimension through {@code getLevel()} rather than by testing the
     * accessor itself: during chunk generation the accessor is a {@code WorldGenRegion}, which is
     * not a {@code Level} at all, and asking it the wrong way would answer "not a Crusade world" for
     * every animal the worldgen ever tried to place.
     */
    private static <T extends FCAnimalEntity> boolean checkWildlifeSpawn(
            EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
            RandomSource random) {
        if (!FCPlanets.isCrusadeWorld(level.getLevel().dimension())) {
            return false;
        }

        return FCAnimalEntity.checkWildlifeSpawn(type, level, reason, pos, random);
    }

    /** Appended to the main First Crusade tab from {@code FCRegistry}. */
    public static void addToCreativeTab(CreativeModeTab.Output output) {
        output.accept(GROX_SPAWN_EGG.get());
        output.accept(CYBER_MASTIFF_SPAWN_EGG.get());
        output.accept(SQUIG_SPAWN_EGG.get());
        output.accept(SUMP_RAT_SPAWN_EGG.get());
        output.accept(ASH_STRIDER_SPAWN_EGG.get());
        output.accept(AMBULL_SPAWN_EGG.get());

        output.accept(GROX_MEAT.get());
        output.accept(COOKED_GROX_MEAT.get());
        output.accept(GROX_HIDE.get());
        output.accept(GROX_HORN.get());
        output.accept(SCAVENGED_MEAT.get());
        output.accept(COOKED_SCAVENGED_MEAT.get());
        output.accept(CHITIN_PLATE.get());
    }
}
