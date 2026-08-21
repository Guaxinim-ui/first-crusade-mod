package com.example.examplemod.fauna.effect;

import java.util.List;

import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.performance.graphics.FCServerParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

/**
 * Os efeitos que a fauna partilha: poeira, areia, quitina, veneno e tremor.
 *
 * <h2>Tudo passa pelo orcamento de servidor</h2>
 *
 * Nenhuma chamada aqui usa {@code level.addParticle} — todas usam {@link FCServerParticles} no canal
 * {@link FCServerParticles.Channel#FAUNA}. Isso e deliberado e importa: {@code sendParticles} e o
 * servidor construindo um pacote e mandando para todos os jogadores em volta, e o preset grafico de
 * um cliente nao tem como decidir isso. O dial que decide vive no config de SERVIDOR, e a fauna tem
 * um canal proprio la para que baixar o custo de uma guerra nao apague o bicho que o jogador
 * atravessou um deserto para encontrar.
 *
 * <h2>A poeira usa o bloco do chao, nao uma cor fixa</h2>
 *
 * {@link #dustRing} le o bloco sob o animal e emite {@code block} particles dele. Uma cor fixa de
 * areia mentiria em cinco dos oito biomas do mod: o Duneskuttler emergindo em cinzas de Armageddon
 * larga cinza, na crosta de sal larga sal, e nenhuma das duas e areia. Custa uma leitura de
 * blockstate por efeito, que acontece uma vez por golpe e nao por tick.
 */
public final class FaunaVisualEffects {

    private FaunaVisualEffects() {
    }

    /** Alcance padrao do tremor, em blocos. Curto de proposito: quem sente e quem esta perto. */
    public static final double TREMOR_RADIUS = 24.0D;

    // ==================================================================== poeira

    /**
     * Anel de poeira nos pes, do material do chao.
     *
     * @param radius  raio do anel em blocos
     * @param density quantas emissoes ao longo do anel; cada uma e um envio
     */
    public static void dustRing(ServerLevel level, Entity source, double radius, int density) {
        ParticleOptions dust = groundParticle(level, source.blockPosition().below());
        double y = source.getY() + 0.1D;

        for (int i = 0; i < density; i++) {
            double angle = (Math.PI * 2.0D) * i / density;
            double x = source.getX() + Math.cos(angle) * radius;
            double z = source.getZ() + Math.sin(angle) * radius;

            FCServerParticles.send(level, dust, FCServerParticles.Channel.FAUNA,
                    x, y, z, 3, 0.15D, 0.05D, 0.15D, 0.02D);
        }
    }

    /**
     * Jato vertical de terra ou areia: o bicho saindo do chao.
     *
     * <p>Duas camadas de proposito — a coluna de material do chao da a cor certa, e a fumaca por
     * cima da altura. Sem a fumaca o efeito acaba na altura do joelho e le como um passo, nao como
     * uma emergencia.
     */
    public static void emergeBurst(ServerLevel level, Entity source) {
        ParticleOptions ground = groundParticle(level, source.blockPosition().below());

        FCServerParticles.send(level, ground, FCServerParticles.Channel.FAUNA,
                source.getX(), source.getY() + 0.2D, source.getZ(), 40,
                0.4D, 0.6D, 0.4D, 0.35D);

        FCServerParticles.send(level, ParticleTypes.CAMPFIRE_COSY_SMOKE,
                FCServerParticles.Channel.FAUNA,
                source.getX(), source.getY() + 0.4D, source.getZ(), 8,
                0.3D, 0.1D, 0.3D, 0.05D);
    }

    /**
     * O rastro de quem esta andando por baixo do chao.
     *
     * <p>Uma emissao por chamada, e quem chama ja esta num intervalo — este e o unico efeito da
     * fauna que repete enquanto uma habilidade dura, entao e o unico que poderia virar spam.
     */
    public static void burrowTrail(ServerLevel level, Entity source) {
        BlockPos below = source.blockPosition().below();
        ParticleOptions ground = groundParticle(level, below);

        FCServerParticles.send(level, ground, FCServerParticles.Channel.FAUNA,
                source.getX(), source.getY() + 0.05D, source.getZ(), 4,
                0.2D, 0.02D, 0.2D, 0.03D);
    }

    /** Pedrinhas saltando: o aviso de que algo grande esta se movendo sob os pes do jogador. */
    public static void jumpingPebbles(ServerLevel level, double x, double y, double z, int count) {
        FCServerParticles.send(level, ParticleTypes.CRIT, FCServerParticles.Channel.FAUNA,
                x, y + 0.1D, z, count, 0.3D, 0.25D, 0.3D, 0.12D);
    }

    // ==================================================================== veneno

