package com.example.examplemod.progression;

import com.example.examplemod.FirstCrusadeNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Centraliza a sincronização da progressão do jogador.
 *
 * O perfil completo é privado e vai apenas para o próprio jogador.
 *
 * O tamanho/corpo é público e precisa ser enviado para todos os clientes
 * que estão renderizando aquele jogador.
 *
 * IMPORTANTE:
 *
 * syncProfile() deve ser usado quando somente dados como XP, pontos,
 * Dentu etc. mudarem.
 *
 * sync() deve ser usado quando existe a possibilidade do CORPO do jogador
 * ter mudado, por exemplo:
 *
 * - evolução Astartes;
 * - evolução Ork;
 * - Boy -> Nob;
 * - Nob -> Warboss;
 * - cirurgia;
 * - mudança de estágio;
 * - login/respawn quando precisamos garantir o tamanho correto.
 *
 * Isso evita chamar refreshDimensions() sem necessidade e impede que
 * alterações simples como ganhar XP mexam na câmera.
 */
public final class PlayerProgressionNetwork {

    private PlayerProgressionNetwork() {
    }

    /**
     * Sincroniza SOMENTE os dados privados da progressão.
     *
     * NÃO envia SyncPlayerStagePacket.
     *
     * Use este método para:
     *
     * - XP;
     * - Doctrine Points;
     * - Commander XP;
     * - informações que não alteram o tamanho físico.
     */
    public static void syncProfile(ServerPlayer player) {

        PlayerProgressionProfile profile =
                PlayerProgressionManager.profile(player);

        FirstCrusadeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncPlayerProgressionPacket(
                        profile,
                        com.example.examplemod.PlayerFactionData
                                .get(player.serverLevel())
                                .getFaction(player.getUUID()),
                        com.example.examplemod.WaaaghOverlordManager
                                .getTier(player.serverLevel())
                )
        );
    }

    /**
     * Sincronização COMPLETA.
     *
     * Envia:
     *
     * 1. Perfil para o próprio jogador.
     * 2. Corpo/tamanho para todos que estão vendo o jogador.
     *
     * Só deve ser usada quando o tamanho ou estágio físico pode ter mudado.
     */
    public static void sync(ServerPlayer player) {

        /*
         * Primeiro sincroniza os dados da progressão.
         */
        syncProfile(player);

        /*
         * Depois sincroniza o corpo.
         *
         * Esse packet pode causar refreshDimensions() no cliente,
         * portanto NÃO deve ser enviado em toda kill ou ganho simples de XP.
         */
        FirstCrusadeNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new SyncPlayerStagePacket(
                        player.getUUID(),
                        PlayerProgressionSizeManager.serverBody(player)
                )
        );
    }

    /**
     * Envia o tamanho de um jogador específico para um cliente que acabou
     * de começar a rastreá-lo.
     *
     * Isso é necessário porque sync() somente envia para quem estava
     * rastreando o jogador naquele momento.
     *
     * Exemplo:
     *
     * Um Warboss está parado.
     * Outro jogador chega depois.
     *
     * O novo jogador precisa receber o tamanho correto do Warboss.
     */
    public static void syncStageTo(
            ServerPlayer watcher,
            ServerPlayer subject
    ) {

        FirstCrusadeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> watcher),
                new SyncPlayerStagePacket(
                        subject.getUUID(),
                        PlayerProgressionSizeManager.serverBody(subject)
                )
        );
    }
}