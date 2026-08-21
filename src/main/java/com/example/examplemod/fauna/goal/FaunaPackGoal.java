package com.example.examplemod.fauna.goal;

import java.util.List;

import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A matilha: quando um lobo escolhe alvo, os vizinhos adotam o mesmo.
 *
 * <h2>Coordenacao de graca, e por que nao mais do que isso</h2>
 *
 * O briefing pede explicitamente para NAO implementar coordenacao pesada, e a razao aparece no custo:
 * uma matilha "de verdade" (flanquear, cercar, revezar) precisa de um estado partilhado, de uma
 * atribuicao de papeis e de um pathfinder por lobo por decisao. O que esta aqui e uma linha de
 * pensamento diferente: <b>o alvo se propaga</b>. Cinco lobos atacando a mesma coisa parecem uma
 * matilha, custam uma consulta a cada tres segundos, e nunca travam.
 *
 * <h2>A varredura, e o unico jeito de faze-la barata</h2>
 *
 * A consulta de entidades e a coisa cara desta classe, entao ela acontece:
 *
 * <ul>
 *   <li>so quando <b>este</b> lobo tem alvo (o caso raro, nao o comum);</li>
 *   <li>uma vez por {@link #INTERVAL} ticks, com o relogio armado <b>antes</b> da varredura;</li>
 *   <li>num raio pequeno, e filtrando pela propria classe antes de qualquer outra coisa.</li>
 * </ul>
 *
 * Armar o relogio antes e o detalhe que a Fase E aprendeu doendo: com o relogio depois, uma varredura
 * que nao encontra ninguem se repete no tick seguinte, para sempre.
 */
public class FaunaPackGoal extends Goal {

    private static final int INTERVAL = 60;

    private final FaunaEntity wolf;
    private final double radius;

    private int nextCheck;

    public FaunaPackGoal(FaunaEntity wolf, double radius) {
        this.wolf = wolf;
        this.radius = radius;
        // Sem flags: esta goal nao move, nao olha e nao ataca. Ela so passa informacao adiante, e
        // reservar MOVE aqui impediria o lobo de perseguir enquanto ela roda.
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.wolf.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (this.wolf.tickCount < this.nextCheck) {
            return false;
        }
        this.nextCheck = this.wolf.tickCount + INTERVAL;

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return false;                   // um pulso, nunca um estado
    }

    @Override
    public void start() {
        LivingEntity target = this.wolf.getTarget();
        if (target == null) {
            return;
        }

        List<? extends FaunaEntity> pack = this.wolf.level().getEntitiesOfClass(
                this.wolf.getClass(), this.wolf.getBoundingBox().inflate(this.radius),
                other -> other != this.wolf && other.isAlive() && other.getTarget() == null);

        for (FaunaEntity member : pack) {
            member.setTarget(target);
        }
    }
}
