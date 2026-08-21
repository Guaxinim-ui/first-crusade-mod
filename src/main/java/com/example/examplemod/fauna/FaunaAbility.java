package com.example.examplemod.fauna;

/**
 * Uma habilidade especial de fauna: qual animacao toca, quanto tempo ela leva e quanto tempo o
 * bicho espera antes de poder repetir.
 *
 * <h2>Por que um record por especie e nao um enum global</h2>
 *
 * A mesma habilidade tem nome de animacao diferente em cada modelo: o salto e {@code leap_attack}
 * no Squig e no Knarloc, {@code pounce} no lobo, {@code pounce_attack} no Cyber-Mastiff. Um enum
 * global teria de escolher um nome e estaria errado em tres modelos — e animacao que nao existe no
 * arquivo nao da erro, so nao toca. Entao o nome pertence a especie, e o que esta classe carrega
 * sao os tempos.
 *
 * <h2>As tres fases, e por que so uma delas e salva</h2>
 *
 * <pre>
 *   preparacao (windup)  o bicho se arma: abaixa a cabeca, raspa o chao, infla a garganta
 *   ativa (active)       o golpe acontece; e aqui que o dano sai, e sempre no servidor
 *   descanso (cooldown)  o tempo em que a habilidade nao pode voltar
 * </pre>
 *
 * O briefing pede para nao salvar timer de poucos ticks. Preparacao e fase ativa duram menos de
 * dois segundos e sao recomecadas de graca no tick seguinte, entao morrem no reload. O
 * <b>cooldown</b> e o unico que vale a pena persistir: sem ele, sair e voltar do mundo devolve uma
 * carga de Duskhorn imediata, e o jogador ve o bicho ignorar a propria regra.
 *
 * @param animation nome exato da animacao no {@code .animation.json} da especie
 * @param windup    ticks de preparacao antes do golpe
 * @param active    ticks em que a habilidade esta acontecendo
 * @param cooldown  ticks de espera depois de terminar
 */
public record FaunaAbility(String animation, int windup, int active, int cooldown) {

    /** Habilidade instantanea: sem preparacao, so a animacao e o descanso. */
    public static FaunaAbility instant(String animation, int active, int cooldown) {
        return new FaunaAbility(animation, 0, active, cooldown);
    }

    /** Duracao total, do primeiro tick de preparacao ao ultimo tick ativo. */
    public int duration() {
        return this.windup + this.active;
    }
}
