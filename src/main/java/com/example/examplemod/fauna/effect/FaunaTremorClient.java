package com.example.examplemod.fauna.effect;

import com.example.examplemod.ExampleMod;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O tremor de tela, do lado do cliente.
 *
 * <h2>Como o tremor e feito, e as duas maneiras erradas de fazer</h2>
 *
 * Aqui o tremor entra por {@link ViewportEvent.ComputeCameraAngles}, o gancho do Forge que roda
 * depois de a camera ja ter os angulos do jogador e antes de a cena ser desenhada. Isso quer dizer
 * que o tremor e <b>puramente visual</b>: a rotacao real do jogador nao muda, a mira nao anda, e
 * nada que o servidor saiba sobre para onde o jogador olha e afetado.
 *
 * <p>As duas alternativas que parecem mais simples e nao servem:
 *
 * <ul>
 *   <li><b>Mexer em {@code player.setYRot/setXRot}</b> — briga com o mouse. O jogador sente a mira
 *       ser arrancada da mao dele, e num tiro em andamento isso muda onde a bala vai.</li>
 *   <li><b>Empurrar o jogador com {@code setDeltaMovement}</b> — o servidor corrige a posicao no
 *       tick seguinte, e o resultado e teleporte, nao tremor.</li>
 * </ul>
 *
 * <h2>O tremor decai, e nunca acumula</h2>
 *
 * Dois eventos proximos nao somam magnitude: {@link #apply} pega o <b>maior</b> dos dois. Somar faria
 * uma manada de Duskhorn carregando junto entregar um tremor que ninguem consegue jogar, e a
 * intensidade deixaria de significar "quao perto voce esta" para significar "quantos bichos ha".
 *
 * <p>A forma da onda e duas senoides de periodos primos entre si (13 e 7 ticks) em eixos diferentes,
 * multiplicadas pelo que resta do tremor ao quadrado. Periodos iguais dariam um balanco circular
 * regular, que le como mareio; periodos que nao fecham dao a irregularidade de um impacto.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class FaunaTremorClient {

    /** Teto de amplitude em graus. Baixo de proposito: o briefing pede leve a moderado. */
    private static final float MAX_DEGREES = 2.6F;

    private static float magnitude;
    private static int remaining;
    private static int duration;

    private FaunaTremorClient() {
    }

    /** Recebe um tremor. O mais forte ganha; nada soma. */
    public static void apply(FaunaTremorPacket packet) {
        if (packet.magnitude() <= magnitude && remaining > 0) {
            return;
        }

        magnitude = Math.min(packet.magnitude(), 1.0F);
        duration = Math.max(packet.ticks(), 1);
        remaining = duration;
    }

    /** Corta o tremor na hora — usado ao trocar de mundo, para nao carregar estado entre saves. */
    public static void clear() {
        magnitude = 0.0F;
        remaining = 0;
        duration = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || remaining <= 0) {
            return;
        }

        remaining--;
        if (remaining == 0) {
            magnitude = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (remaining <= 0 || magnitude <= 0.0F) {
            return;
        }

        // partialTick mantem o tremor suave entre ticks; sem ele a camera pula 20 vezes por segundo
        // e o efeito le como frame perdido.
        float progress = (remaining - (float) event.getPartialTick()) / duration;
        float amplitude = MAX_DEGREES * magnitude * progress * progress;
        float phase = (remaining % 1000) - (float) event.getPartialTick();

        event.setRoll(event.getRoll() + Mth.sin(phase * (Mth.TWO_PI / 13.0F)) * amplitude);
        event.setPitch(event.getPitch() + Mth.sin(phase * (Mth.TWO_PI / 7.0F)) * amplitude * 0.6F);
    }
}
