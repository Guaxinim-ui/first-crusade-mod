package com.example.examplemod.fauna;

import java.util.List;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.animal.FCAnimalEntity;
import com.example.examplemod.fauna.entity.ArthromiteDuneskuttlerEntity;
import com.example.examplemod.fauna.entity.CatachanBarkingToadEntity;
import com.example.examplemod.fauna.entity.CatachanDevilEntity;
import com.example.examplemod.fauna.entity.CthelleanCudbearEntity;
import com.example.examplemod.fauna.entity.DuskhornEntity;
import com.example.examplemod.fauna.entity.DustbackHelamiteEntity;
import com.example.examplemod.fauna.entity.FenrisianWolfEntity;
import com.example.examplemod.fauna.entity.GreaterMalkavanConstrictorEntity;
import com.example.examplemod.fauna.entity.KnarlocEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * O registro da fauna nova: as nove especies que a integracao dos modelos do Blockbench trouxe, os
 * drops delas, os atributos e a regra de onde podem pisar.
 *
 * <h2>Por que existem dois registros de fauna neste mod</h2>
 *
 * A Fase E ja tinha registrado seis especies em {@code FCAnimals} — Grox, Cyber-Mastiff, Squig, Sump
 * Rat, Ash Strider e Ambull — e quatro delas estao na lista de treze desta integracao. Mover essas
 * quatro para ca renomearia entidades que ja existem em saves, em loot tables e nos JSON de bioma,
 * em troca de nada que o jogador possa ver. Entao {@code FCAnimals} continua dono das seis antigas
 * (que ganharam modelo, animacao e habilidades novas onde faltava) e este arquivo e dono das nove
 * novas.
 *
 * <p>A infraestrutura, essa sim, e uma so: {@link FaunaEntity}, {@link FaunaSpawnRules},
 * {@link FaunaSoundEvents} e {@code effect/FaunaVisualEffects} servem as quinze.
 *
 * <h2>Os dois tetos de spawn</h2>
 *
 * Igual a Fase E, e a licao vale repetir porque e contraintuitiva: na geracao de chunk o nivel e um
 * {@code WorldGenRegion}, cujas consultas de entidade devolvem lista vazia <b>por contrato</b>, entao
 * qualquer contagem em Java le zero ali. Quem limita a manada inicial e o datapack
 * ({@code creature_spawn_probability} + {@code minCount}/{@code maxCount} do bioma, em
 * {@code tools/generate_biomes.py}). O teto em Java so vale no spawn continuo.
 */
public final class FirstCrusadeFaunaRegistry {

    private FirstCrusadeFaunaRegistry() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    // ====================================================================================
    // As nove especies
    //
    // As dimensoes de cada caixa vem da geometria medida do .bbmodel do dono, nao de gosto:
    // a largura e a extensao em X (lado a lado) e a altura e o topo do modelo, ambas em pixels
    // divididas por 16. Usar a extensao em Z daria caixas absurdas — quase todos estes bichos sao
    // muito mais compridos do que largos, e a caixa do Minecraft e quadrada no plano.
    //
    // Todos os modelos tem o minimo em Y exatamente 0, entao pata encosta no chao sem ajuste.
    // ====================================================================================

    /** Lobo de matilha de Fenris. dx 21px, alto 30px. */
    public static final RegistryObject<EntityType<FenrisianWolfEntity>> FENRISIAN_WOLF =
            ENTITY_TYPES.register("fenrisian_wolf",
                    () -> EntityType.Builder.of(FenrisianWolfEntity::new, MobCategory.CREATURE)
                            .sized(1.3F, 1.85F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":fenrisian_wolf"));

    /** Predador de emboscada dos ermos de cinza. dx 27px, alto 18px — largo e baixo. */
    public static final RegistryObject<EntityType<ArthromiteDuneskuttlerEntity>> DUNESKUTTLER =
            ENTITY_TYPES.register("arthromite_duneskuttler",
                    () -> EntityType.Builder.of(ArthromiteDuneskuttlerEntity::new, MobCategory.CREATURE)
                            .sized(1.6F, 1.15F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":arthromite_duneskuttler"));

    /** Montaria dos nomades de cinza. dx 27px, alto 22px. */
    public static final RegistryObject<EntityType<DustbackHelamiteEntity>> DUSTBACK_HELAMITE =
            ENTITY_TYPES.register("dustback_helamite",
                    () -> EntityType.Builder.of(DustbackHelamiteEntity::new, MobCategory.CREATURE)
                            .sized(1.7F, 1.4F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":dustback_helamite"));

