package com.example.examplemod;

import com.example.examplemod.unit.imperium.GuardsmanRiflemanEntity;
import com.example.examplemod.unit.imperium.GuardsmanSergeantEntity;
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

/**
 * All First Crusade registrations, extracted from {@link ExampleMod}: the DeferredRegisters and
 * every block, item, entity, block-entity, menu, spawn egg, tier, armour material and the creative
 * tab, plus entity attribute creation. {@link #register(IEventBus)} wires it to the mod event bus.
 */
public final class FCRegistry {
    private FCRegistry() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MODID);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ExampleMod.MODID);
            public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, ExampleMod.MODID);

    // =========================
    // BLOCKS
    // =========================

    public static final RegistryObject<Block> IMPERIAL_COMMAND_CORE = BLOCKS.register("imperial_command_core",
            () -> new ImperialCommandCoreBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 10.0F)));

                    public static final RegistryObject<Block> IMPERIAL_MINE =
        BLOCKS.register("imperial_mine",
                () -> new ImperialMineBlock(BlockBehaviour.Properties.of()
                        .strength(3.5F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)));
public static final RegistryObject<Block> IMPERIAL_GOLD_MINE =
        BLOCKS.register("imperial_gold_mine",
                () -> new ImperialGoldMineBlock(BlockBehaviour.Properties.of()
                        .strength(3.5F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)));

public static final RegistryObject<Item> IMPERIAL_GOLD_MINE_ITEM =
        ITEMS.register("imperial_gold_mine",
                () -> new BlockItem(IMPERIAL_GOLD_MINE.get(), new Item.Properties()));

public static final RegistryObject<Block> IMPERIAL_EMERALD_TRADE_DEPOT =
        BLOCKS.register("imperial_emerald_trade_depot",
                () -> new ImperialEmeraldTradeDepotBlock(BlockBehaviour.Properties.of()
                        .strength(3.5F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.WOOD)));

public static final RegistryObject<Item> IMPERIAL_EMERALD_TRADE_DEPOT_ITEM =
        ITEMS.register("imperial_emerald_trade_depot",
                () -> new BlockItem(IMPERIAL_EMERALD_TRADE_DEPOT.get(), new Item.Properties()));

public static final RegistryObject<Block> IMPERIAL_FARM =
        BLOCKS.register("imperial_farm",
                () -> new ImperialFarmBlock(BlockBehaviour.Properties.of()
                        .strength(2.5F)
                        .sound(SoundType.GRASS)));

public static final RegistryObject<Item> IMPERIAL_FARM_ITEM =
        ITEMS.register("imperial_farm",
                () -> new BlockItem(IMPERIAL_FARM.get(), new Item.Properties()));

public static final RegistryObject<Block> IMPERIAL_SCRAP_YARD =
        BLOCKS.register("imperial_scrap_yard",
                () -> new ImperialScrapYardBlock(BlockBehaviour.Properties.of()
                        .strength(2.5F)
                        .sound(SoundType.METAL)));

public static final RegistryObject<Item> IMPERIAL_SCRAP_YARD_ITEM =
        ITEMS.register("imperial_scrap_yard",
                () -> new BlockItem(IMPERIAL_SCRAP_YARD.get(), new Item.Properties()));

public static final RegistryObject<Block> IMPERIAL_FORGE =
        BLOCKS.register("imperial_forge",
                () -> new ImperialForgeBlock(BlockBehaviour.Properties.of()
                        .strength(4.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL)));

public static final RegistryObject<Item> IMPERIAL_FORGE_ITEM =
        ITEMS.register("imperial_forge",
                () -> new BlockItem(IMPERIAL_FORGE.get(), new Item.Properties()));

public static final RegistryObject<Block> IMPERIAL_PROMETHIUM_REFINERY =
        BLOCKS.register("imperial_promethium_refinery",
                () -> new ImperialPromethiumRefineryBlock(BlockBehaviour.Properties.of()
                        .strength(3.5F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL)));

public static final RegistryObject<Item> IMPERIAL_PROMETHIUM_REFINERY_ITEM =
        ITEMS.register("imperial_promethium_refinery",
                () -> new BlockItem(IMPERIAL_PROMETHIUM_REFINERY.get(), new Item.Properties()));

public static final RegistryObject<Block> IMPERIAL_BARRACKS =
        BLOCKS.register("imperial_barracks",
                () -> new ImperialBarracksBlock(BlockBehaviour.Properties.of()
                        .strength(4.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.STONE)));

public static final RegistryObject<Item> IMPERIAL_BARRACKS_ITEM =
        ITEMS.register("imperial_barracks",
                () -> new BlockItem(IMPERIAL_BARRACKS.get(), new Item.Properties()));

public static final RegistryObject<Item> IMPERIAL_MINE_ITEM =
        ITEMS.register("imperial_mine",
                () -> new BlockItem(IMPERIAL_MINE.get(), new Item.Properties()));

// Planetary travel (Fase E / C6): a second planet as its own dimension, reached via the Spaceport.
// PLANET_SECUNDUS foi removido: era um planeta stub com bioma do Nether, substituido pelos
// quatro planetas de verdade em com.example.examplemod.planet.FCPlanets.

public static final RegistryObject<Block> SPACEPORT =
        BLOCKS.register("spaceport",
                () -> new SpaceportBlock(BlockBehaviour.Properties.of()
                        .strength(5.0F, 1200.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL)
                        .lightLevel(state -> 7)));

public static final RegistryObject<Item> SPACEPORT_ITEM =
        ITEMS.register("spaceport",
                () -> new BlockItem(SPACEPORT.get(), new Item.Properties()));

