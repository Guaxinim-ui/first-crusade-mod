package com.example.examplemod.planet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who has unlocked what, and where each traveller came from.
 *
 * <h2>Stored on the overworld, keyed by player</h2>
 *
 * Same shape as {@link com.example.examplemod.PlayerFactionData}: one {@link SavedData} on the
 * overworld's storage rather than per-player capabilities. That choice is what makes an unlock
 * survive death, relog, dimension change and server restart without a single line of attachment
 * plumbing — the data never travels with the player, so it cannot be lost with them.
 *
 * <h2>Triggers are stored, not just their results</h2>
 *
 * Unlocking records two things: the planet, and the {@link PlanetUnlockRequirement#trigger} that
 * granted it. Keeping the raw triggers matters for a mod that will keep adding planets: a player who
 * killed a Warboss last month has that fact on record, so a destination added tomorrow with the same
 * requirement opens for them immediately instead of asking them to go kill another one.
 */
public class PlanetUnlockData extends SavedData {
    private static final String NAME = "firstcrusade_planet_unlocks";

    private final Map<UUID, Set<ResourceLocation>> unlocked = new HashMap<>();
    private final Map<UUID, Set<String>> triggers = new HashMap<>();
    private final Map<UUID, ReturnPoint> returnPoints = new HashMap<>();

    /**
     * Where a traveller was standing before they launched.
     *
     * @param dimension the level they left
     * @param position  a spot known to have been safe, because they were standing on it
     * @param planetId  the planet entry that level belonged to, or null for the ordinary overworld
     * @param gameTime  when the trip was taken, for the terminal's "last departure" line
     */
    public record ReturnPoint(ResourceKey<Level> dimension, BlockPos position,
                              @Nullable ResourceLocation planetId, long gameTime) {
    }

    public PlanetUnlockData() {
    }

    public static PlanetUnlockData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage()
                .computeIfAbsent(PlanetUnlockData::load, PlanetUnlockData::new, NAME);
    }

    // ====================================================================================
    // Unlocks
    // ====================================================================================

    /**
     * Everything this player may travel to, defaults included.
     *
     * <p>The defaults are folded in on read rather than written into every player's record on first
     * login: a planet that later becomes free-to-visit then applies to everyone at once, and no
     * migration is needed for saves made before it existed.
     */
    public Set<ResourceLocation> unlockedFor(UUID playerId) {
        Set<ResourceLocation> result = new LinkedHashSet<>(PlanetRegistry.defaultUnlocked());
        result.addAll(this.unlocked.getOrDefault(playerId, Set.of()));
        return result;
    }

    public boolean isUnlocked(UUID playerId, PlanetDefinition planet) {
        return planet.isUnlockedByDefault()
                || this.unlocked.getOrDefault(playerId, Set.of()).contains(planet.id());
    }

    public boolean unlock(UUID playerId, ResourceLocation planetId) {
        boolean added = this.unlocked.computeIfAbsent(playerId, key -> new HashSet<>()).add(planetId);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean lock(UUID playerId, ResourceLocation planetId) {
        Set<ResourceLocation> set = this.unlocked.get(playerId);
        boolean removed = set != null && set.remove(planetId);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // ====================================================================================
    // Triggers
    // ====================================================================================

    public boolean hasTrigger(UUID playerId, String trigger) {
        return this.triggers.getOrDefault(playerId, Set.of()).contains(trigger);
    }

    /**
     * Records that something happened to this player, and opens every destination whose
     * requirements are now all satisfied.
     *
     * @return the planets that opened as a result, so the caller can tell the player
     */
    public Set<PlanetDefinition> grantTrigger(UUID playerId, String trigger) {
        boolean isNew = this.triggers.computeIfAbsent(playerId, key -> new HashSet<>()).add(trigger);
        Set<PlanetDefinition> opened = new LinkedHashSet<>();

        if (isNew) {
            setDirty();
        }

        for (PlanetDefinition planet : PlanetRegistry.all()) {
            if (isUnlocked(playerId, planet) || planet.unlockRequirements().isEmpty()) {
                continue;
            }

            boolean all = planet.unlockRequirements().stream()
                    .allMatch(requirement -> hasTrigger(playerId, requirement.trigger()));

            if (all && unlock(playerId, planet.id())) {
                opened.add(planet);
            }
        }

        return opened;
    }

    /** The requirements this player is still missing for a planet — what the tooltip lists. */
    public java.util.List<PlanetUnlockRequirement> missingFor(UUID playerId, PlanetDefinition planet) {
        return planet.unlockRequirements().stream()
                .filter(requirement -> !hasTrigger(playerId, requirement.trigger()))
                .toList();
    }

    // ====================================================================================
    // Return points
    // ====================================================================================

    public void setReturnPoint(UUID playerId, ReturnPoint point) {
        this.returnPoints.put(playerId, point);
        setDirty();
    }

    @Nullable
    public ReturnPoint returnPoint(UUID playerId) {
        return this.returnPoints.get(playerId);
    }

    public void clearReturnPoint(UUID playerId) {
        if (this.returnPoints.remove(playerId) != null) {
            setDirty();
        }
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public static PlanetUnlockData load(CompoundTag tag) {
        PlanetUnlockData data = new PlanetUnlockData();

        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);

            UUID id;
            try {
                id = UUID.fromString(entry.getString("Id"));
            } catch (IllegalArgumentException malformed) {
                // One bad record must not cost the whole world its unlock table.
                continue;
            }

            Set<ResourceLocation> planets = new HashSet<>();
            ListTag list = entry.getList("Unlocked", Tag.TAG_STRING);
            for (int p = 0; p < list.size(); p++) {
                ResourceLocation planetId = ResourceLocation.tryParse(list.getString(p));
                if (planetId != null) {
                    planets.add(planetId);
                }
            }
            if (!planets.isEmpty()) {
                data.unlocked.put(id, planets);
            }

            Set<String> playerTriggers = new HashSet<>();
            ListTag triggerList = entry.getList("Triggers", Tag.TAG_STRING);
            for (int t = 0; t < triggerList.size(); t++) {
                playerTriggers.add(triggerList.getString(t));
            }
            if (!playerTriggers.isEmpty()) {
                data.triggers.put(id, playerTriggers);
            }

            if (entry.contains("ReturnDimension")) {
                ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString("ReturnDimension"));
                if (dimensionId != null) {
                    ResourceLocation planetId = entry.contains("ReturnPlanet")
                            ? ResourceLocation.tryParse(entry.getString("ReturnPlanet"))
                            : null;

                    data.returnPoints.put(id, new ReturnPoint(
                            ResourceKey.create(Registries.DIMENSION, dimensionId),
                            BlockPos.of(entry.getLong("ReturnPos")),
                            planetId,
                            entry.getLong("ReturnTime")));
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        Set<UUID> everyone = new LinkedHashSet<>();
        everyone.addAll(this.unlocked.keySet());
        everyone.addAll(this.triggers.keySet());
        everyone.addAll(this.returnPoints.keySet());

        ListTag players = new ListTag();
        for (UUID id : everyone) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id.toString());

            ListTag list = new ListTag();
            for (ResourceLocation planetId : this.unlocked.getOrDefault(id, Set.of())) {
                list.add(net.minecraft.nbt.StringTag.valueOf(planetId.toString()));
            }
            entry.put("Unlocked", list);

            ListTag triggerList = new ListTag();
            for (String trigger : this.triggers.getOrDefault(id, Set.of())) {
                triggerList.add(net.minecraft.nbt.StringTag.valueOf(trigger));
            }
            entry.put("Triggers", triggerList);

            ReturnPoint point = this.returnPoints.get(id);
            if (point != null) {
                entry.putString("ReturnDimension", point.dimension().location().toString());
                entry.putLong("ReturnPos", point.position().asLong());
                entry.putLong("ReturnTime", point.gameTime());
                if (point.planetId() != null) {
                    entry.putString("ReturnPlanet", point.planetId().toString());
                }
            }

            players.add(entry);
        }

        tag.put("Players", players);
        return tag;
    }
}
