package com.example.examplemod.fauna.world;

import com.example.examplemod.fauna.FaunaEntity;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * A feature que constroi um sitio de fauna — toca, ninho, curral, clareira ou acampamento — e coloca
 * os moradores dele.
 *
 * <h2>Roda uma vez, na geracao do chunk, e nunca mais</h2>
 *
 * Este e o ponto inteiro de fazer isto como {@code Feature} em vez de como sistema em runtime. Uma
 * feature de worldgen e chamada quando o chunk nasce; depois disso o chunk e salvo em disco com a
 * toca e o bicho dentro dela. Nao ha tick, nao ha varredura, nao ha "verificar se ja gerei aqui" —
 * a propria geracao de chunk e o registro de que ja foi feito, e ela e a prova mais barata que
 * existe. O briefing pede "estruturas que geram animais devem registrar que ja fizeram seu spawn"; a
 * resposta e nao precisar registrar nada.
 *
 * <h2>Os moradores sao persistentes e sabem de onde vieram</h2>
 *
 * {@link FaunaEntity#markFromStructure()} deixa o bicho fora do despawn e do teto de populacao
 * natural. O Ambull do Ambull Burrow tem de estar la quando o jogador voltar — uma estrutura que
 * conta uma historia que o mundo desmente e pior do que nenhuma estrutura.
 *
 * <h2>Nada de destruicao ampla</h2>
 *
 * Todo sitio tem raio pequeno (5 a 9 blocos) e escava apenas o que a forma exige. A tentacao de
 * "limpar a area" e forte e errada: uma clareira de vinte blocos num bioma de selva vira uma cicatriz
 * que se ve do mapa, e o jogador nao le como habitat, le como bug de geracao.
 */
public class FaunaSiteFeature extends Feature<FaunaSiteConfig> {

    /**
     * Quantos blocos esta chamada escreveu.
     *
     * <p>Serve para {@link #place} responder a verdade: um sitio que nao escreveu nada nao foi
     * construido. Zerado ao fim de cada chamada — a feature e um singleton no registro, e um contador
     * que sobrevivesse entre chamadas somaria sitios diferentes.
     */
    private int placedBlocks;

    public FaunaSiteFeature(Codec<FaunaSiteConfig> codec) {
        super(codec);
    }

    /** Todo bloco escrito pela feature passa por aqui, para a contagem ser confiavel. */
    private void put(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.setBlock(pos, state, 2)) {
            this.placedBlocks++;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<FaunaSiteConfig> context) {
        WorldGenLevel level = context.level();
        FaunaSiteConfig config = context.config();
        RandomSource random = context.random();

        BlockPos origin = groundAt(level, context.origin(), config.radius());
        if (origin == null) {
            return false;
        }

        switch (config.shape()) {
            case CLEARING -> buildClearing(level, random, origin, config);
            case DEN -> buildDen(level, random, origin, config);
            case BURROW -> buildBurrow(level, random, origin, config);
            case PEN -> buildPen(level, random, origin, config);
            case NEST -> buildNest(level, random, origin, config);
            case CAMP -> buildCamp(level, random, origin, config);
        }

        scatterProps(level, random, origin, config);
        spawnResidents(level, random, origin, config);

        // Um sitio que nao escreveu bloco nenhum nao foi construido, e dizer "coloquei" seria
        // mentir para o jogo — e para quem estiver depurando com /place.
        boolean built = this.placedBlocks > 0;
        this.placedBlocks = 0;
        return built;
    }

    // ==================================================================== formas

    /** Clareira: limpa a vegetacao e marca o chao pisado. */
    private void buildClearing(WorldGenLevel level, RandomSource random, BlockPos origin,
                               FaunaSiteConfig config) {
        int radius = config.radius();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }

                BlockPos surface = surfaceAt(level, origin.offset(dx, 0, dz));
                clearVegetation(level, surface);

                // O chao pisado nao cobre tudo: uma clareira uniforme e um circulo perfeito, e o que
                // se quer e uma mancha. Dois tercos, sorteados, dao a borda irregular de graca.
                if (config.floor().isPresent() && random.nextInt(3) != 0) {
                    setGround(level, surface.below(), config.floor().get());
                }
            }
        }
    }

    /** Toca horizontal: boca na encosta e camara rasa atras. */
    private void buildDen(WorldGenLevel level, RandomSource random, BlockPos origin,
                          FaunaSiteConfig config) {
        int depth = Math.max(3, config.radius() - 1);
        int height = 3;
        int halfWidth = 1;

        for (int forward = 0; forward < depth; forward++) {
            for (int side = -halfWidth; side <= halfWidth; side++) {
                for (int up = 0; up < height; up++) {
                    BlockPos pos = origin.offset(side, up, -forward);
                    put(level, pos, Blocks.CAVE_AIR.defaultBlockState());
                }
            }

            // A camara alarga no fundo: e o que faz a toca ter um "dentro" em vez de ser um corredor.
            if (forward == depth - 2) {
                halfWidth = 2;
            }
        }

        if (config.frame().isPresent()) {
            // Dois troncos na boca. Marcam a entrada de longe, que e o que transforma a toca numa
            // pista ambiental em vez de num buraco que so se ve de cima.
            put(level, origin.offset(-halfWidth - 1, 0, 0), config.frame().get());
            put(level, origin.offset(halfWidth + 1, 0, 0), config.frame().get());
        }
    }

    /** Toca vertical: poco com camara no fundo, e um teto para o poco nao virar cratera. */
    private void buildBurrow(WorldGenLevel level, RandomSource random, BlockPos origin,
                             FaunaSiteConfig config) {
        int shaft = 6 + random.nextInt(4);

        for (int down = 0; down < shaft; down++) {
            BlockPos pos = origin.below(down);
            put(level, pos, Blocks.CAVE_AIR.defaultBlockState());
            put(level, pos.east(), Blocks.CAVE_AIR.defaultBlockState());
        }

        // A camara no fundo, onde o bicho fica.
        BlockPos floor = origin.below(shaft);
        int chamber = Math.max(2, config.radius() - 2);

        for (int dx = -chamber; dx <= chamber; dx++) {
            for (int dz = -chamber; dz <= chamber; dz++) {
                if (dx * dx + dz * dz > chamber * chamber) {
                    continue;
                }
                for (int up = 0; up < 3; up++) {
                    put(level, floor.offset(dx, up, dz), Blocks.CAVE_AIR.defaultBlockState());
                }
            }
        }

        // A boca na superficie: terra revolvida em volta, e nao um buraco limpo. A pista ambiental do
        // Ambull e essa — chao quebrado antes do bicho.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (random.nextInt(3) == 0) {
                    continue;
                }
                BlockPos rim = surfaceAt(level, origin.offset(dx, 0, dz)).below();
                setGround(level, rim, config.floor().orElse(Blocks.COARSE_DIRT.defaultBlockState()));
            }
        }
    }

    /** Cercado: anel de cerca com uma abertura, e chao batido dentro. */
    private void buildPen(WorldGenLevel level, RandomSource random, BlockPos origin,
                          FaunaSiteConfig config) {
        int radius = config.radius();
        BlockState fence = config.frame().orElse(Blocks.OAK_FENCE.defaultBlockState());

        // A abertura: um dos quatro lados fica sem cerca. Um curral fechado nao e curral, e caixa —
        // e o jogador precisa poder entrar sem quebrar nada.
        int gateSide = random.nextInt(4);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean edge = Math.abs(dx) == radius || Math.abs(dz) == radius;
                BlockPos surface = surfaceAt(level, origin.offset(dx, 0, dz));

                if (!edge) {
                    clearVegetation(level, surface);
                    if (config.floor().isPresent() && random.nextInt(4) != 0) {
                        setGround(level, surface.below(), config.floor().get());
                    }
                    continue;
                }

                if (isGate(dx, dz, radius, gateSide)) {
                    continue;
                }

                clearVegetation(level, surface);
                put(level, surface, fence);
            }
        }

        // O cocho: dois caldeirões de agua num canto. Detalhe barato que diz "alguem cuida disto".
        BlockPos trough = origin.offset(radius - 1, 0, radius - 1);
        BlockPos troughSurface = surfaceAt(level, trough);
        put(level, troughSurface, Blocks.WATER_CAULDRON.defaultBlockState());
    }

    private boolean isGate(int dx, int dz, int radius, int gateSide) {
        return switch (gateSide) {
            case 0 -> dz == -radius && Math.abs(dx) <= 1;
            case 1 -> dz == radius && Math.abs(dx) <= 1;
            case 2 -> dx == -radius && Math.abs(dz) <= 1;
            default -> dx == radius && Math.abs(dz) <= 1;
        };
    }

    /** Ninho: uma depressao rasa com borda levantada. */
    private void buildNest(WorldGenLevel level, RandomSource random, BlockPos origin,
                           FaunaSiteConfig config) {
        int radius = config.radius();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distanceSq = dx * dx + dz * dz;
                if (distanceSq > radius * radius) {
                    continue;
                }

                BlockPos surface = surfaceAt(level, origin.offset(dx, 0, dz));
                clearVegetation(level, surface);

                // Quanto mais perto do centro, mais fundo. Uma tigela, nao um buraco de fundo plano.
                int depth = distanceSq < (radius - 1) * (radius - 1) ? 1 : 0;
                if (depth > 0) {
                    put(level, surface.below(), Blocks.CAVE_AIR.defaultBlockState());
                    if (config.floor().isPresent()) {
                        setGround(level, surface.below(2), config.floor().get());
                    }
                }
            }
        }
    }

    /** Acampamento: chao batido, uma tenda de lã e caixas. */
    private void buildCamp(WorldGenLevel level, RandomSource random, BlockPos origin,
                           FaunaSiteConfig config) {
        int radius = config.radius();
        BlockState tent = config.frame().orElse(Blocks.BROWN_WOOL.defaultBlockState());

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos surface = surfaceAt(level, origin.offset(dx, 0, dz));
                clearVegetation(level, surface);
                if (config.floor().isPresent() && random.nextInt(3) != 0) {
                    setGround(level, surface.below(), config.floor().get());
                }
            }
        }

        // A tenda: um telhado de duas aguas de 3x3, aberto nas pontas. Simples de proposito — o que
        // faz o acampamento ler nao e a arquitetura, e o contraste com o deserto em volta.
        BlockPos tentBase = surfaceAt(level, origin.offset(2, 0, 0));
        for (int dz = -1; dz <= 1; dz++) {
            put(level, tentBase.offset(-1, 1, dz), tent);
            put(level, tentBase.offset(0, 2, dz), tent);
            put(level, tentBase.offset(1, 1, dz), tent);
        }

        put(level, surfaceAt(level, origin.offset(-2, 0, 1)), Blocks.BARREL.defaultBlockState());
        put(level, surfaceAt(level, origin.offset(-2, 0, -1)),
                Blocks.WATER_CAULDRON.defaultBlockState());
        put(level, surfaceAt(level, origin.offset(0, 0, 2)), Blocks.CAMPFIRE.defaultBlockState());
    }

    // ==================================================================== props e bichos

    /**
     * Espalha ossos, sucata e carcacas pelo sitio.
     *
     * <p>Estes sao as <b>pistas ambientais</b> que o briefing pede: o jogador ve restos de Guardsmen
     * antes de ver o Ambull, ossos e vegetacao esmagada antes da serpente. Nao ha texto, e nao
     * precisa haver.
     */
    private void scatterProps(WorldGenLevel level, RandomSource random, BlockPos origin,
                              FaunaSiteConfig config) {
        if (config.props().isEmpty()) {
            return;
        }

        int radius = config.radius();

        for (int attempt = 0; attempt < config.propTries(); attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;

            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }

            BlockPos surface = surfaceAt(level, origin.offset(dx, 0, dz));
            if (!level.getBlockState(surface).isAir()
                    || !level.getBlockState(surface.below()).isSolidRender(level, surface.below())) {
                continue;
            }

            BlockState prop = config.props().get(random.nextInt(config.props().size()));
            put(level, surface, prop);
        }
    }

    /**
     * Cria os moradores.
     *
     * <p>{@code MobSpawnType.STRUCTURE} e o motivo declarado, e {@code FaunaSpawnRules} deixa passar
     * spawn de estrutura sem aplicar o teto natural — senao a segunda toca gerada perto da primeira
     * nasceria vazia, e a estrutura ficaria contando uma historia sobre um bicho que nunca existiu.
     */
    private void spawnResidents(WorldGenLevel level, RandomSource random, BlockPos origin,
                                FaunaSiteConfig config) {
        if (config.mob().isEmpty()) {
            return;
        }

        EntityType<?> type = config.mob().get();
        int count = config.rollCount(random);
        int radius = Math.max(1, config.radius() - 1);

        for (int i = 0; i < count; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;

            BlockPos spot = surfaceAt(level, origin.offset(dx, 0, dz));
            Entity entity = type.create(level.getLevel());

            if (entity == null) {
                return;             // tipo invalido: nao adianta tentar mais nenhum
            }

            entity.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);

            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                        MobSpawnType.STRUCTURE, null, null);
                mob.setPersistenceRequired();
            }

            if (entity instanceof FaunaEntity fauna) {
                fauna.markFromStructure();
            }

            level.addFreshEntity(entity);
        }
    }

    // ==================================================================== terreno

    /** Quantos blocos de vegetacao a sonda atravessa antes de desistir. Uma arvore alta cabe. */
    private static final int CANOPY_SCAN = 24;

    /** Desnivel maximo aceito entre o centro do sitio e a borda dele, em blocos. */
    private static final int MAX_SLOPE = 5;

    /**
     * O chao na coluna dada — <b>o chao</b>, nao o topo do que estiver crescendo nele.
     *
     * <h3>Por que o heightmap sozinho nao serve</h3>
     *
     * {@code WORLD_SURFACE_WG} devolve o primeiro ar acima do bloco mais alto, e uma folha de
     * carvalho conta. Numa floresta isso da uma altura dez blocos acima do solo, e o efeito e duplo
     * e silencioso:
     *
     * <ul>
     *   <li>a checagem de declive compara "chao aqui" com "copa de arvore ali" e conclui que o
     *       terreno e um penhasco — <b>medido: 92% de recusa em terreno seco e plano</b>, o que
     *       tornaria a toca do Cudbear praticamente impossivel justamente nos biomas de floresta,
     *       que sao os dela;</li>
     *   <li>os ossos e a sucata seriam colocados <b>em cima da copa</b>, dez blocos no ar.</li>
     * </ul>
     *
     * <p>Por isso a sonda desce do heightmap atravessando tronco, folha, planta e neve ate encontrar
     * o primeiro bloco que e realmente terreno. Custa algumas leituras de blockstate por coluna, uma
     * vez, na geracao do chunk.
     *
     * <p>Nao adianta trocar para {@code OCEAN_FLOOR_WG}: o predicado dele e {@code blocksMotion()},
     * e tronco e folha bloqueiam movimento. Ele resolve agua, nao vegetacao.
     */
    private static BlockPos surfaceAt(WorldGenLevel level, BlockPos column) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.getX(), column.getZ());
        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos(column.getX(), top, column.getZ());

        for (int step = 0; step < CANOPY_SCAN; step++) {
            if (cursor.getY() <= level.getMinBuildHeight() + 1) {
                break;
            }

            if (!isVegetation(level.getBlockState(cursor.below()))) {
                break;
            }

            cursor.move(0, -1, 0);
        }

        return cursor.immutable();
    }

    /** Tronco, folha, planta, neve: coisas que crescem no chao e nao sao o chao. */
    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.SNOW);
    }

    /**
     * O chao no ponto de origem, ou null se o lugar nao serve.
     *
     * <p>Recusa agua e recusa declive forte: um curral metade dentro de um penhasco e a forma mais
     * comum de uma estrutura de worldgen parecer quebrada, e custa quatro sondas de altura evitar.
     *
     * <p>As sondas ficam na <b>borda do sitio</b> e nao a uma distancia fixa. Um ninho de raio 3 e um
     * campo de manada de raio 9 nao tem o mesmo direito a terreno plano, e medir os dois no mesmo
     * ponto rejeitaria o pequeno por causa de um morro que ele nunca tocaria.
     */
    private static BlockPos groundAt(WorldGenLevel level, BlockPos origin, int radius) {
        BlockPos surface = surfaceAt(level, origin);

        if (!level.getFluidState(surface.below()).isEmpty()) {
            return null;
        }

        int centre = surface.getY();
        int drop = 0;

        for (int[] offset : new int[][]{{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}}) {
            int edge = surfaceAt(level, origin.offset(offset[0], 0, offset[1])).getY();
            drop = Math.max(drop, Math.abs(edge - centre));
        }

        return drop > MAX_SLOPE ? null : surface;
    }

    /** Tira a planta que estiver de pe naquele ponto. Nao mexe em bloco solido. */
    private void clearVegetation(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir()) {
            return;
        }

        if (state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.LEAVES)) {
            put(level, pos, Blocks.AIR.defaultBlockState());
        }
    }

    /** Troca o bloco de chao, e so se ele for solido — nunca cria chao no ar. */
    private void setGround(WorldGenLevel level, BlockPos pos, BlockState ground) {
        if (level.getBlockState(pos).isSolidRender(level, pos)) {
            put(level, pos, ground);
        }
    }
}
