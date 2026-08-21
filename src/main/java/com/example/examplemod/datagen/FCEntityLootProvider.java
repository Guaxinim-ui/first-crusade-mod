package com.example.examplemod.datagen;

import java.util.stream.Stream;

import com.example.examplemod.FCRegistry;
import com.example.examplemod.animal.FCAnimals;

import net.minecraft.advancements.critereon.EntityFlagsPredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootingEnchantFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SmeltItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithLootingCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Entity loot tables for the Ork mobs (teeth are their themed currency, scrap their salvage).
 * The old ork_boy/ork_nob tables lived under assets/ instead of data/ and never loaded — these
 * generate into the correct data/firstcrusade/loot_tables/entities/ path. Imperial units drop
 * nothing on purpose (they are the player's own troops).
 *
 * <p>Phase E adds the wildlife, which drops the way vanilla livestock does: a count roll modified by
 * Looting, meat that comes out cooked if the animal died burning, and one rare part that only an
 * adult carries.
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

        // Grox: the reason the Imperium farms them. Meat to eat, hide to work, and the horn a
        // grown animal carries — the one part worth hunting a full-sized bull for.
        add(FCAnimals.GROX.get(), LootTable.lootTable()
                .withPool(drop(FCAnimals.GROX_MEAT.get(), 2.0F, 4.0F)
                        .apply(SmeltItemFunction.smelted().when(
                                LootItemEntityPropertyCondition.hasProperties(
                                        LootContext.EntityTarget.THIS, ENTITY_ON_FIRE))))
                .withPool(drop(FCAnimals.GROX_HIDE.get(), 1.0F, 3.0F))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(FCAnimals.GROX_HORN.get()))
                        .when(ADULT_ONLY)
                        .when(LootItemRandomChanceWithLootingCondition
                                .randomChanceAndLootingBoost(0.35F, 0.1F))));

        // Cyber-mastiff: mais implante que animal, entao o que sobra dele é sucata. Sem carne —
        // ninguém come o cão dos Arbites, e um drop de comida aqui seria o item dizendo o
        // contrário do que a espécie é.
        add(FCAnimals.CYBER_MASTIFF.get(), LootTable.lootTable()
                .withPool(drop(FCRegistry.SCRAP_METAL.get(), 1.0F, 2.0F)));

        // Squig e rato dividem o mesmo drop: dois itens idênticos com nomes diferentes seriam
        // ruído de inventário, não profundidade.
        add(FCAnimals.SQUIG.get(), LootTable.lootTable()
                .withPool(drop(FCAnimals.SCAVENGED_MEAT.get(), 1.0F, 2.0F)
                        .apply(SmeltItemFunction.smelted().when(
                                LootItemEntityPropertyCondition.hasProperties(
                                        LootContext.EntityTarget.THIS, ENTITY_ON_FIRE)))));

        add(FCAnimals.SUMP_RAT.get(), LootTable.lootTable()
                .withPool(drop(FCAnimals.SCAVENGED_MEAT.get(), 0.0F, 1.0F)
                        .apply(SmeltItemFunction.smelted().when(
                                LootItemEntityPropertyCondition.hasProperties(
                                        LootContext.EntityTarget.THIS, ENTITY_ON_FIRE)))));

        // Strider: pouca carne num corpo que é quase só perna, e as placas que o mantêm de pé.
        add(FCAnimals.ASH_STRIDER.get(), LootTable.lootTable()
                .withPool(drop(FCAnimals.CHITIN_PLATE.get(), 1.0F, 2.0F))
                .withPool(drop(FCAnimals.SCAVENGED_MEAT.get(), 0.0F, 2.0F)));

        // Ambull: a razão para tê-lo enfrentado. O melhor rendimento de quitina do mod, e é
        // suposto ser — matar um é uma decisão cara.
        add(FCAnimals.AMBULL.get(), LootTable.lootTable()
                .withPool(drop(FCAnimals.CHITIN_PLATE.get(), 4.0F, 7.0F))
                .withPool(drop(FCAnimals.SCAVENGED_MEAT.get(), 2.0F, 4.0F))
                // O Ambull ganhou trofeu junto com a fauna nova: e uma das cinco especies que o
                // briefing lista, e a unica delas que ja existia.
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(
                                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry
                                        .TROPHY_AMBULL.get()))
                        .when(LootItemRandomChanceWithLootingCondition
                                .randomChanceAndLootingBoost(0.25F, 0.08F))));

        // As nove especies da integracao dos modelos do Blockbench. As tabelas delas vivem em
        // FaunaLootTables — quinze especies dentro deste arquivo o tornariam ilegivel, e "o que cai
        // de um Duskhorn" e decisao de design, nao de datagen.
        com.example.examplemod.fauna.FaunaLootTables.register(this::add);
    }

    /** A calf has no horn yet; the drop should say so rather than let one appear from nowhere. */
    private static final LootItemEntityPropertyCondition.Builder ADULT_ONLY =
            LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS,
                    EntityPredicate.Builder.entity().flags(
                            EntityFlagsPredicate.Builder.flags().setIsBaby(false).build()));

    private static LootPool.Builder pool(ItemLike item, float minRolls, float maxRolls) {
        return LootPool.lootPool()
                .setRolls(UniformGenerator.between(minRolls, maxRolls))
                .add(LootItem.lootTableItem(item));
    }

    /**
     * One roll, a variable stack, and a Looting bonus — the vanilla livestock shape.
     *
     * <p>Different from {@link #pool} above, which rolls a variable number of single items. For an
     * animal the stack size is what a player reads as "how much meat is on this thing", so it
     * belongs in the count rather than in the roll count.
     */
    private static LootPool.Builder drop(ItemLike item, float min, float max) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))
                        .apply(LootingEnchantFunction.lootingMultiplier(
                                UniformGenerator.between(0.0F, 1.0F))));
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return Stream.of(
                FCRegistry.ORK_BOY.get(),
                FCRegistry.ORK_NOB.get(),
                FCRegistry.GRETCHIN.get(),
                FCRegistry.MEGANOB.get(),
                FCRegistry.WARBOSS.get(),
                FCRegistry.KILLA_KAN.get(),
                FCAnimals.GROX.get(),
                FCAnimals.CYBER_MASTIFF.get(),
                FCAnimals.SQUIG.get(),
                FCAnimals.SUMP_RAT.get(),
                FCAnimals.ASH_STRIDER.get(),
                FCAnimals.AMBULL.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.FENRISIAN_WOLF.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.DUNESKUTTLER.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.DUSTBACK_HELAMITE.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.CTHELLEAN_CUDBEAR.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.DUSKHORN.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.KNARLOC.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.CONSTRICTOR.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.BARKING_TOAD.get(),
                com.example.examplemod.fauna.FirstCrusadeFaunaRegistry.CATACHAN_DEVIL.get());
    }
}
