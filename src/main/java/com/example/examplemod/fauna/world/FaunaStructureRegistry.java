package com.example.examplemod.fauna.world;

import com.example.examplemod.ExampleMod;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * O registro das estruturas da fauna: uma {@link Feature} so, e treze sitios em datapack.
 *
 * <h2>Feature e nao Structure, e a escolha importa</h2>
 *
 * O Minecraft tem dois sistemas para "colocar coisas no mundo": {@code Structure} (jigsaw, NBT,
 * StructureStart, registro por regiao, salvamento de referencias) e {@code Feature} (uma funcao que
 * escreve blocos num chunk que esta nascendo). Os sitios da fauna sao o segundo caso:
 *
 * <ul>
 *   <li>sao pequenos — 5 a 9 blocos de raio, sempre dentro de um chunk ou dois;</li>
 *   <li>nao tem salas, nem caminhos, nem pecas que precisem casar;</li>
 *   <li>nao precisam ser localizaveis por bussola nem aparecer em {@code /locate}.</li>
 * </ul>
 *
 * Uma {@code Structure} para isto custaria referencia salva por chunk, {@code StructureManager} e um
 * arquivo de {@code structure_set} por sitio, em troca de nada que o jogador possa notar. O briefing
 * ja antecipa isso: "nem todas precisam ser estruturas NBT enormes".
 *
 * <h2>Onde cada sitio aparece</h2>
 *
 * Aqui nao. A distribuicao vive em datapack — {@code configured_feature} descreve o sitio,
 * {@code placed_feature} descreve a raridade, e a lista de features do bioma decide quais existem
 * naquele bioma. Todos gerados por {@code tools/generate_fauna_sites.py}, que e o dono desses
 * arquivos.
 */
public final class FaunaStructureRegistry {

    private FaunaStructureRegistry() {
    }

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, ExampleMod.MODID);

    /**
     * A feature unica de sitio de fauna.
     *
     * <p>O id {@code firstcrusade:fauna_site} e o que os {@code configured_feature} referenciam. Uma
     * so para os treze sitios: o que muda entre eles e a configuracao, nao o codigo.
     */
    public static final RegistryObject<Feature<FaunaSiteConfig>> FAUNA_SITE =
            FEATURES.register("fauna_site", () -> new FaunaSiteFeature(FaunaSiteConfig.CODEC));

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
