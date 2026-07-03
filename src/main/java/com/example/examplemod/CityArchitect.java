package com.example.examplemod;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * Raises the founding layout of an autonomous Imperial settlement in the Astra Militarum idiom:
 * brutalist, gothic and military. Square plaza of chequered blackstone around the Command Core,
 * two lamp-lit cardinal avenues, a heavy deepslate curtain wall with buttresses, crenellations,
 * four fortified gates and corner watchtowers; an Imperial shrine off the plaza, dark hab-blocks
 * in the residential ring, industry against the wall and the farm outside the east gate.
 *
 * Everything goes through the safety funnel: NPCs are relocated out of every footprint before its
 * blocks are placed ({@link SafeEntityRelocator}), block entities and the Core are never
 * overwritten, every closed building has a doorway, a roof and a 3-high interior, and every
 * structure registers its {@link CityStructureFootprint} in the city's {@link CityLayoutPlan} so
 * later constructions can never collide with it. Gates, towers and patrol points are recorded in
 * the plan for the military AI.
 */
public final class CityArchitect {
    private static final int WALL_RADIUS = 24;
    private static final int WALL_HEIGHT = 6;
    private static final int TOWER_HALF = 2;
    private static final int TOWER_HEIGHT = 12;
    private static final int HAB_COUNT = 4;

    // The W40k palette.
    private static final BlockState FERROCRETE = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState DARK_PLATE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState DARK_TILE = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState BATTLEMENT = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
    private static final BlockState SLIT = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState();
    private static final BlockState HANGING_LAMP =
            Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState BRAZIER = Blocks.SOUL_LANTERN.defaultBlockState();

    private CityArchitect() {
    }

    public static void buildFoundingSettlement(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            StrategicSettlementRecord record
    ) {
        BlockPos center = core.getBlockPos();

        CityLayoutPlan plan = record.getOrCreateLayoutPlan(center);
        plan.setWallRadius(WALL_RADIUS);

        WorldGenPlacement.clearVegetation(level, center, WALL_RADIUS + 6, 10);

        sweepNpcsFromGroundWorks(level, plan);

        pavePlaza(level, plan, center);
        paveAvenues(level, plan, center);
        buildCurtainWall(level, plan, center);
        buildCornerTowers(level, plan, center);
        recordDefensePoints(plan, center);

        buildShrine(level, plan, center);
        buildHabBlocks(level, core, plan, center);
        buildWorksites(level, core, plan);
    }

    // ------------------------------------------------------------------
    // Ground works: plaza and avenues
    // ------------------------------------------------------------------

