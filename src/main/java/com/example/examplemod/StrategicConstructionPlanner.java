package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

public final class StrategicConstructionPlanner {
    private StrategicConstructionPlanner() {
    }

    /**
     * Chooses (and reserves) a zone-appropriate, collision-free, entity-safe site for the given
     * construction using the city's {@link CityLayoutPlan}: habitations go to the residential ring,
     * industry against the wall, command buildings near the plaza, farms outside the gates. The
     * returned footprint is already registered in the plan, so no later construction can overlap
     * it. Returns null when no valid slot exists (the caller simply skips this cycle).
     */
    public static CityStructureFootprint reserveConstructionSite(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            StrategicConstructionType type,
            StrategicWarAIData data
    ) {
        BlockPos corePos = core.getBlockPos();

        StrategicSettlementRecord record = data.getOrCreateImperial(level, corePos);
        CityLayoutPlan plan = record.getOrCreateLayoutPlan(corePos);

        int borderRadius = Math.max(plan.getWallRadius() + 12, core.getBuildBorderRadius());
        int half = type.getFootprintRadius();

        CityLayoutPlan.Zone zone = zoneFor(type);

        CityStructureFootprint slot =
                plan.findSlot(level, type.name(), half, half, 8, 2, zone, borderRadius);

        // Zone full -> the city expands past its walls instead of giving up.
        if (slot == null && zone != CityLayoutPlan.Zone.EXPANSION) {
            slot = plan.findSlot(level, type.name(), half, half, 8, 2,
                    CityLayoutPlan.Zone.EXPANSION, borderRadius);
        }

        if (slot == null || data.hasActiveProjectAt(slot.getOrigin())) {
            return null;
        }

        plan.registerFootprint(slot);
        data.setDirty();

        return slot;
    }

    private static CityLayoutPlan.Zone zoneFor(StrategicConstructionType type) {
        return switch (type) {
            case HABITATION, TRADE_DEPOT -> CityLayoutPlan.Zone.INNER;
            case COMMAND_BASTION -> CityLayoutPlan.Zone.CIVIC;
            case WALL_BASTION -> CityLayoutPlan.Zone.DEFENSE;
            case FARM -> CityLayoutPlan.Zone.EXPANSION;
            default -> CityLayoutPlan.Zone.OUTER;
        };
    }

    public static List<ConstructionPlacement> createPlacements(
            StrategicConstructionType type,
            BlockPos center,
            Direction facing
    ) {
        List<ConstructionPlacement> list = new ArrayList<>();

        switch (type) {
            case HABITATION -> createHabitation(center, list, facing);
            case FARM -> createFarm(center, list, facing);
            case MINE -> createMine(center, list, FCRegistry.IMPERIAL_MINE.get());
            case GOLD_MINE -> createMine(center, list, FCRegistry.IMPERIAL_GOLD_MINE.get());
            case SCRAP_YARD -> createScrapYard(center, list, facing);
            case REFINERY -> createIndustrial(center, list, facing, FCRegistry.IMPERIAL_PROMETHIUM_REFINERY.get());
            case FORGE -> createIndustrial(center, list, facing, FCRegistry.IMPERIAL_FORGE.get());
            case BARRACKS -> createBarracks(center, list, facing);
            case WALL_BASTION -> createWallBastion(center, list);
            case TRADE_DEPOT -> createTradeDepot(center, list, facing);
            case COMMAND_BASTION -> createCommandBastion(center, list, facing);
            default -> createHabitation(center, list, facing);
        }

        return list;
    }

    public static int countBlocks(StrategicConstructionType type) {
        return createPlacements(type, BlockPos.ZERO, Direction.SOUTH).size();
    }

