package com.example.examplemod.fauna.world;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A descricao de um sitio de fauna: que forma tem, quem mora nele e do que ele e feito.
 *
 * <h2>Uma Feature em Java, treze sitios em JSON</h2>
 *
 * Treze classes de Feature quase identicas seriam treze lugares para corrigir o mesmo bug de
 * posicionamento. Em vez disso ha {@link FaunaSiteFeature} (uma classe, seis formas) e treze
 * {@code configured_feature} que so trocam parametros. O ganho concreto: mudar quantos Grox um rancho
 * tem, ou trocar o bloco de sucata do curral Ork, e editar um JSON — nao recompilar o mod.
 *
 * <p>E o mesmo padrao data-driven dos modulos da Hive, pelo mesmo motivo.
 *
 * @param shape     a forma do sitio; decide o que a feature escava e constroi
 * @param mob       quem mora aqui, se alguem morar
 * @param minCount  quantos no minimo
 * @param maxCount  quantos no maximo
 * @param radius    raio do sitio em blocos
 * @param props     blocos espalhados pelo chao: ossos, sucata, vegetacao morta, carcacas
 * @param propTries quantas tentativas de espalhar prop
 * @param floor     bloco que substitui o chao pisado dentro do sitio, se houver
 * @param frame     bloco de estrutura: cerca do curral, tronco da toca, tenda do acampamento
 */
public record FaunaSiteConfig(
        FaunaSiteShape shape,
        Optional<EntityType<?>> mob,
        int minCount,
        int maxCount,
        int radius,
        List<BlockState> props,
        int propTries,
        Optional<BlockState> floor,
        Optional<BlockState> frame,
        Optional<BlockState> centre) implements FeatureConfiguration {

    public static final Codec<FaunaSiteConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FaunaSiteShape.CODEC.fieldOf("shape").forGetter(FaunaSiteConfig::shape),
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("mob")
                            .forGetter(FaunaSiteConfig::mob),
                    Codec.intRange(0, 16).optionalFieldOf("min_count", 1)
                            .forGetter(FaunaSiteConfig::minCount),
                    Codec.intRange(0, 16).optionalFieldOf("max_count", 1)
                            .forGetter(FaunaSiteConfig::maxCount),
                    Codec.intRange(1, 24).optionalFieldOf("radius", 5)
                            .forGetter(FaunaSiteConfig::radius),
                    BlockState.CODEC.listOf().optionalFieldOf("props", List.of())
                            .forGetter(FaunaSiteConfig::props),
                    Codec.intRange(0, 128).optionalFieldOf("prop_tries", 12)
                            .forGetter(FaunaSiteConfig::propTries),
                    BlockState.CODEC.optionalFieldOf("floor").forGetter(FaunaSiteConfig::floor),
                    BlockState.CODEC.optionalFieldOf("frame").forGetter(FaunaSiteConfig::frame),
                    // Um bloco no meio do sitio. Opcional e generico de proposito: "este sitio tem
                    // uma peca no centro" serve para o relicario Necron e para o que vier depois,
                    // e um campo chamado reliquary dentro do pacote da fauna seria uma mentira.
                    BlockState.CODEC.optionalFieldOf("centre").forGetter(FaunaSiteConfig::centre)
            ).apply(instance, FaunaSiteConfig::new));

    /** Quantos moradores este sitio cria nesta geracao. */
    public int rollCount(net.minecraft.util.RandomSource random) {
        if (this.maxCount <= this.minCount) {
            return this.minCount;
        }

        return this.minCount + random.nextInt(this.maxCount - this.minCount + 1);
    }
}