    private static void sweepNpcsFromGroundWorks(ServerLevel level, CityLayoutPlan plan) {
        BlockPos center = plan.getCenter();

        // Plaza sweep.
        SafeEntityRelocator.clearBox(
                level,
                squareBox(center, plan.getPlazaRadius() + 1, 4),
                center,
                plan.getPlazaRadius() + 1
        );

        // Wall band sweep, one strip per side.
        int radius = plan.getWallRadius();

        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos stripCenter = center.relative(side, radius);

            AABB strip = side.getAxis() == Direction.Axis.Z
                    ? new AABB(
                            center.getX() - radius - 1, center.getY() - 1, stripCenter.getZ() - 2,
                            center.getX() + radius + 2, center.getY() + WALL_HEIGHT + 1, stripCenter.getZ() + 3)
                    : new AABB(
                            stripCenter.getX() - 2, center.getY() - 1, center.getZ() - radius - 1,
                            stripCenter.getX() + 3, center.getY() + WALL_HEIGHT + 1, center.getZ() + radius + 2);

            SafeEntityRelocator.clearBox(level, strip, center, radius + 2);
        }
    }

    private static void pavePlaza(ServerLevel level, CityLayoutPlan plan, BlockPos center) {
        int radius = plan.getPlazaRadius();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean checker = ((x + z) & 1) == 0;
                safeSet(level, center, center.offset(x, -1, z), checker ? DARK_PLATE : DARK_TILE);
            }
        }

        // Gilded ring immediately around the Core, plus four corner braziers.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    safeSet(level, center, center.offset(x, -1, z), GILDED);
                }
            }
        }

        int b = radius - 1;
        safeSet(level, center, center.offset(b, 0, b), BRAZIER);
        safeSet(level, center, center.offset(-b, 0, b), BRAZIER);
        safeSet(level, center, center.offset(b, 0, -b), BRAZIER);
        safeSet(level, center, center.offset(-b, 0, -b), BRAZIER);
    }

    private static void paveAvenues(ServerLevel level, CityLayoutPlan plan, BlockPos center) {
        int from = plan.getPlazaRadius() + 1;
        int to = plan.getWallRadius() + 4;

        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int d = from; d <= to; d++) {
                BlockPos axis = center.relative(side, d);

                for (int w = -CityLayoutPlan.ROAD_HALF_WIDTH; w <= CityLayoutPlan.ROAD_HALF_WIDTH; w++) {
                    BlockPos cell = side.getAxis() == Direction.Axis.Z
                            ? axis.offset(w, 0, 0)
                            : axis.offset(0, 0, w);

                    boolean centerLine = w == 0 && d % 4 == 0;
                    safeSet(level, center, cell.below(), centerLine ? GILDED : DARK_PLATE);
                }

                // Lamp posts alternating sides of the avenue.
                if (d % 6 == 0 && d < plan.getWallRadius() - 2) {
                    int lampSide = (d / 6) % 2 == 0 ? 2 : -2;

                    BlockPos lampBase = side.getAxis() == Direction.Axis.Z
                            ? axis.offset(lampSide, 0, 0)
                            : axis.offset(0, 0, lampSide);

                    safeSet(level, center, lampBase, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
                    safeSet(level, center, lampBase.above(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
                    safeSet(level, center, lampBase.above(2), LAMP);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: curtain wall, gates and towers
    // ------------------------------------------------------------------

    private static void buildCurtainWall(ServerLevel level, CityLayoutPlan plan, BlockPos center) {
        int radius = plan.getWallRadius();

        for (int d = -radius; d <= radius; d++) {
            buildWallColumn(level, center, center.offset(d, 0, -radius), d, false);
            buildWallColumn(level, center, center.offset(d, 0, radius), d, false);
            buildWallColumn(level, center, center.offset(-radius, 0, d), d, true);
            buildWallColumn(level, center, center.offset(radius, 0, d), d, true);
        }
    }

    private static void buildWallColumn(
            ServerLevel level,
            BlockPos center,
            BlockPos base,
            int offsetAlongWall,
            boolean eastWestSide
    ) {
        safeSet(level, center, base.below(), DARK_PLATE);

        boolean gateOpening = Math.abs(offsetAlongWall) <= 1;

        if (gateOpening) {
            // Fortified gate: 3-wide, 3-high clear passage, plated lintel and a hanging lumen.
            for (int y = 0; y <= 2; y++) {
                clearBlock(level, center, base.above(y));
            }

            safeSet(level, center, base.above(3), DARK_PLATE);
            safeSet(level, center, base.above(4), FERROCRETE);
            safeSet(level, center, base.above(5), FERROCRETE);

            if (offsetAlongWall == 0) {
                safeSet(level, center, base.above(WALL_HEIGHT), BATTLEMENT);
            }

            return;
        }

        // Gate flanking pillars, one block past the opening.
        boolean gatePillar = Math.abs(offsetAlongWall) == 2;

        for (int y = 0; y < WALL_HEIGHT; y++) {
            safeSet(level, center, base.above(y), gatePillar ? DARK_PLATE : FERROCRETE);
        }

        if (gatePillar) {
            safeSet(level, center, base.above(WALL_HEIGHT), DARK_PLATE);
            safeSet(level, center, base.above(WALL_HEIGHT + 1), LAMP);
            return;
        }

        // Crenellated parapet.
        if ((offsetAlongWall & 1) == 0) {
            safeSet(level, center, base.above(WALL_HEIGHT), BATTLEMENT);
        }

        // Outward buttress with a lantern, every 6 blocks.
        if (offsetAlongWall % 6 == 0) {
            int signX = Integer.signum(base.getX() - center.getX());
            int signZ = Integer.signum(base.getZ() - center.getZ());

            BlockPos out = eastWestSide
                    ? base.offset(signX, 0, 0)
                    : base.offset(0, 0, signZ);

            for (int y = 0; y < WALL_HEIGHT - 1; y++) {
                safeSet(level, center, out.above(y), DARK_PLATE);
            }

            safeSet(level, center, out.above(WALL_HEIGHT - 1), LAMP);
        }
    }

    private static void buildCornerTowers(ServerLevel level, CityLayoutPlan plan, BlockPos center) {
        int radius = plan.getWallRadius();

        int[][] corners = {{radius, radius}, {radius, -radius}, {-radius, radius}, {-radius, -radius}};

        for (int[] corner : corners) {
            BlockPos towerCenter = center.offset(corner[0], 0, corner[1]);

            CityStructureFootprint footprint = new CityStructureFootprint(
                    "WATCHTOWER", towerCenter, TOWER_HALF, TOWER_HALF, TOWER_HEIGHT, 1,
                    towerCenter.offset(-Integer.signum(corner[0]) * TOWER_HALF, 0, 0)
            );

            SafeEntityRelocator.clearFootprint(level, footprint);
            buildWatchtower(level, center, towerCenter, corner[0], corner[1]);

            plan.registerFootprint(footprint);
            plan.getTowers().add(towerCenter.immutable());
        }
    }

    private static void buildWatchtower(
            ServerLevel level,
            BlockPos center,
            BlockPos towerCenter,
            int cornerX,
            int cornerZ
    ) {
        int doorX = -Integer.signum(cornerX) * TOWER_HALF;

        for (int x = -TOWER_HALF; x <= TOWER_HALF; x++) {
            for (int z = -TOWER_HALF; z <= TOWER_HALF; z++) {
                safeSet(level, center, towerCenter.offset(x, -1, z), DARK_PLATE);

                boolean perimeter = Math.abs(x) == TOWER_HALF || Math.abs(z) == TOWER_HALF;
                boolean cornerPillar = Math.abs(x) == TOWER_HALF && Math.abs(z) == TOWER_HALF;

                for (int y = 0; y < TOWER_HEIGHT; y++) {
                    BlockPos cell = towerCenter.offset(x, y, z);

                    if (!perimeter) {
                        // Hollow interior with a mid platform.
                        if (y == TOWER_HEIGHT - 1) {
                            safeSet(level, center, cell, DARK_PLATE);
                        } else {
                            clearBlock(level, center, cell);
                        }

                        continue;
                    }

                    // Doorway facing into the city: 1 wide, 2 high.
                    boolean doorway = x == doorX && z == 0 && y <= 1;

                    if (doorway) {
                        clearBlock(level, center, cell);
                        continue;
                    }

                    // Arrow slits on the face centers.
                    boolean slit = !cornerPillar && (x == 0 || z == 0) && (y == 4 || y == 8);

                    safeSet(level, center, cell, cornerPillar ? DARK_PLATE : (slit ? SLIT : FERROCRETE));
                }
            }
        }

        // Battlemented top with a beacon-lantern.
        for (int x = -TOWER_HALF; x <= TOWER_HALF; x++) {
            for (int z = -TOWER_HALF; z <= TOWER_HALF; z++) {
                boolean edge = Math.abs(x) == TOWER_HALF || Math.abs(z) == TOWER_HALF;

                if (edge && ((x + z) & 1) == 0) {
                    safeSet(level, center, towerCenter.offset(x, TOWER_HEIGHT, z), BATTLEMENT);
                }
            }
        }

        safeSet(level, center, towerCenter.above(TOWER_HEIGHT), BRAZIER);
        safeSet(level, center, towerCenter, LAMP);
    }

    private static void recordDefensePoints(CityLayoutPlan plan, BlockPos center) {
        int radius = plan.getWallRadius();

        plan.getGates().clear();
        plan.getGates().add(center.offset(radius, 0, 0));
        plan.getGates().add(center.offset(-radius, 0, 0));
        plan.getGates().add(center.offset(0, 0, radius));
        plan.getGates().add(center.offset(0, 0, -radius));

        plan.getPatrolPoints().clear();
        plan.getPatrolPoints().addAll(plan.getGates());
        plan.getPatrolPoints().addAll(plan.getTowers());
    }

    // ------------------------------------------------------------------
    // Buildings: shrine and hab-blocks
    // ------------------------------------------------------------------

    private static void buildShrine(ServerLevel level, CityLayoutPlan plan, BlockPos center) {
        CityStructureFootprint slot = plan.findSlot(
                level, "IMPERIAL_SHRINE", 4, 5, 9, 2, CityLayoutPlan.Zone.CIVIC, plan.getWallRadius());

        if (slot == null) {
            return;
        }

        SafeEntityRelocator.clearFootprint(level, slot);

        BlockPos origin = slot.getOrigin();
        Direction facing = entranceDirection(slot);

        // Foundation and floor.
        for (int x = -4; x <= 4; x++) {
            for (int z = -5; z <= 5; z++) {
                boolean nave = facing.getAxis() == Direction.Axis.Z ? x == 0 : z == 0;
                safeSet(level, center, origin.offset(x, -1, z), nave ? DARK_TILE : DARK_PLATE);
            }
        }

        // Walls with corner pillars, iron-bar lancet windows and a 3-high gothic doorway.
        for (int x = -4; x <= 4; x++) {
            for (int z = -5; z <= 5; z++) {
                boolean perimeter = Math.abs(x) == 4 || Math.abs(z) == 5;

                if (!perimeter) {
                    continue;
                }

                boolean cornerPillar = Math.abs(x) == 4 && Math.abs(z) == 5;
                boolean doorwayColumn = isEntranceColumn(origin, facing, x, z, 4, 5);

                for (int y = 0; y <= 4; y++) {
                    BlockPos cell = origin.offset(x, y, z);

                    if (doorwayColumn && y <= 2) {
                        clearBlock(level, center, cell);
                        continue;
                    }

                    boolean window = !cornerPillar && !doorwayColumn
                            && (y == 2 || y == 3)
                            && ((Math.abs(x) == 4 && z % 2 == 0) || (Math.abs(z) == 5 && x % 2 == 0));

                    safeSet(level, center, cell, cornerPillar ? DARK_PLATE : (window ? SLIT : FERROCRETE));
                }

                if (cornerPillar) {
                    safeSet(level, center, origin.offset(x, 5, z), DARK_PLATE);
                    safeSet(level, center, origin.offset(x, 6, z), LAMP);
                }
            }
        }

        // Roof, parapet and the central spire with a gilded finial.
        for (int x = -4; x <= 4; x++) {
            for (int z = -5; z <= 5; z++) {
                safeSet(level, center, origin.offset(x, 5, z), DARK_PLATE);

                boolean edge = Math.abs(x) == 4 || Math.abs(z) == 5;

                if (edge && ((x + z) & 1) == 0) {
                    safeSet(level, center, origin.offset(x, 6, z), BATTLEMENT);
                }
            }
        }

        for (int y = 6; y <= 8; y++) {
            safeSet(level, center, origin.above(y), FERROCRETE);
        }

        safeSet(level, center, origin.above(9), GILDED);
        safeSet(level, center, origin.above(10), Blocks.END_ROD.defaultBlockState());

        // Altar at the end opposite the door, plus interior lumens.
        BlockPos altar = origin.relative(facing.getOpposite(), facing.getAxis() == Direction.Axis.Z ? 3 : 2);
        safeSet(level, center, altar, GILDED);
        safeSet(level, center, altar.above(), BRAZIER);
        safeSet(level, center, origin.offset(2, 0, 0), LAMP);
        safeSet(level, center, origin.offset(-2, 0, 0), LAMP);
        safeSet(level, center, entranceLintel(slot), HANGING_LAMP);

        paveAccessPath(level, plan, slot);
        plan.registerFootprint(slot);
    }

    private static void buildHabBlocks(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            CityLayoutPlan plan,
            BlockPos center
    ) {
        for (int i = 0; i < HAB_COUNT; i++) {
            CityStructureFootprint slot = plan.findSlot(
                    level, "HAB_SMALL", 3, 3, 6, 2, CityLayoutPlan.Zone.INNER, plan.getWallRadius());

            if (slot == null) {
                return;
            }

            SafeEntityRelocator.clearFootprint(level, slot);
            buildHabBlock(level, core, center, slot);

            paveAccessPath(level, plan, slot);
            plan.registerFootprint(slot);
        }
    }

    private static void buildHabBlock(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            BlockPos center,
            CityStructureFootprint slot
    ) {
        BlockPos origin = slot.getOrigin();
        Direction facing = entranceDirection(slot);

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                safeSet(level, center, origin.offset(x, -1, z), DARK_TILE);

                boolean perimeter = Math.abs(x) == 3 || Math.abs(z) == 3;

                if (!perimeter) {
                    continue;
                }

                boolean cornerPillar = Math.abs(x) == 3 && Math.abs(z) == 3;
                boolean doorwayColumn = isEntranceColumn(origin, facing, x, z, 3, 3);

                for (int y = 0; y <= 3; y++) {
                    BlockPos cell = origin.offset(x, y, z);

                    if (doorwayColumn && y <= 1) {
                        clearBlock(level, center, cell);
                        continue;
                    }

                    boolean window = !cornerPillar && !doorwayColumn && y == 2 && (x == 0 || z == 0);

                    safeSet(level, center, cell, cornerPillar ? DARK_PLATE : (window ? SLIT : FERROCRETE));
                }
            }
        }

        // Flat plated roof with a parapet — interior stays 4 blocks clear (y0..3).
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                safeSet(level, center, origin.offset(x, 4, z), DARK_PLATE);

                boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;

                if (edge && ((x + z) & 1) == 0) {
                    safeSet(level, center, origin.offset(x, 5, z), BATTLEMENT);
                }
            }
        }

        // Two bunks, an interior lumen, a lamp over the door, and the habitation marker the
        // morale system reads (bound to the city's Core).
        BlockState bedFoot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
        BlockState bedHead = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);

        safeSet(level, center, origin.offset(-2, 0, -1), bedFoot);
        safeSet(level, center, origin.offset(-2, 0, 0), bedHead);
        safeSet(level, center, origin.offset(2, 0, -1), bedFoot);
        safeSet(level, center, origin.offset(2, 0, 0), bedHead);
        safeSet(level, center, origin.offset(0, 0, 2), LAMP);
        safeSet(level, center, entranceLintel(slot), HANGING_LAMP);

        safeSet(level, center, origin, ExampleMod.IMPERIAL_HABITATION.get().defaultBlockState());

        if (level.getBlockEntity(origin) instanceof ImperialHabitationBlockEntity habitation) {
            habitation.assignToCommandCore(core.getBlockPos());
        }
    }

    // ------------------------------------------------------------------
    // Industry: worksites via the strategic blueprints
    // ------------------------------------------------------------------

    private static void buildWorksites(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            CityLayoutPlan plan
    ) {
        buildWorksite(level, core, plan, StrategicConstructionType.MINE, CityLayoutPlan.Zone.OUTER);
        buildWorksite(level, core, plan, StrategicConstructionType.SCRAP_YARD, CityLayoutPlan.Zone.OUTER);
        buildWorksite(level, core, plan, StrategicConstructionType.FORGE, CityLayoutPlan.Zone.OUTER);
        buildWorksite(level, core, plan, StrategicConstructionType.FARM, CityLayoutPlan.Zone.EXPANSION);
    }

    private static void buildWorksite(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            CityLayoutPlan plan,
            StrategicConstructionType type,
            CityLayoutPlan.Zone zone
    ) {
        int half = type.getFootprintRadius();

        CityStructureFootprint slot = plan.findSlot(
                level, type.name(), half, half, 8, 2, zone,
                Math.max(plan.getWallRadius() + 12, core.getBuildBorderRadius()));

        if (slot == null) {
            return;
        }

        SafeEntityRelocator.clearFootprint(level, slot);

        List<StrategicConstructionPlanner.ConstructionPlacement> placements =
                StrategicConstructionPlanner.createPlacements(type, slot.getOrigin(), entranceDirection(slot));

        for (StrategicConstructionPlanner.ConstructionPlacement placement : placements) {
            safeSet(level, core.getBlockPos(), placement.pos(), placement.state());
        }

        StrategicConstructionBuilder.assignCompletedBlockEntity(
                level, core.getBlockPos(), slot.getOrigin(), type);

        paveAccessPath(level, plan, slot);
        plan.registerFootprint(slot);
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** The horizontal direction from the structure origin to its entrance. */
    private static Direction entranceDirection(CityStructureFootprint footprint) {
        BlockPos entrance = footprint.getEntrance();

        if (entrance == null) {
            return Direction.SOUTH;
        }

        int dx = entrance.getX() - footprint.getOrigin().getX();
        int dz = entrance.getZ() - footprint.getOrigin().getZ();

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }

        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** True when the perimeter column (x, z) is the entrance column on the entrance face. */
    private static boolean isEntranceColumn(
            BlockPos origin,
            Direction facing,
            int x,
            int z,
            int halfWidth,
            int halfDepth
    ) {
        return switch (facing) {
            case EAST -> x == halfWidth && z == 0;
            case WEST -> x == -halfWidth && z == 0;
            case SOUTH -> z == halfDepth && x == 0;
            default -> z == -halfDepth && x == 0;
        };
    }

    /** The block right above the doorway opening, for a hanging lumen. */
    private static BlockPos entranceLintel(CityStructureFootprint footprint) {
        BlockPos entrance = footprint.getEntrance();

        if (entrance == null) {
            return footprint.getOrigin().above(2);
        }

        return entrance.above(2);
    }

    /** A paved straight path from the structure's entrance to the nearest avenue. */
    private static void paveAccessPath(ServerLevel level, CityLayoutPlan plan, CityStructureFootprint slot) {
        BlockPos entrance = slot.getEntrance();

        if (entrance == null) {
            return;
        }

        BlockPos center = plan.getCenter();
        int dx = entrance.getX() - center.getX();
        int dz = entrance.getZ() - center.getZ();

        if (Math.abs(dx) <= Math.abs(dz)) {
            // The north-south avenue is nearest: walk X toward the center axis.
            int step = dx > 0 ? -1 : 1;

            for (int x = entrance.getX() + step;
                    Math.abs(x - center.getX()) > CityLayoutPlan.ROAD_HALF_WIDTH;
                    x += step) {
                pavePathCell(level, center, new BlockPos(x, entrance.getY() - 1, entrance.getZ()));
            }
        } else {
            int step = dz > 0 ? -1 : 1;

            for (int z = entrance.getZ() + step;
                    Math.abs(z - center.getZ()) > CityLayoutPlan.ROAD_HALF_WIDTH;
                    z += step) {
                pavePathCell(level, center, new BlockPos(entrance.getX(), entrance.getY() - 1, z));
            }
        }
    }

    private static void pavePathCell(ServerLevel level, BlockPos center, BlockPos floor) {
        // Only re-surface existing solid ground — a path must never bridge a hole it can fall off.
        if (!level.isEmptyBlock(floor) && level.getFluidState(floor).isEmpty()) {
            safeSet(level, center, floor, DARK_PLATE);
        }
    }

    /** Places a block, refusing to overwrite the Core or any block entity (chests, beds, cores). */
    private static void safeSet(ServerLevel level, BlockPos corePos, BlockPos pos, BlockState state) {
        if (pos.equals(corePos)) {
            return;
        }

        BlockState current = level.getBlockState(pos);

        if (current.hasBlockEntity()) {
            return;
        }

        level.setBlock(pos, state, 3);
    }

    private static void clearBlock(ServerLevel level, BlockPos corePos, BlockPos pos) {
        safeSet(level, corePos, pos, Blocks.AIR.defaultBlockState());
    }

    private static AABB squareBox(BlockPos center, int halfExtent, int height) {
        return new AABB(
                center.getX() - halfExtent,
                center.getY() - 1,
                center.getZ() - halfExtent,
                center.getX() + halfExtent + 1,
                center.getY() + height,
                center.getZ() + halfExtent + 1
        );
    }
}
