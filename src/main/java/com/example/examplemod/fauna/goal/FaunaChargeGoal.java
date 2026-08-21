package com.example.examplemod.fauna.goal;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.world.entity.LivingEntity;

/**
 * A carga: abaixa a cabeca, raspa o chao, e depois corre em linha reta.
 *
 * <p>Grox e Duskhorn. As duas metades da habilidade sao muito diferentes de proposito: a preparacao
 * e o aviso que o jogador tem para sair da frente, e a corrida e uma <b>linha reta</b> que nao segue
 * o alvo. Uma carga que persegue nao e uma carga, e uma investida teleguiada — e o jogador perde a
 * unica coisa que ele pode fazer contra ela, que e se mover para o lado.
 *
 * <p>Quem calcula a direcao e a entidade, no gancho {@code onAbilityStrike}: e ela que congela o
 * vetor no instante em que a corrida comeca. Esta goal so decide que a hora chegou.
 */
public class FaunaChargeGoal extends FaunaAbilityGoal {

    private final double minDistance;
    private final double maxDistance;

    /**
     * @param minDistance perto demais nao da tempo de acelerar; abaixo disto o bicho so morde
     * @param maxDistance longe demais e um convite para o alvo simplesmente andar para o lado
     */
    public FaunaChargeGoal(FaunaEntity animal, FaunaAbility ability,
                           double minDistance, double maxDistance) {
        super(animal, ability, 10);
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
    }

    @Override
    protected boolean shouldStart() {
        LivingEntity target = this.animal.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        double distance = this.animal.distanceTo(target);
        if (distance < this.minDistance || distance > this.maxDistance) {
            return false;
        }

        // Sem linha de visao a carga acerta uma parede, e o bicho fica preso no proprio efeito.
        return this.animal.getSensing().hasLineOfSight(target);
    }

    @Override
    protected void onStarted() {
        // Parado durante a preparacao. E o que faz a preparacao ser legivel: se ele continuasse
        // andando, o jogador nao teria como distinguir o aviso do movimento normal.
        this.animal.getNavigation().stop();
    }
}
