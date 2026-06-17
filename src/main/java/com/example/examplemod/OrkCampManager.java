package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

    private static final int CAMP_RADIUS = 8;

    // Builds a proper Ork stronghold: a spiked timber palisade with a horned, skull-topped gate
    // and red banners, watchtowers, hide tents, scrap and smoking campfires. The central block
    // (set to ORK_CAMP afterwards) is left untouched.
    private static void buildCampStructure(ServerLevel serverLevel, BlockPos center) {
        RandomSource rng = serverLevel.random;
        int r = CAMP_RADIUS;

        // 1. Clear the ground and lay a trampled-dirt floor.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= 5; y++) {
                    BlockPos clear = center.offset(x, y, z);
                    if (!clear.equals(center)) {
                        serverLevel.setBlock(clear, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                serverLevel.setBlock(center.offset(x, -1, z), ((x + z) & 1) == 0
                        ? Blocks.COARSE_DIRT.defaultBlockState()
                        : Blocks.PODZOL.defaultBlockState(), 3);
            }
        }

        // 2. Spiked timber palisade with a southern gate.
        BlockState logState = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean perimeter = Math.abs(x) == r || Math.abs(z) == r;
                if (!perimeter) {
                    continue;
                }

                boolean gateGap = z == r && x >= -1 && x <= 1;
                if (gateGap) {
                    continue;
                }

                for (int y = 0; y < 3; y++) {
                    serverLevel.setBlock(center.offset(x, y, z), logState, 3);
                }
                // Sharpened stake on top.
                serverLevel.setBlock(center.offset(x, 3, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 3);

                // Occasional red banner along the wall.
                if (Math.floorMod(x + z, 6) == 0) {
                    place(serverLevel, center.offset(x, 4, z), Blocks.RED_BANNER.defaultBlockState());
                }
            }
        }

        // 3. Outer ring of jutting stakes.
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                boolean outer = Math.abs(x) == r + 1 || Math.abs(z) == r + 1;
                if (outer && ((x + z) & 1) == 0) {
                    place(serverLevel, center.offset(x, 0, z), Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        }

        // 4. Horned, skull-topped gate totems flanking the southern opening.
        buildGateTotem(serverLevel, center.offset(-2, 0, r));
        buildGateTotem(serverLevel, center.offset(2, 0, r));
        // Banner lintel over the gate.
        for (int x = -2; x <= 2; x++) {
            serverLevel.setBlock(center.offset(x, 4, r), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), 3);
            if (x >= -1 && x <= 1) {
                place(serverLevel, center.offset(x, 5, r), Blocks.RED_BANNER.defaultBlockState());
            }
        }

        // 5. Hide tents, watchtowers, and a smoking heart of scrap.
        buildTent(serverLevel, center.offset(-4, 0, -3));
        buildTent(serverLevel, center.offset(4, 0, -4));
        buildTent(serverLevel, center.offset(-5, 0, 3));

        buildWatchtower(serverLevel, center.offset(r - 2, 0, -(r - 2)));
        buildWatchtower(serverLevel, center.offset(-(r - 2), 0, -(r - 2)));

        scatterScrap(serverLevel, center, r, rng);
    }

    // A thick post crowned with horns (logs) and a trophy skull.
    private static void buildGateTotem(ServerLevel serverLevel, BlockPos base) {
        for (int y = 0; y < 4; y++) {
            serverLevel.setBlock(base.offset(0, y, 0), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), 3);
        }
        // Horns to either side.
        place(serverLevel, base.offset(-1, 4, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
        place(serverLevel, base.offset(1, 4, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
        // Trophy skull on top.
        place(serverLevel, base.offset(0, 4, 0), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
    }

    // An open hide tent: four posts under a stepped terracotta roof.
    private static void buildTent(ServerLevel serverLevel, BlockPos base) {
        BlockState post = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState hide = Blocks.ORANGE_TERRACOTTA.defaultBlockState();

        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                place(serverLevel, base.offset(dx, 0, dz), post);
                place(serverLevel, base.offset(dx, 1, dz), post);
                place(serverLevel, base.offset(dx, 2, dz), post);
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                place(serverLevel, base.offset(dx, 3, dz), hide);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                place(serverLevel, base.offset(dx, 4, dz), hide);
            }
        }
        place(serverLevel, base.offset(0, 5, 0), hide);
    }

    // A rickety lookout tower with a banner and a trophy skull.
    private static void buildWatchtower(ServerLevel serverLevel, BlockPos base) {
        int height = 6;
        for (int y = 0; y < height; y++) {
            serverLevel.setBlock(base.offset(0, y, 0), Blocks.SPRUCE_LOG.defaultBlockState(), 3);
            serverLevel.setBlock(base.offset(1, y, 0), Blocks.SPRUCE_LOG.defaultBlockState(), 3);
            serverLevel.setBlock(base.offset(0, y, 1), Blocks.SPRUCE_LOG.defaultBlockState(), 3);
            serverLevel.setBlock(base.offset(1, y, 1), Blocks.SPRUCE_LOG.defaultBlockState(), 3);
        }

        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                place(serverLevel, base.offset(dx, height, dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        // Railing, banner and skull on the lookout.
        place(serverLevel, base.offset(-1, height + 1, -1), Blocks.SPRUCE_FENCE.defaultBlockState());
        place(serverLevel, base.offset(2, height + 1, 2), Blocks.SPRUCE_FENCE.defaultBlockState());
        place(serverLevel, base.offset(0, height + 1, 0), Blocks.RED_BANNER.defaultBlockState());
        place(serverLevel, base.offset(1, height + 1, 1), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
    }

    // Smoking campfires, a battered anvil and looted scrap strewn around the camp.
    private static void scatterScrap(ServerLevel serverLevel, BlockPos center, int r, RandomSource rng) {
        place(serverLevel, center.offset(2, 0, 1), Blocks.CAMPFIRE.defaultBlockState());
        place(serverLevel, center.offset(-2, 0, -1), Blocks.CAMPFIRE.defaultBlockState());
        place(serverLevel, center.offset(3, 0, -2), Blocks.ANVIL.defaultBlockState());
        place(serverLevel, center.offset(-3, 0, 2), Blocks.CAULDRON.defaultBlockState());

        for (int i = 0; i < 10; i++) {
            int x = rng.nextInt(r * 2 - 2) - (r - 1);
            int z = rng.nextInt(r * 2 - 2) - (r - 1);
            BlockPos pos = center.offset(x, 0, z);

            if (pos.equals(center) || !serverLevel.isEmptyBlock(pos)) {
                continue;
            }

            BlockState scrap = switch (rng.nextInt(3)) {
                case 0 -> Blocks.IRON_BARS.defaultBlockState();
                case 1 -> Blocks.CHAIN.defaultBlockState();
                default -> Blocks.SPRUCE_FENCE.defaultBlockState();
            };
            serverLevel.setBlock(pos, scrap, 3);
        }
    }

    // Places a block only into empty space, never overwriting the central camp block.
    private static void place(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (!pos.equals(serverLevel.getSharedSpawnPos()) && serverLevel.isEmptyBlock(pos)) {
            serverLevel.setBlock(pos, state, 3);
        }
    }
}
