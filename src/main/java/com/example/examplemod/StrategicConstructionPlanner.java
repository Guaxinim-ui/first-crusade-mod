package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;

public final class StrategicConstructionPlanner {
    private StrategicConstructionPlanner() {
    }

    public static BlockPos findConstructionSite(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            StrategicConstructionType type,
            StrategicWarAIData data
    ) {
        BlockPos corePos = core.getBlockPos();

        int borderRadius = Math.max(24, core.getBuildBorderRadius());
        int footprint = type.getFootprintRadius();

        for (int radius = 12; radius <= borderRadius; radius += 6) {
            for (int attempt = 0; attempt < 32; attempt++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0D;

                int x = corePos.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = corePos.getZ() + (int) Math.round(Math.sin(angle) * radius);

                // Build on the city's ground plane (the Core's Y), NOT the heightmap. The heightmap
                // returns the top of the tallest block — i.e. the ROOF of a nearby house — so sites
                // were landing on rooftops. On the ground plane an occupied spot fails the clear check
                // (its walls aren't replaceable) and is simply skipped, so houses are built around.
                BlockPos surface = new BlockPos(x, corePos.getY(), z);

                if (data.hasActiveProjectAt(surface)) {
                    continue;
                }

                if (isBuildAreaClear(level, surface, footprint)) {
                    return surface;
                }
            }
        }

        return null;
    }

    public static List<ConstructionPlacement> createPlacements(StrategicConstructionType type, BlockPos center) {
        List<ConstructionPlacement> list = new ArrayList<>();

        switch (type) {
            case HABITATION -> createHabitation(center, list);
            case FARM -> createFarm(center, list);
            case MINE -> createMine(center, list, ExampleMod.IMPERIAL_MINE.get());
            case GOLD_MINE -> createMine(center, list, ExampleMod.IMPERIAL_GOLD_MINE.get());
            case SCRAP_YARD -> createScrapYard(center, list);
            case REFINERY -> createIndustrial(center, list, ExampleMod.IMPERIAL_PROMETHIUM_REFINERY.get());
            case FORGE -> createIndustrial(center, list, ExampleMod.IMPERIAL_FORGE.get());
            case BARRACKS -> createBarracks(center, list);
            case WALL_BASTION -> createWallBastion(center, list);
            case TRADE_DEPOT -> createTradeDepot(center, list);
            case COMMAND_BASTION -> createCommandBastion(center, list);
            default -> createHabitation(center, list);
        }

        return list;
    }

    public static int countBlocks(StrategicConstructionType type) {
        return createPlacements(type, BlockPos.ZERO).size();
    }

    public static BlockPos ground(ServerLevel level, BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
    }

