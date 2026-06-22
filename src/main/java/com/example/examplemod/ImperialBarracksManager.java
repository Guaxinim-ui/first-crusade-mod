package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

public class ImperialBarracksManager {
    private ImperialBarracksManager() {
    }

    public static boolean buildBarracks(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Player player) {
        BlockPos barracksPos = findFreeWorkSitePosition(serverLevel, commandCore.getBlockPos(), 8, Math.min(28, commandCore.getBuildBorderRadius()));

        if (barracksPos == null) {
            player.displayClientMessage(Component.literal("No free space found near the city to build an Imperial Barracks."), true);
            return false;
        }

        buildBarracksStructure(serverLevel, barracksPos);

        BlockEntity blockEntity = serverLevel.getBlockEntity(barracksPos);

        if (blockEntity instanceof ImperialBarracksBlockEntity barracksBlockEntity) {
            barracksBlockEntity.assignToCommandCore(commandCore.getBlockPos());
        }

        player.displayClientMessage(Component.literal(
                "Imperial Barracks built. Use Recruit to assign Citizens for training."
        ), false);

        return true;
    }

    public static int countBarracks(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, int radius) {
        BlockPos corePos = commandCore.getBlockPos();
        int count = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                corePos.offset(-radius, -32, -radius),
                corePos.offset(radius, 64, radius)
        )) {
            if (!serverLevel.getBlockState(pos).is(ExampleMod.IMPERIAL_BARRACKS.get())) {
                continue;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);

            if (blockEntity instanceof ImperialBarracksBlockEntity barracksBlockEntity
                    && barracksBlockEntity.isAssignedToCommandCore(corePos)) {
                count++;
            }
        }

        return count;
    }

    // Returns the position of a Barracks that has no recruit currently in training, or null.
    public static BlockPos findAvailableBarracks(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, int radius) {
        BlockPos corePos = commandCore.getBlockPos();

        for (BlockPos pos : BlockPos.betweenClosed(
                corePos.offset(-radius, -32, -radius),
                corePos.offset(radius, 64, radius)
        )) {
            if (!serverLevel.getBlockState(pos).is(ExampleMod.IMPERIAL_BARRACKS.get())) {
                continue;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);

            if (blockEntity instanceof ImperialBarracksBlockEntity barracksBlockEntity
                    && barracksBlockEntity.isAssignedToCommandCore(corePos)
                    && !barracksBlockEntity.hasAssignedRecruit(serverLevel)) {
                return pos.immutable();
            }
        }

        return null;
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

    private static void buildBarracksStructure(ServerLevel serverLevel, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                serverLevel.setBlock(center.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);

                for (int y = 0; y <= 3; y++) {
                    serverLevel.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        serverLevel.setBlock(center, ExampleMod.IMPERIAL_BARRACKS.get().defaultBlockState(), 3);

        serverLevel.setBlock(center.offset(1, 0, 0), Blocks.BARREL.defaultBlockState(), 3);
        serverLevel.setBlock(center.offset(-1, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        serverLevel.setBlock(center.offset(0, 0, 1), Blocks.GRINDSTONE.defaultBlockState(), 3);
        serverLevel.setBlock(center.offset(0, 0, -1), Blocks.FLETCHING_TABLE.defaultBlockState(), 3);

        for (int y = 0; y <= 2; y++) {
            serverLevel.setBlock(center.offset(2, y, 2), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
            serverLevel.setBlock(center.offset(-2, y, 2), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
            serverLevel.setBlock(center.offset(2, y, -2), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
            serverLevel.setBlock(center.offset(-2, y, -2), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
        }
    }
}