    /** Urso territorial. dx 20px, alto 26px. */
    public static final RegistryObject<EntityType<CthelleanCudbearEntity>> CTHELLEAN_CUDBEAR =
            ENTITY_TYPES.register("cthellean_cudbear",
                    () -> EntityType.Builder.of(CthelleanCudbearEntity::new, MobCategory.CREATURE)
                            .sized(1.3F, 1.6F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":cthellean_cudbear"));

    /** Herbivoro pesado de seis pernas. dx 34px, alto 34px — o maior corpo da fauna. */
    public static final RegistryObject<EntityType<DuskhornEntity>> DUSKHORN =
            ENTITY_TYPES.register("duskhorn",
                    () -> EntityType.Builder.of(DuskhornEntity::new, MobCategory.CREATURE)
                            .sized(2.0F, 2.1F)
                            .clientTrackingRange(12)
                            .build(ExampleMod.MODID + ":duskhorn"));

    /** Animal de guerra Kroot. dx 20px, alto 29px. */
    public static final RegistryObject<EntityType<KnarlocEntity>> KNARLOC =
            ENTITY_TYPES.register("knarloc",
                    () -> EntityType.Builder.of(KnarlocEntity::new, MobCategory.CREATURE)
                            .sized(1.3F, 1.85F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":knarloc"));

    /**
     * Serpente constritora. dx 32px, alto 13px.
     *
     * <p>A unica especie em que a caixa e uma mentira aceita: o corpo tem 82px de comprimento e a
     * caixa do Minecraft e quadrada, entao ou a cobra fica com uma caixa de 5 blocos de lado
     * (intransponivel e absurda) ou parte do rabo fica fora dela. Escolhida a segunda: o que precisa
     * de acerto e a cabeca, e o rabo atravessando a caixa e menos estranho do que uma parede
     * invisivel de cinco blocos em volta de uma cobra.
     */
    public static final RegistryObject<EntityType<GreaterMalkavanConstrictorEntity>> CONSTRICTOR =
            ENTITY_TYPES.register("greater_malkavan_constrictor",
                    () -> EntityType.Builder.of(GreaterMalkavanConstrictorEntity::new,
                                    MobCategory.CREATURE)
                            .sized(1.5F, 0.9F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":greater_malkavan_constrictor"));

    /** Sapo tóxico de Catachan. dx 29px, alto 19px. */
    public static final RegistryObject<EntityType<CatachanBarkingToadEntity>> BARKING_TOAD =
            ENTITY_TYPES.register("catachan_barking_toad",
                    () -> EntityType.Builder.of(CatachanBarkingToadEntity::new, MobCategory.CREATURE)
                            .sized(1.7F, 1.2F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":catachan_barking_toad"));

    /** Fauna apex. dx 34px, alto 25px, 82px de comprimento — e para ser inesquecivel. */
    public static final RegistryObject<EntityType<CatachanDevilEntity>> CATACHAN_DEVIL =
            ENTITY_TYPES.register("catachan_devil",
                    () -> EntityType.Builder.of(CatachanDevilEntity::new, MobCategory.CREATURE)
                            .sized(2.0F, 1.6F)
                            .clientTrackingRange(14)
                            .build(ExampleMod.MODID + ":catachan_devil"));

    // ====================================================================================
    // Ovos de spawn
    // ====================================================================================

    public static final RegistryObject<Item> FENRISIAN_WOLF_SPAWN_EGG =
            ITEMS.register("fenrisian_wolf_spawn_egg",
                    () -> new ForgeSpawnEggItem(FENRISIAN_WOLF, 0x6E7278, 0xC8CED4,
                            new Item.Properties()));

    public static final RegistryObject<Item> DUNESKUTTLER_SPAWN_EGG =
            ITEMS.register("arthromite_duneskuttler_spawn_egg",
                    () -> new ForgeSpawnEggItem(DUNESKUTTLER, 0xB49A6E, 0x5E4A32,
                            new Item.Properties()));

    public static final RegistryObject<Item> DUSTBACK_HELAMITE_SPAWN_EGG =
            ITEMS.register("dustback_helamite_spawn_egg",
                    () -> new ForgeSpawnEggItem(DUSTBACK_HELAMITE, 0x8A7A62, 0xC4B296,
                            new Item.Properties()));

    public static final RegistryObject<Item> CTHELLEAN_CUDBEAR_SPAWN_EGG =
            ITEMS.register("cthellean_cudbear_spawn_egg",
                    () -> new ForgeSpawnEggItem(CTHELLEAN_CUDBEAR, 0x4A3E32, 0x8A7256,
                            new Item.Properties()));

    public static final RegistryObject<Item> DUSKHORN_SPAWN_EGG =
            ITEMS.register("duskhorn_spawn_egg",
                    () -> new ForgeSpawnEggItem(DUSKHORN, 0x3E3630, 0xB49A6E,
                            new Item.Properties()));

