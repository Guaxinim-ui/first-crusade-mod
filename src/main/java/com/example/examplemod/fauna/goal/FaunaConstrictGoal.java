package com.example.examplemod.fauna.goal;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.world.entity.LivingEntity;

/**
 * A constricao da Greater Malkavan Constrictor: enrolar no alvo e apertar.
 *
 * <h2>O jogador tem de conseguir sair, e ele sai de tres maneiras</h2>
 *
 * O briefing e explicito: "o jogador deve conseguir escapar". Uma habilidade que prende sem saida e a
 * unica coisa num jogo de acao que o jogador nao perdoa, porque ela remove a agencia dele em vez de
 * desafia-la. As tres saidas:
 *
 * <ol>
 *   <li><b>Duracao maxima</b> — a fase ativa da {@link FaunaAbility} acaba sozinha;</li>
 *   <li><b>Dano na serpente</b> — a entidade quebra a constricao ao apanhar (no {@code hurt});</li>
 *   <li><b>Cooldown</b> — depois de soltar, ela nao pode reprender imediatamente.</li>
 * </ol>
 *
 * <h2>Quem NAO pode ser preso</h2>
 *
 * Veiculo, boss e entidade enorme. A regra pratica esta em {@link #canConstrict}: largura ou altura
 * acima do teto sai fora. Prender um Baneblade nao seria uma cena impressionante, seria uma cobra
 * segurando um tanque — e o mod tem tanque.
 */
public class FaunaConstrictGoal extends FaunaAbilityGoal {

    /** Largura maxima do alvo, em blocos. Acima disto e veiculo ou monstro grande. */
    private static final float MAX_TARGET_WIDTH = 1.6F;

    /** Altura maxima do alvo, em blocos. */
    private static final float MAX_TARGET_HEIGHT = 2.6F;

    private final double reach;

    public FaunaConstrictGoal(FaunaEntity animal, FaunaAbility constrict, double reach) {
        super(animal, constrict, 10);
        this.reach = reach;
    }

    @Override
    protected boolean shouldStart() {
        LivingEntity target = this.animal.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (!canConstrict(target)) {
            return false;
        }

        return this.animal.distanceTo(target) <= this.reach;
    }

    /** Criatura pequena ou media, viva, e nao um chefe. */
    public static boolean canConstrict(LivingEntity candidate) {
        if (!candidate.canChangeDimensions()) {
            // O sinal que o vanilla usa para "entidade que nao se comporta como criatura normal":
            // Ender Dragon e Wither devolvem false. Mais barato e mais honesto do que uma lista de
            // classes que ficaria desatualizada no primeiro boss novo.
            return false;
        }

        return candidate.getBbWidth() <= MAX_TARGET_WIDTH
                && candidate.getBbHeight() <= MAX_TARGET_HEIGHT;
    }
}