// Planetary Navigation Terminal — o console que abre a Imperial Planetary Navigation. Fica ao
// lado da plataforma; o Spaceport continua com o lançamento de um clique que ele sempre teve.
public static final RegistryObject<Block> PLANETARY_NAVIGATION_TERMINAL =
        BLOCKS.register("planetary_navigation_terminal",
                () -> new com.example.examplemod.planet.PlanetaryNavigationTerminalBlock(
                        BlockBehaviour.Properties.of()
                                .strength(4.0F, 900.0F)
                                .requiresCorrectToolForDrops()
                                .sound(SoundType.METAL)
                                .noOcclusion()
                                .lightLevel(state -> 9)));

public static final RegistryObject<Item> PLANETARY_NAVIGATION_TERMINAL_ITEM =
        ITEMS.register("planetary_navigation_terminal",
                () -> new BlockItem(PLANETARY_NAVIGATION_TERMINAL.get(), new Item.Properties()));

public static final RegistryObject<MenuType<com.example.examplemod.planet.PlanetNavigationMenu>>
        PLANET_NAVIGATION_MENU = MENU_TYPES.register("planet_navigation_menu",
                () -> IForgeMenuType.create((windowId, inventory, data) ->
                        new com.example.examplemod.planet.PlanetNavigationMenu(windowId, inventory, data)));

    public static final RegistryObject<Item> IMPERIAL_COMMAND_CORE_ITEM = ITEMS.register("imperial_command_core",
            () -> new BlockItem(IMPERIAL_COMMAND_CORE.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<ImperialCommandCoreBlockEntity>> IMPERIAL_COMMAND_CORE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("imperial_command_core",
                    () -> BlockEntityType.Builder.of(ImperialCommandCoreBlockEntity::new, IMPERIAL_COMMAND_CORE.get()).build(null));
                    public static final RegistryObject<MenuType<ImperialCommandCoreMenu>> IMPERIAL_COMMAND_CORE_MENU =
        MENU_TYPES.register("imperial_command_core_menu",
                () -> IForgeMenuType.create((windowId, inventory, data) -> new ImperialCommandCoreMenu(windowId, inventory, data)));

                public static final RegistryObject<BlockEntityType<ImperialMineBlockEntity>> IMPERIAL_MINE_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_mine",
                () -> BlockEntityType.Builder.of(ImperialMineBlockEntity::new, IMPERIAL_MINE.get()).build(null));
        
        public static final RegistryObject<BlockEntityType<ImperialEmeraldTradeDepotBlockEntity>> IMPERIAL_EMERALD_TRADE_DEPOT_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_emerald_trade_depot",
                () -> BlockEntityType.Builder.of(ImperialEmeraldTradeDepotBlockEntity::new, IMPERIAL_EMERALD_TRADE_DEPOT.get()).build(null));

        public static final RegistryObject<BlockEntityType<ImperialFarmBlockEntity>> IMPERIAL_FARM_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_farm",
                () -> BlockEntityType.Builder.of(ImperialFarmBlockEntity::new, IMPERIAL_FARM.get()).build(null));

        public static final RegistryObject<BlockEntityType<ImperialGoldMineBlockEntity>> IMPERIAL_GOLD_MINE_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_gold_mine",
                () -> BlockEntityType.Builder.of(ImperialGoldMineBlockEntity::new, IMPERIAL_GOLD_MINE.get()).build(null));

        public static final RegistryObject<BlockEntityType<ImperialScrapYardBlockEntity>> IMPERIAL_SCRAP_YARD_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_scrap_yard",
                () -> BlockEntityType.Builder.of(ImperialScrapYardBlockEntity::new, IMPERIAL_SCRAP_YARD.get()).build(null));

        public static final RegistryObject<BlockEntityType<ImperialForgeBlockEntity>> IMPERIAL_FORGE_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_forge",
                () -> BlockEntityType.Builder.of(ImperialForgeBlockEntity::new, IMPERIAL_FORGE.get()).build(null));

        public static final RegistryObject<BlockEntityType<ImperialPromethiumRefineryBlockEntity>> IMPERIAL_PROMETHIUM_REFINERY_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_promethium_refinery",
                () -> BlockEntityType.Builder.of(ImperialPromethiumRefineryBlockEntity::new, IMPERIAL_PROMETHIUM_REFINERY.get()).build(null));

        public static final RegistryObject<BlockEntityType<ImperialBarracksBlockEntity>> IMPERIAL_BARRACKS_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_barracks",
                () -> BlockEntityType.Builder.of(ImperialBarracksBlockEntity::new, IMPERIAL_BARRACKS.get()).build(null));

public static final RegistryObject<Block> IMPERIAL_HABITATION =
        BLOCKS.register("imperial_habitation",
                () -> new ImperialHabitationBlock(BlockBehaviour.Properties.of()
                        .strength(3.0F)
                        .sound(SoundType.STONE)));

public static final RegistryObject<Item> IMPERIAL_HABITATION_ITEM =
        ITEMS.register("imperial_habitation",
                () -> new BlockItem(IMPERIAL_HABITATION.get(), new Item.Properties()));

public static final RegistryObject<BlockEntityType<ImperialHabitationBlockEntity>> IMPERIAL_HABITATION_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("imperial_habitation",
                () -> BlockEntityType.Builder.of(ImperialHabitationBlockEntity::new, IMPERIAL_HABITATION.get()).build(null));

