package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Seeds and locates living Ork Camps. A single camp is planted at a distance once a settlement
 * has grown enough to draw Ork attention; from then on the camp runs itself (see
 * {@link OrkCampBlockEntity}). The Core remembers the camp's position so the Primarch can lead
 * a sortie against it.
 */
public final class OrkCampManager {
    // Camps must be planted well clear of a city's walls (an imperialized village can wall out to
    // radius ~62), so they never end up inside the base.
    private static final int MIN_DISTANCE = 120;
    private static final int MAX_DISTANCE = 190;

    // When the WAAAGH! spreads, the daughter camp is planted farther out than the original ring.
    private static final int SPREAD_MIN_DISTANCE = 160;
    private static final int SPREAD_MAX_DISTANCE = 240;

    private OrkCampManager() {
    }

    // Plants a camp around the city and returns its position, or null if none could be placed.
    public static BlockPos seedCamp(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        return plantCamp(serverLevel, core.getBlockPos(), core.getBlockPos(), MIN_DISTANCE, MAX_DISTANCE);
    }

    // The green tide spreads: an established camp plants a new camp farther out that joins the
    // assault on the same city. Returns the new camp's position, or null if it could not be placed.
    public static BlockPos seedSpreadCamp(ServerLevel serverLevel, BlockPos fromPos, BlockPos targetCore) {
        return plantCamp(serverLevel, fromPos, targetCore, SPREAD_MIN_DISTANCE, SPREAD_MAX_DISTANCE);
    }

    // World generation: plant a camp directly at (the surface above) a chosen spot, marching on the
    // given city. Used by WorldSettlementSeeder so the planet starts populated by the WAAAGH! too.
    public static BlockPos seedWorldCamp(ServerLevel serverLevel, BlockPos spot, BlockPos targetCore) {
        return plantCamp(serverLevel, spot, targetCore, 0, 0);
    }

    private static BlockPos plantCamp(ServerLevel serverLevel, BlockPos origin, BlockPos targetCore, int minDistance, int maxDistance) {
        double angle = serverLevel.random.nextDouble() * Math.PI * 2.0D;
        int distance = minDistance + serverLevel.random.nextInt(maxDistance - minDistance + 1);

        int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);

        BlockPos surface = WorldGenPlacement.groundPlacement(serverLevel, x, z);

        // Don't plant on top of another camp.
        if (isCampStillThere(serverLevel, surface)) {
            return null;
        }

        buildCampStructure(serverLevel, surface);

        serverLevel.setBlock(surface, ExampleMod.ORK_CAMP.get().defaultBlockState(), 3);

        OrkClan clan = OrkClan.random(serverLevel.random);

        BlockEntity blockEntity = serverLevel.getBlockEntity(surface);

        if (blockEntity instanceof OrkCampBlockEntity camp) {
            camp.setTargetCore(targetCore);
            camp.setClan(clan);
        }

