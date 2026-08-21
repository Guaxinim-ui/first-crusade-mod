package com.example.examplemod.fauna.goal;

import java.util.EnumSet;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A base das goals de habilidade: quem decide <b>quando</b>, deixando a entidade decidir <b>o que</b>.
 *
 * <h2>A divisao, e por que ela e assim</h2>
 *
 * A goal responde uma pergunta de IA ("o alvo esta na distancia certa? ja esperei o bastante?") e
 * chama {@link FaunaEntity#startAbility}. O efeito — dano, particula, empurrao, animacao — vive nos
 * ganchos da entidade. Isso deixa cada habilidade com um dono so e permite dispara-la de fora de uma
 * goal (o comando de debug, um evento de estrutura) sem duplicar nada.
 *
 * <h2>A goal segura o turno enquanto a habilidade corre</h2>
 *
 * {@link #canContinueToUse} devolve true enquanto {@link FaunaEntity#isUsingAbility()}. Sem isso o
 * {@code MeleeAttackGoal} retomaria o controle no tick seguinte ao inicio da preparacao, e o bicho
 * andaria para frente no meio de uma animacao de golpe — o defeito visual mais comum em mob com
 * habilidade, e o mais dificil de diagnosticar depois porque a animacao esta correta.
 *
 * <h2>Cooldown armado ANTES da varredura</h2>
 *
 * {@link #tryStart()} e chamado por {@link #canUse()} so depois de {@link #interval} ter passado. A
 * ordem importa: uma goal que varre primeiro e arma o cooldown depois roda a varredura inteira em
 * todo tick em que a varredura falha, e foi exatamente esse defeito que a Fase E encontrou em
 * {@code SeekWaterGoal} e {@code HerdGoal}.
 */
public abstract class FaunaAbilityGoal extends Goal {

    protected final FaunaEntity animal;
    protected final FaunaAbility ability;

    /** Ticks entre duas tentativas de decidir. Nao e o cooldown da habilidade. */
    private final int interval;

    private int nextCheck;

    protected FaunaAbilityGoal(FaunaEntity animal, FaunaAbility ability, int interval) {
        this.animal = animal;
        this.ability = ability;
        this.interval = Math.max(interval, 1);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * A condicao propria da habilidade. Chamada no maximo uma vez por {@link #interval} ticks, e
     * apenas quando a entidade ja disse que pode usar habilidade e ha alguem por perto.
     */
    protected abstract boolean shouldStart();

    /** Gancho: a goal comecou. A entidade ja disparou a animacao. */
    protected void onStarted() {
    }

    @Override
    public boolean canUse() {
        if (this.animal.isUsingAbility()) {
            return false;
        }

        if (this.animal.tickCount < this.nextCheck) {
            return false;
        }
        this.nextCheck = this.animal.tickCount + this.interval;

        if (!this.animal.canUseAbility()) {
            return false;
        }

        return shouldStart();
    }

    @Override
    public boolean canContinueToUse() {
        return this.animal.isUsingAbility();
    }

    @Override
    public void start() {
        if (this.animal.startAbility(this.ability)) {
            onStarted();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
