package com.example.examplemod.performance.compat;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.ExampleMod;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Which performance mods are installed alongside First Crusade, and what the mod expects of each.
 *
 * <h2>Detection only. Never a dependency.</h2>
 *
 * Everything here goes through {@link ModList#isLoaded(String)}, which takes a string. Not one line
 * of First Crusade references a class from any of these mods, so the JVM never tries to load one
 * that is absent — which is the difference between "works better with Embeddium" and "crashes
 * without Embeddium". The mod starts, runs and plays with none of them installed; that is a
 * requirement, not an aspiration.
 *
 * <h2>Why bother detecting at all, if nothing branches on it</h2>
 *
 * Because the first question after "the game is slow" is "what have you got installed", and the
 * answer should not depend on the player reading their own mods folder correctly. {@code /fc compat}
 * prints this table, so a report about performance arrives with its own context.
 *
 * <h2>What is NOT claimed here</h2>
 *
 * Nothing in this file asserts that any combination has been tested. See {@code docs/PERFORMANCE.md}
 * — the compatibility matrix there is deliberately empty of results, because filling it in requires
 * actually running each combination, and doing that is the owner's next job, not a guess of mine.
 */
public final class FirstCrusadeModCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How strongly First Crusade wants a given mod present. */
    public enum Recommendation {
        /** The mod will not run correctly without it. Nothing in this list is REQUIRED. */
        OBRIGATORIO,
        /** Clearly worth installing for most players or servers. */
        RECOMENDADO,
        /** Helps in specific situations; harmless to leave out. */
        OPCIONAL,
        /** For development and diagnosis, not for playing. */
        DEV
    }

    /** Where a mod does its work. */
    public enum Side {
        CLIENTE,
        SERVIDOR,
        CLIENTE_E_SERVIDOR
    }

    /**
     * One known performance mod.
     *
     * @param modId       the id looked up in {@link ModList}, best-effort and easy to correct
     * @param displayName how it is written in its own documentation
     * @param recommendation how much First Crusade wants it
     * @param side        where it acts
     * @param note        the one thing worth knowing about it in this mod's context
     */
    public record KnownMod(String modId, String displayName, Recommendation recommendation,
                           Side side, String note) {

        public boolean isLoaded() {
            return ModList.get().isLoaded(this.modId);
        }
    }

    /**
     * The stack from the performance brief, classified.
     *
     * <p>Nothing is OBRIGATORIO on purpose. A mod that must be installed for First Crusade to work
     * is a dependency, and the brief is explicit that these are not dependencies.
     */
    public static final List<KnownMod> KNOWN = List.of(
            new KnownMod("embeddium", "Embeddium", Recommendation.RECOMENDADO, Side.CLIENTE,
                    "Renderizador de chunks otimizado. O maior ganho de FPS da lista. "
                            + "Nao afeta TPS: uma batalha que derruba o servidor continua derrubando."),

            new KnownMod("modernfix", "ModernFix", Recommendation.RECOMENDADO, Side.CLIENTE_E_SERVIDOR,
                    "Corta trabalho de inicializacao e memoria. Util dos dois lados; num servidor "
                            + "dedicado e das poucas coisas da lista que ajudam de verdade."),

            new KnownMod("ferritecore", "FerriteCore", Recommendation.RECOMENDADO, Side.CLIENTE_E_SERVIDOR,
                    "Reduz memoria de blockstates. Interessa mais a este mod que a maioria, porque "
                            + "o kit decorativo do Hive registra centenas de blocos."),

            new KnownMod("immediatelyfast", "ImmediatelyFast", Recommendation.OPCIONAL, Side.CLIENTE,
                    "Acelera renderizacao imediata (GUI, particula, texto). Verificar as telas do "
                            + "mod: Command Core, Strategium, Ork Camp e o terminal de navegacao."),

            new KnownMod("entityculling", "Entity Culling", Recommendation.OPCIONAL, Side.CLIENTE,
                    "Deixa de renderizar entidade fora de vista. Onde mais rende aqui: Hive, "
                            + "corredores, bunkers, hangares. Testar com os mobs GeckoLib."),

            new KnownMod("oculus", "Oculus", Recommendation.OPCIONAL, Side.CLIENTE,
                    "Suporte a shaders. Sem ele o mod roda igual, so sem shader."),

            new KnownMod("chunky", "Chunky", Recommendation.RECOMENDADO, Side.SERVIDOR,
                    "Pre-geracao de chunk. Importante aqui por causa dos planetas: gerar terreno "
                            + "no instante em que o jogador viaja pelo Spaceport e o pior momento."),

            new KnownMod("spark", "spark", Recommendation.DEV, Side.CLIENTE_E_SERVIDOR,
                    "Profiler. E a unica ferramenta que atribui tempo por secao do tick; sem ele "
                            + "otimizar vira chute."),

            new KnownMod("fastsuite", "FastSuite", Recommendation.OPCIONAL, Side.SERVIDOR,
                    "Acelera resolucao de receitas. Ainda nao vale a pena: rende quando o mod "
                            + "tiver muitas receitas de arma, armadura, veiculo e municao."));

    private FirstCrusadeModCompat() {
    }

    /** The known mods that are actually present right now. */
    public static List<KnownMod> loaded() {
        List<KnownMod> present = new ArrayList<>();

        for (KnownMod known : KNOWN) {
            if (known.isLoaded()) {
                present.add(known);
            }
        }

        return present;
    }

    /**
     * Logs the detected stack once at startup.
     *
     * <p>So that any log a player sends carries the answer to "what else is installed" without
     * anybody having to ask.
     */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Startup {
        private Startup() {
        }

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                List<KnownMod> present = loaded();

                if (present.isEmpty()) {
                    LOGGER.info("First Crusade: nenhum mod de performance conhecido detectado. "
                            + "O mod funciona assim; veja docs/PERFORMANCE.md para recomendacoes.");
                    return;
                }

                StringBuilder names = new StringBuilder();
                for (KnownMod known : present) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(known.displayName());
                }

                LOGGER.info("First Crusade: mods de performance detectados: {}.", names);
            });
        }
    }
}
