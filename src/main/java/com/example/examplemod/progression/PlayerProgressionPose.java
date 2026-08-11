package com.example.examplemod.progression;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class PlayerProgressionPose {

    private PlayerProgressionPose() {
    }

    private static boolean ours(Pose pose) {
        return pose == Pose.CROUCHING || pose == Pose.SWIMMING;
    }

    public static void tick(Player player) {

        PlayerBody body = PlayerProgressionSizeManager.bodyOf(player);

        if (body.isVanilla()) {
            release(player);
            return;
        }

        if (player.isSpectator()
                || player.isPassenger()
                || player.isSleeping()
                || player.isSwimming()
                || player.isFallFlying()
                || player.isAutoSpinAttack()
                || player.getAbilities().flying) {

            release(player);
            return;
        }

        boolean wantsToCrouch = player.isShiftKeyDown();

        /*
         * Enquanto o jogador está segurando Shift,
         * deixa o Minecraft usar CROUCHING normalmente
         * se a hitbox real couber.
         */
        if (wantsToCrouch) {

            if (fits(player, Pose.CROUCHING, body)) {
                release(player);
                return;
            }

            /*
             * Se nem agachado couber, tenta a pose baixa.
             */
            if (fits(player, Pose.SWIMMING, body)) {
                player.setForcedPose(Pose.SWIMMING);
            }

            return;
        }

        /*
         * O jogador soltou Shift.
         *
         * Só permite levantar se a HITBOX COMPLETA
         * realmente couber no espaço.
         */
        if (fits(player, Pose.STANDING, body)) {
            release(player);
            return;
        }

        /*
         * Não cabe em pé.
         * Mantém agachado mesmo depois de soltar Shift.
         */
        if (fits(player, Pose.CROUCHING, body)) {
            player.setForcedPose(Pose.CROUCHING);
            return;
        }

        /*
         * Espaço ainda menor.
         */
        if (fits(player, Pose.SWIMMING, body)) {
            player.setForcedPose(Pose.SWIMMING);
        }
    }

    public static boolean anyPoseFits(Player player) {

        PlayerBody body = PlayerProgressionSizeManager.bodyOf(player);

        return fits(player, Pose.STANDING, body)
                || fits(player, Pose.CROUCHING, body)
                || fits(player, Pose.SWIMMING, body);
    }

    private static void release(Player player) {

        Pose forced = player.getForcedPose();

        if (forced != null && ours(forced)) {
            player.setForcedPose(null);
        }
    }

    private static boolean fits(
            Player player,
            Pose pose,
            PlayerBody body
    ) {

        AABB box = fullBodyBox(player, pose, body);

        /*
         * Margem microscópica somente para evitar
         * erros de ponto flutuante encostando exatamente
         * no chão ou teto.
         */
        box = box.deflate(1.0E-7D);

        return player.level().noCollision(player, box);
    }

    private static AABB fullBodyBox(
            Player player,
            Pose pose,
            PlayerBody body
    ) {

        EntityDimensions vanillaPose = player.getDimensions(pose);

        double width =
                vanillaPose.width * body.widthScale();

        double height =
                vanillaPose.height * body.heightScale();

        double halfWidth = width * 0.5D;

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        return new AABB(
                x - halfWidth,
                y,
                z - halfWidth,
                x + halfWidth,
                y + height,
                z + halfWidth
        );
    }
}