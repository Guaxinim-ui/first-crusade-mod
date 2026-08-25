package com.example.examplemod.necron;

import com.example.examplemod.ExampleMod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Necrons' own registry.
 *
 * <h2>Separate from FCRegistry on purpose</h2>
 *
 * Same reason the fauna has its own: {@code FCRegistry} is already thousands of lines, and a faction
 * that arrives whole — three units, their attributes and their renderers — is easier to read, and to
 * remove, as one file than as three more entries scattered through that one.
 *
 * <h2>No spawn eggs</h2>
 *
 * Deliberate. Every other faction in the mod has them, and the Necrons are the one faction whose
 * whole point is that they arrive when the tomb decides. An egg would make the awakening — the
 * hundred-point clock the campaign already keeps — a thing you can skip, which is the one way to
 * make it mean nothing. {@code /summon} still works for testing.
 */
public final class FCNecrons {

    private FCNecrons() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    /** Rank and file. 1.9 blocks tall and narrow — the model is 28 units with a 5-wide chest. */
    public static final RegistryObject<EntityType<NecronWarriorEntity>> NECRON_WARRIOR =
            ENTITY_TYPES.register("necron_warrior",
                    () -> EntityType.Builder.of(NecronWarriorEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.9F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":necron_warrior"));

    /** The crown. Taller and wider: 2.4 blocks, so he is findable in a phalanx. */
    public static final RegistryObject<EntityType<NecronOverlordEntity>> NECRON_OVERLORD =
            ENTITY_TYPES.register("necron_overlord",
                    () -> EntityType.Builder.of(NecronOverlordEntity::new, MobCategory.MONSTER)
                            .sized(0.9F, 2.4F)
                            .clientTrackingRange(12)
                            .build(ExampleMod.MODID + ":necron_overlord"));

    /** Knee-high and wide — the hitbox matches the domed shell rather than a cube. */
    public static final RegistryObject<EntityType<NecronScarabEntity>> NECRON_SCARAB =
            ENTITY_TYPES.register("necron_scarab",
                    () -> EntityType.Builder.of(NecronScarabEntity::new, MobCategory.MONSTER)
                            .sized(0.8F, 0.5F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":necron_scarab"));

