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

        serverLevel.setBlock(surface, FCRegistry.ORK_CAMP.get().defaultBlockState(), 3);

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
        return campPos != null && serverLevel.getBlockState(campPos).is(FCRegistry.ORK_CAMP.get());
    }

    // The starter camp is small (radius CAMP_RADIUS_BASE). As its WAAAGH! grows the camp fortifies
    // outward into a full stronghold — see fortifyCamp.
    private static final int CAMP_RADIUS_BASE = 4;

    // Level 1 — a small Ork settlement: a cleared, crudely paved plaza with a couple of scrappy Ork
    // huts, a trophy totem and red banners. NO tents and NO campfires — it reads as a fledgling Ork
    // town, not a camp. The central block (the Núcleo Ork, set afterwards) is left untouched. It
    // grows into a proper walled Ork city over time (fortifyCamp).
    private static void buildCampStructure(ServerLevel serverLevel, BlockPos center) {
        RandomSource rng = serverLevel.random;
        int r = CAMP_RADIUS_BASE;

        // Strip surrounding trees/leaves/plants so the settlement isn't buried in forest canopy.
        WorldGenPlacement.clearVegetation(serverLevel, center, r + 2, 8);

        // Clear the interior to the sky and pave a crude plaza, leaving the central block alone.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= 4; y++) {
                    BlockPos clear = center.offset(x, y, z);
                    if (!clear.equals(center)) {
                        serverLevel.setBlock(clear, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                serverLevel.setBlock(center.offset(x, -1, z), ((x + z) & 1) == 0
                        ? Blocks.COARSE_DIRT.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }

        // A couple of crude Ork huts, a trophy totem and banner stakes.
        buildOrkHut(serverLevel, center.offset(2, 0, 1), 3, 3, 3);
        buildOrkHut(serverLevel, center.offset(-4, 0, -3), 3, 3, 3);
        buildGateTotem(serverLevel, center.offset(0, 0, -r));
        buildBannerStake(serverLevel, center.offset(r, 0, r));
        buildBannerStake(serverLevel, center.offset(-r, 0, -r));

        scatterScrap(serverLevel, center, r, rng);
    }

    // A short stake flying a red Ork banner.
    private static void buildBannerStake(ServerLevel serverLevel, BlockPos base) {
        place(serverLevel, base, Blocks.SPRUCE_FENCE.defaultBlockState());
        place(serverLevel, base.offset(0, 1, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
        place(serverLevel, base.offset(0, 2, 0), Blocks.RED_BANNER.defaultBlockState());
    }

    // As an Ork city grows it expands into a larger WALLED settlement: a low crude wall of dark stone
    // topped with iron-bar spikes, a gate, stone watchtowers, and more Ork huts inside — scaling with
    // campLevel (radius CAMP_RADIUS_BASE + campLevel*2). The Ork mirror of an Imperial city levelling
    // up. The central Núcleo Ork block is never overwritten (the clear loop skips it).
    // The Ork city is raised at the SAME scale as an Imperial city of the same level (mirrors
    // ImperialCommandCoreBlockEntity.getCityStructureRadius: 8/15/22/30), so the two peoples build
    // settlements of equal size — the Ork city is a true peer, not a small camp.
    private static int cityRadiusForLevel(int campLevel) {
        return switch (campLevel) {
            case 2 -> 15;
            case 3 -> 22;
            case 4 -> 30;
            default -> 8;
        };
    }

    public static void fortifyCamp(ServerLevel serverLevel, BlockPos center, int campLevel) {
        RandomSource rng = serverLevel.random;
        int r = cityRadiusForLevel(campLevel);

        WorldGenPlacement.clearVegetation(serverLevel, center, r + 2, 12);

        BlockState wallBase = Blocks.COBBLED_DEEPSLATE.defaultBlockState();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                // Clear the interior to the sky, leaving the central Núcleo Ork block.
                for (int y = 0; y <= 5; y++) {
                    BlockPos c = center.offset(x, y, z);
                    if (!c.equals(center)) {
                        serverLevel.setBlock(c, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                serverLevel.setBlock(center.offset(x, -1, z), ((x + z) & 1) == 0
                        ? Blocks.COARSE_DIRT.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState(), 3);

                boolean perimeter = Math.abs(x) == r || Math.abs(z) == r;
                boolean gateGap = z == r && x >= -1 && x <= 1;
                if (perimeter && !gateGap) {
                    serverLevel.setBlock(center.offset(x, 0, z), wallBase, 3);
                    serverLevel.setBlock(center.offset(x, 1, z), wallBase, 3);
                    serverLevel.setBlock(center.offset(x, 2, z), Blocks.IRON_BARS.defaultBlockState(), 3);
                    if (Math.floorMod(x + z, 6) == 0) {
                        place(serverLevel, center.offset(x, 3, z), Blocks.RED_BANNER.defaultBlockState());
                    }
                }
            }
        }

        // Horned gate totems flanking the southern opening, and stone watchtowers on the corners.
        buildGateTotem(serverLevel, center.offset(-2, 0, r));
        buildGateTotem(serverLevel, center.offset(2, 0, r));
        buildWatchtower(serverLevel, center.offset(r - 2, 0, -(r - 2)));
        buildWatchtower(serverLevel, center.offset(-(r - 2), 0, -(r - 2)));
        if (campLevel >= 3) {
            buildWatchtower(serverLevel, center.offset(r - 2, 0, r - 2));
            buildWatchtower(serverLevel, center.offset(-(r - 2), 0, r - 2));
        }

        // Ork huts filling the city — scaled to the (now city-sized) radius so it doesn't look empty.
        int hutRing = r - 3;
        int huts = Math.max(4, r / 2);
        for (int i = 0; i < huts; i++) {
            double ang = 2.0 * Math.PI * i / huts + 0.4;
            int hx = (int) Math.round(Math.cos(ang) * hutRing);
            int hz = (int) Math.round(Math.sin(ang) * hutRing);
            buildOrkHut(serverLevel, center.offset(hx - 1, 0, hz - 1), 3, 3, 3);
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

    // A crude Ork hut: a small scrap-and-timber shack — log corners, plank walls, an open doorway, a
    // barred window and a flat overhanging roof, with a red banner on the front.
    private static void buildOrkHut(ServerLevel serverLevel, BlockPos start, int width, int depth, int height) {
        BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState post = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                boolean border = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
                boolean corner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);
                if (!border) {
                    continue;
                }

                for (int y = 0; y < height; y++) {
                    boolean doorway = z == 0 && x == width / 2 && y <= 1;
                    if (doorway) {
                        continue;
                    }
                    boolean window = !corner && y == 1 && (x == width / 2 || z == depth / 2);
                    place(serverLevel, start.offset(x, y, z),
                            corner ? post : (window ? Blocks.IRON_BARS.defaultBlockState() : wall));
                }
            }
        }

        // Flat crude roof with a one-block overhang.
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= depth; z++) {
                place(serverLevel, start.offset(x, height, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
        place(serverLevel, start.offset(width / 2, height + 1, 0), Blocks.RED_BANNER.defaultBlockState());
    }

    // A crude Ork lookout tower of dark stone, topped with a banner and a trophy skull.
    private static void buildWatchtower(ServerLevel serverLevel, BlockPos base) {
        int height = 6;
        BlockState stone = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        for (int y = 0; y < height; y++) {
            serverLevel.setBlock(base.offset(0, y, 0), stone, 3);
            serverLevel.setBlock(base.offset(1, y, 0), stone, 3);
            serverLevel.setBlock(base.offset(0, y, 1), stone, 3);
            serverLevel.setBlock(base.offset(1, y, 1), stone, 3);
        }

        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                place(serverLevel, base.offset(dx, height, dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        // Railing, banner and skull on the lookout.
        place(serverLevel, base.offset(-1, height + 1, -1), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
        place(serverLevel, base.offset(2, height + 1, 2), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
        place(serverLevel, base.offset(0, height + 1, 0), Blocks.RED_BANNER.defaultBlockState());
        place(serverLevel, base.offset(1, height + 1, 1), Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
    }

    // A battered anvil, a cauldron and looted scrap strewn around the Ork city — NO campfires.
    private static void scatterScrap(ServerLevel serverLevel, BlockPos center, int r, RandomSource rng) {
        place(serverLevel, center.offset(3, 0, -2), Blocks.ANVIL.defaultBlockState());
        place(serverLevel, center.offset(-3, 0, 2), Blocks.CAULDRON.defaultBlockState());

        for (int i = 0; i < 8; i++) {
            int x = rng.nextInt(r * 2 - 2) - (r - 1);
            int z = rng.nextInt(r * 2 - 2) - (r - 1);
            BlockPos pos = center.offset(x, 0, z);

            if (pos.equals(center) || !serverLevel.isEmptyBlock(pos)) {
                continue;
            }

            BlockState scrap = switch (rng.nextInt(3)) {
                case 0 -> Blocks.IRON_BARS.defaultBlockState();
                case 1 -> Blocks.CHAIN.defaultBlockState();
                default -> Blocks.DEEPSLATE_TILE_WALL.defaultBlockState();
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
