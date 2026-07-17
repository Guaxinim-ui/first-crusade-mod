package com.example.examplemod.hive.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Persisted queue of district-placement tasks for the hive city generator.
 *
 * <p>Stored as {@link SavedData} on the {@code hive_world} level, so it survives save / quit /
 * reload — a half-generated city continues where it left off. Each task is one district to paste at
 * (origin, rotation) using the same {@code HiveDistricts.place} path the {@code /fchive district
 * place} command already uses.
 *
 * <p>The queue is drained by {@link HiveCityTicker} at a fixed budget of districts-per-tick, so we
 * never paste the whole city in one frame (which locks the server — a known trap, spec §4).
 *
 * <p><b>Persistence format</b> (NBT):
 * <pre>
 * {
 *   seed: long,
 *   totalPlanned: int,      // for % progress across reloads
 *   placedSoFar: int,
 *   tasks: [ { d:"firstcrusade:manufactorum", x,y,z:int, r:int }, ... ]  // remaining, in order
 * }
 * </pre>
 */
public class HiveGenerationQueue extends SavedData {

    public static final String DATA_NAME = "firstcrusade_hive_city_queue";

    /** One pending placement. */
    public record Task(String districtId, BlockPos origin, int rotation) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("d", districtId);
            t.putInt("x", origin.getX());
            t.putInt("y", origin.getY());
            t.putInt("z", origin.getZ());
            t.putInt("r", rotation);
            return t;
        }
        static Task load(CompoundTag t) {
            return new Task(t.getString("d"),
                    new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")),
                    t.getInt("r"));
        }
    }

    private final Deque<Task> tasks = new ArrayDeque<>();
    private long seed;
    private int totalPlanned;
    private int placedSoFar;

    public HiveGenerationQueue() {}

    /** Factory used by {@link net.minecraft.world.level.storage.DimensionDataStorage}. */
    public static HiveGenerationQueue load(CompoundTag tag) {
        HiveGenerationQueue q = new HiveGenerationQueue();
        q.seed = tag.getLong("seed");
        q.totalPlanned = tag.getInt("totalPlanned");
        q.placedSoFar = tag.getInt("placedSoFar");
        ListTag list = tag.getList("tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            q.tasks.addLast(Task.load(list.getCompound(i)));
        }
        return q;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("seed", seed);
        tag.putInt("totalPlanned", totalPlanned);
        tag.putInt("placedSoFar", placedSoFar);
        ListTag list = new ListTag();
        for (Task t : tasks) list.add(t.save());
        tag.put("tasks", list);
        return tag;
    }

    /**
     * Get-or-create the queue attached to the hive_world level.
     *
     * <p>NOTE (API version): this uses the <b>Forge 1.20.1 (47.x)</b> three-argument
     * {@code computeIfAbsent(Function loader, Supplier factory, String name)} form. The single
     * {@code SavedData.Factory} overload only exists in 1.20.2+/NeoForge — using it here would not
     * compile against 47.x (spec §4: validate every API against 47.x).
     */
    public static HiveGenerationQueue get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                HiveGenerationQueue::load,      // Function<CompoundTag, HiveGenerationQueue>
                HiveGenerationQueue::new,       // Supplier<HiveGenerationQueue>
                DATA_NAME);
    }

    /** Replace the queue contents with a fresh plan. */
    public void enqueuePlan(long seed, List<HiveCityLayout.PlacedDistrict> plan) {
        this.seed = seed;
        this.tasks.clear();
        for (HiveCityLayout.PlacedDistrict pd : plan) {
            tasks.addLast(new Task(pd.districtId(), pd.origin(), pd.rotation()));
        }
        this.totalPlanned = tasks.size();
        this.placedSoFar = 0;
        setDirty();
    }

    public boolean isEmpty()      { return tasks.isEmpty(); }
    public int remaining()        { return tasks.size(); }
    public int totalPlanned()     { return totalPlanned; }
    public int placedSoFar()      { return placedSoFar; }
    public long seed()            { return seed; }

    public int percent() {
        if (totalPlanned == 0) return 100;
        return (int) Math.floor(100.0 * placedSoFar / totalPlanned);
    }

    /** Peek the next task without removing it. */
    public Task peek() { return tasks.peekFirst(); }

    /** Pop the next task; call after a successful placement. */
    public Task pop() {
        Task t = tasks.pollFirst();
        if (t != null) { placedSoFar++; setDirty(); }
        return t;
    }

    /** Empty the queue (city cancel). */
    public void cancel() {
        tasks.clear();
        totalPlanned = 0;
        placedSoFar = 0;
        setDirty();
    }
}
