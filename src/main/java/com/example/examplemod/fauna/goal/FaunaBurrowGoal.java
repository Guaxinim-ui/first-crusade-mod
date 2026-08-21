package com.example.examplemod.fauna.goal;

import java.util.EnumSet;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Enterrar-se quando nao ha nada acontecendo.
 *
 * <h2>Enterrado nao quebra bloco</h2>
 *
 * O briefing proibe destruicao massiva de terreno, e a proibicao e boa por um motivo que vai alem do
 * custo: um Ambull que abre tuneis reais deixa o mapa cheio de buracos permanentes que nunca fecham,
 * e depois de duas horas o deserto e um queijo. Entao "enterrado" e um <b>estado</b>: o modelo nao e
 * desenhado, a colisao sai, e o que o jogador ve e poeira andando pelo chao. Nenhum bloco muda.
 *
 * <h2>Por que enterrar exige que NAO haja ninguem por perto</h2>
 *
 * Enterrar na frente do jogador seria o bicho desaparecendo — le como despawn, nao como emboscada. A
 * emboscada so funciona se o jogador chegar num deserto que ja esta armado. Entao esta goal exige
 * ausencia de alvo <i>e</i> ausencia de espectador, e por isso ela quase nunca roda: o custo dela e
 * uma consulta a lista de jogadores a cada tres segundos.
 */
public class FaunaBurrowGoal extends Goal {

    private static final int CHECK_INTERVAL = 60;

    private final FaunaEntity animal;
    private final FaunaAbility burrow;

    /** Ticks de calma exigidos antes de enterrar. Impede enterrar entre dois golpes de uma briga. */
    private final int settleTicks;

    private int calmSince = -1;
    private int nextCheck;

    public FaunaBurrowGoal(FaunaEntity animal, FaunaAbility burrow, int settleTicks) {
        this.animal = animal;
        this.burrow = burrow;
        this.settleTicks = settleTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.animal.isBurrowed() || this.animal.isUsingAbility()) {
            this.calmSince = -1;
            return false;
        }

        if (this.animal.getTarget() != null || this.animal.isAlarmed()) {
            this.calmSince = -1;
            return false;
        }

        if (this.animal.tickCount < this.nextCheck) {
            return false;
        }
        this.nextCheck = this.animal.tickCount + CHECK_INTERVAL;

        if (this.animal.level().getNearestPlayer(this.animal, FaunaEntity.ABILITY_RADIUS) != null) {
            this.calmSince = -1;
            return false;
        }

        if (this.calmSince < 0) {
            this.calmSince = this.animal.tickCount;
            return false;
        }

        if (this.animal.tickCount - this.calmSince < this.settleTicks) {
            return false;
        }

        // Nao enterrar em cima de agua nem no ar: nos dois casos o efeito de poeira nao teria de onde
        // sair e o bicho ficaria invisivel sobre nada.
        return this.animal.onGround() && !this.animal.isInWater();
    }

    @Override
    public boolean canContinueToUse() {
        return this.animal.isUsingAbility();
    }

    @Override
    public void start() {
        this.animal.getNavigation().stop();
        this.animal.startAbility(this.burrow);
        this.calmSince = -1;
    }
}
