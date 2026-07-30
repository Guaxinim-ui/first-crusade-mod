package com.example.examplemod.flora.runtime;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.CityLayoutPlan;
import com.example.examplemod.CityStructureFootprint;
import com.example.examplemod.StrategicConstructionProject;
import com.example.examplemod.StrategicSettlementRecord;
import com.example.examplemod.StrategicWarAIData;
import com.example.examplemod.entity.SentinelWalkerEntity;
import com.example.examplemod.entity.ValkyrieGunshipEntity;
import com.example.examplemod.entity.vehicle.ImperialBattleTankEntity;
import com.example.examplemod.flora.FloraConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * Where vegetation is not allowed to grow, resolved once per chunk task.
 *
 * <p>A settlement is a physical thing with doors that have to open, roads that have to be walkable
 * and machines that have to be reachable. The decorator is fast and indiscriminate, so it needs to
 * be told about all of that up front — which is what this class is. It gathers the geometry from
 * the city's own {@link CityLayoutPlan} (the same record the placement validator uses when deciding
 * where a building may go, so vegetation and architecture agree on what the city is), flattens it
 * into plain rectangles, and then answers {@link #blocks(int, int)} with nothing but comparisons.
 *
 * <p><b>Column rules live here; block rules live in {@link FloraPlacementRules}.</b> This class
 * knows about footprints, roads, gates, build sites and parked vehicles — things with an extent on
 * the map. Whether a particular block happens to be a rail, a bed or a machine is a question about
 * one block, and is answered where the block is already in hand.
 *
 * <p>Nothing here loads a chunk: settlement records and layout plans are persisted world data. The
 * one live query is for parked vehicles, which is a single entity lookup over the chunk being
 * decorated — once per task, never per plant.
 */
public final class FloraExclusionZones {

    /** A forbidden rectangle on the XZ plane, margins already folded in. */
    private record Rect(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(int x, int z) {
            return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
        }
    }

    /** A city layout whose roads, plaza and wall band overlap this chunk. */
    private record LayoutGuard(CityLayoutPlan plan, int margin) {
        boolean blocks(int x, int z) {
            // Roads and the plaza are geometric — they are never stored as footprints, so they have
            // to be asked about directly or a street would quietly grass over. The avenues are
            // axis-aligned, so probing the centre and the four axis offsets covers the margin band
            // exactly, without the (2m+1)^2 sweep a naive check would cost on every attempt.
            if (this.plan.isRoadOrPlaza(x, z)
                    || this.plan.isRoadOrPlaza(x + this.margin, z)
                    || this.plan.isRoadOrPlaza(x - this.margin, z)
                    || this.plan.isRoadOrPlaza(x, z + this.margin)
                    || this.plan.isRoadOrPlaza(x, z - this.margin)) {
                return true;
            }

            return this.plan.isOnWallBand(x, z);
        }
    }

    private final List<Rect> rects;
    private final List<LayoutGuard> layouts;

    private FloraExclusionZones(List<Rect> rects, List<LayoutGuard> layouts) {
        this.rects = rects;
        this.layouts = layouts;
    }

    /** Nothing is excluded — used for dimensions with no settlement data (and in tests). */
    public static FloraExclusionZones empty() {
        return new FloraExclusionZones(List.of(), List.of());
    }

    /**
     * Collects every exclusion that can touch the given chunk.
     *
     * @param searchRadius how far outside the chunk to look, in blocks; a building whose walls sit
     *                     just over the chunk edge still has to keep its margin clear on this side
     */
    public static FloraExclusionZones forChunk(ServerLevel level, ChunkPos chunkPos, int searchRadius) {
        int margin = FloraConfig.STRUCTURE_MARGIN.get();

        int minX = chunkPos.getMinBlockX() - searchRadius;
        int minZ = chunkPos.getMinBlockZ() - searchRadius;
        int maxX = chunkPos.getMaxBlockX() + searchRadius;
        int maxZ = chunkPos.getMaxBlockZ() + searchRadius;

        List<Rect> rects = new ArrayList<>();
        List<LayoutGuard> layouts = new ArrayList<>();

        StrategicWarAIData warData = StrategicWarAIData.get(level);

        for (StrategicSettlementRecord record : warData.getImperialSettlements()) {
            CityLayoutPlan plan = record.getLayoutPlan();

            if (plan == null) {
                continue;
            }

            BlockPos centre = plan.getCenter();

            // Cheap rejection: the plan's roads reach a little past the wall, nothing further.
            int reach = plan.getWallRadius() + 8 + margin;

            if (centre.getX() + reach < minX || centre.getX() - reach > maxX
                    || centre.getZ() + reach < minZ || centre.getZ() - reach > maxZ) {
                continue;
            }

            layouts.add(new LayoutGuard(plan, margin));

            // Every building the city has ever raised, with its own circulation margin plus ours.
            for (CityStructureFootprint footprint : plan.getFootprints()) {
                BlockPos origin = footprint.getOrigin();
                int halfWidth = footprint.getHalfWidth() + footprint.getMargin() + margin;
                int halfDepth = footprint.getHalfDepth() + footprint.getMargin() + margin;

                addRect(rects, origin.getX() - halfWidth, origin.getZ() - halfDepth,
                        origin.getX() + halfWidth, origin.getZ() + halfDepth,
                        minX, minZ, maxX, maxZ);

                // The doorway and the ground in front of it stay clear, or a thistle ends up
                // standing in the entrance.
                BlockPos entrance = footprint.getEntrance();

                if (entrance != null) {
                    int doorClearance = margin + 2;

                    addRect(rects, entrance.getX() - doorClearance, entrance.getZ() - doorClearance,
                            entrance.getX() + doorClearance, entrance.getZ() + doorClearance,
                            minX, minZ, maxX, maxZ);
                }
            }

            // Gates are choke points: keep a wider apron so nothing ever blocks one.
            for (BlockPos gate : plan.getGates()) {
                int gateClearance = margin + 4;

                addRect(rects, gate.getX() - gateClearance, gate.getZ() - gateClearance,
                        gate.getX() + gateClearance, gate.getZ() + gateClearance,
                        minX, minZ, maxX, maxZ);
            }

            for (BlockPos tower : plan.getTowers()) {
                addRect(rects, tower.getX() - margin - 2, tower.getZ() - margin - 2,
                        tower.getX() + margin + 2, tower.getZ() + margin + 2,
                        minX, minZ, maxX, maxZ);
            }
        }

        // Ground that is about to become a building. Planting here would only be undone, loudly,
        // the moment construction starts.
        for (StrategicConstructionProject project : warData.getProjects()) {
            BlockPos site = project.getSitePos();
            int clearance = margin + 12;

            addRect(rects, site.getX() - clearance, site.getZ() - clearance,
                    site.getX() + clearance, site.getZ() + clearance,
                    minX, minZ, maxX, maxZ);
        }

        addParkedVehicles(level, chunkPos, margin, rects);

        return new FloraExclusionZones(rects, layouts);
    }

    /**
     * Parked vehicles. One entity query per chunk task — the alternative, asking per placement,
     * is exactly the kind of per-block query the performance rules forbid.
     */
    private static void addParkedVehicles(ServerLevel level, ChunkPos chunkPos, int margin, List<Rect> rects) {
        AABB chunkBox = new AABB(
                chunkPos.getMinBlockX() - 4, level.getMinBuildHeight(), chunkPos.getMinBlockZ() - 4,
                chunkPos.getMaxBlockX() + 5, level.getMaxBuildHeight(), chunkPos.getMaxBlockZ() + 5
        );

        List<Entity> vehicles = level.getEntities((Entity) null, chunkBox, entity ->
                entity instanceof ImperialBattleTankEntity
                        || entity instanceof SentinelWalkerEntity
                        || entity instanceof ValkyrieGunshipEntity);

        for (Entity vehicle : vehicles) {
            BlockPos pos = vehicle.blockPosition();
            int clearance = margin + 3;

            rects.add(new Rect(
                    pos.getX() - clearance, pos.getZ() - clearance,
                    pos.getX() + clearance, pos.getZ() + clearance));
        }
    }

    private static void addRect(List<Rect> into, int minX, int minZ, int maxX, int maxZ,
                                int windowMinX, int windowMinZ, int windowMaxX, int windowMaxZ) {
        // Only keep rectangles that can actually touch the chunk under decoration.
        if (maxX < windowMinX || minX > windowMaxX || maxZ < windowMinZ || minZ > windowMaxZ) {
            return;
        }

        into.add(new Rect(minX, minZ, maxX, maxZ));
    }

    /**
     * True when nothing may be planted in this column. Pure comparisons over a short list — this is
     * called once per placement attempt, so it must stay that way.
     */
    public boolean blocks(int worldX, int worldZ) {
        for (int i = 0; i < this.rects.size(); i++) {
            if (this.rects.get(i).contains(worldX, worldZ)) {
                return true;
            }
        }

        for (int i = 0; i < this.layouts.size(); i++) {
            if (this.layouts.get(i).blocks(worldX, worldZ)) {
                return true;
            }
        }

        return false;
    }

}
