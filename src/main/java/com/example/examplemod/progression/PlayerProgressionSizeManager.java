package com.example.examplemod.progression;

import com.example.examplemod.PlayerFaction;
import com.example.examplemod.PlayerFactionData;
import com.example.examplemod.SafeEntityRelocator;
import com.example.examplemod.progression.ork.PlayerOrkEvolutionStage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityEvent;

public final class PlayerProgressionSizeManager {

    private PlayerProgressionSizeManager() {
    }

    /*
     * Guarda o último corpo aplicado no servidor.
     *
     * Serve apenas para saber quando houve crescimento REAL.
     */
    private static final java.util.Map<java.util.UUID, PlayerBody> LAST_BODY =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ================================================================
    // REFRESH
    // ================================================================

    public static void refresh(Player player) {

        /*
         * Faz o Minecraft recalcular:
         *
         * - hitbox
         * - eye height
         * - pose
         */
        player.refreshDimensions();

        if (!(player instanceof ServerPlayer server) || server.isSpectator()) {
            return;
        }

        PlayerBody body = bodyOf(server);

        PlayerBody previous =
                LAST_BODY.put(server.getUUID(), body);

        /*
         * Só consideramos crescimento quando o tamanho realmente aumentou.
         *
         * Comprar XP, receber sync ou simplesmente recalcular atributos
         * NÃO pode mover o jogador.
         */
        boolean grew =
                previous == null
                        ? !body.isVanilla()
                        : body.height() > previous.height()
                        || body.width() > previous.width();

        /*
         * Só procura outro lugar se NENHUMA pose couber.
         *
         * Estar embaixo de um teto baixo não significa estar preso.
         */
        if (grew && !PlayerProgressionPose.anyPoseFits(server)) {
            makeRoom(server);
        }
    }

    public static void forget(java.util.UUID playerId) {
        LAST_BODY.remove(playerId);
    }

    // ================================================================
    // SIZE EVENT
    // ================================================================

    /**
     * ESTE É O MÉTODO IMPORTANTE PARA O BUG DA CÂMERA.
     *
     * Nunca use:
     *
     * event.getOldEyeHeight() * scale
     *
     * e também não usamos:
     *
     * event.getNewEyeHeight() * scale
     *
     * como base.
     *
     * A altura dos olhos é sempre reconstruída DO ZERO usando a pose
     * vanilla atual.
     *
     * Portanto:
     *
     * crouch -> stand -> crouch -> stand
     *
     * pode acontecer mil vezes e o resultado continuará sendo exatamente
     * o mesmo.
     */
    public static void onSize(EntityEvent.Size event) {

        Entity entity = event.getEntity();

        if (!(entity instanceof Player player)) {
            return;
        }

        PlayerBody body = bodyOf(player);

        /*
         * Jogador humano normal:
         * não alteramos absolutamente nada.
         */
        if (body.isVanilla()) {
            return;
        }

        Pose pose = event.getPose();

        float widthScale = body.widthScale();
        float heightScale = body.heightScale();

        /*
         * MUITO IMPORTANTE:
         *
         * Não usamos event.getNewSize() como base.
         *
         * Pegamos diretamente as dimensões VANILLA daquela pose.
         *
         * Isso impede que um tamanho já escalado seja escalado novamente.
         */
        EntityDimensions vanillaDimensions =
                player.getDimensions(pose);

        float scaledWidth =
                vanillaDimensions.width * widthScale;

        float scaledHeight =
                vanillaDimensions.height * heightScale;

        EntityDimensions scaledDimensions =
                EntityDimensions.scalable(
                        scaledWidth,
                        scaledHeight
                );

        /*
         * Define a hitbox nova.
         *
         * false porque vamos controlar a altura dos olhos manualmente.
         */
        event.setNewSize(
                scaledDimensions,
                false
        );

        /*
         * AQUI ESTÁ A CORREÇÃO DEFINITIVA DA CÂMERA.
         *
         * player.getEyeHeight(pose) devolve a altura BASE da pose.
         *
         * STANDING:
         * sempre parte do valor vanilla.
         *
         * CROUCHING:
         * sempre parte do valor vanilla.
         *
         * SWIMMING:
         * sempre parte do valor vanilla.
         *
         * Depois aplicamos o scale UMA ÚNICA VEZ.
         *
         * Não existe nenhum valor anterior envolvido.
         */
        float vanillaEyeHeight =
                player.getEyeHeight(pose);

        float scaledEyeHeight =
                vanillaEyeHeight * heightScale;

        event.setNewEyeHeight(
                scaledEyeHeight
        );
    }

    // ================================================================
    // BODY
    // ================================================================

    public static PlayerBody bodyOf(Player player) {

        /*
         * SERVIDOR:
         *
         * calcula usando a facção real.
         */
        if (player instanceof ServerPlayer server) {
            return serverBody(server);
        }

        /*
         * CLIENTE:
         *
         * usa o corpo enviado pelo servidor.
         */
        return PlayerProgressionClientView.bodyOf(player);
    }

    public static PlayerBody serverBody(ServerPlayer player) {

        PlayerFaction faction =
                PlayerFactionData
                        .get(player.serverLevel())
                        .getFaction(player.getUUID());

        /*
         * ORKS
         */
        if (faction == PlayerFaction.ORKS) {

            PlayerOrkEvolutionStage stage =
                    PlayerProgressionManager
                            .profile(player)
                            .ork()
                            .stage();

            return new PlayerBody(
                    stage.width(),
                    stage.height()
            );
        }

        /*
         * IMPERIUM / HUMANO
         */
        PlayerEvolutionStage stage =
                PlayerProgressionManager
                        .profile(player)
                        .stage();

        return new PlayerBody(
                stage.width(),
                stage.height()
        );
    }

    // ================================================================
    // MAKE ROOM
    // ================================================================

    /**
     * Usado SOMENTE quando o personagem realmente cresce
     * e nenhuma pose consegue caber onde ele está.
     */
    public static void makeRoom(ServerPlayer player) {

        /*
         * Primeiro tenta alguns blocos acima.
         */
        for (int up = 1; up <= 3; up++) {

            AABB lifted =
                    player.getBoundingBox()
                            .move(
                                    0.0D,
                                    up,
                                    0.0D
                            );

            if (player.level()
                    .noCollision(
                            player,
                            lifted
                    )) {

                player.teleportTo(
                        player.getX(),
                        player.getY() + up,
                        player.getZ()
                );

                return;
            }
        }

        /*
         * Se não existir lugar imediatamente acima,
         * procura um ponto seguro próximo.
         */
        net.minecraft.core.BlockPos spot =
                SafeEntityRelocator.findSafeSpotOutside(
                        player.serverLevel(),
                        player.blockPosition(),
                        4
                );

        if (spot != null) {

            player.teleportTo(
                    spot.getX() + 0.5D,
                    spot.getY(),
                    spot.getZ() + 0.5D
            );
        }
    }
}