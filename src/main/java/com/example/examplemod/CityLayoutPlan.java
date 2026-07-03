package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/**
 * The planned physical layout of one Imperial settlement, AoE-style: a square plaza around the
 * Core, two 3-wide cardinal avenues running to four wall gates, a square curtain wall with corner
 * towers, and concentric placement bands (zones) between them. Persisted inside the city's
 * {@link StrategicSettlementRecord}.
 *
 * The plan is the city's spatial memory: every structure registers a {@link CityStructureFootprint}
 * here, and gates / towers / patrol points are recorded for the military AI. Roads and the plaza
 * are geometric (derived from center + radii), so they cost nothing to store and can never be
 * "forgotten" — the validator simply refuses to let anything be built on top of them.
 *
 * Distances use Chebyshev metric (max of |dx|,|dz|) so all the rings are squares, matching the
 * brutalist square wall.
 */
public class CityLayoutPlan {
    public static final int ROAD_HALF_WIDTH = 1;     // avenues are 3 blocks wide
    public static final int DEFAULT_PLAZA_RADIUS = 6;
    public static final int DEFAULT_WALL_RADIUS = 24;

    /** Placement bands (zones), from the plaza outward. */
    public enum Zone {
        CIVIC,       // shrine / command hall — just off the plaza
        INNER,       // hab blocks, depots
        OUTER,       // industry and military yards, against the wall
        DEFENSE,     // the wall ring itself (bastions, towers)
        EXPANSION    // outside the walls, up to the build border
    }

    private BlockPos center;
    private int plazaRadius = DEFAULT_PLAZA_RADIUS;
    private int wallRadius = DEFAULT_WALL_RADIUS;

    private final List<CityStructureFootprint> footprints = new ArrayList<>();
    private final List<BlockPos> gates = new ArrayList<>();
    private final List<BlockPos> towers = new ArrayList<>();
    private final List<BlockPos> patrolPoints = new ArrayList<>();

    public CityLayoutPlan(BlockPos center) {
        this.center = center.immutable();
    }

    public BlockPos getCenter() {
        return center;
    }

    public int getPlazaRadius() {
        return plazaRadius;
    }

    public int getWallRadius() {
        return wallRadius;
    }

    public void setWallRadius(int wallRadius) {
        this.wallRadius = wallRadius;
    }

    public List<CityStructureFootprint> getFootprints() {
        return footprints;
    }

    public List<BlockPos> getGates() {
        return gates;
    }

    public List<BlockPos> getTowers() {
        return towers;
    }

    public List<BlockPos> getPatrolPoints() {
        return patrolPoints;
    }

    public void registerFootprint(CityStructureFootprint footprint) {
        footprints.add(footprint);
    }

    public void freeFootprintAt(BlockPos origin) {
        footprints.removeIf(footprint -> footprint.getOrigin().equals(origin));
    }

