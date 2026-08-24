package com.example.examplemod.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.example.examplemod.FCRegistry;
import com.example.examplemod.hive.HiveBlocks;

import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block loot tables: every First Crusade block drops itself (slabs use the vanilla slab table so
 * a double slab drops 2). getKnownBlocks enumerates both DeferredRegisters, so datagen fails
 * loudly if a future block is added without a loot table.
 */
public class FCBlockLootProvider extends BlockLootSubProvider {
    public FCBlockLootProvider() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        for (Block block : getKnownBlocks()) {
            // Blocks built with noLootTable() (e.g. the toxic_sludge liquid, which also has no item)
            // must not get a generated table — the parent validator skips them by their EMPTY id.
            if (block.getLootTable() == BuiltInLootTables.EMPTY) {
                continue;
            }
            if (block instanceof SlabBlock slab) {
                add(slab, this::createSlabItemTable);
            } else {
                dropSelf(block);
            }
        }
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        List<Block> blocks = new ArrayList<>();
        FCRegistry.BLOCKS.getEntries().stream().map(RegistryObject::get).forEach(blocks::add);
        HiveBlocks.BLOCKS.getEntries().stream().map(RegistryObject::get).forEach(blocks::add);
        com.example.examplemod.necron.FCNecrons.BLOCKS.getEntries().stream()
                .map(RegistryObject::get).forEach(blocks::add);
        return blocks;
    }
}
