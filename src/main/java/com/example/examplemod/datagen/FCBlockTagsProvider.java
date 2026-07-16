package com.example.examplemod.datagen;

import java.util.concurrent.CompletableFuture;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;
import com.example.examplemod.hive.HiveBlocks;
import com.example.examplemod.hive.HiveTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;
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
                FCRegistry.STRATEGIUM.get(),
                // Hive wooden furniture/crates (FASE 6.5).
                HiveBlocks.SUPPLY_CRATE.get(),
                HiveBlocks.SHELF_UNIT.get(),
                HiveBlocks.HIVE_RUG.get());

        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(
                FCRegistry.ORK_LOOT_PIT.get());

        tag(BlockTags.MINEABLE_WITH_HOE).add(
                FCRegistry.IMPERIAL_FARM.get());

        // --- Hive City set: pickaxe-mined by default. The toxic_sludge liquid (no item) and the
        // three axe-mined furniture blocks are the only exceptions. ---
        Block toxicSludge = HiveBlocks.TOXIC_SLUDGE_BLOCK.get();
        Block supplyCrate = HiveBlocks.SUPPLY_CRATE.get();
        Block shelfUnit = HiveBlocks.SHELF_UNIT.get();
        Block hiveRug = HiveBlocks.HIVE_RUG.get();
        for (RegistryObject<Block> block : HiveBlocks.BLOCKS.getEntries()) {
            Block b = block.get();
            if (b == toxicSludge || b == supplyCrate || b == shelfUnit || b == hiveRug) {
                continue;
            }
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b);
        }

        // Ashcrete/steel/gothic families need at least stone tier; the armored plating, iron.
        tag(BlockTags.NEEDS_STONE_TOOL).add(
                HiveBlocks.REINFORCED_ASHCRETE.get(),
                HiveBlocks.CRACKED_REINFORCED_ASHCRETE.get(),
                HiveBlocks.REINFORCED_ASHCRETE_STAIRS.get(),
                HiveBlocks.REINFORCED_ASHCRETE_SLAB.get(),
                HiveBlocks.REINFORCED_ASHCRETE_WALL.get(),
                HiveBlocks.RIVETED_STEEL_BLOCK.get(),
                HiveBlocks.RUSTED_RIVETED_STEEL.get(),
                HiveBlocks.RIVETED_STEEL_STAIRS.get(),
                HiveBlocks.RIVETED_STEEL_SLAB.get(),
                HiveBlocks.MACHINE_CASING.get(),
                HiveBlocks.CATHEDRAL_WALL.get(),
                HiveBlocks.GOTHIC_ARCH.get(),
                HiveBlocks.IMPERIAL_COLUMN.get(),
                HiveBlocks.SKULL_WALL_RELIEF.get(),
                HiveBlocks.AQUILA_WALL_RELIEF.get());

        tag(BlockTags.NEEDS_IRON_TOOL).add(
                HiveBlocks.ARMORED_HIVE_PLATING.get());

        // Wall connectivity (vanilla walls tag) and pipe arm auto-connection.
        tag(BlockTags.WALLS).add(
                HiveBlocks.REINFORCED_ASHCRETE_WALL.get());

        tag(HiveTags.PIPE_CONNECTABLE).add(
                HiveBlocks.LARGE_HIVE_PIPE.get(),
                HiveBlocks.PIPE_JUNCTION.get(),
                HiveBlocks.PRESSURE_VALVE.get(),
                HiveBlocks.MACHINE_CASING.get(),
                HiveBlocks.INDUSTRIAL_VENT.get(),
                // FASE 6: manufactorum machines and the wider pipes also grow pipe arms.
                HiveBlocks.BOILER_TANK.get(),
                HiveBlocks.INDUSTRIAL_TURBINE.get(),
                HiveBlocks.VENTILATION_DUCT.get(),
                HiveBlocks.COOLANT_TANK.get(),
                HiveBlocks.HUGE_HIVE_PIPE.get(),
                HiveBlocks.MAIN_PIPE_TRUNK.get());
    }
}
