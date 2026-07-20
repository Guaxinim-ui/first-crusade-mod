package com.example.examplemod.hive.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Batched, resumable "set-to-air" clear of a single axis-aligned box, used by
 * {@code /fchive city full_test_clear} to remove the full-city test build without freezing the
 * server (a synchronous fill of a 960×320×960 region would lock the thread — spec §9/§10 trap).
 *
 * <p>Persisted on the {@code hive_world} level so a clear-in-progress survives save/reload. The box
 * is walked chunk-column by chunk-column (16×16×height), and {@link HiveCityTicker} drains it at a
 * bounded {@link #BLOCKS_PER_TICK} budget. The current column's chunk is force-loaded while it is
 * being processed and released as soon as the column is finished — no chunk stays permanently forced.
 */
public class HiveClearQueue extends SavedData {

    public static final String DATA_NAME = "firstcrusade_hive_clear_queue";

    /** Conservative per-tick block budget. Dev tool: prioritises not crashing over raw speed. */
    public static final int BLOCKS_PER_TICK = 96_000;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private boolean active;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int minCX, minCZ, numCX, numCZ;
    private int ci;          // current chunk-column index (0 .. numCX*numCZ-1)
    private int li;          // block index within the current column
    private long total, cleared;

    public HiveClearQueue() {}

    public static HiveClearQueue load(CompoundTag t) {
        HiveClearQueue q = new HiveClearQueue();
        q.active = t.getBoolean("active");
        q.minX = t.getInt("minX"); q.minY = t.getInt("minY"); q.minZ = t.getInt("minZ");
        q.maxX = t.getInt("maxX"); q.maxY = t.getInt("maxY"); q.maxZ = t.getInt("maxZ");
        q.minCX = t.getInt("minCX"); q.minCZ = t.getInt("minCZ");
        q.numCX = t.getInt("numCX"); q.numCZ = t.getInt("numCZ");
        q.ci = t.getInt("ci"); q.li = t.getInt("li");
        q.total = t.getLong("total"); q.cleared = t.getLong("cleared");
        return q;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putBoolean("active", active);
        t.putInt("minX", minX); t.putInt("minY", minY); t.putInt("minZ", minZ);
        t.putInt("maxX", maxX); t.putInt("maxY", maxY); t.putInt("maxZ", maxZ);
        t.putInt("minCX", minCX); t.putInt("minCZ", minCZ);
        t.putInt("numCX", numCX); t.putInt("numCZ", numCZ);
        t.putInt("ci", ci); t.putInt("li", li);
        t.putLong("total", total); t.putLong("cleared", cleared);
        return t;
    }

    /** Forge 1.20.1 (47.x) three-arg get-or-create (see {@link HiveGenerationQueue#get}). */
    public static HiveClearQueue get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                HiveClearQueue::load, HiveClearQueue::new, DATA_NAME);
    }

    /** Begin clearing the given inclusive box. Overwrites any clear already in progress. */
    public void start(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.minCX = minX >> 4; this.minCZ = minZ >> 4;
        this.numCX = (maxX >> 4) - minCX + 1;
        this.numCZ = (maxZ >> 4) - minCZ + 1;
        this.ci = 0; this.li = 0; this.cleared = 0;
        long h = (long) (maxY - minY + 1);
        this.total = (long) (maxX - minX + 1) * (maxZ - minZ + 1) * h;
        this.active = true;
        setDirty();
    }

    public boolean isActive() { return active; }
    public long total()       { return total; }
    public long cleared()     { return cleared; }
    public int percent()      { return total == 0 ? 100 : (int) Math.floor(100.0 * cleared / total); }

    public void cancel() {
        active = false;
        ci = 0; li = 0; total = 0; cleared = 0;
        setDirty();
    }

    /**
     * Clear up to {@link #BLOCKS_PER_TICK} blocks. Returns how many world blocks were actually set
     * to air this call. Sets {@link #active} to false when the whole box is done.
     */
    public int process(ServerLevel level) {
        if (!active) return 0;
        final int numCols = numCX * numCZ;
        final int height = maxY - minY + 1;
        final int perCol = 16 * 16 * height;
        int budget = BLOCKS_PER_TICK;
        int did = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        while (did < budget && active) {
            if (ci >= numCols) { active = false; break; }
            int ccx = minCX + (ci % numCX);
            int ccz = minCZ + (ci / numCX);
            boolean forcedHere = !level.getForcedChunks().contains(new ChunkPos(ccx, ccz).toLong());
            if (forcedHere) level.setChunkForced(ccx, ccz, true);
            try {
                while (did < budget && li < perCol) {
                    int lx = li & 15;
                    int lz = (li >> 4) & 15;
                    int ly = li >> 8;
                    int wx = ccx * 16 + lx, wz = ccz * 16 + lz, wy = minY + ly;
                    if (wx >= minX && wx <= maxX && wz >= minZ && wz <= maxZ) {
                        pos.set(wx, wy, wz);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, AIR, 2);
                        }
                    }
                    li++; did++; cleared++;
                }
            } finally {
                if (forcedHere) level.setChunkForced(ccx, ccz, false);
            }
            if (li >= perCol) { ci++; li = 0; } else { break; } // budget hit mid-column
        }
        setDirty();
        return did;
    }
}
