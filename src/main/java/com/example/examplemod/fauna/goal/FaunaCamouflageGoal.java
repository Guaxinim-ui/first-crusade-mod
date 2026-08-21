package com.example.examplemod.fauna.goal;

import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A camuflagem do Catachan Devil: parado na vegetacao, ele fica dificil de perceber.
 *
 * <h2>NAO e invisibilidade, e a diferenca e o desenho todo</h2>
 *
 * O briefing proibe invisibilidade completa, e com razao — um predador que desaparece nao e um
 * predador escondido, e um bug de renderizacao com dano. O que acontece aqui:
 *
 * <ul>
 *   <li>o bicho para de se mover (camuflagem exige estar parado — e isso que a torna uma escolha
 *       e nao um passivo);</li>
 *   <li>a pose de camuflagem entra pela animacao {@code camouflage_stance};</li>
 *   <li>o <b>alcance de deteccao</b> dele cai, o que significa que ele deixa passar alvos que
 *       normalmente perseguiria: camuflar-se custa oportunidade.</li>
 * </ul>
 *
 * A parte visual — brilho e saturacao um pouco menores — pertence ao renderer, que le
 * {@link FaunaEntity#isUsingAbility()} junto com a habilidade em curso. Sem shader e sem camada nova:
 * uma multiplicacao de cor no {@code GeoEntityRenderer}.
 *
 * <h2>Como isto custa quase nada</h2>
 *
 * A condicao e um teste de bloco na posicao do proprio bicho — uma leitura de blockstate, sem busca.
 * E ela so e feita quando ele nao tem alvo, o que no ciclo de vida de um mob apex e a maior parte do
 * tempo.
 */
public class FaunaCamouflageGoal extends Goal {

    private static final int CHECK_INTERVAL = 40;

    private final FaunaEntity animal;
    private final com.example.examplemod.fauna.FaunaAbility stance;

    private int nextCheck;

    public FaunaCamouflageGoal(FaunaEntity animal,
                               com.example.examplemod.fauna.FaunaAbility stance) {
        this.animal = animal;
        this.stance = stance;
        // MOVE reservado: camuflar-se e ficar parado, e sem reservar o movimento o passeio aleatorio
        // continuaria a andar por baixo da pose.
        this.setFlags(java.util.EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.animal.getTarget() != null || this.animal.isAlarmed()
                || this.animal.isUsingAbility()) {
            return false;
        }

        if (this.animal.tickCount < this.nextCheck) {
            return false;
        }
        this.nextCheck = this.animal.tickCount + CHECK_INTERVAL;

        if (!this.animal.canUseAbility()) {
            return false;
        }

        return inCover(this.animal);
    }

    @Override
    public boolean canContinueToUse() {
        return this.animal.isUsingAbility() && this.animal.getTarget() == null;
    }

    @Override
    public void start() {
        this.animal.getNavigation().stop();
        this.animal.startAbility(this.stance);
    }

    /**
     * Vegetacao densa: folha, planta alta ou o mato do proprio mod.
     *
     * <p>Le a posicao dos pes e a da cabeca. So os pes bastariam para grama; folhagem de arvore fica
     * na altura da cabeca, e um Catachan Devil parado sob uma copa esta escondido tanto quanto um
     * parado num arbusto.
     */
    public static boolean inCover(FaunaEntity animal) {
        BlockPos feet = animal.blockPosition();

        return isCover(animal.level().getBlockState(feet))
                || isCover(animal.level().getBlockState(feet.above()));
    }

    private static boolean isCover(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(com.example.examplemod.flora.FloraTags.FLORA);
    }
}
