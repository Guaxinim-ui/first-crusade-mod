package com.example.examplemod.hive.city;

import com.example.examplemod.ExampleMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Drains {@link HiveGenerationQueue} at a bounded budget each server tick.
 *
 * <p>Registered on the Forge event bus. Only runs on the {@code hive_world} level. Per tick it
 * pastes up to {@link #DISTRICTS_PER_TICK} districts. Before pasting, it force-loads the target
 * chunks (via {@link ServerLevel#setChunkForced}) — otherwise {@code placeInWorld} into an unloaded
 * chunk fails silently (spec §4 trap). After a district is placed, its chunks are released again
 * unless a player is nearby.
 *
 * <p>The actual paste delegates to the existing district placement path. To avoid a hard compile
 * dependency on the exact signature of {@code HiveDistricts} (which lives in the main hive package),
 * placement is routed through {@link HiveCityPlacer}, a thin adapter the integrator points at the
 * real method (one line — see that class).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class HiveCityTicker {
    private HiveCityTicker() {}

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /** How many districts to paste per server tick. Conservative to avoid TPS spikes. */
    public static final int DISTRICTS_PER_TICK = 1;

    /** Progress messages are broadcast at each multiple of this percentage. */
    private static final int PROGRESS_STEP = 10;
    private static int lastReportedPercent = -1;

    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side.isClient()) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (!level.dimension().equals(HiveWorld.LEVEL)) return;

        // Batched full-city-test clear runs on the same level; drain it first so a clear started
        // right before a rebuild finishes before districts start pasting.
        HiveClearQueue clear = HiveClearQueue.get(level);
        if (clear.isActive()) {
            clear.process(level);
            if (!clear.isActive()) {
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("[Hive City] Área de teste limpa ("
                                + clear.cleared() + " blocos)."), false);
            }
            return; // não colar distritos no mesmo tick em que se está limpando
        }

        HiveGenerationQueue queue = HiveGenerationQueue.get(level);
        if (queue.isEmpty()) return;

        MinecraftServer server = level.getServer();

        int budget = DISTRICTS_PER_TICK;
        while (budget-- > 0 && !queue.isEmpty()) {
            HiveGenerationQueue.Task task = queue.peek();
            if (task == null) break;

            Set<ChunkPos> forced = forceLoadFootprint(level, task);
            try {
                boolean ok = HiveCityPlacer.place(level, task.districtId(),
                        task.origin(), task.rotation());
                if (!ok) {
                    LOGGER.warn("[hive city] placement failed for {} — skipping",
                            task.districtId());
                }
            } catch (Exception e) {
                LOGGER.error("[hive city] exception placing {}",
                        task.districtId(), e);
            } finally {
                queue.pop(); // advance regardless, so a bad district can't wedge the queue forever
                releaseChunks(level, forced);
            }
        }

        // Progress feedback.
        int pct = queue.percent();
        if (pct != lastReportedPercent && pct % PROGRESS_STEP == 0) {
            lastReportedPercent = pct;
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[Hive City] Building… " + pct + "% ("
                            + queue.placedSoFar() + "/" + queue.totalPlanned() + " districts)"),
                    false);
        }
        if (queue.isEmpty()) {
            lastReportedPercent = -1;
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[Hive City] Generation complete — "
                            + queue.totalPlanned() + " districts placed."),
                    false);
        }
    }

    /** Force-load every chunk that a 192×128 district footprint touches. */
    private static Set<ChunkPos> forceLoadFootprint(ServerLevel level,
                                                     HiveGenerationQueue.Task task) {
        Set<ChunkPos> forced = new HashSet<>();
        BlockPos o = task.origin();
        // Footprint edges depend on rotation, but 192×128 rotated still fits inside a 192×192 box,
        // so force-load the conservative 192×192 span — cheap insurance against silent failures.
        int minX = o.getX();
        int minZ = o.getZ();
        int maxX = minX + Math.max(HiveCityLayout.DISTRICT_W, HiveCityLayout.DISTRICT_D);
        int maxZ = minZ + Math.max(HiveCityLayout.DISTRICT_W, HiveCityLayout.DISTRICT_D);
        for (int cx = (minX >> 4); cx <= (maxX >> 4); cx++) {
            for (int cz = (minZ >> 4); cz <= (maxZ >> 4); cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                if (!level.getForcedChunks().contains(cp.toLong())) {
                    level.setChunkForced(cx, cz, true);
                    forced.add(cp);
                }
            }
        }
        return forced;
    }

    private static void releaseChunks(ServerLevel level, Set<ChunkPos> forced) {
        for (ChunkPos cp : forced) {
            level.setChunkForced(cp.x, cp.z, false);
        }
    }
}
