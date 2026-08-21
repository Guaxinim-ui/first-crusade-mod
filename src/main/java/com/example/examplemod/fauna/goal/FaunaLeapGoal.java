package com.example.examplemod.fauna.goal;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.world.entity.LivingEntity;

/**
 * O salto sobre o alvo: Squig, Knarloc, lobo, Cyber-Mastiff, Helamite.
 *
 * <p>Uma faixa de distancia, chao sob os pes, e um teto de diferenca de altura. As tres condicoes
 * existem para o mesmo motivo: um salto que sai errado prende o bicho no ar ou dentro de uma parede,
 * e nenhuma das duas coisas parece habilidade. Especialmente a altura — saltar para cima de um alvo
 * quatro blocos acima faz o bicho bater no teto e cair, o que le como bug de fisica.
 *
 * <p>{@code onGround} e a condicao que ninguem lembra de por e que salva o resto: sem ela o bicho
 * dispara um segundo salto no meio do primeiro, e a soma dos dois impulsos manda ele para fora do
 * mundo visivel.
 */
public class FaunaLeapGoal extends FaunaAbilityGoal {

    private final double minDistance;
    private final double maxDistance;
    private final double maxHeightDifference;

    public FaunaLeapGoal(FaunaEntity animal, FaunaAbility ability,
                         double minDistance, double maxDistance) {
        this(animal, ability, minDistance, maxDistance, 2.5D);
    }

    public FaunaLeapGoal(FaunaEntity animal, FaunaAbility ability,
                         double minDistance, double maxDistance, double maxHeightDifference) {
        super(animal, ability, 8);
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.maxHeightDifference = maxHeightDifference;
    }

    @Override
    protected boolean shouldStart() {
        if (!this.animal.onGround() || this.animal.isInWater()) {
            return false;
        }

        LivingEntity target = this.animal.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (Math.abs(target.getY() - this.animal.getY()) > this.maxHeightDifference) {
            return false;
        }

        double distance = this.animal.distanceTo(target);
        if (distance < this.minDistance || distance > this.maxDistance) {
            return false;
        }

        return this.animal.getSensing().hasLineOfSight(target);
    }
}
