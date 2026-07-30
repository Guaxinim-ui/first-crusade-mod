package com.example.examplemod;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
    private static final int WALL_RADIUS = 26;
    private static final int WALL_HEIGHT = 6;
    private static final int TOWER_HALF = 2;
    private static final int TOWER_HEIGHT = 12;
    private static final int HAB_COUNT = 5;
    private static final int TALL_HAB_COUNT = 2;

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

    // Weathering/detail palette: cracked masonry mixed in deterministically, timber beams for
    // corners, glass for hab windows, chains for the wall lumens and stone for the depot.
    private static final BlockState CRACKED = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState STONEWORK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED_STONE = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState BEAM = Blocks.DARK_OAK_LOG.defaultBlockState();
    private static final BlockState BEAM_SPRUCE = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final BlockState SLAB_OVERHANG = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
    private static final BlockState SMOOTH = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState YARD_FLOOR = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState YARD_FLOOR_B = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TARGET = Blocks.HAY_BLOCK.defaultBlockState();

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

        // The ground this city now stands on has a new identity: its vegetation should be the
        // city's, not the wilderness it displaced. Flagging reaches past the wall so the fringe
        // blends outward instead of stopping at the footprint. Only loaded chunks are queued.
        com.example.examplemod.flora.runtime.FloraTransitionManager.onTerritoryCaptured(
                level, center, WALL_RADIUS + 48);

        sweepNpcsFromGroundWorks(level, plan);

        pavePlaza(level, plan, center);
        paveAvenues(level, plan, center);
        buildCurtainWall(level, plan, center);
        buildCornerTowers(level, plan, center);
        recordDefensePoints(plan, center);

        buildShrine(level, plan, center);
        buildHabBlocks(level, core, plan, center);
        buildStorageDepot(level, plan, center);
        buildTrainingYard(level, plan, center);
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
            // Fortified gate: 3-wide, 3-high clear passage under an iron portcullis, plated
            // lintel, gilded threshold on the center line.
            if (offsetAlongWall == 0) {
                safeSet(level, center, base.below(), GILDED);
            }

            for (int y = 0; y <= 2; y++) {
                clearBlock(level, center, base.above(y));
            }

            safeSet(level, center, base.above(3), SLIT);
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
            BlockPos cell = base.above(y);
            safeSet(level, center, cell, gatePillar ? DARK_PLATE : weatheredWall(cell));
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

        int signX = Integer.signum(base.getX() - center.getX());
        int signZ = Integer.signum(base.getZ() - center.getZ());

        // Outward buttress with a lantern, every 6 blocks.
        if (offsetAlongWall % 6 == 0) {
            BlockPos out = eastWestSide
                    ? base.offset(signX, 0, 0)
                    : base.offset(0, 0, signZ);

            for (int y = 0; y < WALL_HEIGHT - 1; y++) {
                safeSet(level, center, out.above(y), DARK_PLATE);
            }

            safeSet(level, center, out.above(WALL_HEIGHT - 1), LAMP);
        }

        // Chain-hung lumen on the INNER face, midway between buttresses — lights the street
        // along the wall for the patrols.
        if (Math.floorMod(offsetAlongWall + 3, 6) == 0) {
            BlockPos inner = eastWestSide
                    ? base.offset(-signX, 0, 0)
                    : base.offset(0, 0, -signZ);

            safeSet(level, center, inner.above(4), CHAIN);
            safeSet(level, center, inner.above(3), HANGING_LAMP);
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

                    // Lancet windows alternate stained glass and iron bars, gothic-chapel style.
                    BlockState windowPane = ((x + z) & 1) == 0 ? GLASS : SLIT;
                    safeSet(level, center, cell, cornerPillar ? DARK_PLATE : (window ? windowPane : weatheredWall(cell)));
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
        // Small habs first, then a couple of taller tenements — varied silhouettes instead of a
        // row of identical boxes. When the residential ring fills up, the overflow becomes a
        // suburb outside the walls (EXPANSION) instead of being dropped.
        for (int i = 0; i < HAB_COUNT; i++) {
            CityStructureFootprint slot = findSlotWithFallback(
                    level, plan, "HAB_SMALL", 3, 3, 8, 2, CityLayoutPlan.Zone.INNER);

            if (slot == null) {
                return;
            }

            SafeEntityRelocator.clearFootprint(level, slot);
            buildHabBlock(level, core, center, slot);

            paveAccessPath(level, plan, slot);
            plan.registerFootprint(slot);
        }

        for (int i = 0; i < TALL_HAB_COUNT; i++) {
            CityStructureFootprint slot = findSlotWithFallback(
                    level, plan, "HAB_TALL", 3, 3, 10, 2, CityLayoutPlan.Zone.INNER);

            if (slot == null) {
                return;
            }

            SafeEntityRelocator.clearFootprint(level, slot);
            buildTallHab(level, core, center, slot);

            paveAccessPath(level, plan, slot);
            plan.registerFootprint(slot);
        }
    }

    /**
     * A small hab: dark-tile foundation, blackstone plinth course, weathered ferrocrete walls
     * with dark-oak corner beams and glass windows, a real dark-oak door, and a stepped ziggurat
     * roof crowned by a lantern. Interior stays 4 blocks clear (y0..3).
     */
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

                boolean cornerBeam = Math.abs(x) == 3 && Math.abs(z) == 3;
                boolean doorwayColumn = isEntranceColumn(origin, facing, x, z, 3, 3);

                for (int y = 0; y <= 3; y++) {
                    BlockPos cell = origin.offset(x, y, z);

                    if (doorwayColumn && y <= 1) {
                        clearBlock(level, center, cell);
                        continue;
                    }

                    if (cornerBeam) {
                        safeSet(level, center, cell, BEAM);
                        continue;
                    }

                    if (y == 0) {
                        safeSet(level, center, cell, DARK_PLATE);
                        continue;
                    }

                    boolean window = !doorwayColumn && y == 2 && (x == 0 || z == 0);
                    safeSet(level, center, cell, window ? GLASS : weatheredWall(cell));
                }
            }
        }

        buildSteppedRoof(level, center, origin, 3, 4, DARK_TILE);
        safeSet(level, center, origin.above(7), LAMP);

        placeDoor(level, center, doorColumn(origin, facing, 3, 3), facing);

        // Two bunks, a hanging lumen under the ceiling, a storage barrel, a lit transom over the
        // door, and the habitation marker the morale system reads (bound to the city's Core).
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
        safeSet(level, center, origin.offset(0, 0, -2), Blocks.BARREL.defaultBlockState());
        safeSet(level, center, origin.above(3), HANGING_LAMP);
        safeSet(level, center, entranceLintel(slot), HANGING_LAMP);

        safeSet(level, center, origin, FCRegistry.IMPERIAL_HABITATION.get().defaultBlockState());

        if (level.getBlockEntity(origin) instanceof ImperialHabitationBlockEntity habitation) {
            habitation.assignToCommandCore(core.getBlockPos());
        }
    }

    /**
     * A taller two-storey-look tenement: same footprint but higher walls with a blackstone band
     * course, two rows of windows and a taller stepped roof. Sleeps three.
     */
    private static void buildTallHab(
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

                boolean cornerBeam = Math.abs(x) == 3 && Math.abs(z) == 3;
                boolean doorwayColumn = isEntranceColumn(origin, facing, x, z, 3, 3);

                for (int y = 0; y <= 5; y++) {
                    BlockPos cell = origin.offset(x, y, z);

                    if (doorwayColumn && y <= 1) {
                        clearBlock(level, center, cell);
                        continue;
                    }

                    if (cornerBeam) {
                        safeSet(level, center, cell, BEAM);
                        continue;
                    }

                    if (y == 0) {
                        safeSet(level, center, cell, DARK_PLATE);
                        continue;
                    }

                    // Band course between the two window rows (a "second floor" read).
                    if (y == 3 && !doorwayColumn) {
                        safeSet(level, center, cell, DARK_PLATE);
                        continue;
                    }

                    boolean window = !doorwayColumn && (y == 2 || y == 4) && (x == 0 || z == 0);
                    safeSet(level, center, cell, window ? GLASS : weatheredWall(cell));
                }
            }
        }

        buildSteppedRoof(level, center, origin, 3, 6, DARK_TILE);
        safeSet(level, center, origin.above(9), LAMP);

        placeDoor(level, center, doorColumn(origin, facing, 3, 3), facing);

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
        safeSet(level, center, origin.offset(0, 0, -2), bedFoot.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        safeSet(level, center, origin.offset(1, 0, -2), bedHead.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        safeSet(level, center, origin.above(4), HANGING_LAMP);
        safeSet(level, center, entranceLintel(slot), HANGING_LAMP);

        safeSet(level, center, origin, FCRegistry.IMPERIAL_HABITATION.get().defaultBlockState());

        if (level.getBlockEntity(origin) instanceof ImperialHabitationBlockEntity habitation) {
            habitation.assignToCommandCore(core.getBlockPos());
        }
    }

    /**
     * The storage depot: stone-brick warehouse with spruce beams, a slab-overhang roof, barrels
     * inside and a real door. The "supplies" building of the production quarter.
     */
    private static void buildStorageDepot(ServerLevel level, CityLayoutPlan plan, BlockPos center) {
        CityStructureFootprint slot = findSlotWithFallback(
                level, plan, "STORAGE_DEPOT", 3, 3, 8, 2, CityLayoutPlan.Zone.OUTER);

        if (slot == null) {
            return;
        }

        SafeEntityRelocator.clearFootprint(level, slot);

        BlockPos origin = slot.getOrigin();
        Direction facing = entranceDirection(slot);

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                safeSet(level, center, origin.offset(x, -1, z), SMOOTH);

                boolean perimeter = Math.abs(x) == 3 || Math.abs(z) == 3;

                if (!perimeter) {
                    continue;
                }

                boolean cornerBeam = Math.abs(x) == 3 && Math.abs(z) == 3;
                boolean doorwayColumn = isEntranceColumn(origin, facing, x, z, 3, 3);

                for (int y = 0; y <= 3; y++) {
                    BlockPos cell = origin.offset(x, y, z);

                    if (doorwayColumn && y <= 1) {
                        clearBlock(level, center, cell);
                        continue;
                    }

                    if (cornerBeam) {
                        safeSet(level, center, cell, BEAM_SPRUCE);
                        continue;
                    }

                    boolean window = !doorwayColumn && y == 2 && (x == 0 || z == 0);
                    safeSet(level, center, cell, window ? SLIT : weatheredStone(cell));
                }
            }
        }

        // Flat smooth-stone roof with a slab overhang all around (visual depth without stairs).
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                safeSet(level, center, origin.offset(x, 4, z), SMOOTH);
            }
        }

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) == 4 || Math.abs(z) == 4) {
                    safeSet(level, center, origin.offset(x, 4, z), SLAB_OVERHANG);
                }
            }
        }

        placeDoor(level, center, doorColumn(origin, facing, 3, 3), facing);

        safeSet(level, center, origin.offset(-2, 0, -2), Blocks.BARREL.defaultBlockState());
        safeSet(level, center, origin.offset(2, 0, -2), Blocks.BARREL.defaultBlockState());
        safeSet(level, center, origin.offset(-2, 0, 2), Blocks.BARREL.defaultBlockState());
        safeSet(level, center, origin.offset(-2, 1, -2), Blocks.BARREL.defaultBlockState());
        safeSet(level, center, origin.above(3), HANGING_LAMP);
        safeSet(level, center, entranceLintel(slot), HANGING_LAMP);

        paveAccessPath(level, plan, slot);
        plan.registerFootprint(slot);
    }

    /**
     * The training yard: an open gravel-and-stone drill square ringed by a low wall, with hay
     * targets, corner lamp posts and a gap toward the city. Its center is registered as a patrol
     * point — the muster ground where troops can form up without piling into a building.
     */
    private static void buildTrainingYard(ServerLevel level, CityLayoutPlan plan, BlockPos center) {
        CityStructureFootprint slot = findSlotWithFallback(
                level, plan, "TRAINING_YARD", 4, 4, 4, 2, CityLayoutPlan.Zone.OUTER);

        if (slot == null) {
            return;
        }

        SafeEntityRelocator.clearFootprint(level, slot);

        BlockPos origin = slot.getOrigin();
        Direction facing = entranceDirection(slot);

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean checker = ((x + z) & 1) == 0;
                safeSet(level, center, origin.offset(x, -1, z), checker ? YARD_FLOOR : YARD_FLOOR_B);

                boolean perimeter = Math.abs(x) == 4 || Math.abs(z) == 4;

                if (!perimeter) {
                    continue;
                }

                boolean corner = Math.abs(x) == 4 && Math.abs(z) == 4;

                if (corner) {
                    safeSet(level, center, origin.offset(x, 0, z), BEAM);
                    safeSet(level, center, origin.offset(x, 1, z), BEAM);
                    safeSet(level, center, origin.offset(x, 2, z), LAMP);
                    continue;
                }

                // Low drill-yard wall with a 3-wide muster gap on the city side.
                boolean gap = switch (facing) {
                    case EAST -> x == 4 && Math.abs(z) <= 1;
                    case WEST -> x == -4 && Math.abs(z) <= 1;
                    case SOUTH -> z == 4 && Math.abs(x) <= 1;
                    default -> z == -4 && Math.abs(x) <= 1;
                };

                if (!gap) {
                    safeSet(level, center, origin.offset(x, 0, z), BATTLEMENT);
                }
            }
        }

        // Hay targets on the far side from the gap.
        BlockPos targetRow = origin.relative(facing.getOpposite(), 3);
        safeSet(level, center, targetRow.offset(facing.getAxis() == Direction.Axis.Z ? -2 : 0, 0,
                facing.getAxis() == Direction.Axis.Z ? 0 : -2), TARGET);
        safeSet(level, center, targetRow.offset(facing.getAxis() == Direction.Axis.Z ? 2 : 0, 0,
                facing.getAxis() == Direction.Axis.Z ? 0 : 2), TARGET);

        // The muster ground doubles as a rally/patrol point for the military AI.
        plan.getPatrolPoints().add(origin.immutable());

        paveAccessPath(level, plan, slot);
        plan.registerFootprint(slot);
    }

    // ------------------------------------------------------------------
    // Industry: worksites via the strategic blueprints
    // ------------------------------------------------------------------

    private static void buildWorksites(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            CityLayoutPlan plan
    ) {
        // The military quarter gets a working Barracks from day one (it trains the recruits).
        buildWorksite(level, core, plan, StrategicConstructionType.BARRACKS, CityLayoutPlan.Zone.OUTER);

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
        int border = Math.max(plan.getWallRadius() + 12, core.getBuildBorderRadius());

        CityStructureFootprint slot = plan.findSlot(
                level, type.name(), half, half, 8, 2, zone, border);

        // Zone full -> spill over to the expansion belt outside the walls instead of giving up.
        if (slot == null && zone != CityLayoutPlan.Zone.EXPANSION) {
            slot = plan.findSlot(level, type.name(), half, half, 8, 2,
                    CityLayoutPlan.Zone.EXPANSION, border);
        }

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

    /**
     * Deterministic weathering: most cells are clean ferrocrete, some are cracked, a few are dark
     * tile — the same position always weathers the same way, so rebuilt walls don't flicker.
     */
    private static BlockState weatheredWall(BlockPos pos) {
        int mix = mixHash(pos);

        if (mix % 7 == 0) {
            return CRACKED;
        }

        if (mix % 11 == 0) {
            return DARK_TILE;
        }

        return FERROCRETE;
    }

    /** Stone-brick variant of the weathering, for the depot/production quarter. */
    private static BlockState weatheredStone(BlockPos pos) {
        int mix = mixHash(pos);

        if (mix % 6 == 0) {
            return CRACKED_STONE;
        }

        if (mix % 13 == 0) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }

        return STONEWORK;
    }

    private static int mixHash(BlockPos pos) {
        int mix = pos.getX() * 31 + pos.getZ() * 17 + pos.getY() * 13;
        mix ^= mix >> 7;
        return mix & 0x7fffffff;
    }

    /** A functional dark-oak door (lower + upper half) in a doorway column. */
    private static void placeDoor(ServerLevel level, BlockPos corePos, BlockPos foot, Direction facing) {
        BlockState lower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing);

        safeSet(level, corePos, foot, lower);
        safeSet(level, corePos, foot.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** The base block of the doorway column on the entrance face. */
    private static BlockPos doorColumn(BlockPos origin, Direction facing, int halfWidth, int halfDepth) {
        return switch (facing) {
            case EAST -> origin.offset(halfWidth, 0, 0);
            case WEST -> origin.offset(-halfWidth, 0, 0);
            case SOUTH -> origin.offset(0, 0, halfDepth);
            default -> origin.offset(0, 0, -halfDepth);
        };
    }

    /**
     * A stepped ziggurat roof: full cover at baseY, then each layer one block smaller and higher.
     * Reads brutalist/gothic, needs no stair block-states, and the bottom layer is the ceiling —
     * the interior below stays exactly as tall as the walls.
     */
    private static void buildSteppedRoof(
            ServerLevel level,
            BlockPos corePos,
            BlockPos origin,
            int half,
            int baseY,
            BlockState material
    ) {
        for (int layer = 0; layer < half; layer++) {
            int r = half - layer;

            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    safeSet(level, corePos, origin.offset(x, baseY + layer, z), material);
                }
            }
        }

        safeSet(level, corePos, origin.offset(0, baseY + half - 1, 0), material);
    }

    /** A slot in the requested ring, spilling over to the expansion belt when the ring is full. */
    private static CityStructureFootprint findSlotWithFallback(
            ServerLevel level,
            CityLayoutPlan plan,
            String typeName,
            int halfWidth,
            int halfDepth,
            int height,
            int margin,
            CityLayoutPlan.Zone zone
    ) {
        CityStructureFootprint slot = plan.findSlot(
                level, typeName, halfWidth, halfDepth, height, margin,
                zone, plan.getWallRadius());

        if (slot == null && zone != CityLayoutPlan.Zone.EXPANSION) {
            slot = plan.findSlot(level, typeName, halfWidth, halfDepth, height, margin,
                    CityLayoutPlan.Zone.EXPANSION, plan.getWallRadius() + 16);
        }

        return slot;
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