    /** A carga: veneno acumulando no corpo do bicho antes de sair. */
    public static void toxinCharge(ServerLevel level, Entity source, double spread) {
        FCServerParticles.send(level, ParticleTypes.ENTITY_EFFECT, FCServerParticles.Channel.FAUNA,
                source.getX(), source.getY() + source.getBbHeight() * 0.6D, source.getZ(), 6,
                spread, spread, spread, 0.0D);
    }

    /** A nuvem: o volume todo de uma vez, em duas camadas para ler como gas e nao como faisca. */
    public static void toxicCloud(ServerLevel level, Entity source, double radius) {
        double y = source.getY() + 0.5D;

        FCServerParticles.send(level, ParticleTypes.SNEEZE, FCServerParticles.Channel.FAUNA,
                source.getX(), y, source.getZ(), 60,
                radius * 0.5D, 0.4D, radius * 0.5D, 0.1D);

        FCServerParticles.send(level, ParticleTypes.ENTITY_EFFECT, FCServerParticles.Channel.FAUNA,
                source.getX(), y, source.getZ(), 50,
                radius * 0.4D, 0.5D, radius * 0.4D, 0.0D);
    }

    /** Brilho verde no ferrao antes da picada. */
    public static void stingGlow(ServerLevel level, double x, double y, double z) {
        FCServerParticles.send(level, ParticleTypes.GLOW, FCServerParticles.Channel.FAUNA,
                x, y, z, 5, 0.1D, 0.1D, 0.1D, 0.01D);
    }

    // ==================================================================== mecanico

    /** Faisca e metal: o Cyber-Mastiff trancando a mandibula. */
    public static void sparks(ServerLevel level, Entity source) {
        FCServerParticles.send(level, ParticleTypes.ELECTRIC_SPARK, FCServerParticles.Channel.FAUNA,
                source.getX(), source.getY() + source.getBbHeight() * 0.5D, source.getZ(), 8,
                0.2D, 0.2D, 0.2D, 0.05D);
    }

    /** O olho do auspex acendendo durante a varredura. */
    public static void scanBeam(ServerLevel level, Entity source) {
        FCServerParticles.send(level, ParticleTypes.END_ROD, FCServerParticles.Channel.FAUNA,
                source.getX(), source.getEyeY(), source.getZ(), 2,
                0.05D, 0.05D, 0.05D, 0.0D);
    }

    /** Uivo, rugido, exibicao: sopro discreto de ar. Nunca a nuvem que o veneno usa. */
    public static void breath(ServerLevel level, Entity source, int count) {
        double look = -Math.sin(Math.toRadians(source.getYRot()));
        double forward = Math.cos(Math.toRadians(source.getYRot()));

        FCServerParticles.send(level, ParticleTypes.CLOUD, FCServerParticles.Channel.FAUNA,
                source.getX() + look * 0.6D, source.getEyeY(), source.getZ() + forward * 0.6D,
                count, 0.15D, 0.1D, 0.15D, 0.02D);
    }

    // ==================================================================== tremor

    /**
     * Tremor de tela para quem esta perto o suficiente.
     *
     * <p>Servidor escolhe quem sente, e a magnitude cai com a distancia. Um tremor de intensidade
     * fixa entregue a todo mundo no alcance faria o jogador a 23 blocos sentir o mesmo que o jogador
     * pisado — e o briefing pede o contrario, explicitamente.
     *
     * <p>Nao mexe em posicao nem em rotacao de jogador: o pacote so ajusta o angulo da camera no
     * cliente. Mexer na rotacao real brigaria com o mouse e apareceria como travamento de mira.
     */
    public static void tremor(ServerLevel level, double x, double y, double z,
                              float magnitude, int ticks, double radius) {
        double radiusSq = radius * radius;
        List<ServerPlayer> players = level.players();

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);

            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            double distanceSq = dx * dx + dy * dy + dz * dz;

            if (distanceSq > radiusSq) {
                continue;
            }

            float falloff = (float) (1.0D - Math.sqrt(distanceSq) / radius);
            float felt = magnitude * falloff * falloff;
            if (felt < 0.01F) {
                continue;
            }

            FirstCrusadeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new FaunaTremorPacket(felt, ticks));
        }
    }

    // ==================================================================== chao

    /**
     * A particula que representa o chao em que o animal esta.
     *
     * <p>Cai para {@code POOF} quando o bloco nao tem particula de quebra (ar, agua, folhas com
     * {@code noCollission}): {@code BlockParticleOption} de um bloco sem modelo desenha nada, o que
     * pareceria efeito quebrado em vez de efeito ausente.
     */
    private static ParticleOptions groundParticle(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return ParticleTypes.POOF;
        }

        return new BlockParticleOption(ParticleTypes.BLOCK, state);
    }
}
