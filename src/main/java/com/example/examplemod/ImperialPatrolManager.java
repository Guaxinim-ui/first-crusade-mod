package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Gives the city's Guardsmen autonomous peacetime patrols: instead of standing on fixed
 * posts, they slowly circulate around the settlement perimeter on a rotating ring of
 * waypoints. Rotation is derived from world time, so no per-entity patrol state is stored.
 *
 * During an active Ork raid this manager stands down so combat goals and the Core's defense
 * orders take over.
 */
public final class ImperialPatrolManager {
    private static final int WAYPOINT_COUNT = 8;

    // Patrol waypoints advance by one position every this many ticks (~30s).
    private static final long PATROL_CYCLE_TICKS = 600L;

    private static final int GUARD_SEARCH_RADIUS = 96;

    private ImperialPatrolManager() {
    }

    public static void tickPatrols(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        // During raids the Guardsmen hold and fight rather than wander the walls.
        if (core.hasActiveOrkRaid()) {
            return;
        }

        BlockPos corePos = core.getBlockPos();

        long gameTime = serverLevel.getGameTime();

        List<GuardsmanEntity> guardsmen = serverLevel.getEntitiesOfClass(
                GuardsmanEntity.class,
                searchBox(corePos, GUARD_SEARCH_RADIUS),
                guardsman -> guardsman.isAlive()
                        && guardsman.isAssignedToCommandCore(corePos)
                        && guardsman.getTarget() == null
                        && !ImperialPrimarchManager.isInRetinue(guardsman, gameTime)
        );

        if (guardsmen.isEmpty()) {
            return;
        }

        BlockPos[] waypoints = computeWaypoints(serverLevel, corePos, patrolRadius(core.getCityLevel()));
        int rotationOffset = (int) ((serverLevel.getGameTime() / PATROL_CYCLE_TICKS) % WAYPOINT_COUNT);

        for (GuardsmanEntity guardsman : guardsmen) {
            int stableIndex = Math.floorMod(guardsman.getId(), WAYPOINT_COUNT);
            int waypointIndex = Math.floorMod(stableIndex + rotationOffset, WAYPOINT_COUNT);

            // Reassigning the same post is harmless: the guard post goal only moves the
            // Guardsman when he is actually away from the assigned point.
            guardsman.assignGuardPost(waypoints[waypointIndex]);
        }
    }

    private static BlockPos[] computeWaypoints(ServerLevel serverLevel, BlockPos corePos, int radius) {
        BlockPos[] points = new BlockPos[WAYPOINT_COUNT];

        for (int i = 0; i < WAYPOINT_COUNT; i++) {
            double angle = (Math.PI * 2.0D / WAYPOINT_COUNT) * i;

            int x = corePos.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = corePos.getZ() + (int) Math.round(Math.sin(angle) * radius);

            points[i] = serverLevel.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, corePos.getY(), z)
            );
        }

        return points;
    }

    private static int patrolRadius(int cityLevel) {
        return switch (cityLevel) {
            case 1 -> 5;
            case 2 -> 9;
            case 3 -> 13;
            case 4 -> 19;
            default -> 27;
        };
    }

    private static AABB searchBox(BlockPos center, int radius) {
        return new AABB(
                center.getX() - radius,
                center.getY() - 48,
                center.getZ() - radius,
                center.getX() + radius,
                center.getY() + 80,
                center.getZ() + radius
        );
    }
}
