package com.example.examplemod.flora.tree;

import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.BlockPos;
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
 * Phase 3: the trees of the First Crusade — six species, each a trunk and (usually) a canopy.
 *
 * <p>A self-contained registry in the same shape as {@code FCFlora} and {@code HiveEssentialBlocks}:
 * its own DeferredRegisters, one {@link #register(IEventBus)} call from {@code ExampleMod}, and no
 * existing ID moved. The blocks appear in the existing {@code flora_tab} rather than a new creative
 * tab, because a tree is vegetation and splitting the tab would only make it harder to find them.
 *
 * <h2>What a species is</h2>
 *
 * A log plus a foliage block, and nothing else. There are deliberately no saplings, no growth
 * mechanics and no block entities: these trees are placed by the chunk decorator as part of a
 * region's identity, and a sapling would imply a planting-and-growing system this phase does not
 * have. Their <i>shape</i> lives entirely in {@link FCTrees}.
 *
 * <p>{@code ash_snag} is the exception with no foliage at all — it is a dead trunk, which is the
 * whole point of it. Forge worlds and old battlefields grow standing deadwood, not canopy.
 *
 * <h2>Leaves behave like vanilla leaves</h2>
 *
 * {@link LeavesBlock} brings the distance/persistent properties and the decay rules with it, so a
 * player felling a trunk sees the canopy rot away exactly as they expect. The datapack tags put
 * every log in {@code minecraft:logs} and every canopy in {@code minecraft:leaves}, which is also
 * what lets {@code WorldGenPlacement.clearVegetation} sweep a First Crusade forest out of a
 * settlement footprint without knowing these blocks exist.
 */
public final class FCFloraTrees {
    private FCFloraTrees() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    // ====================================================================================
    // Imperial pine — the gaunt, near-black conifer of Imperial and neutral ground
    // ====================================================================================

    public static final RegistryObject<Block> IMPERIAL_PINE_LOG = register("imperial_pine_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_BROWN)));

    public static final RegistryObject<Block> IMPERIAL_PINE_LEAVES = register("imperial_pine_leaves",
            () -> new LeavesBlock(leaves(MapColor.PLANT)));

    // ====================================================================================
    // Blighted oak — the broadleaf that stops a wood being all conifer
    // ====================================================================================

    public static final RegistryObject<Block> BLIGHTED_OAK_LOG = register("blighted_oak_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_BROWN)));

    public static final RegistryObject<Block> BLIGHTED_OAK_LEAVES = register("blighted_oak_leaves",
            () -> new LeavesBlock(leaves(MapColor.PLANT)));

    // ====================================================================================
    // Ash snag — standing deadwood. No canopy, on purpose.
    // ====================================================================================

    public static final RegistryObject<Block> ASH_SNAG_LOG = register("ash_snag_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_GRAY)));

    /** The blacker deadwood of the ash wastes: burnt through rather than merely dead. */
    public static final RegistryObject<Block> CHARRED_SNAG_LOG = register("charred_snag_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_BLACK)));

    // ====================================================================================
    // Ork fungal tower — not a tree at all, biologically. It behaves like one.
    // ====================================================================================

    public static final RegistryObject<Block> ORK_FUNGAL_STALK = register("ork_fungal_stalk",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_GREEN).sound(SoundType.STEM)));

    public static final RegistryObject<Block> ORK_FUNGAL_CAP = register("ork_fungal_cap",
            () -> new LeavesBlock(leaves(MapColor.COLOR_GREEN).sound(SoundType.WART_BLOCK)));

    // ====================================================================================
    // Venom bough — the broad, layered canopy of a death world
    // ====================================================================================

    public static final RegistryObject<Block> VENOM_BOUGH_LOG = register("venom_bough_log",
            () -> new RotatedPillarBlock(log(MapColor.TERRACOTTA_BROWN)));

    public static final RegistryObject<Block> VENOM_BOUGH_LEAVES = register("venom_bough_leaves",
            () -> new LeavesBlock(leaves(MapColor.COLOR_GREEN)));

    // ====================================================================================
    // Orchard — the only tree in the mod anybody planted on purpose
    // ====================================================================================

    public static final RegistryObject<Block> ORCHARD_LOG = register("orchard_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_BROWN)));

    public static final RegistryObject<Block> ORCHARD_LEAVES = register("orchard_leaves",
            () -> new LeavesBlock(leaves(MapColor.PLANT)));

    // ====================================================================================
    // Warped bough — a tree that has stopped growing the way trees grow
    // ====================================================================================

    public static final RegistryObject<Block> WARPED_BOUGH_LOG = register("warped_bough_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_PURPLE)));

    public static final RegistryObject<Block> WARPED_BOUGH_LEAVES = register("warped_bough_leaves",
            () -> new LeavesBlock(leaves(MapColor.COLOR_PURPLE)));

    // ====================================================================================
    // Ironwood — the hardwood of the cold forests. Two trunks, one canopy.
    // ====================================================================================

    /**
     * Notably harder than the other logs (strength 3.4 against 2.0). That number is the whole
     * character of the species: an ironwood stand is slow to fell, which is what makes the biome
     * feel like old growth rather than a wood you clear in a minute.
     */
    public static final RegistryObject<Block> IRONWOOD_LOG = register("ironwood_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_GRAY).strength(3.4F)));

    /**
     * The same species where the sap has run and set. A separate trunk block rather than a separate
     * tree: it shares {@link #IRONWOOD_LEAVES}, so a resin stand is visibly the same forest.
     */
    public static final RegistryObject<Block> RESIN_IRONWOOD_LOG = register("resin_ironwood_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_ORANGE).strength(3.4F)));

    public static final RegistryObject<Block> IRONWOOD_LEAVES = register("ironwood_leaves",
            () -> new LeavesBlock(leaves(MapColor.COLOR_BLUE)));

    // ====================================================================================
    // Sump mangrove and toxic willow — the two silhouettes of a marsh
    // ====================================================================================

    public static final RegistryObject<Block> SUMP_MANGROVE_LOG = register("sump_mangrove_log",
            () -> new RotatedPillarBlock(log(MapColor.TERRACOTTA_BROWN)));

    public static final RegistryObject<Block> SUMP_MANGROVE_LEAVES = register("sump_mangrove_leaves",
            () -> new LeavesBlock(leaves(MapColor.TERRACOTTA_GREEN)));

    public static final RegistryObject<Block> TOXIC_WILLOW_LOG = register("toxic_willow_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_BROWN)));

    public static final RegistryObject<Block> TOXIC_WILLOW_LEAVES = register("toxic_willow_leaves",
            () -> new LeavesBlock(leaves(MapColor.COLOR_LIGHT_GREEN)));

    // ====================================================================================
    // Frostnut pine — the one tree that survives the ossuary tundra
    // ====================================================================================

    public static final RegistryObject<Block> FROSTNUT_PINE_LOG = register("frostnut_pine_log",
            () -> new RotatedPillarBlock(log(MapColor.COLOR_BLACK)));

    public static final RegistryObject<Block> FROSTNUT_PINE_LEAVES = register("frostnut_pine_leaves",
            () -> new LeavesBlock(leaves(MapColor.TERRACOTTA_CYAN)));

    // ====================================================================================
    // The dead wood of the salt waste. No canopy for either, on purpose.
    // ====================================================================================

    public static final RegistryObject<Block> SALT_THORN_LOG = register("salt_thorn_log",
            () -> new RotatedPillarBlock(log(MapColor.TERRACOTTA_WHITE)));

    /**
     * A trunk that has turned to stone. It takes the stone sound and pickaxe-grade hardness, because
     * calling it wood and then letting an axe drop it in three hits would undercut the only thing
     * it is for: being visibly older than everything else in the landscape.
     */
    public static final RegistryObject<Block> FOSSILIZED_TRUNK = register("fossilized_trunk",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.2F)
                    .sound(SoundType.STONE)));

    // ====================================================================================
    // Property presets
    // ====================================================================================

    private static BlockBehaviour.Properties log(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    /**
     * Vanilla leaf behaviour, copied deliberately rather than approximated: the occlusion and
     * suffocation predicates below are what stop leaves from darkening the world and from
     * smothering a mob that walks into them.
     */
    private static BlockBehaviour.Properties leaves(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(0.2F)
                .randomTicks()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .isValidSpawn(FCFloraTrees::never)
                .isSuffocating(FCFloraTrees::never)
                .isViewBlocking(FCFloraTrees::never)
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

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> registered = BLOCKS.register(name, block);
        ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    /** Called once from the ExampleMod constructor, after {@code FCFlora.register}. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
