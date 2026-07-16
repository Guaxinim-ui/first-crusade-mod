package com.example.examplemod.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Entry point of the First Crusade data generators ("gradlew runData"). Wires every provider:
 * blockstates + block/item models, spawn egg item models, block/entity loot tables, recipes and
 * block tags. Output lands in src/generated/resources (already a resource source set in
 * build.gradle), so the generated files must be committed for the jar to include them.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FCDataGenerators {
    private FCDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new FCBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new FCItemModelProvider(output, existingFileHelper));

        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(FCBlockLootProvider::new, LootContextParamSets.BLOCK),
                new LootTableProvider.SubProviderEntry(FCEntityLootProvider::new, LootContextParamSets.ENTITY))));
        generator.addProvider(event.includeServer(), new FCRecipeProvider(output));
        generator.addProvider(event.includeServer(), new FCBlockTagsProvider(output, lookupProvider, existingFileHelper));
    }
}