    // =========================================================================================
    // SPAWN EGGS
    //
    // These were deliberately left out when the Necrons were built: the argument was that an egg
    // makes the tomb's hundred-point awakening a thing you can skip. The owner asked where they
    // were, and on reflection the argument does not hold up.
    //
    // A spawn egg is a creative-tab item. It cannot be reached in a survival campaign, so it never
    // touches the clock that gates the tomb — and every other faction in the mod has eggs, so their
    // absence read as an oversight rather than as a rule. Against that, the three Necron models had
    // no way of being LOOKED AT: they only appear through NecronAwakeningSpawner, which needs a tomb
    // world, a player standing on it, and a hundred points of awakening first. Making a mod's own art
    // unreachable to the person who has to review it is a real cost for a rule that protected
    // nothing.
    // =========================================================================================

    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    /** Necron green over gunmetal — the palette the asset generator paints them in. */
    public static final RegistryObject<net.minecraft.world.item.Item> NECRON_WARRIOR_SPAWN_EGG =
            ITEMS.register("necron_warrior_spawn_egg",
                    () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                            NECRON_WARRIOR, 0x3A4046, 0x7FD69A,
                            new net.minecraft.world.item.Item.Properties()));

    public static final RegistryObject<net.minecraft.world.item.Item> NECRON_OVERLORD_SPAWN_EGG =
            ITEMS.register("necron_overlord_spawn_egg",
                    () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                            NECRON_OVERLORD, 0x2B3036, 0xC8A93A,
                            new net.minecraft.world.item.Item.Properties()));

    public static final RegistryObject<net.minecraft.world.item.Item> NECRON_SCARAB_SPAWN_EGG =
            ITEMS.register("necron_scarab_spawn_egg",
                    () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                            NECRON_SCARAB, 0x4A5056, 0x7FD69A,
                            new net.minecraft.world.item.Item.Properties()));

    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    // ITEMS is declared ABOVE the spawn eggs on purpose. Static fields initialise in declaration
    // order, so an egg registered against a DeferredRegister declared further down the file would
    // dereference null at class-load — a crash on startup, not a compile error.

    // =========================================================================================
    // TOMB MASONRY
    //
    // The faction's own material, so a tomb stops being deepslate with Necrons standing on it.
    // Read off the reference plates: the stone is almost black and ALL the shape comes from the
    // green light running through it. A pale stone with a green line on it reads as decoration; a
    // black one with a lit channel reads as a machine that is still powered.
    //
    // Textures are painted by tools/generate_necron_assets.py, from the same palette as the
    // entities — the architecture and the bodies are the same alloy, and two palettes would make
    // the Overlord look like a guest in his own tomb.
    // =========================================================================================

    /** The black plate the whole tomb is cut from. No light of its own. */
    public static final RegistryObject<net.minecraft.world.level.block.Block> NECRON_STONE =
            BLOCKS.register("necron_stone", () -> new net.minecraft.world.level.block.Block(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                            .strength(35.0F, 1200.0F)   // tomb-grade: obsidian territory, minable
                            .requiresCorrectToolForDrops()
                            .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE_TILES)));

    /** The lit channel. This is what a tomb is lit by — nothing else in it glows. */
    public static final RegistryObject<net.minecraft.world.level.block.Block> NECRON_CONDUIT =
            BLOCKS.register("necron_conduit", () -> new net.minecraft.world.level.block.Block(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN)
                            .strength(35.0F, 1200.0F)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 15)
                            .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE_TILES)));

    /** The dynastic mark. Glows, but dimmer: it is a signature, not a lamp. */
    public static final RegistryObject<net.minecraft.world.level.block.Block> NECRON_GLYPH =
            BLOCKS.register("necron_glyph", () -> new net.minecraft.world.level.block.Block(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                            .strength(35.0F, 1200.0F)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 10)
                            .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE_TILES)));

    // BlockItems for the three, so they can be carried and placed. The reliquary deliberately has
    // none: it is taken by hand and never held.
    static {
        for (RegistryObject<net.minecraft.world.level.block.Block> block
                : java.util.List.of(NECRON_STONE, NECRON_CONDUIT, NECRON_GLYPH)) {
            ITEMS.register(block.getId().getPath(),
                    () -> new net.minecraft.world.item.BlockItem(block.get(),
                            new net.minecraft.world.item.Item.Properties()));
        }
    }

    /** The plinth in the middle of a ruin. Right-clicked once, then dark forever. */
    public static final RegistryObject<net.minecraft.world.level.block.Block> NECRON_RELIQUARY =
            BLOCKS.register("necron_reliquary",
                    () -> new NecronReliquaryBlock(
                            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                                    .strength(-1.0F, 3600000.0F)   // unbreakable: taken by hand only
                                    .lightLevel(state -> 7)
                                    .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)));

    /**
     * The artefact itself.
     *
     * <p>No use of its own on purpose. It is a key and a trophy: the unlock is granted the moment it
     * is taken, so the item can be dropped, stored or lost without closing the door again.
     */
    public static final RegistryObject<net.minecraft.world.item.Item> NECRON_ARTEFACT =
            ITEMS.register("necron_artefact",
                    () -> new net.minecraft.world.item.Item(
                            new net.minecraft.world.item.Item.Properties()
                                    .stacksTo(1)
                                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    // =========================================================================================
    // CREATIVE TAB
    //
    // The spawn eggs were registered as items and put in NO tab at all, which is why the owner
    // still could not find them after they were added — an item nobody can reach in the menu is an
    // item that does not exist as far as anyone playing is concerned. Registering it is only half
    // the job; being findable is the other half.
    //
    // A tab of their own rather than a corner of the Imperial one: the Necrons are not a wing of
    // the Crusade, and their masonry is a building set somebody will want to browse on its own.
    // =========================================================================================

    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                    ExampleMod.MODID);

    public static final RegistryObject<net.minecraft.world.item.CreativeModeTab> NECRON_TAB =
            CREATIVE_MODE_TABS.register("necron_tab",
                    () -> net.minecraft.world.item.CreativeModeTab.builder()
                            .withTabsBefore(net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS)
                            .title(net.minecraft.network.chat.Component
                                    .translatable("itemGroup.firstcrusade.necron_tab"))
                            .icon(() -> NECRON_ARTEFACT.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(NECRON_STONE.get());
                                output.accept(NECRON_CONDUIT.get());
                                output.accept(NECRON_GLYPH.get());
                                output.accept(NECRON_ARTEFACT.get());
                                output.accept(NECRON_WARRIOR_SPAWN_EGG.get());
                                output.accept(NECRON_OVERLORD_SPAWN_EGG.get());
                                output.accept(NECRON_SCARAB_SPAWN_EGG.get());
                            })
                            .build());

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(FCNecrons::registerAttributes);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(NECRON_WARRIOR.get(), NecronWarriorEntity.createAttributes().build());
        event.put(NECRON_OVERLORD.get(), NecronOverlordEntity.createAttributes().build());
        event.put(NECRON_SCARAB.get(), NecronScarabEntity.createAttributes().build());
    }
}