    public static final RegistryObject<Item> KNARLOC_SPAWN_EGG =
            ITEMS.register("knarloc_spawn_egg",
                    () -> new ForgeSpawnEggItem(KNARLOC, 0x6E5E42, 0xA8322C,
                            new Item.Properties()));

    public static final RegistryObject<Item> CONSTRICTOR_SPAWN_EGG =
            ITEMS.register("greater_malkavan_constrictor_spawn_egg",
                    () -> new ForgeSpawnEggItem(CONSTRICTOR, 0x3A4A2E, 0x8A9A4E,
                            new Item.Properties()));

    public static final RegistryObject<Item> BARKING_TOAD_SPAWN_EGG =
            ITEMS.register("catachan_barking_toad_spawn_egg",
                    () -> new ForgeSpawnEggItem(BARKING_TOAD, 0x4E6E2E, 0xC8D44A,
                            new Item.Properties()));

    public static final RegistryObject<Item> CATACHAN_DEVIL_SPAWN_EGG =
            ITEMS.register("catachan_devil_spawn_egg",
                    () -> new ForgeSpawnEggItem(CATACHAN_DEVIL, 0x2E4A2A, 0x9A8A56,
                            new Item.Properties()));

    // ====================================================================================
    // Drops
    //
    // A regra que organiza a lista: um item por MATERIAL, nao um item por bicho. Couro de
    // Duskhorn e couro de Knarloc seriam mecanicamente identicos, e um inventario com sete couros
    // de nomes diferentes nao e profundidade, e ruido — a mesma decisao que juntou a carne do
    // squig e a do rato na Fase E.
    //
    // As excecoes sao os trofeus, e sao excecao de proposito: um trofeu existe justamente para
    // dizer QUAL bicho voce matou.
    // ====================================================================================

    /** Couro grosso de fauna grande. Duskhorn, Knarloc, Cudbear, Constrictor. */
    public static final RegistryObject<Item> BEAST_HIDE = ITEMS.register("beast_hide",
            () -> new Item(new Item.Properties()));

    /** Pele de pelo: lobo e urso. Separada do couro porque tem uso diferente (frio). */
    public static final RegistryObject<Item> THICK_PELT = ITEMS.register("thick_pelt",
            () -> new Item(new Item.Properties()));

    /** Presa, garra, dente. O material cortante da fauna. */
    public static final RegistryObject<Item> BEAST_FANG = ITEMS.register("beast_fang",
            () -> new Item(new Item.Properties()));

    /** Chifre de herbivoro pesado. Duskhorn, e o Grox ja tem o proprio. */
    public static final RegistryObject<Item> DUSKHORN_HORN = ITEMS.register("duskhorn_horn",
            () -> new Item(new Item.Properties()));

    /** Escama de reptil. Constrictor. */
    public static final RegistryObject<Item> SERPENT_SCALE = ITEMS.register("serpent_scale",
            () -> new Item(new Item.Properties()));

    /** Quills de Knarloc — o material Kroot. */
    public static final RegistryObject<Item> KNARLOC_QUILL = ITEMS.register("knarloc_quill",
            () -> new Item(new Item.Properties()));

