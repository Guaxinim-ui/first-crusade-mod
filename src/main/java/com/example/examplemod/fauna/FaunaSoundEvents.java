package com.example.examplemod.fauna;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

/**
 * A voz de cada especie da fauna, e o portao que impede a fauna de falar sem parar.
 *
 * <h2>Sao sons do vanilla, e isso e uma escolha declarada — nao uma omissao</h2>
 *
 * O dono entregou modelos, texturas e animacoes. Nao entregou audio, e nenhum {@code .ogg} de fauna
 * existe no mod. As duas saidas possiveis eram:
 *
 * <ol>
 *   <li>registrar {@code SoundEvent} proprios para as quinze especies e apontar para arquivos que nao
 *       existem — o jogo aceita, e o resultado e fauna <b>muda</b>, com um log de asset faltando por
 *       som;</li>
 *   <li>escolher, para cada especie, o conjunto vanilla que menos mente sobre o que ela e.</li>
 * </ol>
 *
 * A segunda. Um Grox com voz de Ravager e um Grox; um Grox com um {@code SoundEvent} vazio e um bug.
 * Quando houver audio proprio, este arquivo e o unico lugar que muda: as entidades pedem
 * {@code FaunaSoundEvents.ambient(...)}, nunca {@code SoundEvents.X} direto.
 *
 * <h2>O portao de cooldown</h2>
 *
 * O briefing pede que grandes predadores emitam som distante ocasionalmente e que nada toque
 * constantemente. {@link #canSpeak} e a resposta: um unico comparativo de inteiro contra o relogio do
 * mundo, sem timer por entidade, sem estado para salvar. O intervalo e por chamada e nao global, entao
 * o rugido territorial pode ter cadencia diferente do uivo sem inventar dois campos.
 */
public final class FaunaSoundEvents {

    private FaunaSoundEvents() {
    }

    /** Cadencia minima de um som ambiente grande, em ticks. Vinte segundos. */
    public static final int AMBIENT_INTERVAL = 400;

    /** Cadencia minima de um som de habilidade, em ticks. Cinco segundos. */
    public static final int ABILITY_INTERVAL = 100;

    /**
     * Se este bicho pode emitir som agora, dado um intervalo minimo.
     *
     * <p>A fase e derivada do id da entidade e nao de um sorteio: dois Fenrisian Wolves lado a lado
     * uivam em momentos diferentes de graca, e o custo continua sendo um resto de divisao. Com sorteio
     * seria preciso um campo por bicho e uma chamada de random por tick.
     */
    public static boolean canSpeak(Entity entity, int interval) {
        long time = entity.level().getGameTime() + entity.getId() * 7L;
        return time % interval == 0L;
    }

    // ==================================================================== os conjuntos
    //
    // A regra de escolha, em ordem de importancia: massa do corpo primeiro, material depois. Um
    // predador de duas toneladas com voz de lobo soa pequeno, e nenhuma quantidade de textura
    // conserta isso. Por isso a fauna grande empresta do Ravager e do Hoglin, a mecanica empresta
    // do Iron Golem, e o artropode empresta do Spider e do Silverfish.

    // ---------------------------------------------------------------- lobo de Fenris

    public static SoundEvent wolfAmbient() {
        return SoundEvents.WOLF_GROWL;
    }

    public static SoundEvent wolfHurt() {
        return SoundEvents.WOLF_HURT;
    }

    public static SoundEvent wolfDeath() {
        return SoundEvents.WOLF_DEATH;
    }

    /** O uivo. Emprestado do Warden por alcance: e o unico som vanilla que viaja longe. */
    public static SoundEvent wolfHowl() {
        return SoundEvents.WOLF_HOWL;
    }

    // ---------------------------------------------------------------- fauna grande

    /** Duskhorn, Cudbear, Knarloc, Grox: corpo pesado. */
    public static SoundEvent largeBeastAmbient() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    public static SoundEvent largeBeastHurt() {
        return SoundEvents.RAVAGER_HURT;
    }

    public static SoundEvent largeBeastDeath() {
        return SoundEvents.RAVAGER_DEATH;
    }

