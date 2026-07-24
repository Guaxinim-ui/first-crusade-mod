package com.example.examplemod.datagen;

import java.util.concurrent.CompletableFuture;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;

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
                FCRegistry.ORK_CAMP.get());

        tag(BlockTags.MINEABLE_WITH_AXE).add(
                FCRegistry.IMPERIAL_EMERALD_TRADE_DEPOT.get(),
                FCRegistry.STRATEGIUM.get());

        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(
                FCRegistry.ORK_LOOT_PIT.get());

        tag(BlockTags.MINEABLE_WITH_HOE).add(
                FCRegistry.IMPERIAL_FARM.get());
    }
}