        // In the fixed test world the 5+5 cities are simply present from the start — nothing "rises",
        // so no announcement. (Normal play still heralds a new Ork Core appearing.)
        if (!ExampleMod.TEST_FIXED_WORLD) {
            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    surface,
                    Component.translatable("msg.firstcrusade.bcast.camp_raised", clan.getDisplayName())
            );
        }

        return surface.immutable();
    }

    public static boolean isCampStillThere(ServerLevel serverLevel, BlockPos campPos) {
        return campPos != null && serverLevel.getBlockState(campPos).is(ExampleMod.ORK_CAMP.get());
    }

    // The starter camp is small (radius CAMP_RADIUS_BASE). As its WAAAGH! grows the camp fortifies
    // outward into a full stronghold — see fortifyCamp.
    private static final int CAMP_RADIUS_BASE = 4;

    // Level 1 — a small Ork encampment: a trampled clearing ringed with campfires and red banners,
    // a hide tent and a trophy totem. Home to a handful (6-8) of Boyz. The central block (set to
    // ORK_CAMP afterwards) is left untouched. It grows into a stronghold over time (fortifyCamp).
    private static void buildCampStructure(ServerLevel serverLevel, BlockPos center) {
        RandomSource rng = serverLevel.random;
        int r = CAMP_RADIUS_BASE;

        // Strip surrounding trees/leaves/plants so the camp isn't buried in forest canopy.
        WorldGenPlacement.clearVegetation(serverLevel, center, r + 2, 8);

        // Clear the ground and lay a trampled-dirt floor.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= 3; y++) {
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

        // Campfires around the heart and red banners on stakes at the corners.
        place(serverLevel, center.offset(r - 1, 0, 0), Blocks.CAMPFIRE.defaultBlockState());
        place(serverLevel, center.offset(-(r - 1), 0, 0), Blocks.CAMPFIRE.defaultBlockState());
        place(serverLevel, center.offset(0, 0, r - 1), Blocks.CAMPFIRE.defaultBlockState());
        place(serverLevel, center.offset(0, 0, -(r - 1)), Blocks.CAMPFIRE.defaultBlockState());

        buildBannerStake(serverLevel, center.offset(r, 0, r));
        buildBannerStake(serverLevel, center.offset(-r, 0, r));
        buildBannerStake(serverLevel, center.offset(r, 0, -r));
        buildBannerStake(serverLevel, center.offset(-r, 0, -r));

        // A single hide tent and a trophy totem.
        buildTent(serverLevel, center.offset(-3, 0, 2));
        buildGateTotem(serverLevel, center.offset(0, 0, -r));

        scatterScrap(serverLevel, center, r, rng);
    }

    // A short stake flying a red Ork banner.
    private static void buildBannerStake(ServerLevel serverLevel, BlockPos base) {
        place(serverLevel, base, Blocks.SPRUCE_FENCE.defaultBlockState());
        place(serverLevel, base.offset(0, 1, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
        place(serverLevel, base.offset(0, 2, 0), Blocks.RED_BANNER.defaultBlockState());
    }

    // As a camp's WAAAGH! grows, it fortifies into a larger Ork stronghold: a spiked timber palisade
    // with a horned gate and watchtowers, expanding with each level (radius CAMP_RADIUS_BASE +
    // campLevel*2). Called by OrkCampBlockEntity when the camp grows a level — the Ork mirror of an
    // Imperial city levelling up. The central ORK_CAMP block is never overwritten (place/floor skip it).
    public static void fortifyCamp(ServerLevel serverLevel, BlockPos center, int campLevel) {
        RandomSource rng = serverLevel.random;
        int r = CAMP_RADIUS_BASE + campLevel * 2;

        WorldGenPlacement.clearVegetation(serverLevel, center, r + 2, 10);

        BlockState logState = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean perimeter = Math.abs(x) == r || Math.abs(z) == r;

                if (!perimeter) {
                    // Lay (or refresh) the trampled floor, never touching the central camp block.
                    if (!center.offset(x, -1, z).equals(center)) {
                        serverLevel.setBlock(center.offset(x, -1, z), ((x + z) & 1) == 0
                                ? Blocks.COARSE_DIRT.defaultBlockState()
                                : Blocks.PODZOL.defaultBlockState(), 3);
                    }
                    continue;
                }

                boolean gateGap = z == r && x >= -1 && x <= 1;
                if (gateGap) {
                    continue;
                }

                for (int y = 0; y < 3; y++) {
                    serverLevel.setBlock(center.offset(x, y, z), logState, 3);
                }
                serverLevel.setBlock(center.offset(x, 3, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 3);

                if (Math.floorMod(x + z, 6) == 0) {
                    place(serverLevel, center.offset(x, 4, z), Blocks.RED_BANNER.defaultBlockState());
                }
            }
        }

        // Horned gate totems flanking the southern opening.
        buildGateTotem(serverLevel, center.offset(-2, 0, r));
        buildGateTotem(serverLevel, center.offset(2, 0, r));

        // Watchtowers and tents, more of them as the stronghold grows.
        buildWatchtower(serverLevel, center.offset(r - 2, 0, -(r - 2)));
        buildWatchtower(serverLevel, center.offset(-(r - 2), 0, -(r - 2)));
        buildTent(serverLevel, center.offset(-4, 0, -3));
        buildTent(serverLevel, center.offset(4, 0, -4));

        if (campLevel >= 3) {
            buildWatchtower(serverLevel, center.offset(r - 2, 0, r - 2));
            buildWatchtower(serverLevel, center.offset(-(r - 2), 0, r - 2));
            buildTent(serverLevel, center.offset(-5, 0, 3));
        }

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