public static final RegistryObject<Block> ORK_CAMP =
        BLOCKS.register("ork_camp",
                () -> new OrkCampBlock(BlockBehaviour.Properties.of()
                        .strength(4.0F, 8.0F)
                        .sound(SoundType.NETHER_BRICKS)
                        .lightLevel(state -> 8)
                        .noOcclusion()));

public static final RegistryObject<Item> ORK_CAMP_ITEM =
        ITEMS.register("ork_camp",
                () -> new BlockItem(ORK_CAMP.get(), new Item.Properties()));

public static final RegistryObject<BlockEntityType<OrkCampBlockEntity>> ORK_CAMP_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("ork_camp",
                () -> BlockEntityType.Builder.of(OrkCampBlockEntity::new, ORK_CAMP.get()).build(null));

// Ork Loot Pit — the first buildable Ork city structure (raised from the Ork Core panel). Grots dig
// it for loot, boosting the city's economy (see OrkCampBlockEntity.produceLoot).
public static final RegistryObject<Block> ORK_LOOT_PIT =
        BLOCKS.register("ork_loot_pit",
                () -> new Block(BlockBehaviour.Properties.of()
                        .strength(2.0F, 4.0F)
                        .sound(SoundType.GRAVEL)));

public static final RegistryObject<Item> ORK_LOOT_PIT_ITEM =
        ITEMS.register("ork_loot_pit",
                () -> new BlockItem(ORK_LOOT_PIT.get(), new Item.Properties()));

public static final RegistryObject<MenuType<OrkCampMenu>> ORK_CAMP_MENU =
        MENU_TYPES.register("ork_camp_menu",
                () -> IForgeMenuType.create((windowId, inventory, data) -> new OrkCampMenu(windowId, inventory, data)));

// Strategium — the "Mesa de Estratégia" research bench (paid Age-up research).
public static final RegistryObject<Block> STRATEGIUM =
        BLOCKS.register("strategium",
                () -> new StrategiumBlock(BlockBehaviour.Properties.of()
                        .strength(3.5F)
                        .sound(SoundType.WOOD)));

public static final RegistryObject<Item> STRATEGIUM_ITEM =
        ITEMS.register("strategium",
                () -> new BlockItem(STRATEGIUM.get(), new Item.Properties()));

public static final RegistryObject<BlockEntityType<StrategiumBlockEntity>> STRATEGIUM_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("strategium",
                () -> BlockEntityType.Builder.of(StrategiumBlockEntity::new, STRATEGIUM.get()).build(null));

