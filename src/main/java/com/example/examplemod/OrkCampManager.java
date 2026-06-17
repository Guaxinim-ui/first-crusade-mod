package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Seeds and locates living Ork Camps. A single camp is planted at a distance once a settlement
 * has grown enough to draw Ork attention; from then on the camp runs itself (see
 * {@link OrkCampBlockEntity}). The Core remembers the camp's position so the Primarch can lead
 * a sortie against it.
 */
public final class OrkCampManager {
    private static final int MIN_DISTANCE = 64;
    private static final int MAX_DISTANCE = 96;

    private OrkCampManager() {
    }

    // Plants a camp around the city and returns its position, or null if none could be placed.
    public static BlockPos seedCamp(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        BlockPos corePos = core.getBlockPos();

        double angle = serverLevel.random.nextDouble() * Math.PI * 2.0D;
        int distance = MIN_DISTANCE + serverLevel.random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);

        int x = corePos.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = corePos.getZ() + (int) Math.round(Math.sin(angle) * distance);

        BlockPos surface = serverLevel.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, corePos.getY(), z)
        );

        buildCampStructure(serverLevel, surface);

        serverLevel.setBlock(surface, ExampleMod.ORK_CAMP.get().defaultBlockState(), 3);

        OrkClan clan = OrkClan.random(serverLevel.random);

        BlockEntity blockEntity = serverLevel.getBlockEntity(surface);

        if (blockEntity instanceof OrkCampBlockEntity camp) {
            camp.setTargetCore(corePos);
            camp.setClan(clan);
        }

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                corePos,
                clan.getDisplayName() + " Orks have raised a camp nearby. It will grow into a threat."
        );

        return surface.immutable();
    }

    public static boolean isCampStillThere(ServerLevel serverLevel, BlockPos campPos) {
        return campPos != null && serverLevel.getBlockState(campPos).is(ExampleMod.ORK_CAMP.get());
    }

    private static void buildCampStructure(ServerLevel serverLevel, BlockPos center) {
        // A crude ring of scrap and a campfire heart, just enough to read as an Ork camp.
        serverLevel.setBlock(center.below(), Blocks.COARSE_DIRT.defaultBlockState(), 3);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                BlockPos floor = center.offset(x, -1, z);

                if (serverLevel.isEmptyBlock(floor)) {
                    serverLevel.setBlock(floor, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                }

                if (edge && (x == z || x == -z)) {
                    serverLevel.setBlock(center.offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
                    serverLevel.setBlock(center.offset(x, 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
                }
            }
        }
    }
}
