package com.example.examplemod.flora.runtime;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.flora.FloraConfig;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The work queue: which chunks are waiting to be decorated, and how much work may happen this tick.
 *
 * <h2>Two stages, on purpose</h2>
 *
 * Chunk loading does not always happen on the server thread, so {@link #offer} does the absolute
 * minimum — it drops a coordinate into a concurrent intake and returns. Every decision that touches
 * world data (is this chunk stale? what palette does it resolve to? has it been decorated already?)
 * happens later, on the server thread, in {@link #tick}. A chunk load event therefore costs a queue
 * push and nothing else, which is what the phase brief means by not doing hundreds of block changes
 * inside {@code ChunkEvent.Load}.
 *
 * <h2>Budgets</h2>
 *
 * Two ceilings apply every tick: how many chunks may be decorated, and how many placement attempts
 * may be spent in total across them. The second is the one that matters when a player flies into
 * unexplored territory and fifty chunks load at once — the queue simply drains over the following
 * ticks instead of the server stuttering. A chunk that runs out of budget mid-pass is marked
 * incomplete and put back; because placement is deterministic, restarting it later reproduces the
 * same plants rather than adding new ones.
 *
 * <h2>No duplicated work</h2>
 *
 * A chunk already waiting is never queued twice. The queue itself is never persisted — on reload,
 * chunks flagged incomplete or dirty in {@link FloraChunkSavedData} are re-queued the next time they
 * load, which is both cheaper and more robust than serialising a work list.
 */
public final class FloraChunkQueue {

    /** One queue per dimension. Dropped when the level unloads. */
    private static final Map<ResourceKey<Level>, FloraChunkQueue> QUEUES = new HashMap<>();

    /** Intake entries examined per tick, so a mass chunk load cannot stall the tick by itself. */
    private static final int INTAKE_PER_TICK = 256;

    private final ConcurrentLinkedQueue<Long> intake = new ConcurrentLinkedQueue<>();

    private final ArrayDeque<Long> ready = new ArrayDeque<>();
    private final Set<Long> readySet = new HashSet<>();

    // ---- statistics, for /firstcrusade flora stats ----
    private long totalQueued;
    private long totalProcessed;
    private long totalSkipped;
    private long totalPlaced;
    private long totalRemoved;
    private long totalPlacementFailures;
    private long totalRequeued;

    private FloraChunkQueue() {
    }

    public static FloraChunkQueue get(ServerLevel level) {
        return QUEUES.computeIfAbsent(level.dimension(), key -> new FloraChunkQueue());
    }

    /** Drops a dimension's queue — called when the level unloads and when the server stops. */
    public static void forget(ResourceKey<Level> dimension) {
        QUEUES.remove(dimension);
    }

    public static void forgetAll() {
        QUEUES.clear();
    }

    /**
     * Registers interest in a chunk. Safe to call from any thread and deliberately does no work:
     * the chunk may turn out to be already decorated, unloaded again, or in a dimension this system
     * ignores, and all of that is decided on the server thread in {@link #tick}.
     */
    public void offer(ChunkPos pos) {
        this.intake.add(pos.toLong());
    }

    /**
     * Flags a chunk for retrogen and queues it: the runtime decorator will add the natural
     * vegetation that its worldgen never produced. Safe to call on a chunk that already has it —
     * placement is deterministic, so a second pass writes the same blocks into the same positions.
     */
    public boolean enqueueRetrogen(ServerLevel level, ChunkPos pos) {
        FloraChunkSavedData.get(level).markDirty(pos, FloraChunkSavedData.Transition.RETROGEN);
        return enqueueDirect(pos);
    }

    /** Queues a chunk for decoration immediately, bypassing the intake. Server thread only. */
    public boolean enqueueDirect(ChunkPos pos) {
        long key = pos.toLong();

        if (this.readySet.contains(key)) {
            return false;
        }

        if (this.ready.size() >= FloraConfig.QUEUE_CAPACITY.get()) {
            this.totalSkipped++;
            return false;
        }

        this.ready.add(key);
        this.readySet.add(key);
        this.totalQueued++;

        return true;
    }

    /**
     * One tick of work: sort the intake, then decorate up to the tick's allowance.
     */
    public void tick(ServerLevel level) {
        if (!FloraConfig.CHUNK_DECORATION_ENABLED.get()) {
            this.intake.clear();
            return;
        }

        drainIntake(level);
        processReady(level);
    }

    /**
     * Decides which newly loaded chunks actually need work. This is where
     * {@link FloraChunkSavedData} is consulted and the palette resolved — on the server thread,
     * once per chunk, not once per block.
     */
    private void drainIntake(ServerLevel level) {
        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);
        int currentRevision = WorldWarMapData.get(level).getTerritoryRevision();

        boolean redecorationEnabled = FloraConfig.DYNAMIC_REDECORATION_ENABLED.get();
        boolean neutralEnabled = FloraConfig.NEUTRAL_CHUNK_DECORATION_ENABLED.get();

        for (int i = 0; i < INTAKE_PER_TICK; i++) {
            Long key = this.intake.poll();

            if (key == null) {
                return;
            }

            ChunkPos pos = new ChunkPos(key);

            if (this.readySet.contains(key)) {
                continue;
            }

            FloraChunkSavedData.ChunkState state = floraData.getState(key);

            if (state == null) {
                // Never touched. Decorate it — unless it is neutral ground and the server has
                // asked for neutral ground to be left alone.
                if (!neutralEnabled && FloraRegionResolver.resolveChunkPalette(level, pos) == FloraPalette.NEUTRAL_DARK) {
                    this.totalSkipped++;
                    continue;
                }

                enqueueDirect(pos);
                continue;
            }

            // Unfinished work always resumes, whatever the redecoration setting says: a chunk left
            // half-planted is a bug, not a stylistic choice.
            if (state.has(FloraChunkSavedData.FLAG_INCOMPLETE)) {
                if (enqueueDirect(pos)) {
                    this.totalRequeued++;
                }
                continue;
            }

            if (!redecorationEnabled) {
                this.totalSkipped++;
                continue;
            }

            boolean stale = state.has(FloraChunkSavedData.FLAG_DIRTY)
                    || state.decoratorVersion() != FloraChunkDecorator.DECORATOR_VERSION
                    || state.revision() != currentRevision;

            if (!stale) {
                this.totalSkipped++;
                continue;
            }

            // The territory counter moved, but that does not mean it moved under *this* chunk.
            // Re-resolve before committing to the work; usually the palette is unchanged and the
            // chunk is simply restamped with the new revision for free.
            if (!state.has(FloraChunkSavedData.FLAG_DIRTY)
                    && state.decoratorVersion() == FloraChunkDecorator.DECORATOR_VERSION) {

                FloraPalette resolved = FloraRegionResolver.resolveChunkPalette(level, pos);

                if (resolved == state.palette()) {
                    floraData.recordDecorated(pos, resolved, FloraChunkDecorator.DECORATOR_VERSION, currentRevision);
                    this.totalSkipped++;
                    continue;
                }
            }

            if (enqueueDirect(pos)) {
                this.totalRequeued++;
            }
        }
    }

    private void processReady(ServerLevel level) {
        int chunkBudget = FloraConfig.CHUNKS_PROCESSED_PER_TICK.get();
        int attemptBudget = FloraConfig.PLACEMENT_ATTEMPTS_PER_TICK.get();

        if (chunkBudget <= 0 || attemptBudget <= 0) {
            return;
        }

        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);
        int currentRevision = WorldWarMapData.get(level).getTerritoryRevision();

        while (chunkBudget > 0 && attemptBudget > 0 && !this.ready.isEmpty()) {
            long key = takeNearestToPlayer(level);
            this.readySet.remove(key);

            ChunkPos pos = new ChunkPos(key);

            // Never force a chunk to load. getChunkNow returns null for anything not already fully
            // loaded, which is exactly the semantics wanted here: if it went away, forget it — it
            // will be offered again the next time it loads.
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);

            if (chunk == null) {
                this.totalSkipped++;
                continue;
            }

            chunkBudget--;

            FloraChunkSavedData.ChunkState previous = floraData.getState(key);
            FloraChunkSavedData.Transition transition = previous == null
                    ? FloraChunkSavedData.Transition.NONE
                    : previous.transition();

            FloraChunkContext context = FloraRegionResolver.buildContext(level, pos, chunk);

            // Ground that was already decorated has to be cleared first whenever the previous
            // layout can no longer be reproduced — otherwise the new pass adds plants on top of
            // the old ones and the chunk thickens a little every time.
            //
            // Two things break reproducibility: the palette changing, and the decorator version
            // changing. The version is part of the placement seed, so bumping it moves every plant;
            // without this check, an update that changed the decorator would silently double the
            // vegetation of every chunk already in the save.
            if (transition == FloraChunkSavedData.Transition.NONE
                    && previous != null
                    && previous.has(FloraChunkSavedData.FLAG_DECORATED)
                    && (previous.palette() != context.dominantPalette()
                        || previous.decoratorVersion() != FloraChunkDecorator.DECORATOR_VERSION)) {
                transition = FloraChunkSavedData.Transition.CONQUEST;
            }

            FloraChunkDecorator.Result result = FloraChunkDecorator.decorate(
                    level, chunk, context, transition,
                    previous == null ? null : previous.palette(),
                    previous == null ? FloraChunkDecorator.DECORATOR_VERSION : previous.decoratorVersion(),
                    attemptBudget);

            attemptBudget -= result.attemptsUsed();

            this.totalProcessed++;
            this.totalPlaced += result.placed();
            this.totalRemoved += result.removed();

            if (result.attemptsUsed() > result.placed()) {
                this.totalPlacementFailures += result.attemptsUsed() - result.placed();
            }

            if (result.complete()) {
                floraData.recordDecorated(pos, context.dominantPalette(),
                        FloraChunkDecorator.DECORATOR_VERSION, currentRevision);
            } else {
                floraData.recordIncomplete(pos, context.dominantPalette(),
                        FloraChunkDecorator.DECORATOR_VERSION, currentRevision, transition);

                // Put it straight back so it resumes next tick rather than waiting for a reload.
                enqueueDirect(pos);
                this.totalRequeued++;
            }
        }
    }

    /**
     * Takes the queued chunk closest to any player.
     *
     * <p>A plain FIFO is the wrong order here: a player who flies into new terrain queues hundreds
     * of chunks, and strict arrival order means the ones under their feet wait behind everything
     * queued earlier. Serving the nearest chunk first puts the work where it can actually be seen,
     * and the far ones drain afterwards.
     *
     * <p>The scan is linear over the queue, once per decorated chunk — a couple of thousand long
     * comparisons per tick at the configured budget, which is far cheaper than the block work that
     * follows it.
     */
    private long takeNearestToPlayer(ServerLevel level) {
        List<ServerPlayer> players = level.players();

        if (players.isEmpty()) {
            return this.ready.poll();
        }

        long best = 0L;
        long bestDistance = Long.MAX_VALUE;
        boolean found = false;

        for (long key : this.ready) {
            ChunkPos pos = new ChunkPos(key);
            long distance = Long.MAX_VALUE;

            for (ServerPlayer player : players) {
                long dx = pos.x - (player.getBlockX() >> 4);
                long dz = pos.z - (player.getBlockZ() >> 4);
                distance = Math.min(distance, dx * dx + dz * dz);
            }

            if (!found || distance < bestDistance) {
                best = key;
                bestDistance = distance;
                found = true;
            }
        }

        this.ready.remove(best);
        return best;
    }

    public int pendingCount() {
        return this.ready.size();
    }

    public int intakeCount() {
        return this.intake.size();
    }

    public boolean isQueued(ChunkPos pos) {
        return this.readySet.contains(pos.toLong());
    }

    /** Snapshot for {@code /firstcrusade flora stats}. */
    public Stats stats() {
        return new Stats(
                this.ready.size(),
                this.intake.size(),
                this.totalQueued,
                this.totalProcessed,
                this.totalSkipped,
                this.totalRequeued,
                this.totalPlaced,
                this.totalRemoved,
                this.totalPlacementFailures
        );
    }

    /**
     * @param placementFailures attempts that found no valid position — a high ratio is normal on
     *                          broken terrain and inside settlements, and only worth worrying about
     *                          when it approaches the attempt count everywhere
     */
    public record Stats(
            int pending,
            int intake,
            long queued,
            long processed,
            long skipped,
            long requeued,
            long placed,
            long removed,
            long placementFailures
    ) {
    }
}
