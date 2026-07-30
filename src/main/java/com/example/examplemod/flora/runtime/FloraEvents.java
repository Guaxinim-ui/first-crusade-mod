package com.example.examplemod.flora.runtime;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.ImperialCitizenEntity;
import com.example.examplemod.flora.FloraConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the vegetation system meets the running server.
 *
 * <p>Four hooks, and each one does as little as it possibly can:
 *
 * <ul>
 *   <li><b>Chunk load</b> — pushes a coordinate into the queue's intake. Nothing else. No block is
 *       read, no palette resolved, no SavedData touched. Chunk loading is not always on the server
 *       thread and it happens in bursts, so this is the one place where doing real work would be
 *       most expensive and least visible.</li>
 *   <li><b>Server tick</b> — the only place vegetation is ever placed, under a budget.</li>
 *   <li><b>Level unload / server stopped</b> — drops the in-memory queue. It is never persisted;
 *       unfinished chunks are flagged in the SavedData and picked up when they next load.</li>
 *   <li><b>Death</b> — stamps the ground where the war was actually fought.</li>
 * </ul>
 *
 * <p>Everything here is server-side. There is no client counterpart, and none of these handlers is
 * reachable from a client level: each one checks for {@link ServerLevel} before doing anything.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FloraEvents {
    private FloraEvents() {
    }

    /** How often the battlefield marks are swept for scars nobody will ever see again. */
    private static final int BATTLEFIELD_PRUNE_INTERVAL_TICKS = 6000;

    /** Scars older than this are dropped: the resolver stopped reading them long ago. */
    private static final long BATTLEFIELD_MAX_AGE_TICKS = 24000L * 20L;

    /**
     * A chunk is now loaded and might want vegetation.
     *
     * <p>Deliberately the cheapest handler in the mod. Whether the chunk has already been
     * decorated, whether the territory moved under it, and which palette it resolves to are all
     * decided later, on the server thread, in {@link FloraChunkQueue#tick}.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Only fully loaded, playable chunks. A ProtoChunk is still being generated and has no
        // business being decorated.
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        if (!FloraRegionResolver.isDecoratedDimension(serverLevel)) {
            return;
        }

        if (!FloraConfig.CHUNK_DECORATION_ENABLED.get()) {
            return;
        }

        FloraChunkQueue.get(serverLevel).offer(chunk.getPos());
    }

    /** The one place blocks are actually placed, and always under the tick's budget. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!FloraRegionResolver.isDecoratedDimension(level)) {
                continue;
            }

            FloraChunkQueue.get(level).tick(level);

            if (level.getGameTime() % BATTLEFIELD_PRUNE_INTERVAL_TICKS == 0L) {
                FloraChunkSavedData.get(level)
                        .pruneBattlefields(level.getGameTime() - BATTLEFIELD_MAX_AGE_TICKS);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            FloraChunkQueue.forget(serverLevel.dimension());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FloraChunkQueue.forgetAll();
    }

    /**
     * The war leaves marks on the ground it is fought over.
     *
     * <p>Only combatants count. Citizens are excluded — a farmer dying to a fall does not turn a
     * field into a battlefield — and so is anything outside the two warring factions, so vanilla
     * mobs and player accidents leave the landscape alone.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();

        if (!(dead.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (dead instanceof ImperialCitizenEntity) {
            return;
        }

        if (!isCombatant(dead)) {
            Entity killer = event.getSource().getEntity();

            if (killer == null || !isCombatant(killer)) {
                return;
            }
        }

        FloraTransitionManager.markBattlefield(serverLevel, dead.blockPosition());
    }

    private static boolean isCombatant(Entity entity) {
        FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(entity);

        return faction == FirstCrusadeFaction.IMPERIUM || faction == FirstCrusadeFaction.ORKS;
    }
}
