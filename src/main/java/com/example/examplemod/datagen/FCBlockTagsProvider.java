package com.example.examplemod.datagen;

import java.util.concurrent.CompletableFuture;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;
import com.example.examplemod.flora.tree.FCFloraTrees;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Block tags. The mineable/* tags are what make the blocks actually harvestable: several blocks
 * use requiresCorrectToolForDrops(), and without a mineable tag they break slowly and drop
 * nothing. No needs_*_tool tier tag on purpose — any pickaxe/axe tier works.
 */
public class FCBlockTagsProvider extends BlockTagsProvider {
    public FCBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                               @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, ExampleMod.MODID, existingFileHelper);
    }

    /**
     * Every log-shaped block of the mod's trees. Kept as one array because four separate tags
     * ({@code mineable/axe}, {@code logs_that_burn}, and the two below) all need exactly this list,
     * and a species added to only three of them is the kind of omission nothing reports.
     *
     * <p>{@code fossilized_trunk} is deliberately absent: it is stone, so it belongs to the pickaxe
     * list and must never enter {@code logs_that_burn}.
     */
    private static net.minecraft.world.level.block.Block[] logs() {
        return new net.minecraft.world.level.block.Block[]{
                FCFloraTrees.IMPERIAL_PINE_LOG.get(),
                FCFloraTrees.BLIGHTED_OAK_LOG.get(),
                FCFloraTrees.ASH_SNAG_LOG.get(),
                FCFloraTrees.CHARRED_SNAG_LOG.get(),
                FCFloraTrees.ORK_FUNGAL_STALK.get(),
                FCFloraTrees.VENOM_BOUGH_LOG.get(),
                FCFloraTrees.ORCHARD_LOG.get(),
                FCFloraTrees.WARPED_BOUGH_LOG.get(),
                FCFloraTrees.IRONWOOD_LOG.get(),
                FCFloraTrees.RESIN_IRONWOOD_LOG.get(),
                FCFloraTrees.SUMP_MANGROVE_LOG.get(),
                FCFloraTrees.TOXIC_WILLOW_LOG.get(),
                FCFloraTrees.FROSTNUT_PINE_LOG.get(),
                FCFloraTrees.SALT_THORN_LOG.get(),
                com.example.examplemod.flora.fruit.FCFruits.RATIONFRUIT_LOG.get(),
                com.example.examplemod.flora.fruit.FCFruits.LUMENFRUIT_LOG.get(),
        };
    }

    private static net.minecraft.world.level.block.Block[] canopies() {
        return new net.minecraft.world.level.block.Block[]{
                FCFloraTrees.IMPERIAL_PINE_LEAVES.get(),
                FCFloraTrees.BLIGHTED_OAK_LEAVES.get(),
                FCFloraTrees.ORK_FUNGAL_CAP.get(),
                FCFloraTrees.VENOM_BOUGH_LEAVES.get(),
                FCFloraTrees.ORCHARD_LEAVES.get(),
                FCFloraTrees.WARPED_BOUGH_LEAVES.get(),
                FCFloraTrees.IRONWOOD_LEAVES.get(),
                FCFloraTrees.SUMP_MANGROVE_LEAVES.get(),
                FCFloraTrees.TOXIC_WILLOW_LEAVES.get(),
                FCFloraTrees.FROSTNUT_PINE_LEAVES.get(),
                com.example.examplemod.flora.fruit.FCFruits.RATIONFRUIT_LEAVES.get(),
                com.example.examplemod.flora.fruit.FCFruits.LUMENFRUIT_LEAVES.get(),
                com.example.examplemod.flora.fruit.FCFruits.FEED_POD_LEAVES.get(),
                com.example.examplemod.flora.fruit.FCFruits.VENOM_PEAR_LEAVES.get(),
        };
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Only the functional FCRegistry blocks remain (the decorative Hive block set was removed).
        // The toxic-sludge liquid and the structure-marker blocks need no mineable tag (liquid /
        // instabreak). The new decorative kit provides its own tags.
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                FCRegistry.IMPERIAL_COMMAND_CORE.get(),
                FCRegistry.IMPERIAL_MINE.get(),
                FCRegistry.IMPERIAL_GOLD_MINE.get(),
                FCRegistry.IMPERIAL_SCRAP_YARD.get(),
                FCRegistry.IMPERIAL_FORGE.get(),
                FCRegistry.IMPERIAL_PROMETHIUM_REFINERY.get(),
                FCRegistry.IMPERIAL_BARRACKS.get(),
                FCRegistry.IMPERIAL_HABITATION.get(),
                FCRegistry.SPACEPORT.get(),
                FCRegistry.PLANETARY_NAVIGATION_TERMINAL.get(),
                FCRegistry.WAR_TABLE.get(),
                FCRegistry.ORK_CAMP.get(),
                // Petrified wood, so a pickaxe rather than an axe — and it must stay out of
                // logs_that_burn, which is why it is not in logs().
                FCFloraTrees.FOSSILIZED_TRUNK.get());

        tag(BlockTags.MINEABLE_WITH_AXE).add(
                FCRegistry.IMPERIAL_EMERALD_TRADE_DEPOT.get(),
                FCRegistry.STRATEGIUM.get()).add(logs());

        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(
                FCRegistry.ORK_LOOT_PIT.get(),
                com.example.examplemod.flora.FCFloraGround.SUMP_MUD.get(),
                com.example.examplemod.flora.FCFloraGround.SALT_CRUST.get());

        tag(BlockTags.MINEABLE_WITH_HOE).add(
                FCRegistry.IMPERIAL_FARM.get()).add(canopies());

        // Os troncos entram por LOGS_THAT_BURN, que e o que vanilla agrega dentro de
        // minecraft:logs. Sem isso, WorldGenPlacement.clearVegetation nao reconheceria uma
        // arvore do mod ao limpar o terreno de um assentamento.
        tag(BlockTags.LOGS_THAT_BURN).add(logs());

        tag(BlockTags.LEAVES).add(canopies());

        // Os dois solos proprios entram em dirt para que qualquer feature ou mecanica vanilla que
        // pergunte "isto e terra?" continue funcionando na marsh e no ermo de sal.
        tag(BlockTags.DIRT).add(
                com.example.examplemod.flora.FCFloraGround.SUMP_MUD.get(),
                com.example.examplemod.flora.FCFloraGround.SALT_CRUST.get());
    }
}
