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

    private static final String NETWORK_PROTOCOL_VERSION = "1";

public static final SimpleChannel NETWORK_CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(MODID, "main"),
        () -> NETWORK_PROTOCOL_VERSION,
        NETWORK_PROTOCOL_VERSION::equals,
        NETWORK_PROTOCOL_VERSION::equals
);

private static int networkPacketId = 0;

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
            public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

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
                        .strength(3.0F)
                        .sound(SoundType.WOOD)));

public static final RegistryObject<Item> ORK_CAMP_ITEM =
        ITEMS.register("ork_camp",
                () -> new BlockItem(ORK_CAMP.get(), new Item.Properties()));

public static final RegistryObject<BlockEntityType<OrkCampBlockEntity>> ORK_CAMP_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("ork_camp",
                () -> BlockEntityType.Builder.of(OrkCampBlockEntity::new, ORK_CAMP.get()).build(null));

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
                            .build(MODID + ":lasgun_shot"));

    // =========================
    // GUARDSMEN ENTITIES
    // =========================

    public static final RegistryObject<EntityType<GuardsmanEntity>> GUARDSMAN =
            ENTITY_TYPES.register("guardsman",
                    () -> EntityType.Builder.of(GuardsmanEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":guardsman"));

                            public static final RegistryObject<EntityType<ImperialCitizenEntity>> IMPERIAL_CITIZEN =
        ENTITY_TYPES.register("imperial_citizen",
                () -> EntityType.Builder.of(ImperialCitizenEntity::new, MobCategory.CREATURE)
                        .sized(0.6F, 1.95F)
                        .build("imperial_citizen"));

    public static final RegistryObject<Item> GUARDSMAN_SPAWN_EGG = ITEMS.register("guardsman_spawn_egg",
            () -> new ForgeSpawnEggItem(GUARDSMAN, 0x3A3A3A, 0xB0A060, new Item.Properties()));
            public static final RegistryObject<EntityType<SpaceMarineEntity>> SPACE_MARINE =
        ENTITY_TYPES.register("space_marine",
                () -> EntityType.Builder.of(SpaceMarineEntity::new, MobCategory.CREATURE)
                        .sized(0.85F, 2.35F)
                        .clientTrackingRange(10)
                        .build(MODID + ":space_marine"));


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
                        .build(MODID + ":custodes"));

public static final RegistryObject<Item> CUSTODES_SPAWN_EGG = ITEMS.register("custodes_spawn_egg",
        () -> new ForgeSpawnEggItem(CUSTODES, 0xC9A227, 0x5A0A0A, new Item.Properties()));

public static final RegistryObject<EntityType<PrimarchEntity>> PRIMARCH =
        ENTITY_TYPES.register("primarch",
                () -> EntityType.Builder.of(PrimarchEntity::new, MobCategory.CREATURE)
                        .sized(1.4F, 3.6F)
                        .clientTrackingRange(16)
                        .build(MODID + ":primarch"));