    public static SoundEvent largeBeastStep() {
        return SoundEvents.RAVAGER_STEP;
    }

    /** A carga: o som que avisa que a coisa vai andar em linha reta. */
    public static SoundEvent chargeRoar() {
        return SoundEvents.RAVAGER_ROAR;
    }

    /** Exibicao de ameaca de fauna grande. Mais curto que a carga, de proposito. */
    public static SoundEvent threatDisplay() {
        return SoundEvents.HOGLIN_ANGRY;
    }

    /** Rugido territorial do Cudbear: o mais grave que o vanilla tem. */
    public static SoundEvent territorialRoar() {
        return SoundEvents.WARDEN_ROAR;
    }

    // ---------------------------------------------------------------- artropode

    /** Ambull, Duneskuttler, Catachan Devil: quitina. */
    public static SoundEvent arthropodAmbient() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    public static SoundEvent arthropodHurt() {
        return SoundEvents.SPIDER_HURT;
    }

    public static SoundEvent arthropodDeath() {
        return SoundEvents.SPIDER_DEATH;
    }

    public static SoundEvent arthropodStep() {
        return SoundEvents.SPIDER_STEP;
    }

    /** Mandibula fechando. */
    public static SoundEvent mandibles() {
        return SoundEvents.SILVERFISH_HURT;
    }

    /** O bicho entrando no chao. */
    public static SoundEvent burrow() {
        return SoundEvents.GRAVEL_BREAK;
    }

    /** O bicho saindo do chao. Mais alto que o burrow: e o susto. */
    public static SoundEvent emerge() {
        return SoundEvents.WARDEN_EMERGE;
    }

    /** O golpe no chao. */
    public static SoundEvent groundSlam() {
        return SoundEvents.GENERIC_EXPLODE;
    }

    // ---------------------------------------------------------------- reptil e anfibio

    public static SoundEvent serpentAmbient() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    public static SoundEvent serpentHurt() {
        return SoundEvents.HOGLIN_HURT;
    }

    public static SoundEvent serpentDeath() {
        return SoundEvents.HOGLIN_DEATH;
    }

    /** A investida da cabeca. */
    public static SoundEvent serpentStrike() {
        return SoundEvents.SNIFFER_SCENTING;
    }

    public static SoundEvent toadAmbient() {
        return SoundEvents.FROG_AMBIENT;
    }

    public static SoundEvent toadHurt() {
        return SoundEvents.FROG_HURT;
    }

    public static SoundEvent toadDeath() {
        return SoundEvents.FROG_DEATH;
    }

    /** A carga toxica inflando. O aviso que da ao jogador os segundos para correr. */
    public static SoundEvent toxinCharge() {
        return SoundEvents.FROG_LONG_JUMP;
    }

    /** A descarga. */
    public static SoundEvent toxinBurst() {
        return SoundEvents.DRAGON_FIREBALL_EXPLODE;
    }

    // ---------------------------------------------------------------- mecanico

    /** Cyber-Mastiff: metade cao, metade implante. A voz e de cao; o corpo, de maquina. */
    public static SoundEvent mastiffAmbient() {
        return SoundEvents.WOLF_GROWL;
    }

    public static SoundEvent mastiffHurt() {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    public static SoundEvent mastiffDeath() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    public static SoundEvent mastiffStep() {
        return SoundEvents.IRON_GOLEM_STEP;
    }

    /** O auspex varrendo. */
    public static SoundEvent auspexScan() {
        return SoundEvents.BEACON_ACTIVATE;
    }

    /** A mandibula travando na presa. */
    public static SoundEvent combatLock() {
        return SoundEvents.IRON_TRAPDOOR_CLOSE;
    }

    // ---------------------------------------------------------------- salto

    /** Qualquer salto grande saindo do chao. */
    public static SoundEvent leapOff() {
        return SoundEvents.RAVAGER_STEP;
    }

    /** Pouso de salto grande. */
    public static SoundEvent leapLand() {
        return SoundEvents.GENERIC_BIG_FALL;
    }
}
