package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class VanillaVillageCoreBootstrap {
    private static final int CELL_SIZE = 128;

    private static final int PLAYER_SCAN_RADIUS = 120;
    private static final int VILLAGE_SCAN_RADIUS = 56;
    private static final int VILLAGER_CONVERT_RADIUS = 64;

    private static final int MIN_BEDS = 3;
    private static final int MIN_VILLAGERS = 2;

    private VanillaVillageCoreBootstrap() {
    }

    public static void serverTick(ServerLevel level) {
        if (level.getGameTime() % 200L != 0L) {
            return;
        }

        VanillaVillageCoreData data = VanillaVillageCoreData.get(level);

        for (ServerPlayer player : level.players()) {
            scanAroundPlayer(level, data, player.blockPosition());
        }
    }

    private static void scanAroundPlayer(ServerLevel level, VanillaVillageCoreData data, BlockPos playerPos) {
        int playerCellX = Math.floorDiv(playerPos.getX(), CELL_SIZE);
        int playerCellZ = Math.floorDiv(playerPos.getZ(), CELL_SIZE);

        for (int cellX = playerCellX - 1; cellX <= playerCellX + 1; cellX++) {
            for (int cellZ = playerCellZ - 1; cellZ <= playerCellZ + 1; cellZ++) {
                if (data.hasProcessed(cellX, cellZ)) {
                    continue;
                }

                BlockPos cellCenter = new BlockPos(
                        cellX * CELL_SIZE + CELL_SIZE / 2,
                        playerPos.getY(),
                        cellZ * CELL_SIZE + CELL_SIZE / 2
                );

                if (cellCenter.distSqr(playerPos) > PLAYER_SCAN_RADIUS * PLAYER_SCAN_RADIUS) {
                    continue;
                }

                VillageScanResult result = scanForVillage(level, cellCenter);

                if (!result.isValidVillage()) {
                    continue;
                }

                BlockPos corePos = findBestCorePosition(level, result);

                if (corePos == null) {
                    continue;
                }

                if (hasExistingCoreNearby(level, corePos)) {
                    data.markProcessed(cellX, cellZ);
                    continue;
                }

                placeCommandCore(level, corePos);
                convertVillagers(level, corePos);
                WorldWarMapData.get(level).recordCity(level, corePos);

                data.markProcessed(cellX, cellZ);
                data.setDirty();

                return;
            }
        }
    }

    private static VillageScanResult scanForVillage(ServerLevel level, BlockPos center) {
        int beds = 0;
        int bells = 0;
        int workstations = 0;

        List<BlockPos> points = new ArrayList<>();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = -VILLAGE_SCAN_RADIUS; x <= VILLAGE_SCAN_RADIUS; x++) {
            for (int z = -VILLAGE_SCAN_RADIUS; z <= VILLAGE_SCAN_RADIUS; z++) {
                for (int y = -16; y <= 24; y++) {
                    mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);

                    BlockState state = level.getBlockState(mutable);
                    Block block = state.getBlock();

                    if (state.is(BlockTags.BEDS)) {
                        beds++;
                        points.add(mutable.immutable());
                        continue;
                    }

                    if (block == Blocks.BELL) {
                        bells++;
                        points.add(mutable.immutable());
                        continue;
                    }

                    if (isVillageWorkstation(block)) {
                        workstations++;
                        points.add(mutable.immutable());
                    }
                }
            }
        }

        AABB villagerBox = new AABB(
                center.getX() - VILLAGE_SCAN_RADIUS,
                center.getY() - 24,
                center.getZ() - VILLAGE_SCAN_RADIUS,
                center.getX() + VILLAGE_SCAN_RADIUS,
                center.getY() + 32,
                center.getZ() + VILLAGE_SCAN_RADIUS
        );

        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class,
                villagerBox,
                Villager::isAlive
        );

        for (Villager villager : villagers) {
            points.add(villager.blockPosition());
        }

        BlockPos villageCenter = calculateCenter(center, points);

        return new VillageScanResult(
                villageCenter,
                beds,
                bells,
                workstations,
                villagers.size(),
                points
        );
    }

    private static BlockPos calculateCenter(BlockPos fallback, List<BlockPos> points) {
        if (points.isEmpty()) {
            return fallback;
        }

        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;

        for (BlockPos pos : points) {
            sumX += pos.getX();
            sumY += pos.getY();
            sumZ += pos.getZ();
        }

        int size = points.size();

        return new BlockPos(
                (int) (sumX / size),
                (int) (sumY / size),
                (int) (sumZ / size)
        );
    }

    private static BlockPos findBestCorePosition(ServerLevel level, VillageScanResult result) {
        BlockPos center = ground(level, result.center());

        for (BlockPos point : result.points()) {
            if (level.getBlockState(point).is(Blocks.BELL)) {
                BlockPos nearBell = ground(level, point.offset(2, 0, 2));

                if (canPlaceCoreAt(level, nearBell)) {
                    return nearBell;
                }
            }
        }

        for (int radius = 0; radius <= 12; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }

                    BlockPos candidate = ground(level, center.offset(x, 0, z));

                    if (canPlaceCoreAt(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private static boolean canPlaceCoreAt(ServerLevel level, BlockPos pos) {
        if (level.isEmptyBlock(pos.below())) {
            return false;
        }

        for (int y = 0; y <= 2; y++) {
            BlockPos check = pos.above(y);
            BlockState state = level.getBlockState(check);

            if (!isReplaceable(level, check, state)) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasExistingCoreNearby(ServerLevel level, BlockPos center) {
        int radius = 48;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -8; y <= 8; y++) {
                    mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);

                    if (level.getBlockState(mutable).is(FCRegistry.IMPERIAL_COMMAND_CORE.get())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void placeCommandCore(ServerLevel level, BlockPos corePos) {
        clearSmallArea(level, corePos);

        level.setBlock(
                corePos.below(),
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                3
        );

        level.setBlock(
                corePos,
                FCRegistry.IMPERIAL_COMMAND_CORE.get().defaultBlockState(),
                3
        );

        level.setBlock(
                corePos.north(),
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                3
        );

        level.setBlock(
                corePos.south(),
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                3
        );

        level.setBlock(
                corePos.east(),
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                3
        );

        level.setBlock(
                corePos.west(),
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                3
        );
    }

    private static void clearSmallArea(ServerLevel level, BlockPos corePos) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 3; y++) {
                    BlockPos pos = corePos.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (isReplaceable(level, pos, state) || y > 0) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void convertVillagers(ServerLevel level, BlockPos corePos) {
        AABB box = new AABB(
                corePos.getX() - VILLAGER_CONVERT_RADIUS,
                corePos.getY() - 24,
                corePos.getZ() - VILLAGER_CONVERT_RADIUS,
                corePos.getX() + VILLAGER_CONVERT_RADIUS,
                corePos.getY() + 32,
                corePos.getZ() + VILLAGER_CONVERT_RADIUS
        );

        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class,
                box,
                Villager::isAlive
        );

        for (Villager villager : villagers) {
            ImperialCitizenEntity citizen = FCRegistry.IMPERIAL_CITIZEN.get().create(level);

            if (citizen == null) {
                continue;
            }

            citizen.moveTo(
                    villager.getX(),
                    villager.getY(),
                    villager.getZ(),
                    villager.getYRot(),
                    villager.getXRot()
            );

            citizen.assignToCommandCore(corePos);
            citizen.setPersistenceRequired();

            if (villager.isBaby()) {
                citizen.setChild(24000);
            }

            level.addFreshEntity(citizen);
            villager.discard();
        }
    }

    private static BlockPos ground(ServerLevel level, BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
    }

    private static boolean isReplaceable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir()
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isVillageWorkstation(Block block) {
        return block == Blocks.BARREL
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.BREWING_STAND
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.COMPOSTER
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.GRINDSTONE
                || block == Blocks.LECTERN
                || block == Blocks.LOOM
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.SMOKER
                || block == Blocks.STONECUTTER;
    }

    private record VillageScanResult(
            BlockPos center,
            int beds,
            int bells,
            int workstations,
            int villagers,
            List<BlockPos> points
    ) {
        boolean isValidVillage() {
            if (beds >= MIN_BEDS && villagers >= 1) {
                return true;
            }

            if (villagers >= MIN_VILLAGERS && bells >= 1) {
                return true;
            }

            return bells >= 1 && workstations >= 2 && beds >= 1;
        }
    }
}