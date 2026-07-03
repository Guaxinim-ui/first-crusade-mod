package com.example.examplemod;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Makes construction harmless to the living: before (and while) blocks are placed, any NPC —
 * villager, citizen, troop, animal — standing inside a build volume is walked out to the nearest
 * safe open spot (solid floor, two blocks of air) just outside the area. Players are never
 * teleported: the placement code skips blocks that would intersect a player instead.
 *
 * Rule number one of the whole city system: never place a block in a living thing's head.
 */
public final class SafeEntityRelocator {
    private static final int RELOCATION_RING_LIMIT = 12;

    private SafeEntityRelocator() {
    }

    /** Moves every NPC out of the footprint's volume. Returns false if a player is inside. */
    public static boolean clearFootprint(ServerLevel level, CityStructureFootprint footprint) {
        return clearBox(level, footprint.asAABB(), footprint.getOrigin(),
                Math.max(footprint.getHalfWidth(), footprint.getHalfDepth()));
    }

    /** Moves every NPC out of an arbitrary box (used for wall bands and plaza sweeps). */
    public static boolean clearBox(ServerLevel level, AABB box, BlockPos around, int halfExtent) {
        List<LivingEntity> inside = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isAlive() && !(entity instanceof Player)
        );

        for (LivingEntity entity : inside) {
            BlockPos safe = findSafeSpotOutside(level, around, halfExtent);

            if (safe != null) {
                entity.teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
            }
        }

        return level.getEntitiesOfClass(
                Player.class,
                box,
                player -> player.isAlive() && !player.isSpectator()
        ).isEmpty();
    }

    /**
     * True when placing a solid block at {@code pos} would intersect a living entity (its feet OR
     * head). Construction code must skip (and retry later) instead of placing through them.
     */
    public static boolean isBlockOccupiedByEntity(ServerLevel level, BlockPos pos) {
        AABB blockBox = new AABB(pos);

        return !level.getEntitiesOfClass(
                LivingEntity.class,
                blockBox,
                entity -> entity.isAlive() && entity.getBoundingBox().intersects(blockBox)
        ).isEmpty();
    }

    /** Gently walks NPCs off a block that is about to be placed (players are left alone). */
    public static void nudgeAwayFrom(ServerLevel level, BlockPos pos, BlockPos cityCenter) {
        AABB blockBox = new AABB(pos).inflate(0.5D);

        List<LivingEntity> standing = level.getEntitiesOfClass(
                LivingEntity.class,
                blockBox,
                entity -> entity.isAlive() && !(entity instanceof Player)
        );

        for (LivingEntity entity : standing) {
            BlockPos safe = findSafeSpotOutside(level, pos, 1);

            if (safe != null) {
                entity.teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
            }
        }
    }

    /**
     * The nearest standable spot (solid floor + 2 air) on expanding square rings just outside the
     * given area. Ground level only — never drops anyone into a hole or onto a roof.
     */
    @Nullable
    public static BlockPos findSafeSpotOutside(ServerLevel level, BlockPos around, int halfExtent) {
        for (int ring = halfExtent + 2; ring <= halfExtent + RELOCATION_RING_LIMIT; ring++) {
            for (int i = -ring; i <= ring; i++) {
                BlockPos[] candidates = {
                        around.offset(i, 0, -ring),
                        around.offset(i, 0, ring),
                        around.offset(-ring, 0, i),
                        around.offset(ring, 0, i)
                };

                for (BlockPos candidate : candidates) {
                    BlockPos standable = findStandableNear(level, candidate);

                    if (standable != null) {
                        return standable;
                    }
                }
            }
        }

        return null;
    }

    /** Checks the column at the candidate (±2 blocks of Y) for a floor with two air above. */
    @Nullable
    private static BlockPos findStandableNear(ServerLevel level, BlockPos candidate) {
        for (int dy = 0; dy >= -2; dy--) {
            BlockPos feet = candidate.above(dy);

            boolean floorSolid = !level.isEmptyBlock(feet.below())
                    && level.getFluidState(feet.below()).isEmpty();

            if (floorSolid && level.isEmptyBlock(feet) && level.isEmptyBlock(feet.above())) {
                return feet;
            }
        }

        return null;
    }
}