    /** The direction a building's door must face so it opens toward the city center. */
    public static Direction facingToward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }

        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * The perimeter column that stays open as the doorway (every closed building gets one, on the
     * face pointing at the city — so the door always opens onto the structure's access path).
     */
    private static boolean isDoorColumn(int x, int z, Direction facing, int halfWidth, int halfDepth) {
        return switch (facing) {
            case EAST -> x == halfWidth && z == 0;
            case WEST -> x == -halfWidth && z == 0;
            case NORTH -> z == -halfDepth && x == 0;
            default -> z == halfDepth && x == 0;
        };
    }

    public static BlockPos ground(ServerLevel level, BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
    }

    /** Appends a real dark-oak door (both halves) in the doorway column of the entrance face. */
    private static void addDoor(
            BlockPos center,
            List<ConstructionPlacement> list,
            Direction facing,
            int halfWidth,
            int halfDepth
    ) {
        BlockPos foot = switch (facing) {
            case EAST -> center.offset(halfWidth, 0, 0);
            case WEST -> center.offset(-halfWidth, 0, 0);
            case NORTH -> center.offset(0, 0, -halfDepth);
            default -> center.offset(0, 0, halfDepth);
        };

        BlockState lower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing);

        list.add(new ConstructionPlacement(foot, lower));
        list.add(new ConstructionPlacement(foot.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
    }

    private static void createFoundation(BlockPos center, List<ConstructionPlacement> list, int radius, BlockState floor) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                list.add(new ConstructionPlacement(center.offset(x, -1, z), floor));
            }
        }
    }

    private static void createHabitation(BlockPos center, List<ConstructionPlacement> list, Direction facing) {
        createFoundation(center, list, 3, Blocks.DEEPSLATE_TILES.defaultBlockState());

        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState pillar = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState roof = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean perimeter = Math.abs(x) == 3 || Math.abs(z) == 3;

                if (!perimeter) {
                    continue;
                }

                boolean cornerPillar = Math.abs(x) == 3 && Math.abs(z) == 3;
                boolean doorway = isDoorColumn(x, z, facing, 3, 3);

                for (int y = 0; y <= 3; y++) {
                    // 2-high doorway facing the city — never a sealed box.
                    if (doorway && y <= 1) {
                        continue;
                    }

                    if (!cornerPillar && !doorway && y == 2 && (x == 0 || z == 0)) {
                        list.add(new ConstructionPlacement(center.offset(x, y, z), bars));
                    } else {
                        list.add(new ConstructionPlacement(center.offset(x, y, z), cornerPillar ? pillar : wall));
                    }
                }
            }
        }

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                list.add(new ConstructionPlacement(center.offset(x, 4, z), roof));
            }
        }

        list.add(new ConstructionPlacement(center.offset(0, 0, 2), Blocks.LANTERN.defaultBlockState()));

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

        addDoor(center, list, facing, 3, 3);
        list.add(new ConstructionPlacement(center, FCRegistry.IMPERIAL_HABITATION.get().defaultBlockState()));
    }

    private static void createFarm(BlockPos center, List<ConstructionPlacement> list, Direction facing) {
        createFoundation(center, list, 4, Blocks.DIRT.defaultBlockState());

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                if (Math.abs(x) == 4 || Math.abs(z) == 4) {
                    // The fence ring opens at the door column so farmers can walk in.
                    if (!isDoorColumn(x, z, facing, 4, 4)) {
                        list.add(new ConstructionPlacement(center.offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    list.add(new ConstructionPlacement(center.offset(x, 0, z), Blocks.FARMLAND.defaultBlockState()));
                    list.add(new ConstructionPlacement(center.offset(x, 1, z), Blocks.WHEAT.defaultBlockState()));
                }
            }
        }

        list.add(new ConstructionPlacement(center, FCRegistry.IMPERIAL_FARM.get().defaultBlockState()));
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

    private static void createScrapYard(BlockPos center, List<ConstructionPlacement> list, Direction facing) {
        createFoundation(center, list, 3, Blocks.COARSE_DIRT.defaultBlockState());

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if ((Math.abs(x) == 3 || Math.abs(z) == 3) && !isDoorColumn(x, z, facing, 3, 3)) {
                    list.add(new ConstructionPlacement(center.offset(x, 0, z), Blocks.IRON_BARS.defaultBlockState()));
                }
            }
        }

        list.add(new ConstructionPlacement(center.offset(2, 0, 1), Blocks.ANVIL.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(-2, 0, -1), Blocks.CHAIN.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(1, 0, -2), Blocks.IRON_BARS.defaultBlockState()));
        list.add(new ConstructionPlacement(center, FCRegistry.IMPERIAL_SCRAP_YARD.get().defaultBlockState()));
    }

    private static void createIndustrial(BlockPos center, List<ConstructionPlacement> list, Direction facing, Block centralBlock) {
        createFoundation(center, list, 4, Blocks.POLISHED_BLACKSTONE.defaultBlockState());

        BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState metal = Blocks.IRON_BLOCK.defaultBlockState();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean perimeter = Math.abs(x) == 4 || Math.abs(z) == 4;

                if (!perimeter) {
                    continue;
                }

                boolean doorway = isDoorColumn(x, z, facing, 4, 4);

                for (int y = 0; y <= 3; y++) {
                    if (doorway && y <= 1) {
                        continue;
                    }

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
        addDoor(center, list, facing, 4, 4);
        list.add(new ConstructionPlacement(center, centralBlock.defaultBlockState()));
    }

    private static void createBarracks(BlockPos center, List<ConstructionPlacement> list, Direction facing) {
        createFoundation(center, list, 4, Blocks.POLISHED_BLACKSTONE.defaultBlockState());

        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState roof = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean perimeter = Math.abs(x) == 4 || Math.abs(z) == 4;

                if (!perimeter) {
                    continue;
                }

                boolean doorway = isDoorColumn(x, z, facing, 4, 4);

                for (int y = 0; y <= 3; y++) {
                    if (doorway && y <= 1) {
                        continue;
                    }

                    if (y == 1 && (x == 0 || z == 0) && !doorway) {
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
        addDoor(center, list, facing, 4, 4);
        list.add(new ConstructionPlacement(center, FCRegistry.IMPERIAL_BARRACKS.get().defaultBlockState()));
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

    private static void createTradeDepot(BlockPos center, List<ConstructionPlacement> list, Direction facing) {
        createFoundation(center, list, 4, Blocks.SMOOTH_STONE.defaultBlockState());

        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) == 4 || Math.abs(z) == 4) {
                    boolean doorway = isDoorColumn(x, z, facing, 4, 4);

                    for (int y = 0; y <= 2; y++) {
                        if (doorway && y <= 1) {
                            continue;
                        }

                        list.add(new ConstructionPlacement(center.offset(x, y, z), wall));
                    }
                }
            }
        }

        list.add(new ConstructionPlacement(center.offset(2, 0, 2), Blocks.CHEST.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(-2, 0, 2), Blocks.CHEST.defaultBlockState()));
        list.add(new ConstructionPlacement(center.offset(2, 0, -2), Blocks.GOLD_BLOCK.defaultBlockState()));
        list.add(new ConstructionPlacement(center, FCRegistry.IMPERIAL_EMERALD_TRADE_DEPOT.get().defaultBlockState()));
    }

    private static void createCommandBastion(BlockPos center, List<ConstructionPlacement> list, Direction facing) {
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

                boolean doorway = isDoorColumn(x, z, facing, 5, 5);

                for (int y = 0; y <= 4; y++) {
                    // 3-high gothic gateway into the bastion.
                    if (doorway && y <= 2) {
                        continue;
                    }

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