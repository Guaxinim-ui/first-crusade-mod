package com.example.examplemod.hive.city;

import com.example.examplemod.hive.HiveMarkers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistent registry of every structure marker placed anywhere in the Hive City, plus the
 * district "instances" (one per physical placement — a district id can be placed many times
 * across the city; each copy is its own instance) that own them.
 *
 * <p>Spec §11: markers must stay valid after save/quit/reload/chunk-unload/cancel-and-resume, and
 * the store must remember more than just the last placement. The pre-existing mechanism
 * ({@link HiveMarkers}) is a transient in-memory buffer scoped to a single {@code placeInWorld}
 * call — it is still the right tool for the low-level capture channel (a {@link
 * net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor} runs mid-paste
 * and has no access to persistent storage), but it only ever remembers the *last* placement. This
 * class is the durable store the rest of the game (population, patrols, loot, diagnostics) reads
 * and writes.
 *
 * <p>Stored as {@link SavedData} on the {@code hive_world} level under {@link #DATA_NAME}, using
 * the same Forge 1.20.1 (47.x) three-argument {@code computeIfAbsent} pattern as {@link
 * HiveGenerationQueue} and {@link HiveClearQueue}.
 *
 * <p>Only one Hive City is tracked per level at a time (mirroring {@link HiveGenerationQueue},
 * which is itself a single queue per level) — {@link #resetForNewCity} wipes prior
 * districts/markers when a fresh build starts, so re-running {@code /fchive city generate} or
 * {@code build_full_test} never leaves stale ghost entries from a previous seed.
 *
 * <p><b>Scope note:</b> only the real city-generation path ({@code HiveCommands.placeDistrict},
 * used by {@link HiveCityPlacer} → {@link HiveCityTicker}) writes here. The interactive
 * {@code /fchive district place} dev command (single isolated district, no city context) keeps
 * using the ephemeral {@link HiveMarkers#last()} buffer only — mixing ad-hoc test placements into
 * "the" persisted city would corrupt the population/diagnostic queries built on top of this store.
 */
public class HiveCityMarkerData extends SavedData {

    public static final String DATA_NAME = "firstcrusade_hive_city_markers";

    // ---------------------------------------------------------------- district instances

    /** Runtime + persisted state of one physically-placed copy of a district. */
    public static final class DistrictInstance {
        public final int id;
        public final String districtId;
        public final BlockPos origin;
        public final int rotation;
        public SectorState sectorState = SectorState.NORMAL;

        DistrictInstance(int id, String districtId, BlockPos origin, int rotation) {
            this.id = id;
            this.districtId = districtId;
            this.origin = origin;
            this.rotation = rotation;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putString("district", districtId);
            t.putInt("x", origin.getX());
            t.putInt("y", origin.getY());
            t.putInt("z", origin.getZ());
            t.putInt("rot", rotation);
            t.putString("sector", sectorState.name());
            return t;
        }

        static DistrictInstance load(CompoundTag t) {
            DistrictInstance di = new DistrictInstance(t.getInt("id"), t.getString("district"),
                    new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")), t.getInt("rot"));
            try {
                di.sectorState = SectorState.valueOf(t.getString("sector"));
            } catch (IllegalArgumentException ignored) {
                di.sectorState = SectorState.NORMAL;
            }
            return di;
        }
    }

    /** Control state of a district instance's sector (spec §11: "estado de invasão ou controle"). */
    public enum SectorState { NORMAL, CONTESTED, ENEMY_CONTROLLED }

    // ---------------------------------------------------------------- marker records

    /** One persisted marker: every field spec §11 asks the store to remember. */
    public static final class MarkerRecord {
        public final int id;
        public final int instanceId;          // owning DistrictInstance.id
        public final String districtId;       // denormalised for cheap by-district queries
        public final HiveMarkers.MarkerType type;
        public final BlockPos pos;
        public boolean active = true;
        @Nullable public UUID linkedEntity;
        public long nextSpawnAtGameTime = 0L;
        public boolean lootCollected = false;

        MarkerRecord(int id, int instanceId, String districtId, HiveMarkers.MarkerType type, BlockPos pos) {
            this.id = id;
            this.instanceId = instanceId;
            this.districtId = districtId;
            this.type = type;
            this.pos = pos;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putInt("instance", instanceId);
            t.putString("district", districtId);
            t.putString("type", type.name());
            t.putInt("x", pos.getX());
            t.putInt("y", pos.getY());
            t.putInt("z", pos.getZ());
            t.putBoolean("active", active);
            if (linkedEntity != null) t.putUUID("entity", linkedEntity);
            t.putLong("nextSpawn", nextSpawnAtGameTime);
            t.putBoolean("loot", lootCollected);
            return t;
        }

        /** Returns {@code null} (skip, don't corrupt the store) for a type no longer registered. */
        @Nullable
        static MarkerRecord load(CompoundTag t) {
            HiveMarkers.MarkerType type;
            try {
                type = HiveMarkers.MarkerType.valueOf(t.getString("type"));
            } catch (IllegalArgumentException e) {
                return null;
            }
            MarkerRecord r = new MarkerRecord(t.getInt("id"), t.getInt("instance"),
                    t.getString("district"), type,
                    new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")));
            r.active = t.getBoolean("active");
            if (t.hasUUID("entity")) r.linkedEntity = t.getUUID("entity");
            r.nextSpawnAtGameTime = t.getLong("nextSpawn");
            r.lootCollected = t.getBoolean("loot");
            return r;
        }
    }

    // ---------------------------------------------------------------- city-level metadata

    private String cityId = "main";
    private long seed;
    private BlockPos center = BlockPos.ZERO;
    private int[] box = new int[]{0, 0, 0, 0, 0, 0};
    private boolean buildComplete;

    private int nextInstanceId = 1;
    private int nextMarkerId = 1;

    private final List<DistrictInstance> instances = new ArrayList<>();
    private final List<MarkerRecord> markers = new ArrayList<>();

    public HiveCityMarkerData() {}

    /** Forge 1.20.1 (47.x) three-arg get-or-create (see {@link HiveGenerationQueue#get}). */
    public static HiveCityMarkerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                HiveCityMarkerData::load, HiveCityMarkerData::new, DATA_NAME);
    }

    // ---------------------------------------------------------------- lifecycle

    /** Wipe every instance/marker and start tracking a fresh city build (process step 6). */
    public void resetForNewCity(long seed, BlockPos center, int[] box) {
        this.cityId = "main";
        this.seed = seed;
        this.center = center;
        this.box = box.clone();
        this.buildComplete = false;
        this.nextInstanceId = 1;
        this.nextMarkerId = 1;
        this.instances.clear();
        this.markers.clear();
        setDirty();
    }

    /** Register one physically-placed district copy and its captured markers (process step 15). */
    public int recordPlacement(String districtId, BlockPos origin, int rotation,
                                List<HiveMarkers.Captured> captured) {
        DistrictInstance instance = new DistrictInstance(nextInstanceId++, districtId, origin, rotation);
        instances.add(instance);
        for (HiveMarkers.Captured c : captured) {
            markers.add(new MarkerRecord(nextMarkerId++, instance.id, districtId, c.type(), c.pos()));
        }
        setDirty();
        return instance.id;
    }

    /** Marks the tracked city as fully placed (process step 19). */
    public void markComplete() {
        this.buildComplete = true;
        setDirty();
    }

    public void setSectorState(int instanceId, SectorState state) {
        DistrictInstance instance = findInstance(instanceId);
        if (instance != null) {
            instance.sectorState = state;
            setDirty();
        }
    }

    public void setActive(int markerId, boolean active) {
        MarkerRecord m = findMarker(markerId);
        if (m != null) {
            m.active = active;
            setDirty();
        }
    }

    public void linkEntity(int markerId, @Nullable UUID entity, long nextSpawnAtGameTime) {
        MarkerRecord m = findMarker(markerId);
        if (m != null) {
            m.linkedEntity = entity;
            m.nextSpawnAtGameTime = nextSpawnAtGameTime;
            setDirty();
        }
    }

    public void markLootCollected(int markerId) {
        MarkerRecord m = findMarker(markerId);
        if (m != null) {
            m.lootCollected = true;
            setDirty();
        }
    }

    // ---------------------------------------------------------------- queries

    public String cityId()      { return cityId; }
    public long seed()          { return seed; }
    public BlockPos center()    { return center; }
    public int[] box()          { return box.clone(); }
    public boolean isComplete() { return buildComplete; }

    public List<DistrictInstance> instances() { return List.copyOf(instances); }
    public List<MarkerRecord> markers()       { return List.copyOf(markers); }

    public List<MarkerRecord> markersOfType(HiveMarkers.MarkerType type) {
        return markers.stream().filter(m -> m.type == type).collect(Collectors.toList());
    }

    public List<MarkerRecord> markersInDistrict(String districtId) {
        return markers.stream().filter(m -> m.districtId.equals(districtId)).collect(Collectors.toList());
    }

    public List<MarkerRecord> markersInInstance(int instanceId) {
        return markers.stream().filter(m -> m.instanceId == instanceId).collect(Collectors.toList());
    }

    /** Every PATROL_POINT within one district instance forms that instance's implicit patrol route. */
    public List<MarkerRecord> patrolRoute(int instanceId) {
        return markers.stream()
                .filter(m -> m.instanceId == instanceId && m.type == HiveMarkers.MarkerType.PATROL_POINT)
                .collect(Collectors.toList());
    }

    public Map<HiveMarkers.MarkerType, Integer> countsByType() {
        Map<HiveMarkers.MarkerType, Integer> out = new EnumMap<>(HiveMarkers.MarkerType.class);
        for (MarkerRecord m : markers) out.merge(m.type, 1, Integer::sum);
        return out;
    }

    public Map<String, Integer> countsByDistrict() {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (MarkerRecord m : markers) out.merge(m.districtId, 1, Integer::sum);
        return out;
    }

    @Nullable
    public MarkerRecord findMarker(int markerId) {
        for (MarkerRecord m : markers) if (m.id == markerId) return m;
        return null;
    }

    @Nullable
    public DistrictInstance findInstance(int instanceId) {
        for (DistrictInstance d : instances) if (d.id == instanceId) return d;
        return null;
    }

    // ---------------------------------------------------------------- NBT

    public static HiveCityMarkerData load(CompoundTag tag) {
        HiveCityMarkerData d = new HiveCityMarkerData();
        d.cityId = tag.getString("cityId");
        if (d.cityId.isEmpty()) d.cityId = "main";
        d.seed = tag.getLong("seed");
        d.center = new BlockPos(tag.getInt("cx"), tag.getInt("cy"), tag.getInt("cz"));
        int[] loadedBox = tag.getIntArray("box");
        d.box = loadedBox.length == 6 ? loadedBox : new int[]{0, 0, 0, 0, 0, 0};
        d.buildComplete = tag.getBoolean("complete");
        d.nextInstanceId = tag.getInt("nextInstanceId");
        d.nextMarkerId = tag.getInt("nextMarkerId");
        if (d.nextInstanceId == 0) d.nextInstanceId = 1;
        if (d.nextMarkerId == 0) d.nextMarkerId = 1;

        ListTag instTag = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < instTag.size(); i++) {
            d.instances.add(DistrictInstance.load(instTag.getCompound(i)));
        }
        ListTag markTag = tag.getList("markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < markTag.size(); i++) {
            MarkerRecord r = MarkerRecord.load(markTag.getCompound(i));
            if (r != null) d.markers.add(r);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString("cityId", cityId);
        tag.putLong("seed", seed);
        tag.putInt("cx", center.getX());
        tag.putInt("cy", center.getY());
        tag.putInt("cz", center.getZ());
        tag.putIntArray("box", box);
        tag.putBoolean("complete", buildComplete);
        tag.putInt("nextInstanceId", nextInstanceId);
        tag.putInt("nextMarkerId", nextMarkerId);

        ListTag instTag = new ListTag();
        for (DistrictInstance d : instances) instTag.add(d.save());
        tag.put("instances", instTag);

        ListTag markTag = new ListTag();
        for (MarkerRecord m : markers) markTag.add(m.save());
        tag.put("markers", markTag);
        return tag;
    }
}
