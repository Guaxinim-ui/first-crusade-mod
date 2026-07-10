package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Shared placement rules for the City Builder Tool, used by both the server (to validate a
 * placement before charging/building) and the client (to colour the translucent ghost).
 *
 * A structure "footprint" is a (2r+1) x (2r+1) column, `height` blocks tall, sitting on the
 * clicked surface. The area is buildable when every block in that column is replaceable and the
 * whole floor underneath is solid ground (no cliffs, no floating placements).
 */
public final class CityBuilderPlacement {
    private CityBuilderPlacement() {
    }

    // True if the footprint at `center` is clear to build and stands on solid ground.
    public static boolean isAreaBuildable(Level level, BlockPos center, int radius, int height) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos floor = center.offset(x, -1, z);

                // The ground must be solid enough to stand a building on.
                if (!level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)) {
                    return false;
                }

                for (int y = 0; y < height; y++) {
                    if (!isReplaceable(level, center.offset(x, y, z))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    // A block the tool may overwrite: air, or anything with no collision (grass, flowers, snow…).
    public static boolean isReplaceable(Level level, BlockPos pos) {
        return level.isEmptyBlock(pos)
                || level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
