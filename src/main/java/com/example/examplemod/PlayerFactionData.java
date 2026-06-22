package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-player allegiance (Imperium / Orks), persisted for the whole world on the overworld's data
 * storage — same pattern as the global overlord state ({@link WaaaghOverlordData}). A player keeps
 * their choice across deaths and relogs; it is read on login to decide whether to show the faction
 * selection screen, and is the hook later gameplay can branch on.
 */
public class PlayerFactionData extends SavedData {
    private static final String NAME = "firstcrusade_player_factions";

    private final Map<UUID, PlayerFaction> factions = new HashMap<>();

    public PlayerFactionData() {
    }

    public static PlayerFactionData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(PlayerFactionData::load, PlayerFactionData::new, NAME);
    }

    public static PlayerFactionData load(CompoundTag tag) {
        PlayerFactionData data = new PlayerFactionData();

        for (String key : tag.getAllKeys()) {
            try {
                data.factions.put(UUID.fromString(key), PlayerFaction.fromName(tag.getString(key)));
            } catch (IllegalArgumentException ignored) {
                // Skip any malformed key rather than fail the whole world load.
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<UUID, PlayerFaction> entry : this.factions.entrySet()) {
            tag.putString(entry.getKey().toString(), entry.getValue().name());
        }

        return tag;
    }

    public PlayerFaction getFaction(UUID playerId) {
        return this.factions.getOrDefault(playerId, PlayerFaction.UNCHOSEN);
    }

    public PlayerFaction getFaction(Player player) {
        return getFaction(player.getUUID());
    }

    public boolean hasChosen(UUID playerId) {
        return getFaction(playerId).isChosen();
    }

    public void setFaction(UUID playerId, PlayerFaction faction) {
        this.factions.put(playerId, faction);
        setDirty();
    }
}
