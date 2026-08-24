package com.example.examplemod.fauna.world;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * As seis formas que um sitio de fauna pode ter.
 *
 * <p>Sao seis e nao treze porque varios sitios partilham a mesma geometria e diferem so nos blocos:
 * o Squig Pen e o Grox Ranch sao os dois um cercado, e o que os torna reconheciveis e a sucata contra
 * a cerca de madeira, nao a planta baixa.
 */
public enum FaunaSiteShape implements StringRepresentable {

    /**
     * Clareira: chao aberto, vegetacao removida, props espalhados.
     *
     * <p>Barking Toad Clearing e Duskhorn Herd Area. A pista ambiental e a ausencia — mato pisado
     * onde devia haver mato de pe.
     */
    CLEARING("clearing"),

    /**
     * Toca: uma boca escavada na encosta e uma camara rasa atras dela.
     *
     * <p>Cudbear Den e Fenrisian Wolf Den. Escava para dentro do terreno, nao para baixo: uma toca
     * que se le de fora e uma toca que o jogador encontra.
     */
    DEN("den"),

    /**
     * Toca vertical: um poco com uma camara no fundo.
     *
     * <p>Ambull Burrow. E o unico sitio que desce, e por isso e o unico que precisa de teto — sem
     * ele o poco vira um buraco no ceu aberto e deixa de parecer uma toca.
     */
    BURROW("burrow"),

    /**
     * Cercado: um anel de cerca com uma abertura, cocho e bicho dentro.
     *
     * <p>Grox Ranch, Squig Pen, Kroot Knarloc Pen, Imperial Kennel. A abertura importa: um cercado
     * fechado e uma prisao e nao um curral, e o jogador precisa poder entrar.
     */
    PEN("pen"),

    /**
     * Ninho: uma depressao no terreno com bordas de material do proprio bicho.
     *
     * <p>Duneskuttler Nest, Constrictor Nest, Catachan Devil Nest. A depressao e o sinal — o chao
     * afunda onde algo pesado dorme.
     */
    NEST("nest"),

    /**
     * Acampamento: piso batido, uma tenda simples e caixas.
     *
     * <p>Ash Nomad Helamite Post. E o unico sitio da fauna feito por gente, e tem de parecer isso.
     */
    CAMP("camp"),

    /**
     * Ruina: um anel de parede partida meio afundado no terreno, com uma peca no centro.
     *
     * <p>Feita para a tumba Necron sob o sal, mas nao e Necron: e "construcao antiga que ficou".
     * A diferenca para o CAMP e que o CAMP foi montado e esta de pe, e esta foi construida e caiu —
     * por isso a parede tem falhas em vez de porta, e o piso desce um degrau. O bloco do centro e o
     * unico motivo de o jogador entrar.
     */
    RUIN("ruin");

    public static final Codec<FaunaSiteShape> CODEC =
            StringRepresentable.fromEnum(FaunaSiteShape::values);

    private final String name;

    FaunaSiteShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
