package com.example.examplemod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide register of every settlement in the war: the positions of all Imperial Command Cores
 * (cities) and all Ork Camps. It lets the Core's War Table draw the whole planet's strategic picture
 * — every city and camp, wherever it is — instead of only what is within a small radius. Stored on
 * the overworld's data storage (same pattern as {@link WaaaghOverlordData}).
 *
 * Entries are self-registered: a city/camp adds itself while it ticks (so existing and new ones are
 * covered, even after their chunk reloads), and removes itself when its block is destroyed. Positions
 * for settlements in unloaded chunks stay on the map until they are actually torn down.
 *
 * <h2>Territorial attributes (added for the flora decorator)</h2>
 *
 * Alongside each position the map now also keeps the handful of <i>attributes</i> that describe what
 * kind of territory that settlement projects: an Imperial city's type and radii, an Ork camp's level
 * and corruption reach. These are pushed by the very same tick that already calls
 * {@link #recordCity(BlockPos)} / {@link #recordCamp(BlockPos)}, so the block entity stays the single
 * source of truth and nothing keeps a second copy of the faction.
 *
 * <p>Why here and not in a new registry: {@link com.example.examplemod.flora.runtime.FloraRegionResolver}
 * has to answer "who owns this chunk?" for chunks whose settlement is in an <b>unloaded</b> chunk. A
 * block entity cannot be read then; this SavedData can. Keeping the attributes next to the positions
 * they belong to means one lookup, one persisted structure, and no chance of the two drifting apart.
 *
 * <p>{@link #getTerritoryRevision()} counts every change to the territorial picture. The flora
 * decorator stores the revision it decorated a chunk under; when the counter moves past it, that
 * chunk is stale and gets re-resolved. Writes that change nothing do not bump it, so a city ticking
 * with unchanged attributes never causes redecoration.
 */
public class WorldWarMapData extends SavedData {
    private static final String NAME = "firstcrusade_warmap";

    /** Bumped when the NBT layout changes; older saves are read on a best-effort basis. */
    private static final int FORMAT_VERSION = 2;

    private final Set<Long> cities = new HashSet<>();
    private final Set<Long> camps = new HashSet<>();

    private final Map<Long, CityInfo> cityInfo = new HashMap<>();
    private final Map<Long, CampInfo> campInfo = new HashMap<>();

    private int territoryRevision;

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

    public static WorldWarMapData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldWarMapData::load, WorldWarMapData::new, NAME);
    }

    public static WorldWarMapData load(CompoundTag tag) {
        WorldWarMapData data = new WorldWarMapData();

        for (long packed : tag.getLongArray("Cities")) {
            data.cities.add(packed);
        }

        for (long packed : tag.getLongArray("Camps")) {
            data.camps.add(packed);
        }

        data.territoryRevision = tag.getInt("TerritoryRevision");

        ListTag cityInfoList = tag.getList("CityInfo", Tag.TAG_COMPOUND);

        for (int i = 0; i < cityInfoList.size(); i++) {
            CompoundTag entry = cityInfoList.getCompound(i);
            long packed = entry.getLong("Pos");

            // Only keep attributes for a settlement that still exists on the map.
            if (!data.cities.contains(packed)) {
                continue;
            }

            data.cityInfo.put(packed, new CityInfo(
                    entry.getString("Type"),
                    entry.getString("Scale"),
                    entry.getInt("Level"),
                    entry.getInt("Border")
            ));
        }

        ListTag campInfoList = tag.getList("CampInfo", Tag.TAG_COMPOUND);

        for (int i = 0; i < campInfoList.size(); i++) {
            CompoundTag entry = campInfoList.getCompound(i);
            long packed = entry.getLong("Pos");

            if (!data.camps.contains(packed)) {
                continue;
            }

            data.campInfo.put(packed, new CampInfo(
                    entry.getString("Clan"),
                    entry.getInt("Level"),
                    entry.getInt("Corruption")
            ));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", FORMAT_VERSION);
        tag.putLongArray("Cities", this.cities.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("Camps", this.camps.stream().mapToLong(Long::longValue).toArray());
        tag.putInt("TerritoryRevision", this.territoryRevision);

        ListTag cityInfoList = new ListTag();

        for (Map.Entry<Long, CityInfo> entry : this.cityInfo.entrySet()) {
            CompoundTag saved = new CompoundTag();
            saved.putLong("Pos", entry.getKey());
            saved.putString("Type", entry.getValue().typeName());
            saved.putString("Scale", entry.getValue().scaleName());
            saved.putInt("Level", entry.getValue().cityLevel());
            saved.putInt("Border", entry.getValue().borderRadius());
            cityInfoList.add(saved);
        }

        tag.put("CityInfo", cityInfoList);

        ListTag campInfoList = new ListTag();

        for (Map.Entry<Long, CampInfo> entry : this.campInfo.entrySet()) {
            CompoundTag saved = new CompoundTag();
            saved.putLong("Pos", entry.getKey());
            saved.putString("Clan", entry.getValue().clanName());
            saved.putInt("Level", entry.getValue().campLevel());
            saved.putInt("Corruption", entry.getValue().corruptionRadius());
            campInfoList.add(saved);
        }

        tag.put("CampInfo", campInfoList);

        return tag;
    }

    public void recordCity(BlockPos pos) {
        if (this.cities.add(pos.asLong())) {
            this.territoryRevision++;
            setDirty();
        }
    }

    public void recordCamp(BlockPos pos) {
        if (this.camps.add(pos.asLong())) {
            this.territoryRevision++;
            setDirty();
        }
    }

    public void removeCity(BlockPos pos) {
        if (this.cities.remove(pos.asLong())) {
            this.cityInfo.remove(pos.asLong());
            this.territoryRevision++;
            setDirty();
        }
    }

    public void removeCamp(BlockPos pos) {
        if (this.camps.remove(pos.asLong())) {
            this.campInfo.remove(pos.asLong());
            this.territoryRevision++;
            setDirty();
        }
    }

    /**
     * Publishes the territorial attributes of a city. Called from the Core's tick right after
     * {@link #recordCity(BlockPos)}; a write that changes nothing is free and does <b>not</b> bump
     * the revision, so a quiet city never triggers redecoration.
     */
    public void recordCityInfo(BlockPos pos, ImperialCityType type, SettlementScale scale,
                               int cityLevel, int borderRadius) {
        CityInfo info = new CityInfo(
                type == null ? ImperialCityType.CIVILISED.name() : type.name(),
                scale == null ? SettlementScale.TOWN.name() : scale.name(),
                cityLevel,
                borderRadius
        );

        if (info.equals(this.cityInfo.get(pos.asLong()))) {
            return;
        }

        this.cityInfo.put(pos.asLong(), info);
        this.territoryRevision++;
        setDirty();
    }

    /** Publishes the territorial attributes of an Ork camp. See {@link #recordCityInfo}. */
    public void recordCampInfo(BlockPos pos, OrkClan clan, int campLevel, int corruptionRadius) {
        CampInfo info = new CampInfo(
                clan == null ? OrkClan.GOFFS.name() : clan.name(),
                campLevel,
                corruptionRadius
        );

        if (info.equals(this.campInfo.get(pos.asLong()))) {
            return;
        }

        this.campInfo.put(pos.asLong(), info);
        this.territoryRevision++;
        setDirty();
    }

    @Nullable
    public CityInfo getCityInfo(long packedPos) {
        return this.cityInfo.get(packedPos);
    }

    @Nullable
    public CampInfo getCampInfo(long packedPos) {
        return this.campInfo.get(packedPos);
    }

    /**
     * Monotonic counter over every change to the territorial picture: a settlement founded, razed or
     * captured, or any of its published attributes changing. Never resets within a save.
     */
    public int getTerritoryRevision() {
        return this.territoryRevision;
    }


    public Set<Long> getCities() {
        return this.cities;
    }

    public Set<Long> getCamps() {
        return this.camps;
    }

}
