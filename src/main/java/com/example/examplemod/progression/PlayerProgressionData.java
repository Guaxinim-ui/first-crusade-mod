package com.example.examplemod.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every player's progression, saved on the overworld and keyed by UUID.
 *
 * <h2>The same choice the rest of the mod made</h2>
 *
 * {@link com.example.examplemod.PlayerFactionData} and
 * {@link com.example.examplemod.planet.PlanetUnlockData} both keep per-player state as one
 * {@link SavedData} on the overworld rather than as a capability attached to the player. That is why
 * a faction survives death and a planet unlock survives a relog with no attachment plumbing, and it
 * is the reason this follows suit: the data never travels with the player, so it cannot be lost with
 * them — not on death, not on respawn, not on a jump to Armageddon.
 *
 * <p>Note the overworld: {@code level.getServer().overworld()} is the storage, whichever planet the
 * player is standing on when the lookup happens.
 */
public class PlayerProgressionData extends SavedData {
    private static final String NAME = "firstcrusade_player_progression";

    private final Map<UUID, PlayerProgressionProfile> profiles = new HashMap<>();

    public PlayerProgressionData() {
    }

    public static PlayerProgressionData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage()
                .computeIfAbsent(PlayerProgressionData::load, PlayerProgressionData::new, NAME);
    }

    /**
     * This player's profile, created on first ask.
     *
     * <p>A new profile is born with the root node already at rank 1: the recruit node is free and
     * automatic, so there is no moment where a player exists on the tree without standing on it.
     */
    public PlayerProgressionProfile profile(UUID playerId) {
        return this.profiles.computeIfAbsent(playerId, id -> {
            PlayerProgressionProfile fresh = new PlayerProgressionProfile();
            fresh.setRank(PlayerProgressionTree.ROOT_ID, 1);
            setDirty();
            return fresh;
        });
    }

    public PlayerProgressionProfile profile(Player player) {
        return profile(player.getUUID());
    }

    public boolean has(UUID playerId) {
        return this.profiles.containsKey(playerId);
    }

    /** Call after any mutation; SavedData only writes what it has been told changed. */
    public void markChanged() {
        setDirty();
    }

    // ==================================================================== persistence

    public static PlayerProgressionData load(CompoundTag tag) {
        PlayerProgressionData data = new PlayerProgressionData();
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            UUID id;
            try {
                id = UUID.fromString(entry.getString("Id"));
            } catch (IllegalArgumentException malformed) {
                // One unreadable record must not cost the server every other player's progression.
                continue;
            }

            data.profiles.put(id, PlayerProgressionProfile.load(entry.getCompound("Profile")));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();

        for (Map.Entry<UUID, PlayerProgressionProfile> entry : this.profiles.entrySet()) {
            CompoundTag record = new CompoundTag();
            record.putString("Id", entry.getKey().toString());
            record.put("Profile", entry.getValue().save());
            list.add(record);
        }

        tag.put("Players", list);
        return tag;
    }
}
