package com.example.examplemod.fauna;

import com.example.examplemod.animal.FCAnimals;

import net.minecraft.advancements.critereon.EntityFlagsPredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.world.level.ItemLike;
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
 * As tabelas de loot da fauna, montadas aqui e emitidas pelo datagen.
 *
 * <h2>Por que uma classe propria e nao mais linhas no provider</h2>
 *
 * {@code FCEntityLootProvider} e o dono do arquivo gerado, e continua sendo — ele chama
 * {@link #register}. Mas a decisao "o que cai de um Duskhorn" e de design, nao de datagen, e quinze
 * especies dentro do provider fariam dele um arquivo em que ninguem acha nada. Aqui as quinze estao
 * lado a lado e da para ler a economia inteira da fauna de uma vez.
 *
 * <h2>As tres regras que organizam a tabela toda</h2>
 *
 * <ol>
 *   <li><b>Nada exagerado.</b> Duas ou tres linhas por bicho. Um predador apex que solta oito itens
 *       diferentes vira uma maquina de recursos, e o encontro deixa de ser sobre o encontro.</li>
 *   <li><b>Carne cozinha se o bicho morreu queimando</b> — {@code SmeltItemFunction}, a forma vanilla.
 *       De graca, e o jogador nota.</li>
 *   <li><b>Trofeu e raro e so em apex.</b> Cinco especies tem trofeu (as cinco que o briefing lista) e
 *       a chance e baixa. Trofeu que cai sempre nao e trofeu, e componente.</li>
 * </ol>
 */
public final class FaunaLootTables {

    private FaunaLootTables() {
    }

    /** Chance base de trofeu, e o bonus por nivel de Looting. */
    private static final float TROPHY_CHANCE = 0.25F;
    private static final float TROPHY_LOOTING_BONUS = 0.08F;

    /** O que o registrador precisa saber fazer. Uma interface para nao acoplar ao provider. */
    public interface Sink {
        void accept(net.minecraft.world.entity.EntityType<?> type, LootTable.Builder table);
    }

    /**
     * Emite as nove tabelas da fauna nova.
     *
     * <p>As seis da Fase E continuam em {@code FCEntityLootProvider}, onde sempre estiveram: os
     * arquivos gerados delas ja existem em disco e mudar de dono aqui geraria diff sem ganho.
     */
    public static void register(Sink sink) {
        // ------------------------------------------------------------ lobo de Fenris
        // Pele e presa. O briefing pede exatamente estes dois, e dois basta: o lobo e comum o
        // suficiente para que um terceiro drop inundasse o inventario.
        sink.accept(FirstCrusadeFaunaRegistry.FENRISIAN_WOLF.get(), LootTable.lootTable()
                .withPool(drop(FirstCrusadeFaunaRegistry.THICK_PELT.get(), 1.0F, 2.0F))
                .withPool(drop(FirstCrusadeFaunaRegistry.BEAST_FANG.get(), 0.0F, 2.0F)));

        // ------------------------------------------------------------ Duneskuttler
        // So placa de carapaca, como pedido. Sem carne: nada em Armageddon come artropode de cinza.
        sink.accept(FirstCrusadeFaunaRegistry.DUNESKUTTLER.get(), LootTable.lootTable()
                .withPool(drop(FirstCrusadeFaunaRegistry.HEAVY_CARAPACE.get(), 1.0F, 3.0F))
                .withPool(drop(FCAnimals.CHITIN_PLATE.get(), 1.0F, 2.0F)));

        // ------------------------------------------------------------ Helamite
        sink.accept(FirstCrusadeFaunaRegistry.DUSTBACK_HELAMITE.get(), LootTable.lootTable()
                .withPool(cookable(FirstCrusadeFaunaRegistry.GAME_MEAT.get(), 1.0F, 3.0F))
                .withPool(drop(FirstCrusadeFaunaRegistry.BEAST_HIDE.get(), 1.0F, 2.0F)));

        // ------------------------------------------------------------ Cudbear
        // Pele, garras, e o trofeu de um predador que o jogador escolheu enfrentar.
        sink.accept(FirstCrusadeFaunaRegistry.CTHELLEAN_CUDBEAR.get(), LootTable.lootTable()
                .withPool(drop(FirstCrusadeFaunaRegistry.THICK_PELT.get(), 2.0F, 3.0F))
                .withPool(drop(FirstCrusadeFaunaRegistry.BEAST_FANG.get(), 1.0F, 3.0F))
                .withPool(cookable(FirstCrusadeFaunaRegistry.GAME_MEAT.get(), 1.0F, 3.0F))
                .withPool(trophy(FirstCrusadeFaunaRegistry.TROPHY_CUDBEAR.get())));

        // ------------------------------------------------------------ Duskhorn
        // O maior rendimento de couro do mod, e e para ser: sao duas toneladas de bicho.
        sink.accept(FirstCrusadeFaunaRegistry.DUSKHORN.get(), LootTable.lootTable()
                .withPool(drop(FirstCrusadeFaunaRegistry.BEAST_HIDE.get(), 3.0F, 6.0F))
                .withPool(cookable(FirstCrusadeFaunaRegistry.GAME_MEAT.get(), 2.0F, 5.0F))
                .withPool(adultOnly(FirstCrusadeFaunaRegistry.DUSKHORN_HORN.get(), 0.6F))
                .withPool(trophy(FirstCrusadeFaunaRegistry.TROPHY_DUSKHORN.get())));

        // ------------------------------------------------------------ Knarloc
        sink.accept(FirstCrusadeFaunaRegistry.KNARLOC.get(), LootTable.lootTable()
                .withPool(drop(FirstCrusadeFaunaRegistry.BEAST_HIDE.get(), 2.0F, 4.0F))
                .withPool(drop(FirstCrusadeFaunaRegistry.KNARLOC_QUILL.get(), 1.0F, 4.0F))
                .withPool(cookable(FirstCrusadeFaunaRegistry.GAME_MEAT.get(), 1.0F, 3.0F)));

        // ------------------------------------------------------------ Constrictor
        sink.accept(FirstCrusadeFaunaRegistry.CONSTRICTOR.get(), LootTable.lootTable()
                .withPool(drop(FirstCrusadeFaunaRegistry.BEAST_HIDE.get(), 2.0F, 4.0F))
                .withPool(drop(FirstCrusadeFaunaRegistry.SERPENT_SCALE.get(), 3.0F, 6.0F))
                .withPool(trophy(FirstCrusadeFaunaRegistry.TROPHY_CONSTRICTOR.get())));

        // ------------------------------------------------------------ Barking Toad
        // Quase nada. Matar um Barking Toad nao e uma fonte de recursos, e uma sobrevivencia — e a
        // tabela tem de dizer isso, senao o jogador aprende a farmar o bicho que devia evitar.
        sink.accept(FirstCrusadeFaunaRegistry.BARKING_TOAD.get(), LootTable.lootTable()
                .withPool(drop(FCAnimals.SCAVENGED_MEAT.get(), 0.0F, 1.0F)));

        // ------------------------------------------------------------ Catachan Devil
        // Carapaca, ferrao, trofeu. O melhor loot da fauna, atras do encontro mais raro dela.
        sink.accept(FirstCrusadeFaunaRegistry.CATACHAN_DEVIL.get(), LootTable.lootTable()
                .withPool(drop(FirstCrusadeFaunaRegistry.HEAVY_CARAPACE.get(), 4.0F, 8.0F))
                .withPool(drop(FirstCrusadeFaunaRegistry.DEVIL_STINGER.get(), 1.0F, 2.0F))
                .withPool(cookable(FirstCrusadeFaunaRegistry.GAME_MEAT.get(), 2.0F, 4.0F))
                .withPool(trophy(FirstCrusadeFaunaRegistry.TROPHY_CATACHAN_DEVIL.get())));
    }

    // ==================================================================== ajudantes

    /** Uma rolagem, pilha variavel, bonus de Looting. A forma vanilla de drop de animal. */
    private static LootPool.Builder drop(ItemLike item, float min, float max) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))
                        .apply(LootingEnchantFunction.lootingMultiplier(
                                UniformGenerator.between(0.0F, 1.0F))));
    }

    /** Carne: sai assada se o bicho morreu pegando fogo. */
    private static LootPool.Builder cookable(ItemLike item, float min, float max) {
        return drop(item, min, max)
                .apply(SmeltItemFunction.smelted().when(
                        LootItemEntityPropertyCondition.hasProperties(
                                net.minecraft.world.level.storage.loot.LootContext.EntityTarget.THIS,
                                EntityPredicate.Builder.entity().flags(
                                        EntityFlagsPredicate.Builder.flags()
                                                .setOnFire(true).build()))));
    }

    /** Peca que so um adulto tem. Um filhote sem chifre nao pode soltar um chifre. */
    private static LootPool.Builder adultOnly(ItemLike item, float chance) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(item))
                .when(LootItemEntityPropertyCondition.hasProperties(
                        net.minecraft.world.level.storage.loot.LootContext.EntityTarget.THIS,
                        EntityPredicate.Builder.entity().flags(
                                EntityFlagsPredicate.Builder.flags().setIsBaby(false).build())))
                .when(LootItemRandomChanceWithLootingCondition
                        .randomChanceAndLootingBoost(chance, 0.1F));
    }

    /** Trofeu: um, raro, e so de apex. */
    private static LootPool.Builder trophy(ItemLike item) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(item))
                .when(LootItemRandomChanceWithLootingCondition
                        .randomChanceAndLootingBoost(TROPHY_CHANCE, TROPHY_LOOTING_BONUS));
    }
}