public static final RegistryObject<Item> PRIMARCH_SPAWN_EGG = ITEMS.register("primarch_spawn_egg",
        () -> new ForgeSpawnEggItem(PRIMARCH, 0xD4AF37, 0x2B1B5A, new Item.Properties()));

        public static final RegistryObject<EntityType<RobouteGuillimanEntity>> ROBOUTE_GUILLIMAN =
        ENTITY_TYPES.register("roboute_guilliman",
                () -> EntityType.Builder.of(RobouteGuillimanEntity::new, MobCategory.CREATURE)
                        .sized(1.4F, 3.2F)
                        .clientTrackingRange(16)
                        .build(MODID + ":roboute_guilliman"));

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
                            .build(MODID + ":ork_boy"));

    public static final RegistryObject<Item> ORK_BOY_SPAWN_EGG = ITEMS.register("ork_boy_spawn_egg",
            () -> new ForgeSpawnEggItem(ORK_BOY, 0x2B5E2B, 0x7A5A22, new Item.Properties()));

    public static final RegistryObject<EntityType<OrkNobEntity>> ORK_NOB =
            ENTITY_TYPES.register("ork_nob",
                    () -> EntityType.Builder.of(OrkNobEntity::new, MobCategory.MONSTER)
                            .sized(0.85F, 2.25F)
                            .clientTrackingRange(8)
                            .build(MODID + ":ork_nob"));

    public static final RegistryObject<Item> ORK_NOB_SPAWN_EGG = ITEMS.register("ork_nob_spawn_egg",
            () -> new ForgeSpawnEggItem(ORK_NOB, 0x1F4D1F, 0x8A6A24, new Item.Properties()));

    public static final RegistryObject<EntityType<WarbossEntity>> WARBOSS =
            ENTITY_TYPES.register("warboss",
                    () -> EntityType.Builder.of(WarbossEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 2.6F)
                            .clientTrackingRange(12)
                            .build(MODID + ":warboss"));

    public static final RegistryObject<Item> WARBOSS_SPAWN_EGG = ITEMS.register("warboss_spawn_egg",
            () -> new ForgeSpawnEggItem(WARBOSS, 0x143314, 0xB02020, new Item.Properties()));

    public static final RegistryObject<EntityType<MeganobEntity>> MEGANOB =
            ENTITY_TYPES.register("meganob",
                    () -> EntityType.Builder.of(MeganobEntity::new, MobCategory.MONSTER)
                            .sized(0.9F, 2.3F)
                            .clientTrackingRange(10)
                            .build(MODID + ":meganob"));

    public static final RegistryObject<Item> MEGANOB_SPAWN_EGG = ITEMS.register("meganob_spawn_egg",
            () -> new ForgeSpawnEggItem(MEGANOB, 0x254D25, 0x6E6E6E, new Item.Properties()));

    public static final RegistryObject<EntityType<GretchinEntity>> GRETCHIN =
            ENTITY_TYPES.register("gretchin",
                    () -> EntityType.Builder.of(GretchinEntity::new, MobCategory.MONSTER)
                            .sized(0.5F, 1.2F)
                            .clientTrackingRange(8)
                            .build(MODID + ":gretchin"));

    public static final RegistryObject<Item> GRETCHIN_SPAWN_EGG = ITEMS.register("gretchin_spawn_egg",
            () -> new ForgeSpawnEggItem(GRETCHIN, 0x3A7A1F, 0x9A7A30, new Item.Properties()));

    public static final RegistryObject<EntityType<SkitariiRangerEntity>> SKITARII_RANGER =
            ENTITY_TYPES.register("skitarii_ranger",
                    () -> EntityType.Builder.of(SkitariiRangerEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":skitarii_ranger"));

    public static final RegistryObject<Item> SKITARII_RANGER_SPAWN_EGG = ITEMS.register("skitarii_ranger_spawn_egg",
            () -> new ForgeSpawnEggItem(SKITARII_RANGER, 0x6B6B6B, 0x8A1F1F, new Item.Properties()));

    public static final RegistryObject<EntityType<KasrkinEntity>> KASRKIN =
            ENTITY_TYPES.register("kasrkin",
                    () -> EntityType.Builder.of(KasrkinEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":kasrkin"));

    public static final RegistryObject<Item> KASRKIN_SPAWN_EGG = ITEMS.register("kasrkin_spawn_egg",
            () -> new ForgeSpawnEggItem(KASRKIN, 0x2E3B2E, 0xC8A23A, new Item.Properties()));

    public static final RegistryObject<EntityType<EnforcerEntity>> ENFORCER =
            ENTITY_TYPES.register("enforcer",
                    () -> EntityType.Builder.of(EnforcerEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":enforcer"));

    public static final RegistryObject<Item> ENFORCER_SPAWN_EGG = ITEMS.register("enforcer_spawn_egg",
            () -> new ForgeSpawnEggItem(ENFORCER, 0x1C1C22, 0xB02020, new Item.Properties()));

    public static final RegistryObject<EntityType<MineGuardEntity>> MINE_GUARD =
            ENTITY_TYPES.register("mine_guard",
                    () -> EntityType.Builder.of(MineGuardEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":mine_guard"));

    public static final RegistryObject<Item> MINE_GUARD_SPAWN_EGG = ITEMS.register("mine_guard_spawn_egg",
            () -> new ForgeSpawnEggItem(MINE_GUARD, 0x4A4036, 0xD8A024, new Item.Properties()));

    public static final RegistryObject<EntityType<AgriMilitiaEntity>> AGRI_MILITIA =
            ENTITY_TYPES.register("agri_militia",
                    () -> EntityType.Builder.of(AgriMilitiaEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":agri_militia"));

    public static final RegistryObject<Item> AGRI_MILITIA_SPAWN_EGG = ITEMS.register("agri_militia_spawn_egg",
            () -> new ForgeSpawnEggItem(AGRI_MILITIA, 0x5A6B2A, 0x9AAE4A, new Item.Properties()));

    public static final RegistryObject<EntityType<SisterOfBattleEntity>> SISTER_OF_BATTLE =
            ENTITY_TYPES.register("sister_of_battle",
                    () -> EntityType.Builder.of(SisterOfBattleEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":sister_of_battle"));

    public static final RegistryObject<Item> SISTER_OF_BATTLE_SPAWN_EGG = ITEMS.register("sister_of_battle_spawn_egg",
            () -> new ForgeSpawnEggItem(SISTER_OF_BATTLE, 0x111114, 0xC02028, new Item.Properties()));

    public static final RegistryObject<EntityType<PenalLegionnaireEntity>> PENAL_LEGIONNAIRE =
            ENTITY_TYPES.register("penal_legionnaire",
                    () -> EntityType.Builder.of(PenalLegionnaireEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":penal_legionnaire"));

    public static final RegistryObject<Item> PENAL_LEGIONNAIRE_SPAWN_EGG = ITEMS.register("penal_legionnaire_spawn_egg",
            () -> new ForgeSpawnEggItem(PENAL_LEGIONNAIRE, 0x3A2A1A, 0xD4622A, new Item.Properties()));

    public static final RegistryObject<EntityType<JungleFighterEntity>> JUNGLE_FIGHTER =
            ENTITY_TYPES.register("jungle_fighter",
                    () -> EntityType.Builder.of(JungleFighterEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MODID + ":jungle_fighter"));

    public static final RegistryObject<Item> JUNGLE_FIGHTER_SPAWN_EGG = ITEMS.register("jungle_fighter_spawn_egg",
            () -> new ForgeSpawnEggItem(JUNGLE_FIGHTER, 0x3A5A2A, 0x6E4A22, new Item.Properties()));

    public static final RegistryObject<EntityType<KillaKanEntity>> KILLA_KAN =
            ENTITY_TYPES.register("killa_kan",
                    () -> EntityType.Builder.of(KillaKanEntity::new, MobCategory.MONSTER)
                            .sized(1.1F, 2.6F)
                            .clientTrackingRange(10)
                            .build(MODID + ":killa_kan"));

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
            return MODID + ":guardsman";
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
            return MODID + ":space_marine";
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
                        output.accept(IMPERIAL_SCRAP_YARD_ITEM.get());
                        output.accept(IMPERIAL_FORGE_ITEM.get());
                        output.accept(IMPERIAL_PROMETHIUM_REFINERY_ITEM.get());
                        output.accept(IMPERIAL_BARRACKS_ITEM.get());
                        output.accept(IMPERIAL_HABITATION_ITEM.get());
                        output.accept(ORK_CAMP_ITEM.get());

                        output.accept(CRUSADIUM_INGOT.get());
                        output.accept(CRUSADIUM_PLATE.get());
                        output.accept(ORK_TEETH.get());
                        output.accept(SCRAP_METAL.get());
                        output.accept(IMPERIAL_CITIZEN_SPAWN_EGG.get());
                        output.accept(IMPERIAL_MINE_ITEM.get());
                        output.accept(IMPERIAL_GOLD_MINE_ITEM.get());
                        output.accept(IMPERIAL_FARM_ITEM.get());
                        output.accept(IMPERIAL_EMERALD_TRADE_DEPOT_ITEM.get());

                        output.accept(LASGUN_POWER_CELL.get());
                        output.accept(LASGUN.get());
                        output.accept(GUARDSMAN_COMBAT_KNIFE.get());
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
                    })
                    .build());

    public ExampleMod(FMLJavaModLoadingContext context) {
    IEventBus modEventBus = context.getModEventBus();

    modEventBus.addListener(this::commonSetup);
    modEventBus.addListener(this::registerAttributes);

    BLOCKS.register(modEventBus);
    ITEMS.register(modEventBus);
    CREATIVE_MODE_TABS.register(modEventBus);
    ENTITY_TYPES.register(modEventBus);
    BLOCK_ENTITY_TYPES.register(modEventBus);
    MENU_TYPES.register(modEventBus);

    MinecraftForge.EVENT_BUS.register(this);
}

