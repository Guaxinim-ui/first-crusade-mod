package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class VanillaVillageImperializer {
    private static final int PLAYER_SCAN_RADIUS = 96;
    private static final int VILLAGE_SCAN_RADIUS = 48;

    private static final int MIN_BEDS_TO_COUNT_AS_VILLAGE = 3;
    private static final int MIN_BELL_AND_WORKSTATIONS_TO_COUNT = 2;

    private static final int CENTRAL_PLAZA_RADIUS = 10;
    private static final int WALL_EXTRA_RADIUS = 14;

    private static final int MIN_WALL_RADIUS = 34;
    private static final int MAX_WALL_RADIUS = 62;

    private static final int VILLAGE_CELL_SIZE = 128;

    private VanillaVillageImperializer() {
    }

    public static void serverTick(ServerLevel level) {
        if (level.getGameTime() % 200 != 0) {
            return;
        }

        VanillaVillageImperializationData data = VanillaVillageImperializationData.get(level);

        for (ServerPlayer player : level.players()) {
            tryImperializeVillageNear(level, data, player.blockPosition());
        }
    }

    private static void tryImperializeVillageNear(
            ServerLevel level,
            VanillaVillageImperializationData data,
            BlockPos playerPos
    ) {
        int playerCellX = Math.floorDiv(playerPos.getX(), VILLAGE_CELL_SIZE);
        int playerCellZ = Math.floorDiv(playerPos.getZ(), VILLAGE_CELL_SIZE);

        for (int cellX = playerCellX - 1; cellX <= playerCellX + 1; cellX++) {
            for (int cellZ = playerCellZ - 1; cellZ <= playerCellZ + 1; cellZ++) {
                if (data.hasProcessed(cellX, cellZ)) {
                    continue;
                }

                BlockPos cellCenter = new BlockPos(
                        cellX * VILLAGE_CELL_SIZE + VILLAGE_CELL_SIZE / 2,
                        playerPos.getY(),
                        cellZ * VILLAGE_CELL_SIZE + VILLAGE_CELL_SIZE / 2
                );

                if (cellCenter.distSqr(playerPos) > PLAYER_SCAN_RADIUS * PLAYER_SCAN_RADIUS) {
                    continue;
                }

                // Only scan cells whose chunks are already loaded, so getBlockState never forces
                // synchronous worldgen during the tick. Unloaded cells are retried once the player
                // gets closer and their chunks come in.
                if (!isAreaLoaded(level, cellCenter, VILLAGE_SCAN_RADIUS)) {
                    continue;
                }

                VillageScan scan = scanVillage(level, cellCenter, VILLAGE_SCAN_RADIUS);

                if (scan.isVanillaVillage()) {
                    BlockPos corePos = findCorePosition(level, scan);
                    int wallRadius = calculateWallRadius(scan, corePos);

                    buildCentralImperialPlaza(level, corePos);
                    buildVillageWall(level, corePos, wallRadius);

                    WorldWarMapData.get(level).recordCity(level, corePos);
                }

                // Mark processed whether or not a village was here: a fully-loaded cell is fully
                // generated, so an empty cell will stay empty. This avoids re-scanning it forever.
                data.markProcessed(cellX, cellZ);

                return;
            }
        }
    }

    private static boolean isAreaLoaded(ServerLevel level, BlockPos center, int radius) {
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static VillageScan scanVillage(ServerLevel level, BlockPos center, int radius) {
        int beds = 0;
        int bells = 0;
        int workstations = 0;

        int sumX = 0;
        int sumY = 0;
        int sumZ = 0;
        int importantPoints = 0;

        List<BlockPos> importantPositions = new ArrayList<>();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -12; y <= 18; y++) {
                    mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);

                    BlockState state = level.getBlockState(mutable);
                    Block block = state.getBlock();

                    boolean important = false;

                    if (state.is(BlockTags.BEDS)) {
                        beds++;
                        important = true;
                    }

                    if (block == Blocks.BELL) {
                        bells++;
                        important = true;
                    }

                    if (isVillageWorkstation(block)) {
                        workstations++;
                        important = true;
                    }

                    if (important) {
                        BlockPos immutable = mutable.immutable();
                        importantPositions.add(immutable);

                        sumX += immutable.getX();
                        sumY += immutable.getY();
                        sumZ += immutable.getZ();
                        importantPoints++;
                    }
                }
            }
        }

        if (importantPoints <= 0) {
            return new VillageScan(center, beds, bells, workstations, importantPositions);
        }

        BlockPos average = new BlockPos(
                sumX / importantPoints,
                sumY / importantPoints,
                sumZ / importantPoints
        );

        return new VillageScan(average, beds, bells, workstations, importantPositions);
    }

    private static BlockPos findCorePosition(ServerLevel level, VillageScan scan) {
        BlockPos center = ground(level, scan.center());

        for (int radius = 0; radius <= 8; radius++) {
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

        return center;
    }

    private static boolean canPlaceCoreAt(ServerLevel level, BlockPos pos) {
        for (int y = 0; y <= 3; y++) {
            BlockState state = level.getBlockState(pos.offset(0, y, 0));

            if (!isReplaceable(state)) {
                return false;
            }
        }

        return !level.isEmptyBlock(pos.below());
    }

    private static int calculateWallRadius(VillageScan scan, BlockPos corePos) {
        int farthest = MIN_WALL_RADIUS;

        for (BlockPos pos : scan.importantPositions()) {
            int distance = Math.max(
                    Math.abs(pos.getX() - corePos.getX()),
                    Math.abs(pos.getZ() - corePos.getZ())
            );

            if (distance > farthest) {
                farthest = distance;
            }
        }

        int result = farthest + WALL_EXTRA_RADIUS;

        if (result < MIN_WALL_RADIUS) {
            return MIN_WALL_RADIUS;
        }

        if (result > MAX_WALL_RADIUS) {
            return MAX_WALL_RADIUS;
        }

        return result;
    }

    private static void buildCentralImperialPlaza(ServerLevel level, BlockPos corePos) {
        for (int x = -CENTRAL_PLAZA_RADIUS; x <= CENTRAL_PLAZA_RADIUS; x++) {
            for (int z = -CENTRAL_PLAZA_RADIUS; z <= CENTRAL_PLAZA_RADIUS; z++) {
                BlockPos surface = ground(level, corePos.offset(x, 0, z));

                int distance = Math.max(Math.abs(x), Math.abs(z));

                if (distance <= CENTRAL_PLAZA_RADIUS) {
                    clearAbove(level, surface, 8);

                    if (distance <= 4) {
                        level.setBlock(surface.below(), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                    } else if (distance <= 8) {
                        level.setBlock(surface.below(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    } else {
                        level.setBlock(surface.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
                    }
                }
            }
        }

        level.setBlock(corePos, FCRegistry.IMPERIAL_COMMAND_CORE.get().defaultBlockState(), 3);

        placeCathedralMarkers(level, corePos);
        placePlazaDetails(level, corePos);
    }

    private static void placeCathedralMarkers(ServerLevel level, BlockPos corePos) {
        placePillar(level, corePos.offset(7, 0, 7));
        placePillar(level, corePos.offset(-7, 0, 7));
        placePillar(level, corePos.offset(7, 0, -7));
        placePillar(level, corePos.offset(-7, 0, -7));
    }

    private static void placePillar(ServerLevel level, BlockPos rawPos) {
        BlockPos pos = ground(level, rawPos);

        for (int y = 0; y <= 3; y++) {
            level.setBlock(pos.offset(0, y, 0), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }

        level.setBlock(pos.offset(0, 4, 0), Blocks.LANTERN.defaultBlockState(), 3);
    }

    private static void placePlazaDetails(ServerLevel level, BlockPos corePos) {
        level.setBlock(corePos.offset(0, 0, 3), Blocks.BELL.defaultBlockState(), 3);
        level.setBlock(corePos.offset(3, 0, 0), Blocks.TORCH.defaultBlockState(), 3);
        level.setBlock(corePos.offset(-3, 0, 0), Blocks.TORCH.defaultBlockState(), 3);
        level.setBlock(corePos.offset(0, 0, -3), Blocks.TORCH.defaultBlockState(), 3);
    }

    private static void buildVillageWall(ServerLevel level, BlockPos corePos, int radius) {
        int min = -radius;
        int max = radius;

        for (int i = min; i <= max; i++) {
            buildWallColumn(level, corePos.offset(i, 0, min), isGate(i));
            buildWallColumn(level, corePos.offset(i, 0, max), isGate(i));
            buildWallColumn(level, corePos.offset(min, 0, i), isGate(i));
            buildWallColumn(level, corePos.offset(max, 0, i), isGate(i));
        }

        buildGate(level, corePos.offset(0, 0, min), true);
        buildGate(level, corePos.offset(0, 0, max), true);
        buildGate(level, corePos.offset(min, 0, 0), false);
        buildGate(level, corePos.offset(max, 0, 0), false);
    }

    private static boolean isGate(int offset) {
        return offset >= -2 && offset <= 2;
    }

    private static void buildWallColumn(ServerLevel level, BlockPos rawPos, boolean gateOpening) {
        BlockPos pos = ground(level, rawPos);

        if (gateOpening) {
            return;
        }

        clearAbove(level, pos, 4);

        level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(pos.above(2), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);

        if ((pos.getX() + pos.getZ()) % 7 == 0) {
            level.setBlock(pos.above(3), Blocks.TORCH.defaultBlockState(), 3);
        }
    }

    private static void buildGate(ServerLevel level, BlockPos rawCenter, boolean northSouth) {
        BlockPos center = ground(level, rawCenter);

        for (int offset = -2; offset <= 2; offset++) {
            BlockPos pos = northSouth ? center.offset(offset, 0, 0) : center.offset(0, 0, offset);
            pos = ground(level, pos);

            clearAbove(level, pos, 5);
            level.setBlock(pos.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }

        BlockPos left = northSouth ? ground(level, center.offset(-3, 0, 0)) : ground(level, center.offset(0, 0, -3));
        BlockPos right = northSouth ? ground(level, center.offset(3, 0, 0)) : ground(level, center.offset(0, 0, 3));

        buildGateTower(level, left);
        buildGateTower(level, right);
    }

    private static void buildGateTower(ServerLevel level, BlockPos pos) {
        clearAbove(level, pos, 7);

        for (int y = 0; y <= 4; y++) {
            level.setBlock(pos.offset(0, y, 0), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }

        level.setBlock(pos.offset(0, 5, 0), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        level.setBlock(pos.offset(0, 6, 0), Blocks.TORCH.defaultBlockState(), 3);
    }

    private static void clearAbove(ServerLevel level, BlockPos pos, int height) {
        for (int y = 0; y <= height; y++) {
            BlockPos check = pos.offset(0, y, 0);
            BlockState state = level.getBlockState(check);

            if (isReplaceable(state) || y > 0) {
                level.setBlock(check, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static boolean isReplaceable(BlockState state) {
        return state.isAir()
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.WATER)
                || state.is(Blocks.LAVA)
                || state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    private static BlockPos ground(ServerLevel level, BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
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

    private record VillageScan(
            BlockPos center,
            int beds,
            int bells,
            int workstations,
            List<BlockPos> importantPositions
    ) {
        boolean isVanillaVillage() {
            if (beds >= MIN_BEDS_TO_COUNT_AS_VILLAGE) {
                return true;
            }

            return bells >= 1 && workstations >= MIN_BELL_AND_WORKSTATIONS_TO_COUNT;
        }
    }
}