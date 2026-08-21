package com.example.examplemod.fauna.effect;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Servidor -> um jogador: "o chao tremeu, e voce estava perto o suficiente para sentir".
 *
 * <p>Dois floats e um int, mandado uma vez por evento — nunca por tick. Quem manda e
 * {@link FaunaVisualEffects#tremor}, que ja decidiu quem sente e com que intensidade; o cliente nao
 * recalcula distancia nenhuma.
 *
 * <p>O pacote carrega a magnitude <b>ja atenuada</b> pela distancia em vez da posicao da fonte. Isso
 * e escolha: mandar a posicao e deixar o cliente medir daria ao cliente uma decisao que e do
 * servidor, e no instante em que os dois discordassem (jogador andando enquanto o pacote viaja) o
 * tremor apareceria mais forte ou mais fraco do que a regra do servidor mandou.
 */
public record FaunaTremorPacket(float magnitude, int ticks) {

    public static void encode(FaunaTremorPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.magnitude);
        buffer.writeVarInt(packet.ticks);
    }

    public static FaunaTremorPacket decode(FriendlyByteBuf buffer) {
        return new FaunaTremorPacket(buffer.readFloat(), buffer.readVarInt());
    }

    /**
     * Aplicado no cliente, e só no cliente.
     *
     * <p>{@link DistExecutor#unsafeRunWhenOn} e o padrao que o resto do mod usa para isto e existe
     * porque um servidor dedicado nao tem a classe de cliente no classpath: uma referencia direta a
     * {@code FaunaTremorClient} faria a JVM tentar resolver {@code Minecraft} num servidor e derrubar
     * o handler. O supplier de supplier e o que mantem o nome fora do bytecode deste metodo.
     */
    public static void handle(FaunaTremorPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();

        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> FaunaTremorClient.apply(packet)));

        ctx.setPacketHandled(true);
    }
}
