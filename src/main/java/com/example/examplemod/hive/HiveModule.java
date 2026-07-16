package com.example.examplemod.hive;

import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/**
 * Descrição data-driven de um módulo da Hive (carregada de data/&lt;ns&gt;/hive_modules/*.json).
 *
 * Sockets: um tipo (string livre, ex.: "street", "corridor_l2", "canopy", "foundation",
 * "sealed") por face LOCAL do template. Dois módulos encaixam numa direção quando o
 * socket da face de um é igual ao socket da face oposta do outro, consideradas as
 * rotações. "sealed" nunca encaixa com nada — é parede cega.
 */
public record HiveModule(ResourceLocation id, ResourceLocation template, String category,
                         Vec3i size, int weight, Map<Direction, String> sockets) {

    public static final String SOCKET_SEALED = "sealed";

    /** Tamanho ocupado no mundo para uma rotação (90/270 trocam X e Z). */
    public Vec3i rotatedSize(Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return new Vec3i(size.getZ(), size.getY(), size.getX());
        }
        return size;
    }

    /** Socket exposto na face MUNDIAL {@code worldFace} quando o módulo está girado. */
    public String socketAt(Direction worldFace, Rotation rotation) {
        if (worldFace.getAxis() == Direction.Axis.Y) {
            return sockets.getOrDefault(worldFace, SOCKET_SEALED);
        }
        Direction localFace = inverse(rotation).rotate(worldFace);
        return sockets.getOrDefault(localFace, SOCKET_SEALED);
    }

    /** Dois sockets encaixam se forem iguais e nenhum for "sealed". */
    public static boolean fits(String a, String b) {
        return !SOCKET_SEALED.equals(a) && a.equals(b);
    }

    private static Rotation inverse(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }
}