    public boolean collides(CityStructureFootprint candidate) {
        for (CityStructureFootprint existing : footprints) {
            if (existing.intersects(candidate)) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------
    // Geometric features (roads, plaza, wall) — computed, never stored.
    // ------------------------------------------------------------------

    /** Chebyshev distance from the city center (square rings). */
    public int ringDistance(int x, int z) {
        return Math.max(Math.abs(x - center.getX()), Math.abs(z - center.getZ()));
    }

    /** The plaza and the two cardinal avenues (out to just past the wall) are protected ground. */
    public boolean isRoadOrPlaza(int x, int z) {
        int dx = x - center.getX();
        int dz = z - center.getZ();

        if (Math.max(Math.abs(dx), Math.abs(dz)) <= plazaRadius) {
            return true;
        }

        int roadReach = wallRadius + 4;

        boolean onNorthSouthAvenue = Math.abs(dx) <= ROAD_HALF_WIDTH && Math.abs(dz) <= roadReach;
        boolean onEastWestAvenue = Math.abs(dz) <= ROAD_HALF_WIDTH && Math.abs(dx) <= roadReach;

        return onNorthSouthAvenue || onEastWestAvenue;
    }

    /** The curtain wall band. Nothing but defense structures may straddle it. */
    public boolean isOnWallBand(int x, int z) {
        int distance = ringDistance(x, z);

        return distance >= wallRadius - 1 && distance <= wallRadius + 1;
    }

    /**
     * The [min, max] ring band a zone's structure CENTERS may occupy. Bands overlap slightly on
     * purpose — they steer placement, while hard rules (roads, wall band, collisions) are enforced
     * by the {@link CityPlacementValidator}, so a band never needs to be exact.
     */
    public int[] zoneBand(Zone zone, int buildBorderRadius) {
        return switch (zone) {
            case CIVIC -> new int[]{plazaRadius + 6, plazaRadius + 9};
            case INNER -> new int[]{plazaRadius + 8, Math.max(plazaRadius + 10, wallRadius - 6)};
            case OUTER -> new int[]{wallRadius - 7, wallRadius - 4};
            case DEFENSE -> new int[]{wallRadius - 1, wallRadius + 2};
            case EXPANSION -> new int[]{wallRadius + 6, Math.max(wallRadius + 12, buildBorderRadius)};
        };
    }

    // ------------------------------------------------------------------
    // Slot search
    // ------------------------------------------------------------------

    /**
     * Finds a valid, collision-free, entity-safe slot for a structure inside the given zone band.
     * Candidates are generated on square rings at regular steps (planned look, not random scatter)
     * with a rotating offset per ring so cities don't all look identical. Returns null when the
     * band is full — the caller may then try {@link Zone#EXPANSION}.
     */
    @Nullable
    public CityStructureFootprint findSlot(
            ServerLevel level,
            String typeName,
            int halfWidth,
            int halfDepth,
            int height,
            int margin,
            Zone zone,
            int buildBorderRadius
    ) {
        int[] band = zoneBand(zone, buildBorderRadius);

        for (int radius = band[0]; radius <= band[1]; radius += 3) {
            int positions = Math.max(8, radius);
            double offset = (radius % 3) * (Math.PI / positions);

            for (int i = 0; i < positions; i++) {
                double angle = offset + (Math.PI * 2.0D / positions) * i;

                int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);

                BlockPos origin = new BlockPos(x, center.getY(), z);

                // The entrance always faces the city center, so the doorway path leads to a road.
                BlockPos entrance = entranceToward(origin, halfWidth, halfDepth);

                CityStructureFootprint candidate = new CityStructureFootprint(
                        typeName, origin, halfWidth, halfDepth, height, margin, entrance
                );

                if (CityPlacementValidator.isValidSite(level, this, candidate, zone)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /** The doorway block on the structure edge that faces the city center. */
    private BlockPos entranceToward(BlockPos origin, int halfWidth, int halfDepth) {
        int dx = center.getX() - origin.getX();
        int dz = center.getZ() - origin.getZ();

        if (Math.abs(dx) >= Math.abs(dz)) {
            return origin.offset(dx > 0 ? halfWidth : -halfWidth, 0, 0);
        }

        return origin.offset(0, 0, dz > 0 ? halfDepth : -halfDepth);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putLong("Center", center.asLong());
        tag.putInt("PlazaRadius", plazaRadius);
        tag.putInt("WallRadius", wallRadius);

        ListTag footprintList = new ListTag();

        for (CityStructureFootprint footprint : footprints) {
            footprintList.add(footprint.save());
        }

        tag.put("Footprints", footprintList);
        tag.put("Gates", savePosList(gates));
        tag.put("Towers", savePosList(towers));
        tag.put("PatrolPoints", savePosList(patrolPoints));

        return tag;
    }

    public static CityLayoutPlan load(CompoundTag tag) {
        CityLayoutPlan plan = new CityLayoutPlan(BlockPos.of(tag.getLong("Center")));

        plan.plazaRadius = Math.max(4, tag.getInt("PlazaRadius"));
        plan.wallRadius = Math.max(12, tag.getInt("WallRadius"));

        ListTag footprintList = tag.getList("Footprints", Tag.TAG_COMPOUND);

        for (int i = 0; i < footprintList.size(); i++) {
            plan.footprints.add(CityStructureFootprint.load(footprintList.getCompound(i)));
        }

        loadPosList(tag, "Gates", plan.gates);
        loadPosList(tag, "Towers", plan.towers);
        loadPosList(tag, "PatrolPoints", plan.patrolPoints);

        return plan;
    }

    private static ListTag savePosList(List<BlockPos> positions) {
        ListTag list = new ListTag();

        for (BlockPos pos : positions) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos.asLong());
            list.add(entry);
        }

        return list;
    }

    private static void loadPosList(CompoundTag tag, String key, List<BlockPos> into) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            into.add(BlockPos.of(list.getCompound(i).getLong("Pos")));
        }
    }
}
