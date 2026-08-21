package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.example.examplemod.campaign.StrategicLocation;
import com.example.examplemod.planet.FCPlanets;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Register of every settlement in the war — every Imperial Command Core and every Ork Camp — on
 * every world the Crusade is fought on.
 *
 * <h2>One file, but never one planet (format 3)</h2>
 *
 * Entries used to be a flat set of packed {@link BlockPos} longs. That was correct while the mod
 * owned a single world. It is not correct now: {@code (100, 64, 100)} exists on Macragge and on
 * Armageddon, and the flat set could not tell an Imperial city on one from an Ork camp on the other.
 * Worse, {@link #get} resolves against the <i>overworld's</i> data storage no matter which level it
 * is handed, so all nine planets were writing into the same bucket and reading each other's
 * settlements back out. A city on Cadia could be "the nearest city" to a camp on Valhalla.
 *
 * <p>Entries are now bucketed by dimension. The storage stays on the overworld — deliberately, and
 * this is the one thing about the old design worth keeping: the War Table has to draw Armageddon
 * while the player stands on Macragge, and a per-level file cannot be read without loading the
 * level. One global file, keyed by planet, answers both questions.
 *
 * <p>Every accessor therefore takes a dimension. That is not ceremony: it is the compiler refusing
 * to let a caller forget which planet it meant, which is the bug this rewrite exists to close.
 *
 * <h2>Old saves</h2>
 *
 * A format-2 tag's flat sets are read into the {@link FCPlanets#DEFAULT} bucket. That save really
 * did have one world's settlements in it, and Macragge is where the Crusade starts. Anything that
 * was actually somewhere else re-registers itself on the correct planet the next time its chunk
 * ticks — settlements self-register, which is what makes the migration safe rather than merely
 * convenient. {@link #pruneOrphans} clears out whatever is left behind.
 *
 * <h2>Territorial attributes</h2>
 *
 * Alongside each position the map keeps the attributes that describe what territory that settlement
 * projects: an Imperial city's type and radii, an Ork camp's level and corruption reach. They are
 * pushed by the same tick that records the position, so the block entity stays the single source of
 * truth. {@link com.example.examplemod.flora.runtime.FloraRegionResolver} needs them for chunks whose
 * settlement is in an <b>unloaded</b> chunk, where no block entity can be read but this can.
 *
 * <p>{@link #getTerritoryRevision(ResourceKey)} counts changes to one planet's territorial picture,
 * per planet rather than globally: a city founded on Cadia used to invalidate every decorated chunk
 * on Catachan.
 */
public class WorldWarMapData extends SavedData {
    private static final String NAME = "firstcrusade_warmap";

    /** Bumped when the NBT layout changes; older saves are read on a best-effort basis. */
    private static final int FORMAT_VERSION = 3;

    /** One planet's settlements. Nothing here is shared with any other planet. */
    private static final class PlanetEntry {
        private final Set<Long> cities = new HashSet<>();
        private final Set<Long> camps = new HashSet<>();
        private final Map<Long, CityInfo> cityInfo = new HashMap<>();
        private final Map<Long, CampInfo> campInfo = new HashMap<>();
        private int territoryRevision;

        private boolean isEmpty() {
            return this.cities.isEmpty() && this.camps.isEmpty();
        }
    }

    private final Map<ResourceLocation, PlanetEntry> planets = new HashMap<>();

    public WorldWarMapData() {
    }

    /**
     * The flora-relevant attributes of one Imperial settlement, as last published by its Command
     * Core. {@code typeName}/{@code scaleName} are the enum names of {@link ImperialCityType} and
     * {@link SettlementScale} — stored as strings so an unknown value from an older save degrades to
     * the enum default instead of throwing.
     */
    public record CityInfo(String typeName, String scaleName, int cityLevel, int borderRadius) {
        public ImperialCityType type() {
            return ImperialCityType.fromName(typeName);
        }

        public SettlementScale scale() {
            return SettlementScale.fromName(scaleName);
        }
    }

    /** The flora-relevant attributes of one Ork camp, as last published by its camp block. */
    public record CampInfo(String clanName, int campLevel, int corruptionRadius) {
        public OrkClan clan() {
            return OrkClan.fromName(clanName);
        }
    }

    /**
     * The global map. Stored on the overworld on purpose — see the class note: the War Table must be
     * able to read a planet nobody is standing on.
     */
    public static WorldWarMapData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldWarMapData::load, WorldWarMapData::new, NAME);
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public static WorldWarMapData load(CompoundTag tag) {
        WorldWarMapData data = new WorldWarMapData();

        int version = tag.getInt("Version");

        if (version < 3) {
            data.loadLegacyFlat(tag);
            return data;
        }

        ListTag planetList = tag.getList("Planets", Tag.TAG_COMPOUND);

        for (int i = 0; i < planetList.size(); i++) {
            CompoundTag planetTag = planetList.getCompound(i);

            ResourceLocation id = ResourceLocation.tryParse(planetTag.getString("Dim"));
            if (id == null) {
                continue;
            }

            PlanetEntry entry = data.planets.computeIfAbsent(id, key -> new PlanetEntry());
            readPlanetEntry(planetTag, entry);
        }

        return data;
    }

    /**
     * Reads a format-2 (pre-campaign) tag, whose sets carry no dimension at all, into the default
     * planet's bucket. See the class note on why that is the honest reading of such a save.
     */
    private void loadLegacyFlat(CompoundTag tag) {
        PlanetEntry entry = this.planets.computeIfAbsent(
                FCPlanets.DEFAULT.location(), key -> new PlanetEntry());

        readPlanetEntry(tag, entry);
    }

    private static void readPlanetEntry(CompoundTag tag, PlanetEntry entry) {
        for (long packed : tag.getLongArray("Cities")) {
            entry.cities.add(packed);
        }

        for (long packed : tag.getLongArray("Camps")) {
            entry.camps.add(packed);
        }

        entry.territoryRevision = tag.getInt("TerritoryRevision");

        ListTag cityInfoList = tag.getList("CityInfo", Tag.TAG_COMPOUND);

        for (int i = 0; i < cityInfoList.size(); i++) {
            CompoundTag saved = cityInfoList.getCompound(i);
            long packed = saved.getLong("Pos");

            // Only keep attributes for a settlement that still exists on the map.
            if (!entry.cities.contains(packed)) {
                continue;
            }

            entry.cityInfo.put(packed, new CityInfo(
                    saved.getString("Type"),
                    saved.getString("Scale"),
                    saved.getInt("Level"),
                    saved.getInt("Border")
            ));
        }

        ListTag campInfoList = tag.getList("CampInfo", Tag.TAG_COMPOUND);

        for (int i = 0; i < campInfoList.size(); i++) {
            CompoundTag saved = campInfoList.getCompound(i);
            long packed = saved.getLong("Pos");

            if (!entry.camps.contains(packed)) {
                continue;
            }

            entry.campInfo.put(packed, new CampInfo(
                    saved.getString("Clan"),
                    saved.getInt("Level"),
                    saved.getInt("Corruption")
            ));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", FORMAT_VERSION);

        ListTag planetList = new ListTag();

        for (Map.Entry<ResourceLocation, PlanetEntry> planet : this.planets.entrySet()) {
            PlanetEntry entry = planet.getValue();

            CompoundTag planetTag = new CompoundTag();
            planetTag.putString("Dim", planet.getKey().toString());
            planetTag.putLongArray("Cities", entry.cities.stream().mapToLong(Long::longValue).toArray());
            planetTag.putLongArray("Camps", entry.camps.stream().mapToLong(Long::longValue).toArray());
            planetTag.putInt("TerritoryRevision", entry.territoryRevision);

            ListTag cityInfoList = new ListTag();

            for (Map.Entry<Long, CityInfo> info : entry.cityInfo.entrySet()) {
                CompoundTag saved = new CompoundTag();
                saved.putLong("Pos", info.getKey());
                saved.putString("Type", info.getValue().typeName());
                saved.putString("Scale", info.getValue().scaleName());
                saved.putInt("Level", info.getValue().cityLevel());
                saved.putInt("Border", info.getValue().borderRadius());
                cityInfoList.add(saved);
            }

            planetTag.put("CityInfo", cityInfoList);

            ListTag campInfoList = new ListTag();

            for (Map.Entry<Long, CampInfo> info : entry.campInfo.entrySet()) {
                CompoundTag saved = new CompoundTag();
                saved.putLong("Pos", info.getKey());
                saved.putString("Clan", info.getValue().clanName());
                saved.putInt("Level", info.getValue().campLevel());
                saved.putInt("Corruption", info.getValue().corruptionRadius());
                campInfoList.add(saved);
            }

            planetTag.put("CampInfo", campInfoList);

            planetList.add(planetTag);
        }

        tag.put("Planets", planetList);

        return tag;
    }

    // ====================================================================================
    // Writing
    // ====================================================================================

    private PlanetEntry entry(ResourceKey<Level> dimension) {
        return this.planets.computeIfAbsent(dimension.location(), key -> new PlanetEntry());
    }

    @Nullable
    private PlanetEntry existing(ResourceKey<Level> dimension) {
        return this.planets.get(dimension.location());
    }

    public void recordCity(ResourceKey<Level> dimension, BlockPos pos) {
        PlanetEntry entry = entry(dimension);

        if (entry.cities.add(pos.asLong())) {
            entry.territoryRevision++;
            setDirty();
        }
    }

    public void recordCity(ServerLevel level, BlockPos pos) {
        recordCity(level.dimension(), pos);
    }

    public void recordCamp(ResourceKey<Level> dimension, BlockPos pos) {
        PlanetEntry entry = entry(dimension);

        if (entry.camps.add(pos.asLong())) {
            entry.territoryRevision++;
            setDirty();
        }
    }

    public void recordCamp(ServerLevel level, BlockPos pos) {
        recordCamp(level.dimension(), pos);
    }

    public void removeCity(ResourceKey<Level> dimension, BlockPos pos) {
        PlanetEntry entry = existing(dimension);

        if (entry != null && entry.cities.remove(pos.asLong())) {
            entry.cityInfo.remove(pos.asLong());
            entry.territoryRevision++;
            setDirty();
        }
    }

    public void removeCity(ServerLevel level, BlockPos pos) {
        removeCity(level.dimension(), pos);
    }

    public void removeCamp(ResourceKey<Level> dimension, BlockPos pos) {
        PlanetEntry entry = existing(dimension);

        if (entry != null && entry.camps.remove(pos.asLong())) {
            entry.campInfo.remove(pos.asLong());
            entry.territoryRevision++;
            setDirty();
        }
    }

    public void removeCamp(ServerLevel level, BlockPos pos) {
        removeCamp(level.dimension(), pos);
    }

    /**
     * Publishes the territorial attributes of a city. Called from the Core's tick right after
     * {@link #recordCity}; a write that changes nothing is free and does <b>not</b> bump the
     * revision, so a quiet city never triggers redecoration.
     */
    public void recordCityInfo(ResourceKey<Level> dimension, BlockPos pos, ImperialCityType type,
                               SettlementScale scale, int cityLevel, int borderRadius) {
        CityInfo info = new CityInfo(
                type == null ? ImperialCityType.CIVILISED.name() : type.name(),
                scale == null ? SettlementScale.TOWN.name() : scale.name(),
                cityLevel,
                borderRadius
        );

        PlanetEntry entry = entry(dimension);

        if (info.equals(entry.cityInfo.get(pos.asLong()))) {
            return;
        }

        entry.cityInfo.put(pos.asLong(), info);
        entry.territoryRevision++;
        setDirty();
    }

    public void recordCityInfo(ServerLevel level, BlockPos pos, ImperialCityType type,
                               SettlementScale scale, int cityLevel, int borderRadius) {
        recordCityInfo(level.dimension(), pos, type, scale, cityLevel, borderRadius);
    }

    /** Publishes the territorial attributes of an Ork camp. See {@link #recordCityInfo}. */
    public void recordCampInfo(ResourceKey<Level> dimension, BlockPos pos, OrkClan clan,
                               int campLevel, int corruptionRadius) {
        CampInfo info = new CampInfo(
                clan == null ? OrkClan.GOFFS.name() : clan.name(),
                campLevel,
                corruptionRadius
        );

        PlanetEntry entry = entry(dimension);

        if (info.equals(entry.campInfo.get(pos.asLong()))) {
            return;
        }

        entry.campInfo.put(pos.asLong(), info);
        entry.territoryRevision++;
        setDirty();
    }

    public void recordCampInfo(ServerLevel level, BlockPos pos, OrkClan clan,
                               int campLevel, int corruptionRadius) {
        recordCampInfo(level.dimension(), pos, clan, campLevel, corruptionRadius);
    }

    // ====================================================================================
    // Reading
    // ====================================================================================

    public Set<Long> getCities(ResourceKey<Level> dimension) {
        PlanetEntry entry = existing(dimension);
        return entry == null ? Collections.emptySet() : Collections.unmodifiableSet(entry.cities);
    }

    public Set<Long> getCities(ServerLevel level) {
        return getCities(level.dimension());
    }

    public Set<Long> getCamps(ResourceKey<Level> dimension) {
        PlanetEntry entry = existing(dimension);
        return entry == null ? Collections.emptySet() : Collections.unmodifiableSet(entry.camps);
    }

    public Set<Long> getCamps(ServerLevel level) {
        return getCamps(level.dimension());
    }

    @Nullable
    public CityInfo getCityInfo(ResourceKey<Level> dimension, long packedPos) {
        PlanetEntry entry = existing(dimension);
        return entry == null ? null : entry.cityInfo.get(packedPos);
    }

    @Nullable
    public CampInfo getCampInfo(ResourceKey<Level> dimension, long packedPos) {
        PlanetEntry entry = existing(dimension);
        return entry == null ? null : entry.campInfo.get(packedPos);
    }

    /**
     * Monotonic counter over changes to <b>one planet's</b> territorial picture: a settlement
     * founded, razed or captured there, or any of its published attributes changing. Never resets
     * within a save.
     */
    public int getTerritoryRevision(ResourceKey<Level> dimension) {
        PlanetEntry entry = existing(dimension);
        return entry == null ? 0 : entry.territoryRevision;
    }

    public int getTerritoryRevision(ServerLevel level) {
        return getTerritoryRevision(level.dimension());
    }

    /** Every dimension this map holds settlements for. */
    public Set<ResourceLocation> knownDimensions() {
        return Collections.unmodifiableSet(this.planets.keySet());
    }

    public int countCities(ResourceKey<Level> dimension) {
        PlanetEntry entry = existing(dimension);
        return entry == null ? 0 : entry.cities.size();
    }

    public int countCamps(ResourceKey<Level> dimension) {
        PlanetEntry entry = existing(dimension);
        return entry == null ? 0 : entry.camps.size();
    }

    /**
     * Every Imperial city on every world, as located values.
     *
     * <p>For the War Table and the campaign layer, which are the only callers that legitimately want
     * to cross a planet boundary. Anything asking "what is near me" wants
     * {@link #getCities(ResourceKey)} instead.
     */
    public List<StrategicLocation> allCities() {
        return collect(true);
    }

    /** Every Ork camp on every world. See {@link #allCities()}. */
    public List<StrategicLocation> allCamps() {
        return collect(false);
    }

    private List<StrategicLocation> collect(boolean cities) {
        List<StrategicLocation> found = new ArrayList<>();

        for (Map.Entry<ResourceLocation, PlanetEntry> planet : this.planets.entrySet()) {
            ResourceKey<Level> dimension = StrategicLocation.dimensionKey(planet.getKey());

            for (long packed : cities ? planet.getValue().cities : planet.getValue().camps) {
                found.add(StrategicLocation.of(dimension, packed));
            }
        }

        return found;
    }

    // ====================================================================================
    // Upkeep
    // ====================================================================================

    /**
     * Drops entries whose block is provably gone.
     *
     * <p>The map is written by settlements registering themselves and cleared by their block being
     * broken, which leaves one hole: an entry that was never valid on this planet in the first place
     * — most of all a format-2 save's entries, all of which were read into the default planet's
     * bucket whether they belonged there or not. Those would sit on the map forever, pulling raid
     * targeting and flora territory toward ground with nothing on it.
     *
     * <p>Only <b>loaded</b> chunks are examined, so this never forces terrain to load to answer a
     * bookkeeping question. An entry in an unloaded chunk is left exactly where it is: absent is not
     * the same as gone, and deleting a real city because nobody was standing near it would be a far
     * worse bug than the one this fixes.
     *
     * @return how many entries were dropped
     */
    public int pruneOrphans(ServerLevel level) {
        PlanetEntry entry = existing(level.dimension());

        if (entry == null) {
            return 0;
        }

        int removed = 0;

        removed += pruneSet(level, entry.cities, entry.cityInfo, true);
        removed += pruneSet(level, entry.camps, entry.campInfo, false);

        if (removed > 0) {
            entry.territoryRevision++;
            setDirty();
        }

        return removed;
    }

    private static int pruneSet(ServerLevel level, Set<Long> positions,
                                Map<Long, ?> info, boolean cities) {
        List<Long> doomed = new ArrayList<>();

        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);

            if (!level.isLoaded(pos)) {
                continue;
            }

            boolean present = cities
                    ? level.getBlockEntity(pos) instanceof ImperialCommandCoreBlockEntity
                    : level.getBlockEntity(pos) instanceof OrkCampBlockEntity;

            if (!present) {
                doomed.add(packed);
            }
        }

        for (long packed : doomed) {
            positions.remove(packed);
            info.remove(packed);
        }

        return doomed.size();
    }
}
