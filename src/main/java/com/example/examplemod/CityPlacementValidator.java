package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * All the "don't build stupidly" checks, run ONCE when a site is being chosen (never per tick).
 * A candidate footprint is only accepted when:
 *
 * - it doesn't overlap any registered footprint of the city (margins included);
 * - it doesn't cover the plaza, the avenues or the Command Core;
 * - it doesn't straddle the curtain wall band (unless it IS a defense structure);
 * - the ground under every column is solid (no building over holes/water) and the build volume
 *   only contains replaceable blocks (air/plants) — so it can never carve into an existing
 *   building, vanilla house or terrain feature;
 * - no player is standing inside the volume (NPCs are fine — the {@link SafeEntityRelocator}
 *   walks them out before blocks are placed).
 */
public final class CityPlacementValidator {
    private CityPlacementValidator() {
    }

    public static boolean isValidSite(
            ServerLevel level,
            CityLayoutPlan plan,
            CityStructureFootprint candidate,
            CityLayoutPlan.Zone zone
    ) {
        if (plan.collides(candidate)) {
            return false;
        }

        BlockPos origin = candidate.getOrigin();
        int halfWidth = candidate.getHalfWidth();
        int halfDepth = candidate.getHalfDepth();

        boolean isDefense = zone == CityLayoutPlan.Zone.DEFENSE;

        for (int x = -halfWidth - 1; x <= halfWidth + 1; x++) {
            for (int z = -halfDepth - 1; z <= halfDepth + 1; z++) {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;

                // Never on the plaza, the avenues, or (for non-defense buildings) the wall band.
                // The one-block skirt around the footprint keeps doors from opening into a wall.
                if (plan.isRoadOrPlaza(worldX, worldZ)) {
                    return false;
                }

                if (!isDefense && plan.isOnWallBand(worldX, worldZ)) {
                    return false;
                }

                // The Core itself (plus breathing room) is sacred ground.
                if (Math.abs(worldX - plan.getCenter().getX()) <= 2
                        && Math.abs(worldZ - plan.getCenter().getZ()) <= 2) {
                    return false;
                }
            }
        }

        if (!isTerrainBuildable(level, candidate)) {
            return false;
        }

        return !isPlayerInside(level, candidate);
    }

    /** Solid floor under every column, and nothing but replaceable blocks in the volume. */
    public static boolean isTerrainBuildable(ServerLevel level, CityStructureFootprint candidate) {
        BlockPos origin = candidate.getOrigin();

        for (int x = -candidate.getHalfWidth(); x <= candidate.getHalfWidth(); x++) {
            for (int z = -candidate.getHalfDepth(); z <= candidate.getHalfDepth(); z++) {
                BlockPos floor = origin.offset(x, -1, z);

                if (level.isEmptyBlock(floor) || !level.getFluidState(floor).isEmpty()) {
                    return false;
                }

                for (int y = 0; y <= candidate.getHeight(); y++) {
                    if (!isReplaceable(level, origin.offset(x, y, z))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public static boolean isReplaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        return state.isAir()
                || state.getCollisionShape(level, pos).isEmpty()
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW);
    }

    public static boolean isPlayerInside(ServerLevel level, CityStructureFootprint candidate) {
        return !level.getEntitiesOfClass(
                Player.class,
                candidate.asAABB(),
                player -> player.isAlive() && !player.isSpectator()
        ).isEmpty();
    }
}