    /** Carne de caca grande. Melhor que a carne catada, pior que a de Grox. */
    public static final RegistryObject<Item> GAME_MEAT = ITEMS.register("game_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(3).saturationMod(0.3F).meat().build())));

    public static final RegistryObject<Item> COOKED_GAME_MEAT = ITEMS.register("cooked_game_meat",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(7).saturationMod(0.8F).meat().build())));

    /**
     * O ferrao do Catachan Devil. Nao e comida, nao e material comum: e prova.
     *
     * <p>Um item so, do bicho mais raro do mod, e por isso ele carrega o nome dele.
     */
    public static final RegistryObject<Item> DEVIL_STINGER = ITEMS.register("devil_stinger",
            () -> new Item(new Item.Properties().stacksTo(16)));

    /** Carapaca de artropode grande: Devil e Duneskuttler. Distinta da quitina da Fase E. */
    public static final RegistryObject<Item> HEAVY_CARAPACE = ITEMS.register("heavy_carapace",
            () -> new Item(new Item.Properties()));

    // ====================================================================================
    // Trofeus
    //
    // Um por especie apex, e nada mais. O briefing lista cinco prioridades (Ambull, Catachan
    // Devil, Duskhorn, Constrictor, Cudbear) e sao exatamente estes cinco — inventar trofeu para
    // o resto tiraria o significado dos que importam.
    //
    // Continuam sendo itens simples de proposito. Eles vao aparecer no Crusade Record / Command
    // Core depois; um item que ja existe pode ser consultado por qualquer sistema futuro, e um
    // sistema de registro de caca construido antes de haver o que registrar seria adivinhacao.
    // ====================================================================================

    public static final RegistryObject<Item> TROPHY_AMBULL = ITEMS.register("trophy_ambull",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> TROPHY_CATACHAN_DEVIL =
            ITEMS.register("trophy_catachan_devil",
                    () -> new Item(new Item.Properties().stacksTo(1)
                            .rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final RegistryObject<Item> TROPHY_DUSKHORN = ITEMS.register("trophy_duskhorn",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> TROPHY_CONSTRICTOR =
            ITEMS.register("trophy_greater_malkavan_constrictor",
                    () -> new Item(new Item.Properties().stacksTo(1)
                            .rarity(net.minecraft.world.item.Rarity.RARE)));

    public static final RegistryObject<Item> TROPHY_CUDBEAR = ITEMS.register("trophy_cthellean_cudbear",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    // ====================================================================================
    // Wiring
    // ====================================================================================

    /**
     * Toda a fauna nova numa lista, para os lacos que falam com "as especies".
     *
     * <p>Esquecer uma especie aqui nao da erro nenhum — ela so nunca aparece no mundo. Foi
     * exatamente assim que o {@code rocky_highland} passou a Fase E inteira sem ver um Grox.
     */
    private static final List<RegistryObject<? extends EntityType<? extends FCAnimalEntity>>> SPECIES =
            List.of(FENRISIAN_WOLF, DUNESKUTTLER, DUSTBACK_HELAMITE, CTHELLEAN_CUDBEAR, DUSKHORN,
                    KNARLOC, CONSTRICTOR, BARKING_TOAD, CATACHAN_DEVIL);

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(FirstCrusadeFaunaRegistry::registerAttributes);
        modEventBus.addListener(FirstCrusadeFaunaRegistry::registerSpawnPlacements);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(FENRISIAN_WOLF.get(), FenrisianWolfEntity.createAttributes().build());
        event.put(DUNESKUTTLER.get(), ArthromiteDuneskuttlerEntity.createAttributes().build());
        event.put(DUSTBACK_HELAMITE.get(), DustbackHelamiteEntity.createAttributes().build());
        event.put(CTHELLEAN_CUDBEAR.get(), CthelleanCudbearEntity.createAttributes().build());
        event.put(DUSKHORN.get(), DuskhornEntity.createAttributes().build());
        event.put(KNARLOC.get(), KnarlocEntity.createAttributes().build());
        event.put(CONSTRICTOR.get(), GreaterMalkavanConstrictorEntity.createAttributes().build());
        event.put(BARKING_TOAD.get(), CatachanBarkingToadEntity.createAttributes().build());
        event.put(CATACHAN_DEVIL.get(), CatachanDevilEntity.createAttributes().build());
    }

    /**
     * Sem isto a entrada de spawner do bioma e inerte: o jogo nao sabe que superficie a especie
     * precisa, e toda tentativa de spawn natural falha em silencio.
     */
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        for (RegistryObject<? extends EntityType<? extends FCAnimalEntity>> type : SPECIES) {
            event.register(type.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FaunaSpawnRules::checkFaunaSpawn,
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }

    /** Anexado a aba criativa principal a partir de {@code FCRegistry}. */
    public static void addToCreativeTab(CreativeModeTab.Output output) {
        output.accept(FENRISIAN_WOLF_SPAWN_EGG.get());
        output.accept(DUNESKUTTLER_SPAWN_EGG.get());
        output.accept(DUSTBACK_HELAMITE_SPAWN_EGG.get());
        output.accept(CTHELLEAN_CUDBEAR_SPAWN_EGG.get());
        output.accept(DUSKHORN_SPAWN_EGG.get());
        output.accept(KNARLOC_SPAWN_EGG.get());
        output.accept(CONSTRICTOR_SPAWN_EGG.get());
        output.accept(BARKING_TOAD_SPAWN_EGG.get());
        output.accept(CATACHAN_DEVIL_SPAWN_EGG.get());

        output.accept(GAME_MEAT.get());
        output.accept(COOKED_GAME_MEAT.get());
        output.accept(BEAST_HIDE.get());
        output.accept(THICK_PELT.get());
        output.accept(BEAST_FANG.get());
        output.accept(DUSKHORN_HORN.get());
        output.accept(SERPENT_SCALE.get());
        output.accept(KNARLOC_QUILL.get());
        output.accept(HEAVY_CARAPACE.get());
        output.accept(DEVIL_STINGER.get());

        output.accept(TROPHY_AMBULL.get());
        output.accept(TROPHY_CATACHAN_DEVIL.get());
        output.accept(TROPHY_DUSKHORN.get());
        output.accept(TROPHY_CONSTRICTOR.get());
        output.accept(TROPHY_CUDBEAR.get());
    }
}