    private static boolean isBuildAreaClear(ServerLevel level, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos floor = center.offset(x, -1, z);

                if (level.isEmptyBlock(floor)) {
                    return false;
                }

                for (int y = 0; y <= 7; y++) {
                    BlockPos check = center.offset(x, y, z);

                    if (!isReplaceable(level, check)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean isReplaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        return state.isAir()
                || state.getCollisionShape(level, pos).isEmpty()
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW);
    }

    private static void createFoundation(BlockPos center, List<ConstructionPlacement> list, int radius, BlockState floor) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                list.add(new ConstructionPlacement(center.offset(x, -1, z), floor));
            }
        }
    }

    private static void createHabitation(BlockPos center, List<ConstructionPlacement> list) {
        createFoundation(center, list, 3, Blocks.POLISHED_ANDESITE.defaultBlockState());

        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState glass = Blocks.GLASS_PANE.defaultBlockState();

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean perimeter = Math.abs(x) == 3 || Math.abs(z) == 3;

                if (!perimeter) {
                    continue;
                }

                for (int y = 0; y <= 3; y++) {
                    if (y == 1 && (x == 0 || z == 0)) {
                        list.add(new ConstructionPlacement(center.offset(x, y, z), glass));
                    } else {
                        list.add(new ConstructionPlacement(center.offset(x, y, z), wall));
                    }
                }
            }
        }

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                list.add(new ConstructionPlacement(center.offset(x, 4, z), roof));
            }
        }

        BlockState bedFoot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);

        BlockState bedHead = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);

        list.add(new ConstructionPlacement(center.offset(-1, 0, -1), bedFoot));
        list.add(new ConstructionPlacement(center.offset(-1, 0, 0), bedHead));
        list.add(new ConstructionPlacement(center.offset(1, 0, -1), bedFoot));
        list.add(new ConstructionPlacement(center.offset(1, 0, 0), bedHead));

        list.add(new ConstructionPlacement(center, ExampleMod.IMPERIAL_HABITATION.get().defaultBlockState()));
    }

    private static void createFarm(BlockPos center, List<ConstructionPlacement> list) {
        createFoundation(center, list, 4, Blocks.DIRT.defaultBlockState());

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                if (Math.abs(x) == 4 || Math.abs(z) == 4) {
                    list.add(new ConstructionPlacement(center.offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState()));
                } else {
                    list.add(new ConstructionPlacement(center.offset(x, 0, z), Blocks.FARMLAND.defaultBlockState()));
                    list.add(new ConstructionPlacement(center.offset(x, 1, z), Blocks.WHEAT.defaultBlockState()));
                }
            }
        }

        list.add(new ConstructionPlacement(center, ExampleMod.IMPERIAL_FARM.get().defaultBlockState()));
    }

    private static void createMine(BlockPos center, List<ConstructionPlacement> list, Block centralBlock) {
        createFoundation(center, list, 3, Blocks.COBBLED_DEEPSLATE.defaultBlockState());

        BlockState support = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState roof = Blocks.DEEPSLATE_BRICKS.defaultBlockState();

        for (int y = 0; y <= 3; y++) {
            list.add(new ConstructionPlacement(center.offset(3, y, 3), support));
            list.add(new ConstructionPlacement(center.offset(-3, y, 3), support));
            list.add(new ConstructionPlacement(center.offset(3, y, -3), support));
            list.add(new ConstructionPlacement(center.offset(-3, y, -3), support));
        }

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    list.add(new ConstructionPlacement(center.offset(x, 4, z), roof));
                }
            }
        }

        list.add(new ConstructionPlacement(center.offset(0, 0, 1), Blocks.RAIL.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(0, 0, 2), Blocks.RAIL.defaultBlockState()));
        list.add(new ConstructionPlacement(center, centralBlock.defaultBlockState()));
    }

    private static void createScrapYard(BlockPos center, List<ConstructionPlacement> list) {
        createFoundation(center, list, 3, Blocks.COARSE_DIRT.defaultBlockState());

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    list.add(new ConstructionPlacement(center.offset(x, 0, z), Blocks.IRON_BARS.defaultBlockState()));
                }
            }
        }

        list.add(new ConstructionPlacement(center.offset(2, 0, 1), Blocks.ANVIL.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(-2, 0, -1), Blocks.CHAIN.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(1, 0, -2), Blocks.IRON_BARS.defaultBlockState()));
        list.add(new ConstructionPlacement(center, ExampleMod.IMPERIAL_SCRAP_YARD.get().defaultBlockState()));
    }

    private static void createIndustrial(BlockPos center, List<ConstructionPlacement> list, Block centralBlock) {
        createFoundation(center, list, 4, Blocks.POLISHED_BLACKSTONE.defaultBlockState());

        BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState metal = Blocks.IRON_BLOCK.defaultBlockState();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean perimeter = Math.abs(x) == 4 || Math.abs(z) == 4;

                if (!perimeter) {
                    continue;
                }

                for (int y = 0; y <= 3; y++) {
                    list.add(new ConstructionPlacement(center.offset(x, y, z), wall));
                }
            }
        }

        for (int y = 0; y <= 5; y++) {
            list.add(new ConstructionPlacement(center.offset(3, y, 0), metal));
            list.add(new ConstructionPlacement(center.offset(-3, y, 0), metal));
        }

        list.add(new ConstructionPlacement(center.offset(3, 6, 0), Blocks.CAMPFIRE.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(-3, 6, 0), Blocks.CAMPFIRE.defaultBlockState()));
        list.add(new ConstructionPlacement(center, centralBlock.defaultBlockState()));
    }

    private static void createBarracks(BlockPos center, List<ConstructionPlacement> list) {
        createFoundation(center, list, 4, Blocks.POLISHED_ANDESITE.defaultBlockState());

        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean perimeter = Math.abs(x) == 4 || Math.abs(z) == 4;

                if (!perimeter) {
                    continue;
                }

                for (int y = 0; y <= 3; y++) {
                    if (y == 1 && (x == 0 || z == 0)) {
                        list.add(new ConstructionPlacement(center.offset(x, y, z), bars));
                    } else {
                        list.add(new ConstructionPlacement(center.offset(x, y, z), wall));
                    }
                }
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                list.add(new ConstructionPlacement(center.offset(x, 4, z), roof));
            }
        }

        list.add(new ConstructionPlacement(center.offset(-2, 0, 0), Blocks.IRON_BARS.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(2, 0, 0), Blocks.IRON_BARS.defaultBlockState()));
        list.add(new ConstructionPlacement(center, ExampleMod.IMPERIAL_BARRACKS.get().defaultBlockState()));
    }

    private static void createWallBastion(BlockPos center, List<ConstructionPlacement> list) {
        BlockState ferrocrete = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState plasteel = Blocks.IRON_BARS.defaultBlockState();

        for (int x = -5; x <= 5; x++) {
            for (int y = 0; y <= 3; y++) {
                list.add(new ConstructionPlacement(center.offset(x, y, -1), ferrocrete));
                list.add(new ConstructionPlacement(center.offset(x, y, 1), ferrocrete));
            }
        }

        for (int y = 0; y <= 5; y++) {
            list.add(new ConstructionPlacement(center.offset(-5, y, -1), ferrocrete));
            list.add(new ConstructionPlacement(center.offset(-5, y, 1), ferrocrete));
            list.add(new ConstructionPlacement(center.offset(5, y, -1), ferrocrete));
            list.add(new ConstructionPlacement(center.offset(5, y, 1), ferrocrete));
        }

        for (int x = -4; x <= 4; x += 2) {
            list.add(new ConstructionPlacement(center.offset(x, 4, -1), plasteel));
            list.add(new ConstructionPlacement(center.offset(x, 4, 1), plasteel));
        }

        list.add(new ConstructionPlacement(center.offset(0, 5, 0), Blocks.BELL.defaultBlockState()));
    }

    private static void createTradeDepot(BlockPos center, List<ConstructionPlacement> list) {
        createFoundation(center, list, 4, Blocks.SMOOTH_STONE.defaultBlockState());

        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) == 4 || Math.abs(z) == 4) {
                    for (int y = 0; y <= 2; y++) {
                        list.add(new ConstructionPlacement(center.offset(x, y, z), wall));
                    }
                }
            }
        }

        list.add(new ConstructionPlacement(center.offset(2, 0, 2), Blocks.CHEST.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(-2, 0, 2), Blocks.CHEST.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(2, 0, -2), Blocks.GOLD_BLOCK.defaultBlockState()));
        list.add(new ConstructionPlacement(center, ExampleMod.IMPERIAL_EMERALD_TRADE_DEPOT.get().defaultBlockState()));
    }

    private static void createCommandBastion(BlockPos center, List<ConstructionPlacement> list) {
        createFoundation(center, list, 5, Blocks.POLISHED_DEEPSLATE.defaultBlockState());

        BlockState ferrocrete = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState ceramite = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState plasteel = Blocks.IRON_BLOCK.defaultBlockState();

        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                boolean perimeter = Math.abs(x) == 5 || Math.abs(z) == 5;

                if (!perimeter) {
                    continue;
                }

                for (int y = 0; y <= 4; y++) {
                    list.add(new ConstructionPlacement(center.offset(x, y, z), ferrocrete));
                }
            }
        }

        for (int y = 0; y <= 7; y++) {
            list.add(new ConstructionPlacement(center.offset(0, y, 0), plasteel));
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                list.add(new ConstructionPlacement(center.offset(x, 5, z), ceramite));
            }
        }

        list.add(new ConstructionPlacement(center.offset(0, 8, 0), Blocks.BEACON.defaultBlockState()));
    }

    public record ConstructionPlacement(BlockPos pos, BlockState state) {
    }
}