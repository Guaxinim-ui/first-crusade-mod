package com.example.examplemod.fauna;

import com.example.examplemod.animal.FCAnimalConfig;
import com.example.examplemod.animal.FCAnimalEntity;
import com.example.examplemod.flora.FloraTags;
import com.example.examplemod.planet.FCPlanets;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Onde a fauna pode nascer, e quantas dela.
 *
 * <h2>Isto NAO e um manager, e nao varre nada</h2>
 *
 * Sao predicados puros, chamados pelo jogo no momento em que ele ja decidiu tentar um spawn. Nao ha
 * tick, nao ha lista, nao ha estado. O briefing proibe explicitamente um {@code FaunaSpawnManager}
 * que varra o mundo, e a razao pratica e melhor do que a regra: o Minecraft ja tem o laco de spawn,
 * e um segundo laco em paralelo seria trabalho duplicado que ainda erraria os dois tetos abaixo.
 *
 * <h2>Onde a raridade realmente vive</h2>
 *
 * Nao esta neste arquivo. Peso de spawn, tamanho de grupo e probabilidade por chunk sao <b>datapack</b>
 * — a lista {@code spawners} de cada bioma, escrita por {@code tools/generate_biomes.py}. Este arquivo
 * so responde "este bloco serve?" quando o peso ja sorteou a especie. Procurar raridade aqui e o erro
 * que faz alguem passar uma tarde ajustando Java sem nada mudar no mundo.
 *
 * <h2>Tres perfis, porque um so estaria errado em metade das especies</h2>
 *
 * <ul>
 *   <li>{@link #checkFaunaSpawn} — o padrao: chao natural, claro, com teto. Herbivoro e animal de
 *       superficie.</li>
 *   <li>{@link #checkBurrowerSpawn} — quem vive sob o chao nao se importa com luz. Exigir claridade
 *       de um Duneskuttler seria exigir que ele esperasse o dia para estar enterrado.</li>
 *   <li>{@link #checkPredatorSpawn} — predador de superficie que caca de noite tambem. Chao e teto,
 *       sem regra de luz.</li>
 * </ul>
 */
public final class FaunaSpawnRules {

    private FaunaSpawnRules() {
    }

    /**
     * Quantos de uma especie apex cabem em 48 blocos.
     *
     * <p>Um. E a diferenca entre "encontrei um Catachan Devil" e "encontrei uma praga de Catachan
     * Devils"; o briefing pede isso em letras maiusculas e ele esta certo, porque a segunda coisa
     * destroi a primeira de forma irreversivel na cabeca do jogador.
     */
    public static final int APEX_LIMIT = 1;

    /** Quantos de um predador territorial cabem em 48 blocos. */
    public static final int TERRITORIAL_LIMIT = 2;

    // ==================================================================== os predicados

    /** O padrao: chao natural, luz suficiente, teto de populacao da especie. */
    public static <T extends FCAnimalEntity> boolean checkFaunaSpawn(
            EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
            RandomSource random) {
        if (!allowed(type, level, reason, pos, FCAnimalConfig.WILDLIFE_POPULATION_LIMIT.get())) {
            return false;
        }

        return FCAnimalEntity.brightEnoughToSpawn(level, pos);
    }

    /** Escavador: mesma regra de chao, sem regra de luz. */
    public static <T extends FCAnimalEntity> boolean checkBurrowerSpawn(
            EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
            RandomSource random) {
        return allowed(type, level, reason, pos, TERRITORIAL_LIMIT);
    }

    /** Predador de superficie que tambem caca no escuro. */
    public static <T extends FCAnimalEntity> boolean checkPredatorSpawn(
            EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
            RandomSource random) {
        return allowed(type, level, reason, pos, TERRITORIAL_LIMIT);
    }

    /** Apex: um por area, e nada mais. */
    public static <T extends FCAnimalEntity> boolean checkApexSpawn(
            EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
            RandomSource random) {
        return allowed(type, level, reason, pos, APEX_LIMIT);
    }

    // ==================================================================== o comum

    /**
     * As tres portas que todo perfil partilha: mundo da Cruzada, chao do mod, teto de populacao.
     *
     * <p>O dimension check chega pelo {@code getLevel()} e nao testando o accessor: durante a geracao
     * de chunk o accessor e um {@code WorldGenRegion}, que nao e um {@code Level}, e perguntar do jeito
     * errado responderia "nao e mundo da Cruzada" para todo animal que a worldgen tentasse colocar.
     * Esse erro exato ja custou uma sessao na Fase E.
     */
    private static boolean allowed(EntityType<?> type, ServerLevelAccessor level,
                                   MobSpawnType reason, BlockPos pos, int limit) {
        if (!FCAnimalConfig.ANIMAL_SPAWN_ENABLED.get()) {
            return false;
        }

        // Ovo, comando ou estrutura significa que alguem pediu este bicho de proposito. Recusar um
        // spawn deliberado por causa do teto natural le como mod quebrado.
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND
                || reason == MobSpawnType.BUCKET || reason == MobSpawnType.STRUCTURE) {
            return true;
        }

        if (!FCPlanets.isCrusadeWorld(level.getLevel().dimension())) {
            return false;
        }

        if (!level.getBlockState(pos.below()).is(FloraTags.GROUND_NATURAL)) {
            return false;
        }

        return !FCAnimalEntity.tooCrowded(level, type, pos, limit);
    }
}
