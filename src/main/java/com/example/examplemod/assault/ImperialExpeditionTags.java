package com.example.examplemod.assault;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * The four persistent marks a soldier carries while it is away on a raid.
 *
 * <h2>Why marks on the entity and not just a list in the manager</h2>
 *
 * The manager's list is the truth, but the entity outlives any one server session and can be
 * unloaded, moved between chunks, or left behind by a crash halfway through a deployment. A soldier
 * that carries its own "I am on expedition, my home is here" can always be sent back — that is what
 * {@code /fcassault clear_orphans} reads, and it is the only thing that can recover a trooper whose
 * raid record no longer exists.
 *
 * <h2>UUIDs, never entity ids</h2>
 *
 * The numeric entity id is reassigned on reload. Everything here that names an entity or a raid uses
 * a {@link UUID}, so a restart in the middle of an assault finds the same soldiers.
 */
public final class ImperialExpeditionTags {
    private ImperialExpeditionTags() {
    }

    public static final String ON_EXPEDITION = "FirstCrusadeExpedition";
    public static final String RAID_ID = "FirstCrusadeExpeditionRaid";
    public static final String HOME_CORE = "FirstCrusadeExpeditionHomeCore";
    public static final String HOME_POS = "FirstCrusadeExpeditionHomePos";

    public static boolean isOnExpedition(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(ON_EXPEDITION);
    }

    public static void mark(Entity entity, UUID raidId, BlockPos homeCore, BlockPos homePos) {
        CompoundTag data = entity.getPersistentData();
        data.putBoolean(ON_EXPEDITION, true);
        data.putUUID(RAID_ID, raidId);
        data.putLong(HOME_CORE, homeCore.asLong());
        data.putLong(HOME_POS, homePos.asLong());
    }

    public static void clear(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        data.remove(ON_EXPEDITION);
        data.remove(RAID_ID);
        data.remove(HOME_CORE);
        data.remove(HOME_POS);
    }

    @Nullable
    public static UUID raidId(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(RAID_ID) ? data.getUUID(RAID_ID) : null;
    }

    @Nullable
    public static BlockPos homeCore(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(HOME_CORE) ? BlockPos.of(data.getLong(HOME_CORE)) : null;
    }

    @Nullable
    public static BlockPos homePos(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(HOME_POS) ? BlockPos.of(data.getLong(HOME_POS)) : null;
    }
}
