package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ImperialFarmManager {
    private ImperialFarmManager() {
    }

    public static boolean buildImperialFarm(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Player player) {
        BlockPos farmPos = findFreeWorkSitePosition(serverLevel, commandCore.getBlockPos(), 8, 28);

        if (farmPos == null) {
            player.displayClientMessage(Component.literal("No free space found near the city to build an Imperial Farm."), true);
            return false;
        }

        buildFarmStructure(serverLevel, farmPos);

        BlockEntity blockEntity = serverLevel.getBlockEntity(farmPos);

        if (blockEntity instanceof ImperialFarmBlockEntity farmBlockEntity) {
            farmBlockEntity.assignToCommandCore(commandCore.getBlockPos());
        }

        ImperialCitizenEntity worker = findAvailableCitizen(serverLevel, commandCore);

        if (worker != null) {
            worker.assignToCommandCore(commandCore.getBlockPos());
            worker.assignJob(ImperialCitizenJob.FARMER, farmPos);

            player.displayClientMessage(Component.literal(
                    "Imperial Farm built. An Imperial Citizen was assigned as Farmer."
            ), false);
        } else {
            player.displayClientMessage(Component.literal(
                    "Imperial Farm built, but no unemployed Imperial Citizen was available."
            ), false);
        }

        return true;
    }

    public static int countFarms(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, int radius) {
        BlockPos corePos = commandCore.getBlockPos();
        int count = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                corePos.offset(-radius, -32, -radius),
                corePos.offset(radius, 64, radius)
        )) {
            if (!serverLevel.getBlockState(pos).is(ExampleMod.IMPERIAL_FARM.get())) {
                continue;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);

            if (blockEntity instanceof ImperialFarmBlockEntity farmBlockEntity
                    && farmBlockEntity.isAssignedToCommandCore(corePos)) {
                count++;
            }
        }

        return count;
    }

    public static int countStaffedFarms(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, int radius) {
        BlockPos corePos = commandCore.getBlockPos();
        int count = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                corePos.offset(-radius, -32, -radius),
                corePos.offset(radius, 64, radius)
        )) {
            if (!serverLevel.getBlockState(pos).is(ExampleMod.IMPERIAL_FARM.get())) {
                continue;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);

            if (blockEntity instanceof ImperialFarmBlockEntity farmBlockEntity
                    && farmBlockEntity.isAssignedToCommandCore(corePos)
                    && farmBlockEntity.hasActiveWorker(serverLevel)) {
                count++;
            }
        }

        return count;
    }

    private static ImperialCitizenEntity findAvailableCitizen(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        AABB searchBox = new AABB(
                corePos.getX() - 96,
                corePos.getY() - 32,
                corePos.getZ() - 96,
                corePos.getX() + 96,
                corePos.getY() + 64,
                corePos.getZ() + 96
        );

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                searchBox,
                citizen -> citizen.isAlive()
                        && citizen.isUnemployed()
                        && (
                                citizen.getCommandCorePos() == null
                                        || citizen.isAssignedToCommandCore(corePos)
                        )
        );

        if (citizens.isEmpty()) {
            return null;
        }

        ImperialCitizenEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ImperialCitizenEntity citizen : citizens) {
            double distance = citizen.distanceToSqr(
                    corePos.getX() + 0.5D,
                    corePos.getY() + 0.5D,
                    corePos.getZ() + 0.5D
            );

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = citizen;
            }
        }

        return nearest;
    }

    private static BlockPos findFreeWorkSitePosition(ServerLevel serverLevel, BlockPos corePos, int minRadius, int maxRadius) {
        for (int radius = minRadius; radius <= maxRadius; radius += 4) {
            for (int xOffset = -radius; xOffset <= radius; xOffset += 4) {
                for (int zOffset = -radius; zOffset <= radius; zOffset += 4) {
                    if (Math.abs(xOffset) != radius && Math.abs(zOffset) != radius) {
                        continue;
                    }

                    BlockPos searchPos = new BlockPos(
                            corePos.getX() + xOffset,
                            corePos.getY(),
                            corePos.getZ() + zOffset
                    );

                    BlockPos surfacePos = serverLevel.getHeightmapPos(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            searchPos
                    );

                    if (isWorkSiteAreaFree(serverLevel, surfacePos)) {
                        return surfacePos;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isWorkSiteAreaFree(ServerLevel serverLevel, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos floorPos = center.offset(x, -1, z);

                if (serverLevel.isEmptyBlock(floorPos)) {
                    return false;
                }

                for (int y = 0; y <= 3; y++) {
                    BlockPos checkPos = center.offset(x, y, z);

                    if (!isReplaceable(serverLevel, checkPos)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean isReplaceable(ServerLevel serverLevel, BlockPos pos) {
        return serverLevel.isEmptyBlock(pos)
                || serverLevel.getBlockState(pos).getCollisionShape(serverLevel, pos).isEmpty();
    }

    private static void buildFarmStructure(ServerLevel serverLevel, BlockPos center) {
        // A tilled field: farmland and crops around a central control block.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                serverLevel.setBlock(center.offset(x, -1, z), Blocks.FARMLAND.defaultBlockState(), 3);

                for (int y = 0; y <= 3; y++) {
                    serverLevel.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }

                boolean center3x3 = Math.abs(x) <= 1 && Math.abs(z) <= 1;

                if (!center3x3) {
                    serverLevel.setBlock(center.offset(x, 0, z), Blocks.WHEAT.defaultBlockState(), 3);
                }
            }
        }

        serverLevel.setBlock(center, ExampleMod.IMPERIAL_FARM.get().defaultBlockState(), 3);

        // Corner posts with hay accents.
        serverLevel.setBlock(center.offset(2, 0, 2), Blocks.OAK_FENCE.defaultBlockState(), 3);
        serverLevel.setBlock(center.offset(-2, 0, 2), Blocks.OAK_FENCE.defaultBlockState(), 3);
        serverLevel.setBlock(center.offset(2, 0, -2), Blocks.OAK_FENCE.defaultBlockState(), 3);
        serverLevel.setBlock(center.offset(-2, 0, -2), Blocks.OAK_FENCE.defaultBlockState(), 3);

        serverLevel.setBlock(center.offset(1, 0, 0), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        serverLevel.setBlock(center.offset(-1, 0, 0), Blocks.HAY_BLOCK.defaultBlockState(), 3);
    }
}
