package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ImperialPopulationManager {
    private static final int CITIZEN_GROWTH_INTERVAL_TICKS = 1200;

    private ImperialPopulationManager() {
    }

    public static void tickCitizenGrowth(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        if (serverLevel.getGameTime() % CITIZEN_GROWTH_INTERVAL_TICKS != 0) {
            return;
        }

        int currentCitizens = countAssignedCitizens(serverLevel, commandCore);
        int capacity = getCitizenCapacity(commandCore);

        if (currentCitizens >= capacity) {
            return;
        }

        BlockPos spawnPos = findFreeCitizenSpawnPosition(serverLevel, commandCore.getBlockPos());

        if (spawnPos == null) {
            return;
        }

        ImperialCitizenEntity citizen = ExampleMod.IMPERIAL_CITIZEN.get().create(serverLevel);

        if (citizen == null) {
            return;
        }

        citizen.assignToCommandCore(commandCore.getBlockPos());
        citizen.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        citizen.setCustomName(Component.literal("Imperial Citizen"));
        serverLevel.addFreshEntity(citizen);

        commandCore.setChanged();
    }

    public static boolean trainNearestCitizenAsGuardsman(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Player player) {
        ImperialCitizenEntity citizen = findNearestTrainableCitizen(serverLevel, commandCore, player);

        if (citizen == null) {
            player.displayClientMessage(Component.literal("No trainable Imperial Citizen found near the Command Core."), true);
            return false;
        }

        GuardsmanEntity guardsman = ExampleMod.GUARDSMAN.get().create(serverLevel);

        if (guardsman == null) {
            player.displayClientMessage(Component.literal("Failed to create Guardsman."), true);
            return false;
        }

        guardsman.moveTo(
                citizen.getX(),
                citizen.getY(),
                citizen.getZ(),
                citizen.getYRot(),
                citizen.getXRot()
        );

        guardsman.assignToCommandCore(commandCore.getBlockPos());
        guardsman.setCustomName(Component.literal("Imperial Guardsman"));

        citizen.discard();
        serverLevel.addFreshEntity(guardsman);

        player.displayClientMessage(Component.literal("Imperial Citizen trained into Guardsman."), false);
        return true;
    }

    public static int countAssignedCitizens(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive() && citizen.isAssignedToCommandCore(corePos)
        );

        return citizens.size();
    }

    public static int countUnemployedCitizens(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.isUnemployed()
        );

        return citizens.size();
    }

    public static int getCitizenCapacity(ImperialCommandCoreBlockEntity commandCore) {
        return switch (commandCore.getCityLevel()) {
            case 1 -> 3;
            case 2 -> 6;
            case 3 -> 10;
            case 4 -> 15;
            default -> 25;
        };
    }

    public static ImperialCitizenEntity findNearestTrainableCitizen(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Player player) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.isReadyForTraining()
        );

        ImperialCitizenEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ImperialCitizenEntity citizen : citizens) {
            double distance = citizen.distanceToSqr(player);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = citizen;
            }
        }

        return nearest;
    }

    private static BlockPos findFreeCitizenSpawnPosition(ServerLevel serverLevel, BlockPos corePos) {
        for (int radius = 4; radius <= 24; radius += 4) {
            for (int xOffset = -radius; xOffset <= radius; xOffset += 2) {
                for (int zOffset = -radius; zOffset <= radius; zOffset += 2) {
                    if (Math.abs(xOffset) != radius && Math.abs(zOffset) != radius) {
                        continue;
                    }

                    BlockPos searchPos = corePos.offset(xOffset, 0, zOffset);
                    BlockPos surfacePos = serverLevel.getHeightmapPos(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            searchPos
                    );

                    if (isSafeCitizenSpawnPosition(serverLevel, surfacePos)) {
                        return surfacePos;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isSafeCitizenSpawnPosition(ServerLevel serverLevel, BlockPos pos) {
        if (!serverLevel.isEmptyBlock(pos)) {
            return false;
        }

        if (!serverLevel.isEmptyBlock(pos.above())) {
            return false;
        }

        if (serverLevel.isEmptyBlock(pos.below())) {
            return false;
        }

        return true;
    }

    private static AABB createSearchBox(BlockPos center, int radius) {
        return new AABB(
                center.getX() - radius,
                center.getY() - 32,
                center.getZ() - radius,
                center.getX() + radius,
                center.getY() + 64,
                center.getZ() + radius
        );
    }
}