public static final RegistryObject<MenuType<StrategiumMenu>> STRATEGIUM_MENU =
        MENU_TYPES.register("strategium_menu",
                () -> IForgeMenuType.create((windowId, inventory, data) -> new StrategiumMenu(windowId, inventory, data)));

    // =========================
    // MATERIALS
    // =========================

    public static final RegistryObject<Item> CRUSADIUM_INGOT = ITEMS.register("crusadium_ingot",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> CRUSADIUM_PLATE = ITEMS.register("crusadium_plate",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> ORK_TEETH = ITEMS.register("ork_teeth",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SCRAP_METAL = ITEMS.register("scrap_metal",
            () -> new Item(new Item.Properties()));

    // =========================
    // GUARDSMEN WEAPONS
    // =========================

    public static final RegistryObject<Item> LASGUN_POWER_CELL = ITEMS.register("lasgun_power_cell",
            () -> new Item(new Item.Properties().stacksTo(1).durability(30)));

    public static final RegistryObject<Item> LASGUN = ITEMS.register("lasgun",
            () -> new LasgunItem(new Item.Properties().stacksTo(1).durability(500)));

    public static final RegistryObject<Item> BOLTER = ITEMS.register("bolter",
            () -> new BolterItem(new Item.Properties().stacksTo(1).durability(768)));

    public static final RegistryObject<Item> PLASMA_GUN = ITEMS.register("plasma_gun",
            () -> new PlasmaGunItem(new Item.Properties().stacksTo(1).durability(400)));

    public static final Tier GUARDSMAN_MELEE_TIER = new Tier() {
        @Override
        public int getUses() {
            return 250;
        }

        @Override
        public float getSpeed() {
            return 6.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 2.0F;
        }

        @Override
        public int getLevel() {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 10;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(CRUSADIUM_PLATE.get());
        }
    };

    public static final RegistryObject<Item> GUARDSMAN_COMBAT_KNIFE = ITEMS.register("guardsman_combat_knife",
            () -> new SwordItem(GUARDSMAN_MELEE_TIER, 2, -1.8F, new Item.Properties()));

    // Astartes close-combat weapon: heavier and far stronger than the combat knife.
    public static final Tier CHAINSWORD_TIER = new Tier() {
        @Override
        public int getUses() {
            return 700;
        }

        @Override
        public float getSpeed() {
            return 7.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 5.0F;
        }

        @Override
        public int getLevel() {
            return 3;
        }

        @Override
        public int getEnchantmentValue() {
            return 14;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(CRUSADIUM_PLATE.get());
        }
    };

    public static final RegistryObject<Item> CHAINSWORD = ITEMS.register("chainsword",
            () -> new ChainswordItem(CHAINSWORD_TIER, 4, -2.0F, new Item.Properties()));

    // Auramite halberd of the Adeptus Custodes: heavy blade + built-in bolter (see GuardianSpearItem).
    public static final Tier GUARDIAN_SPEAR_TIER = new Tier() {
        @Override
        public int getUses() {
            return 1200;
        }

        @Override
        public float getSpeed() {
            return 8.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 6.0F;
        }

        @Override
        public int getLevel() {
            return 4;
        }

        @Override
        public int getEnchantmentValue() {
            return 16;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(CRUSADIUM_PLATE.get());
        }
    };

    public static final RegistryObject<Item> GUARDIAN_SPEAR = ITEMS.register("guardian_spear",
            () -> new GuardianSpearItem(GUARDIAN_SPEAR_TIER, 5, -2.6F, new Item.Properties()));

    // Power sword of the Primarch: a master-crafted blade wreathed in a disruption field.
    public static final Tier POWER_SWORD_TIER = new Tier() {
        @Override
        public int getUses() {
            return 1500;
        }

        @Override
        public float getSpeed() {
            return 9.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 7.0F;
        }

        @Override
        public int getLevel() {
            return 4;
        }

        @Override
        public int getEnchantmentValue() {
            return 18;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(CRUSADIUM_PLATE.get());
        }
    };

    public static final RegistryObject<Item> POWER_SWORD = ITEMS.register("power_sword",
            () -> new SwordItem(POWER_SWORD_TIER, 5, -2.4F, new Item.Properties()));

    // The Ferramenta de Construção: taken from a Command Core, places city structures with a ghost
    // preview (see CityBuilderToolItem). Single-stack, no durability — it's a controller, not a weapon.
    public static final RegistryObject<Item> CITY_BUILDER_TOOL = ITEMS.register("city_builder_tool",
            () -> new CityBuilderToolItem(new Item.Properties().stacksTo(1)));

    // Crude Ork melee weapon: brutal but poorly made (low durability), repaired with Ork Teeth.
    public static final Tier ORK_MELEE_TIER = new Tier() {
        @Override
        public int getUses() {
            return 180;
        }

        @Override
        public float getSpeed() {
            return 5.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 2.0F;
        }

        @Override
        public int getLevel() {
            return 1;
        }

        @Override
        public int getEnchantmentValue() {
            return 5;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(ORK_TEETH.get());
        }
    };

    public static final RegistryObject<Item> CHOPPA = ITEMS.register("choppa",
            () -> new ChoppaItem(ORK_MELEE_TIER, 0, -2.4F, new Item.Properties()));

    public static final RegistryObject<Item> SHOOTA = ITEMS.register("shoota",
            () -> new ShootaItem(new Item.Properties().stacksTo(1).durability(220)));

    // Elite Ork close-combat weapon (Warboss/Meganob): a massive powered claw — slow but devastating.
    public static final Tier POWER_KLAW_TIER = new Tier() {
        @Override
        public int getUses() {
            return 900;
        }

        @Override
        public float getSpeed() {
            return 6.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 5.0F;
        }

        @Override
        public int getLevel() {
            return 4;
        }

        @Override
        public int getEnchantmentValue() {
            return 8;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(ORK_TEETH.get());
        }
    };

    public static final RegistryObject<Item> POWER_KLAW = ITEMS.register("power_klaw",
            () -> new PowerKlawItem(POWER_KLAW_TIER, 2, -2.8F, new Item.Properties()));

    // =========================
    // GUARDSMEN SUPPORT ITEMS
    // =========================

    public static final RegistryObject<Item> GUARDSMAN_MED_KIT = ITEMS.register("guardsman_med_kit",
            () -> new MedKitItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> GUARDSMAN_COMMAND_BATON = ITEMS.register("guardsman_command_baton",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // =========================
    // PROJECTILES
    // =========================

    public static final RegistryObject<EntityType<LasgunShotEntity>> LASGUN_SHOT =
            ENTITY_TYPES.register("lasgun_shot",
                    () -> EntityType.Builder.<LasgunShotEntity>of(LasgunShotEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(ExampleMod.MODID + ":lasgun_shot"));

    // =========================
    // GUARDSMEN ENTITIES
    // =========================

    public static final RegistryObject<EntityType<GuardsmanEntity>> GUARDSMAN =
            ENTITY_TYPES.register("guardsman",
                    () -> EntityType.Builder.of(GuardsmanEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":guardsman"));

                            public static final RegistryObject<EntityType<ImperialCitizenEntity>> IMPERIAL_CITIZEN =
        ENTITY_TYPES.register("imperial_citizen",
                () -> EntityType.Builder.of(ImperialCitizenEntity::new, MobCategory.CREATURE)
                        .sized(0.6F, 1.95F)
                        .build("imperial_citizen"));

    public static final RegistryObject<Item> GUARDSMAN_SPAWN_EGG = ITEMS.register("guardsman_spawn_egg",
            () -> new ForgeSpawnEggItem(GUARDSMAN, 0x3A3A3A, 0xB0A060, new Item.Properties()));

    // --- Fase C: squad-capable Guardsman variants (GeckoLib). See unit/imperium/. ---
    public static final RegistryObject<EntityType<GuardsmanRiflemanEntity>> GUARDSMAN_RIFLEMAN =
            ENTITY_TYPES.register("guardsman_rifleman",
                    () -> EntityType.Builder.of(GuardsmanRiflemanEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":guardsman_rifleman"));

    public static final RegistryObject<Item> GUARDSMAN_RIFLEMAN_SPAWN_EGG =
            ITEMS.register("guardsman_rifleman_spawn_egg",
                    () -> new ForgeSpawnEggItem(GUARDSMAN_RIFLEMAN, 0x3A3A3A, 0x8FA050, new Item.Properties()));

    public static final RegistryObject<EntityType<GuardsmanSergeantEntity>> GUARDSMAN_SERGEANT =
            ENTITY_TYPES.register("guardsman_sergeant",
                    () -> EntityType.Builder.of(GuardsmanSergeantEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":guardsman_sergeant"));

    public static final RegistryObject<Item> GUARDSMAN_SERGEANT_SPAWN_EGG =
            ITEMS.register("guardsman_sergeant_spawn_egg",
                    () -> new ForgeSpawnEggItem(GUARDSMAN_SERGEANT, 0x2A2A2A, 0xC0A030, new Item.Properties()));

            public static final RegistryObject<EntityType<SpaceMarineEntity>> SPACE_MARINE =
        ENTITY_TYPES.register("space_marine",
                () -> EntityType.Builder.of(SpaceMarineEntity::new, MobCategory.CREATURE)
                        .sized(0.85F, 2.35F)
                        .clientTrackingRange(10)
                        .build(ExampleMod.MODID + ":space_marine"));


                        public static final RegistryObject<Item> IMPERIAL_CITIZEN_SPAWN_EGG =
        ITEMS.register("imperial_citizen_spawn_egg",
                () -> new ForgeSpawnEggItem(IMPERIAL_CITIZEN, 0x7A6A4A, 0xD6B85A, new Item.Properties()));

public static final RegistryObject<Item> SPACE_MARINE_SPAWN_EGG = ITEMS.register("space_marine_spawn_egg",
        () -> new ForgeSpawnEggItem(SPACE_MARINE, 0x1C3F8F, 0xD8C06A, new Item.Properties()));

public static final RegistryObject<EntityType<CustodesEntity>> CUSTODES =
        ENTITY_TYPES.register("custodes",
                () -> EntityType.Builder.of(CustodesEntity::new, MobCategory.CREATURE)
                        .sized(0.9F, 2.5F)
                        .clientTrackingRange(12)
                        .build(ExampleMod.MODID + ":custodes"));

public static final RegistryObject<Item> CUSTODES_SPAWN_EGG = ITEMS.register("custodes_spawn_egg",
        () -> new ForgeSpawnEggItem(CUSTODES, 0xC9A227, 0x5A0A0A, new Item.Properties()));

public static final RegistryObject<EntityType<PrimarchEntity>> PRIMARCH =
        ENTITY_TYPES.register("primarch",
                () -> EntityType.Builder.of(PrimarchEntity::new, MobCategory.CREATURE)
                        .sized(1.4F, 3.6F)
                        .clientTrackingRange(16)
                        .build(ExampleMod.MODID + ":primarch"));

public static final RegistryObject<Item> PRIMARCH_SPAWN_EGG = ITEMS.register("primarch_spawn_egg",
        () -> new ForgeSpawnEggItem(PRIMARCH, 0xD4AF37, 0x2B1B5A, new Item.Properties()));

        public static final RegistryObject<EntityType<RobouteGuillimanEntity>> ROBOUTE_GUILLIMAN =
        ENTITY_TYPES.register("roboute_guilliman",
                () -> EntityType.Builder.of(RobouteGuillimanEntity::new, MobCategory.CREATURE)
                        .sized(1.4F, 3.2F)
                        .clientTrackingRange(16)
                        .build(ExampleMod.MODID + ":roboute_guilliman"));

public static final RegistryObject<Item> ROBOUTE_GUILLIMAN_SPAWN_EGG =
        ITEMS.register("roboute_guilliman_spawn_egg",
                () -> new ForgeSpawnEggItem(
                        ROBOUTE_GUILLIMAN,
                        0x0B2A66,
                        0xD6A23A,
                        new Item.Properties()
                ));

    // =========================
    // ORK ENTITIES
    // =========================

    public static final RegistryObject<EntityType<OrkBoyEntity>> ORK_BOY =
            ENTITY_TYPES.register("ork_boy",
                    () -> EntityType.Builder.of(OrkBoyEntity::new, MobCategory.MONSTER)
                            .sized(0.7F, 2.05F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":ork_boy"));

    public static final RegistryObject<Item> ORK_BOY_SPAWN_EGG = ITEMS.register("ork_boy_spawn_egg",
            () -> new ForgeSpawnEggItem(ORK_BOY, 0x2B5E2B, 0x7A5A22, new Item.Properties()));

    public static final RegistryObject<EntityType<OrkNobEntity>> ORK_NOB =
            ENTITY_TYPES.register("ork_nob",
                    () -> EntityType.Builder.of(OrkNobEntity::new, MobCategory.MONSTER)
                            .sized(0.85F, 2.25F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":ork_nob"));

    public static final RegistryObject<Item> ORK_NOB_SPAWN_EGG = ITEMS.register("ork_nob_spawn_egg",
            () -> new ForgeSpawnEggItem(ORK_NOB, 0x1F4D1F, 0x8A6A24, new Item.Properties()));

    public static final RegistryObject<EntityType<WarbossEntity>> WARBOSS =
            ENTITY_TYPES.register("warboss",
                    () -> EntityType.Builder.of(WarbossEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 2.6F)
                            .clientTrackingRange(12)
                            .build(ExampleMod.MODID + ":warboss"));

    public static final RegistryObject<Item> WARBOSS_SPAWN_EGG = ITEMS.register("warboss_spawn_egg",
            () -> new ForgeSpawnEggItem(WARBOSS, 0x143314, 0xB02020, new Item.Properties()));

    public static final RegistryObject<EntityType<MeganobEntity>> MEGANOB =
            ENTITY_TYPES.register("meganob",
                    () -> EntityType.Builder.of(MeganobEntity::new, MobCategory.MONSTER)
                            .sized(0.9F, 2.3F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":meganob"));

    public static final RegistryObject<Item> MEGANOB_SPAWN_EGG = ITEMS.register("meganob_spawn_egg",
            () -> new ForgeSpawnEggItem(MEGANOB, 0x254D25, 0x6E6E6E, new Item.Properties()));

    public static final RegistryObject<EntityType<GretchinEntity>> GRETCHIN =
            ENTITY_TYPES.register("gretchin",
                    () -> EntityType.Builder.of(GretchinEntity::new, MobCategory.MONSTER)
                            .sized(0.5F, 1.2F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":gretchin"));

    public static final RegistryObject<Item> GRETCHIN_SPAWN_EGG = ITEMS.register("gretchin_spawn_egg",
            () -> new ForgeSpawnEggItem(GRETCHIN, 0x3A7A1F, 0x9A7A30, new Item.Properties()));

    public static final RegistryObject<EntityType<SkitariiRangerEntity>> SKITARII_RANGER =
            ENTITY_TYPES.register("skitarii_ranger",
                    () -> EntityType.Builder.of(SkitariiRangerEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":skitarii_ranger"));

    public static final RegistryObject<Item> SKITARII_RANGER_SPAWN_EGG = ITEMS.register("skitarii_ranger_spawn_egg",
            () -> new ForgeSpawnEggItem(SKITARII_RANGER, 0x6B6B6B, 0x8A1F1F, new Item.Properties()));

    public static final RegistryObject<EntityType<KasrkinEntity>> KASRKIN =
            ENTITY_TYPES.register("kasrkin",
                    () -> EntityType.Builder.of(KasrkinEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":kasrkin"));

    public static final RegistryObject<Item> KASRKIN_SPAWN_EGG = ITEMS.register("kasrkin_spawn_egg",
            () -> new ForgeSpawnEggItem(KASRKIN, 0x2E3B2E, 0xC8A23A, new Item.Properties()));

    public static final RegistryObject<EntityType<EnforcerEntity>> ENFORCER =
            ENTITY_TYPES.register("enforcer",
                    () -> EntityType.Builder.of(EnforcerEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":enforcer"));

    public static final RegistryObject<Item> ENFORCER_SPAWN_EGG = ITEMS.register("enforcer_spawn_egg",
            () -> new ForgeSpawnEggItem(ENFORCER, 0x1C1C22, 0xB02020, new Item.Properties()));

    public static final RegistryObject<EntityType<MineGuardEntity>> MINE_GUARD =
            ENTITY_TYPES.register("mine_guard",
                    () -> EntityType.Builder.of(MineGuardEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":mine_guard"));

    public static final RegistryObject<Item> MINE_GUARD_SPAWN_EGG = ITEMS.register("mine_guard_spawn_egg",
            () -> new ForgeSpawnEggItem(MINE_GUARD, 0x4A4036, 0xD8A024, new Item.Properties()));

    public static final RegistryObject<EntityType<AgriMilitiaEntity>> AGRI_MILITIA =
            ENTITY_TYPES.register("agri_militia",
                    () -> EntityType.Builder.of(AgriMilitiaEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":agri_militia"));

    public static final RegistryObject<Item> AGRI_MILITIA_SPAWN_EGG = ITEMS.register("agri_militia_spawn_egg",
            () -> new ForgeSpawnEggItem(AGRI_MILITIA, 0x5A6B2A, 0x9AAE4A, new Item.Properties()));

    public static final RegistryObject<EntityType<SisterOfBattleEntity>> SISTER_OF_BATTLE =
            ENTITY_TYPES.register("sister_of_battle",
                    () -> EntityType.Builder.of(SisterOfBattleEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":sister_of_battle"));

    public static final RegistryObject<Item> SISTER_OF_BATTLE_SPAWN_EGG = ITEMS.register("sister_of_battle_spawn_egg",
            () -> new ForgeSpawnEggItem(SISTER_OF_BATTLE, 0x111114, 0xC02028, new Item.Properties()));

    public static final RegistryObject<EntityType<PenalLegionnaireEntity>> PENAL_LEGIONNAIRE =
            ENTITY_TYPES.register("penal_legionnaire",
                    () -> EntityType.Builder.of(PenalLegionnaireEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":penal_legionnaire"));

    public static final RegistryObject<Item> PENAL_LEGIONNAIRE_SPAWN_EGG = ITEMS.register("penal_legionnaire_spawn_egg",
            () -> new ForgeSpawnEggItem(PENAL_LEGIONNAIRE, 0x3A2A1A, 0xD4622A, new Item.Properties()));

    public static final RegistryObject<EntityType<JungleFighterEntity>> JUNGLE_FIGHTER =
            ENTITY_TYPES.register("jungle_fighter",
                    () -> EntityType.Builder.of(JungleFighterEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":jungle_fighter"));

    public static final RegistryObject<Item> JUNGLE_FIGHTER_SPAWN_EGG = ITEMS.register("jungle_fighter_spawn_egg",
            () -> new ForgeSpawnEggItem(JUNGLE_FIGHTER, 0x3A5A2A, 0x6E4A22, new Item.Properties()));

    public static final RegistryObject<EntityType<FeudalKnightEntity>> FEUDAL_KNIGHT =
            ENTITY_TYPES.register("feudal_knight",
                    () -> EntityType.Builder.of(FeudalKnightEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":feudal_knight"));

    public static final RegistryObject<Item> FEUDAL_KNIGHT_SPAWN_EGG = ITEMS.register("feudal_knight_spawn_egg",
            () -> new ForgeSpawnEggItem(FEUDAL_KNIGHT, 0x6E6E78, 0x8A2A2A, new Item.Properties()));

    public static final RegistryObject<EntityType<CityCommanderEntity>> CITY_COMMANDER =
            ENTITY_TYPES.register("city_commander",
                    () -> EntityType.Builder.of(CityCommanderEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":city_commander"));

    public static final RegistryObject<Item> CITY_COMMANDER_SPAWN_EGG = ITEMS.register("city_commander_spawn_egg",
            () -> new ForgeSpawnEggItem(CITY_COMMANDER, 0x1A2A4A, 0xD4AF37, new Item.Properties()));

    public static final RegistryObject<EntityType<KillaKanEntity>> KILLA_KAN =
            ENTITY_TYPES.register("killa_kan",
                    () -> EntityType.Builder.of(KillaKanEntity::new, MobCategory.MONSTER)
                            .sized(1.1F, 2.6F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":killa_kan"));

    public static final RegistryObject<Item> KILLA_KAN_SPAWN_EGG = ITEMS.register("killa_kan_spawn_egg",
            () -> new ForgeSpawnEggItem(KILLA_KAN, 0x4A4A2A, 0x8A2020, new Item.Properties()));

    // =========================
    // ARMOR MATERIALS
    // =========================

    public static final ArmorMaterial GUARDSMAN_ARMOR_MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 165;
                case CHESTPLATE -> 240;
                case LEGGINGS -> 225;
                case BOOTS -> 195;
            };
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 2;
                case CHESTPLATE -> 6;
                case LEGGINGS -> 5;
                case BOOTS -> 2;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 10;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(CRUSADIUM_PLATE.get());
        }

        @Override
        public String getName() {
            return ExampleMod.MODID + ":guardsman";
        }

        @Override
        public float getToughness() {
            return 1.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    // =========================
    // GUARDSMAN ARMOR
    // =========================

    public static final RegistryObject<Item> GUARDSMAN_HELMET = ITEMS.register("guardsman_helmet",
            () -> new ArmorItem(GUARDSMAN_ARMOR_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<Item> GUARDSMAN_CHESTPLATE = ITEMS.register("guardsman_chestplate",
            () -> new ArmorItem(GUARDSMAN_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final RegistryObject<Item> GUARDSMAN_LEGGINGS = ITEMS.register("guardsman_leggings",
            () -> new ArmorItem(GUARDSMAN_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static final RegistryObject<Item> GUARDSMAN_BOOTS = ITEMS.register("guardsman_boots",
            () -> new ArmorItem(GUARDSMAN_ARMOR_MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties()));

    // =========================
    // SPACE MARINE ARMOR
    // =========================

    // Power armor: significantly tougher than Guardsman flak. Worn-layer textures are
    // placeholders copied from the Guardsman layers until dedicated art is added.
    public static final ArmorMaterial SPACE_MARINE_ARMOR_MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 407;
                case CHESTPLATE -> 592;
                case LEGGINGS -> 555;
                case BOOTS -> 481;
            };
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 3;
                case CHESTPLATE -> 8;
                case LEGGINGS -> 6;
                case BOOTS -> 3;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 12;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(CRUSADIUM_PLATE.get());
        }

        @Override
        public String getName() {
            return ExampleMod.MODID + ":space_marine";
        }

        @Override
        public float getToughness() {
            return 3.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.1F;
        }
    };

    public static final RegistryObject<Item> SPACE_MARINE_HELMET = ITEMS.register("space_marine_helmet",
            () -> new ArmorItem(SPACE_MARINE_ARMOR_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<Item> SPACE_MARINE_CHESTPLATE = ITEMS.register("space_marine_chestplate",
            () -> new ArmorItem(SPACE_MARINE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final RegistryObject<Item> SPACE_MARINE_LEGGINGS = ITEMS.register("space_marine_leggings",
            () -> new ArmorItem(SPACE_MARINE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static final RegistryObject<Item> SPACE_MARINE_BOOTS = ITEMS.register("space_marine_boots",
            () -> new ArmorItem(SPACE_MARINE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties()));

    // =========================
    // CREATIVE TAB
    // =========================

    public static final RegistryObject<CreativeModeTab> FIRST_CRUSADE_TAB =
            CREATIVE_MODE_TABS.register("first_crusade_tab", () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.translatable("itemGroup.firstcrusade.first_crusade_tab"))
                    .icon(() -> IMPERIAL_COMMAND_CORE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(IMPERIAL_COMMAND_CORE_ITEM.get());
                        output.accept(CITY_BUILDER_TOOL.get());
                        output.accept(IMPERIAL_SCRAP_YARD_ITEM.get());
                        output.accept(IMPERIAL_FORGE_ITEM.get());
                        output.accept(IMPERIAL_PROMETHIUM_REFINERY_ITEM.get());
                        output.accept(IMPERIAL_BARRACKS_ITEM.get());
                        output.accept(IMPERIAL_HABITATION_ITEM.get());
                        output.accept(ORK_CAMP_ITEM.get());
                        output.accept(ORK_LOOT_PIT_ITEM.get());
                        output.accept(STRATEGIUM_ITEM.get());
                        output.accept(SPACEPORT_ITEM.get());
                        output.accept(PLANETARY_NAVIGATION_TERMINAL_ITEM.get());

                        output.accept(CRUSADIUM_INGOT.get());
                        output.accept(CRUSADIUM_PLATE.get());
                        output.accept(ORK_TEETH.get());
                        output.accept(CHOPPA.get());
                        output.accept(SHOOTA.get());
                        output.accept(POWER_KLAW.get());
                        output.accept(SCRAP_METAL.get());
                        output.accept(IMPERIAL_CITIZEN_SPAWN_EGG.get());
                        output.accept(IMPERIAL_MINE_ITEM.get());
                        output.accept(IMPERIAL_GOLD_MINE_ITEM.get());
                        output.accept(IMPERIAL_FARM_ITEM.get());
                        output.accept(IMPERIAL_EMERALD_TRADE_DEPOT_ITEM.get());

                        output.accept(LASGUN_POWER_CELL.get());
                        output.accept(LASGUN.get());
                        output.accept(BOLTER.get());
                        output.accept(PLASMA_GUN.get());
                        output.accept(GUARDSMAN_COMBAT_KNIFE.get());
                        output.accept(CHAINSWORD.get());
                        output.accept(GUARDIAN_SPEAR.get());
                        output.accept(POWER_SWORD.get());
                        output.accept(GUARDSMAN_MED_KIT.get());
                        output.accept(GUARDSMAN_COMMAND_BATON.get());

                        output.accept(GUARDSMAN_HELMET.get());
                        output.accept(GUARDSMAN_CHESTPLATE.get());
                        output.accept(GUARDSMAN_LEGGINGS.get());
                        output.accept(GUARDSMAN_BOOTS.get());

                        output.accept(SPACE_MARINE_HELMET.get());
                        output.accept(SPACE_MARINE_CHESTPLATE.get());
                        output.accept(SPACE_MARINE_LEGGINGS.get());
                        output.accept(SPACE_MARINE_BOOTS.get());

                       output.accept(GUARDSMAN_SPAWN_EGG.get());
                       output.accept(GUARDSMAN_RIFLEMAN_SPAWN_EGG.get());
                       output.accept(GUARDSMAN_SERGEANT_SPAWN_EGG.get());
output.accept(SPACE_MARINE_SPAWN_EGG.get());
output.accept(CUSTODES_SPAWN_EGG.get());
output.accept(PRIMARCH_SPAWN_EGG.get());
output.accept(ROBOUTE_GUILLIMAN_SPAWN_EGG.get());
output.accept(ORK_BOY_SPAWN_EGG.get());
output.accept(ORK_NOB_SPAWN_EGG.get());
output.accept(WARBOSS_SPAWN_EGG.get());
output.accept(MEGANOB_SPAWN_EGG.get());
output.accept(GRETCHIN_SPAWN_EGG.get());
output.accept(KILLA_KAN_SPAWN_EGG.get());
output.accept(SKITARII_RANGER_SPAWN_EGG.get());
output.accept(KASRKIN_SPAWN_EGG.get());
output.accept(ENFORCER_SPAWN_EGG.get());
output.accept(MINE_GUARD_SPAWN_EGG.get());
output.accept(AGRI_MILITIA_SPAWN_EGG.get());
output.accept(SISTER_OF_BATTLE_SPAWN_EGG.get());
output.accept(PENAL_LEGIONNAIRE_SPAWN_EGG.get());
output.accept(JUNGLE_FIGHTER_SPAWN_EGG.get());
output.accept(FEUDAL_KNIGHT_SPAWN_EGG.get());
output.accept(CITY_COMMANDER_SPAWN_EGG.get());

                        // Fase E: a fauna registra-se em .animal, e so a vitrine passa por aqui.
                        com.example.examplemod.animal.FCAnimals.addToCreativeTab(output);
                    })
                    .build());

    // Wires all DeferredRegisters (and entity attributes) to the mod event bus.
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(FCRegistry::registerAttributes);
    }

public static void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(GUARDSMAN.get(), GuardsmanEntity.createAttributes().build());
    event.put(GUARDSMAN_RIFLEMAN.get(), GuardsmanRiflemanEntity.createAttributes().build());
    event.put(GUARDSMAN_SERGEANT.get(), GuardsmanSergeantEntity.createAttributes().build());
    event.put(IMPERIAL_CITIZEN.get(), ImperialCitizenEntity.createAttributes().build());
    event.put(SPACE_MARINE.get(), SpaceMarineEntity.createAttributes().build());
    event.put(CUSTODES.get(), CustodesEntity.createAttributes().build());
    event.put(PRIMARCH.get(), PrimarchEntity.createAttributes().build());
    event.put(ROBOUTE_GUILLIMAN.get(), RobouteGuillimanEntity.createAttributes().build());
    event.put(ORK_BOY.get(), OrkBoyEntity.createAttributes().build());
    event.put(ORK_NOB.get(), OrkNobEntity.createAttributes().build());
    event.put(WARBOSS.get(), WarbossEntity.createAttributes().build());
    event.put(MEGANOB.get(), MeganobEntity.createAttributes().build());
    event.put(GRETCHIN.get(), GretchinEntity.createAttributes().build());
    event.put(KILLA_KAN.get(), KillaKanEntity.createAttributes().build());
    event.put(SKITARII_RANGER.get(), SkitariiRangerEntity.createAttributes().build());
    event.put(KASRKIN.get(), KasrkinEntity.createAttributes().build());
    event.put(ENFORCER.get(), EnforcerEntity.createAttributes().build());
    event.put(MINE_GUARD.get(), MineGuardEntity.createAttributes().build());
    event.put(AGRI_MILITIA.get(), AgriMilitiaEntity.createAttributes().build());
    event.put(SISTER_OF_BATTLE.get(), SisterOfBattleEntity.createAttributes().build());
    event.put(PENAL_LEGIONNAIRE.get(), PenalLegionnaireEntity.createAttributes().build());
    event.put(JUNGLE_FIGHTER.get(), JungleFighterEntity.createAttributes().build());
    event.put(FEUDAL_KNIGHT.get(), FeudalKnightEntity.createAttributes().build());
    event.put(CITY_COMMANDER.get(), CityCommanderEntity.createAttributes().build());
}
}
