package com.example.examplemod.datagen;

import java.util.stream.Stream;

import com.example.examplemod.FCRegistry;

import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Entity loot tables for the Ork mobs (teeth are their themed currency, scrap their salvage).
 * The old ork_boy/ork_nob tables lived under assets/ instead of data/ and never loaded — these
 * generate into the correct data/firstcrusade/loot_tables/entities/ path. Imperial units drop
 * nothing on purpose (they are the player's own troops).
 */
public class FCEntityLootProvider extends EntityLootSubProvider {
    public FCEntityLootProvider() {
        super(FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    public void generate() {
        add(FCRegistry.ORK_BOY.get(), LootTable.lootTable()
                .withPool(pool(FCRegistry.ORK_TEETH.get(), 0.0F, 2.0F))
                .withPool(pool(FCRegistry.SCRAP_METAL.get(), 0.0F, 1.0F)));

        add(FCRegistry.ORK_NOB.get(), LootTable.lootTable()
                .withPool(pool(FCRegistry.ORK_TEETH.get(), 1.0F, 4.0F))
                .withPool(pool(FCRegistry.SCRAP_METAL.get(), 1.0F, 3.0F)));

        add(FCRegistry.GRETCHIN.get(), LootTable.lootTable()
                .withPool(pool(FCRegistry.ORK_TEETH.get(), 0.0F, 1.0F)));

        add(FCRegistry.MEGANOB.get(), LootTable.lootTable()
                .withPool(pool(FCRegistry.ORK_TEETH.get(), 2.0F, 5.0F))
                .withPool(pool(FCRegistry.SCRAP_METAL.get(), 2.0F, 4.0F)));

        add(FCRegistry.WARBOSS.get(), LootTable.lootTable()
                .withPool(pool(FCRegistry.ORK_TEETH.get(), 4.0F, 8.0F))
                .withPool(pool(FCRegistry.SCRAP_METAL.get(), 2.0F, 5.0F)));

        // Killa Kan is a walking scrap machine piloted by a grot: salvage, not teeth.
        add(FCRegistry.KILLA_KAN.get(), LootTable.lootTable()
                .withPool(pool(FCRegistry.SCRAP_METAL.get(), 3.0F, 6.0F))
                .withPool(pool(Items.IRON_INGOT, 0.0F, 2.0F)));
    }

    private static LootPool.Builder pool(ItemLike item, float minRolls, float maxRolls) {
        return LootPool.lootPool()
                .setRolls(UniformGenerator.between(minRolls, maxRolls))
                .add(LootItem.lootTableItem(item));
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return Stream.of(
                FCRegistry.ORK_BOY.get(),
                FCRegistry.ORK_NOB.get(),
                FCRegistry.GRETCHIN.get(),
                FCRegistry.MEGANOB.get(),
                FCRegistry.WARBOSS.get(),
                FCRegistry.KILLA_KAN.get());
    }
}
