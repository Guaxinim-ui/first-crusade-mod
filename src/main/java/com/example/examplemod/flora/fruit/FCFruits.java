package com.example.examplemod.flora.fruit;

import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Phase D: the fruit trees, their fruit, and the nodes the fruit hangs from.
 *
 * <h2>Five species, three of them borrowed</h2>
 *
 * The brief is explicit that a new silhouette does not deserve a new species. So only three woods
 * are new here — rationfruit, lumenfruit and the feed pod's canopy. The frostnut hangs from the
 * pine that already grows in the ossuary tundra, and the venom pear from the death world's own
 * bough. A player who learns one tree keeps that knowledge.
 *
 * <h2>What a fruit is worth</h2>
 *
 * Each fruit's {@link FoodProperties} is the mechanical statement of what the brief says it is for,
 * and the numbers are deliberately unexciting: this is field ration for a war, not a potion shelf.
 * The two that carry an effect carry it because the effect <i>is</i> the fruit — a lumenfruit that
 * did not light the way would just be a worse apple, and a venom pear that was safe to eat would
 * have no reason to grow on a death world.
 */
public final class FCFruits {
    private FCFruits() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    // ====================================================================================
    // The fruit
    // ====================================================================================

    /** The Imperium's field ration in its original form. Plain, filling, everywhere. */
    public static final RegistryObject<Item> RATION_FRUIT = ITEMS.register("ration_fruit",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(0.3F).build())));

    /**
     * Animal feed, not people food. Edible in the way straw is edible: it fills you and it is
     * unpleasant, which is the honest way to say "this is for the Grox" before the Grox exists.
     */
    public static final RegistryObject<Item> GROX_FEED_POD = ITEMS.register("grox_feed_pod",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2).saturationMod(0.1F)
                    .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 100, 0), 0.4F)
                    .build())));

    /** Eaten, it keeps glowing for a while. That is the whole point of it. */
    public static final RegistryObject<Item> LUMENFRUIT = ITEMS.register("lumenfruit",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(3).saturationMod(0.3F)
                    .effect(() -> new MobEffectInstance(MobEffects.GLOWING, 400, 0), 1.0F)
                    .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0), 0.35F)
                    .build())));

    /** Oily and dense; the reason anything survives a tundra winter. */
    public static final RegistryObject<Item> FROSTNUT = ITEMS.register("frostnut",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(3).saturationMod(0.8F).fast().build())));

    /**
     * Poisonous raw, and that is not a warning label — it is the item's function. It exists to be
     * an ingredient, and eating one is the game telling you so.
     */
    public static final RegistryObject<Item> VENOM_PEAR = ITEMS.register("venom_pear",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2).saturationMod(0.1F).alwaysEat()
                    .effect(() -> new MobEffectInstance(MobEffects.POISON, 200, 1), 1.0F)
                    .build())));

    // ====================================================================================
    // The nodes the fruit hangs from
    // ====================================================================================

    public static final RegistryObject<Block> RATIONFRUIT_NODE = registerNode("rationfruit_node",
            MapColor.COLOR_ORANGE, RATION_FRUIT, 1, 2, 0);

    public static final RegistryObject<Block> FEED_POD_NODE = registerNode("feed_pod_node",
            MapColor.TERRACOTTA_YELLOW, GROX_FEED_POD, 1, 3, 0);

    public static final RegistryObject<Block> LUMENFRUIT_NODE = registerNode("lumenfruit_node",
            MapColor.COLOR_LIGHT_BLUE, LUMENFRUIT, 1, 2, 8);

    public static final RegistryObject<Block> FROSTNUT_NODE = registerNode("frostnut_node",
            MapColor.TERRACOTTA_WHITE, FROSTNUT, 1, 2, 0);

    public static final RegistryObject<Block> VENOM_PEAR_NODE = registerNode("venom_pear_node",
            MapColor.COLOR_PURPLE, VENOM_PEAR, 1, 2, 0);

    // ====================================================================================
    // The three new woods
    // ====================================================================================

    public static final RegistryObject<Block> RATIONFRUIT_LOG = registerBlock("rationfruit_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_BROWN)));

    public static final RegistryObject<Block> RATIONFRUIT_LEAVES = registerBlock("rationfruit_leaves",
            () -> new LeavesBlock(leaves(MapColor.PLANT)));

    public static final RegistryObject<Block> LUMENFRUIT_LOG = registerBlock("lumenfruit_log",
            () -> new RotatedPillarBlock(log(MapColor.TERRACOTTA_BROWN)));

    /** Faintly lit even without fruit: the tree itself is bioluminescent, not just the crop. */
    public static final RegistryObject<Block> LUMENFRUIT_LEAVES = registerBlock("lumenfruit_leaves",
            () -> new LeavesBlock(leaves(MapColor.COLOR_CYAN).lightLevel(state -> 4)));

    /**
     * The feed pod shares the orchard trunk. Two planted trees in the same Agri world should look
     * like the same forestry programme, because that is what they are.
     */
    public static final RegistryObject<Block> FEED_POD_LEAVES = registerBlock("feed_pod_leaves",
            () -> new LeavesBlock(leaves(MapColor.TERRACOTTA_YELLOW)));

    /** The death world's own bough carries the pear; only the canopy differs. */
    public static final RegistryObject<Block> VENOM_PEAR_LEAVES = registerBlock("venom_pear_leaves",
            () -> new LeavesBlock(leaves(MapColor.COLOR_PURPLE)));

    // ====================================================================================
    // Property presets — kept identical to FCFloraTrees so the woods behave alike
    // ====================================================================================

    private static BlockBehaviour.Properties log(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties leaves(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(0.2F)
                .randomTicks()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .isValidSpawn(FCFruits::never)
                .isSuffocating(FCFruits::never)
                .isViewBlocking(FCFruits::never)
                .pushReaction(PushReaction.DESTROY)
                .ignitedByLava();
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos,
                                 net.minecraft.world.entity.EntityType<?> type) {
        return false;
    }

    // ====================================================================================

    private static RegistryObject<Block> registerNode(String name, MapColor color,
                                                      RegistryObject<Item> fruit,
                                                      int minYield, int maxYield, int light) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(color)
                .noCollission()
                .instabreak()
                .randomTicks()
                .sound(SoundType.GRASS)
                .pushReaction(PushReaction.DESTROY)
                .ignitedByLava();

        if (light > 0) {
            // Only a ripe node is worth lighting; an unripe one glowing would give away the harvest
            // from across the marsh.
            properties = properties.lightLevel(
                    state -> state.getValue(FruitNodeBlock.AGE) >= FruitNodeBlock.RIPE ? light : 0);
        }

        BlockBehaviour.Properties finished = properties;

        // The node's item form is the fruit, so no BlockItem: a node in the inventory would be a
        // second, confusing way to hold a pear.
        return BLOCKS.register(name, () -> new FruitNodeBlock(finished, fruit::get, minYield, maxYield));
    }

    private static RegistryObject<Block> registerBlock(String name, Supplier<Block> block) {
        RegistryObject<Block> registered = BLOCKS.register(name, block);
        ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    /** Called once from the ExampleMod constructor, after {@code FCFloraTrees.register}. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    /** Everything in this class, appended to the existing flora tab. */
    public static void addToCreativeTab(net.minecraft.world.item.CreativeModeTab.Output output) {
        output.accept(RATION_FRUIT.get());
        output.accept(GROX_FEED_POD.get());
        output.accept(LUMENFRUIT.get());
        output.accept(FROSTNUT.get());
        output.accept(VENOM_PEAR.get());
        output.accept(RATIONFRUIT_LOG.get());
        output.accept(RATIONFRUIT_LEAVES.get());
        output.accept(LUMENFRUIT_LOG.get());
        output.accept(LUMENFRUIT_LEAVES.get());
        output.accept(FEED_POD_LEAVES.get());
        output.accept(VENOM_PEAR_LEAVES.get());
    }
}