private void commonSetup(final FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        NETWORK_CHANNEL.registerMessage(
                networkPacketId++,
                ImperialCommandCoreActionPacket.class,
                ImperialCommandCoreActionPacket::encode,
                ImperialCommandCoreActionPacket::decode,
                ImperialCommandCoreActionPacket::handle
        );
    });

    LOGGER.info("First Crusade loaded successfully.");
}

private void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(GUARDSMAN.get(), GuardsmanEntity.createAttributes().build());
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
}

@SubscribeEvent
public void onServerStarting(ServerStartingEvent event) {
    LOGGER.info("First Crusade server starting.");
}

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public static class ClientModEvents {
        @SubscribeEvent
public static void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> {
        MenuScreens.register(IMPERIAL_COMMAND_CORE_MENU.get(), ImperialCommandCoreScreen::new);
    });
}
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RobouteGuillimanModel.LAYER_LOCATION, RobouteGuillimanModel::createBodyLayer);
        event.registerLayerDefinition(EliteModelLayers.SPACE_MARINE, EliteModelLayers::createSpaceMarineLayer);
        event.registerLayerDefinition(EliteModelLayers.CUSTODES, EliteModelLayers::createCustodesLayer);
        event.registerLayerDefinition(EliteModelLayers.PRIMARCH, EliteModelLayers::createPrimarchLayer);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IMPERIAL_CITIZEN.get(), ImperialCitizenRenderer::new);
        event.registerEntityRenderer(LASGUN_SHOT.get(), LasgunShotRenderer::new);
        event.registerEntityRenderer(GUARDSMAN.get(), GuardsmanRenderer::new);
        event.registerEntityRenderer(SPACE_MARINE.get(), SpaceMarineRenderer::new);
        event.registerEntityRenderer(CUSTODES.get(), CustodesRenderer::new);
        event.registerEntityRenderer(PRIMARCH.get(), PrimarchRenderer::new);
        event.registerEntityRenderer(ORK_BOY.get(), OrkBoyRenderer::new);
        event.registerEntityRenderer(ORK_NOB.get(), OrkNobRenderer::new);
        event.registerEntityRenderer(WARBOSS.get(), WarbossRenderer::new);
        event.registerEntityRenderer(MEGANOB.get(), MeganobRenderer::new);
        event.registerEntityRenderer(GRETCHIN.get(), GretchinRenderer::new);
        event.registerEntityRenderer(KILLA_KAN.get(), KillaKanRenderer::new);
        event.registerEntityRenderer(ROBOUTE_GUILLIMAN.get(), RobouteGuillimanRenderer::new);
        event.registerEntityRenderer(SKITARII_RANGER.get(), SkitariiRangerRenderer::new);
        event.registerEntityRenderer(KASRKIN.get(), KasrkinRenderer::new);
        event.registerEntityRenderer(ENFORCER.get(), EnforcerRenderer::new);
        event.registerEntityRenderer(MINE_GUARD.get(), MineGuardRenderer::new);
        event.registerEntityRenderer(AGRI_MILITIA.get(), AgriMilitiaRenderer::new);
        event.registerEntityRenderer(SISTER_OF_BATTLE.get(), SisterOfBattleRenderer::new);
        event.registerEntityRenderer(PENAL_LEGIONNAIRE.get(), PenalLegionnaireRenderer::new);
        event.registerEntityRenderer(JUNGLE_FIGHTER.get(), JungleFighterRenderer::new);
    }
}
}