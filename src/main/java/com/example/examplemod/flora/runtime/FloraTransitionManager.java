package com.example.examplemod.flora.runtime;

import com.example.examplemod.WorldGenPlacement;
import com.example.examplemod.flora.FloraConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * How vegetation changes when the war changes who holds the ground.
 *
 * <h2>What a transition actually does</h2>
 *
 * Almost nothing, immediately. It works out which chunks are affected, flags exactly those in
 * {@link FloraChunkSavedData}, and queues the ones that happen to be loaded. Everything else waits:
 * a chunk nobody is standing in is transformed the next time it loads, which costs nothing now and
 * looks identical when it is finally seen.
 *
 * <p>The transformation itself is then spread over ticks by {@link FloraChunkQueue}'s budget. No
 * transition ever tries to convert a whole chunk — let alone a whole region — in one tick.
 *
 * <h2>What a transition never does</h2>
 *
 * Removal is limited to blocks in the {@code firstcrusade:flora} tag. A region changing hands
 * strips the previous occupant's plants and nothing else: no buildings, no player blocks, no
 * vanilla crops, no player-planted trees. Heavy clearing — vanilla trees and undergrowth inside a
 * footprint — happens only at the two moments the phase brief allows it, founding and expansion,
 * through {@link #onSettlementFounded}.
 *
 * <h2>Global revision versus local flags</h2>
 *
 * Territorial changes that alter the settlement map (a city razed, a camp founded) bump the war
 * map's revision counter by themselves, which marks every decorated chunk in the world as worth
 * re-checking. That check is deliberately cheap: the queue re-resolves the palette and, when it is
 * unchanged, simply restamps the chunk with the new revision without touching a single block. The
 * per-chunk dirty flags set here are for changes the settlement map cannot see — a field burned, a
 * battlefield, corruption taking hold.
 */
public final class FloraTransitionManager {
    private FloraTransitionManager() {
    }

    /** Upper bound on the chunk radius any single transition may flag, as a safety rail. */
    private static final int MAX_TRANSITION_CHUNK_RADIUS = 24;

    /**
     * Ground stays flagged as a battlefield for this long before another death there re-flags it.
     * Without it, a running battle would flag its chunk on every single kill and the queue would
     * spend the whole fight redecorating the same ground.
     */
    private static final long BATTLEFIELD_REFRESH_TICKS = 600L;

    /**
     * Flags every chunk within {@code radius} blocks of {@code centre} for retransformation, and
     * queues the loaded ones straight away.
     *
     * @return how many chunks were flagged
     */
    public static int markRegion(ServerLevel level, BlockPos centre, int radius,
                                 FloraChunkSavedData.Transition transition) {
        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            return 0;
        }

        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);
        FloraChunkQueue queue = FloraChunkQueue.get(level);

        int chunkRadius = Math.min(MAX_TRANSITION_CHUNK_RADIUS, (radius >> 4) + 1);

        ChunkPos origin = new ChunkPos(centre);
        int flagged = 0;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos pos = new ChunkPos(origin.x + dx, origin.z + dz);

                floraData.markDirty(pos, transition);
                flagged++;

                // Only chunks that are already loaded go into the queue. The rest are picked up on
                // their next load — a transition must never be a reason to load a chunk.
                if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                    queue.enqueueDirect(pos);
                }
            }
        }

        return flagged;
    }

    /**
     * A settlement has just been founded, or an existing one has grown.
     *
     * <p>This is one of only two moments heavy clearing is permitted, and it delegates that
     * clearing to the mod's existing {@link WorldGenPlacement#clearVegetation}. That routine was
     * read before being trusted: it removes logs, leaves and every replaceable block within a
     * square footprint and leaves solid ground alone — which covers vanilla trees, vanilla grass
     * and flowers, and this mod's own small flora, since every plant in {@code FCFlora} is
     * registered {@code replaceable()}. Wall-clinging lichen is not replaceable and survives, which
     * is the intended behaviour: moss on a wall is atmosphere, not an obstruction.
     *
     * <p>It is called once here, at founding — never on chunk load, and never per tick.
     */
    public static void onSettlementFounded(ServerLevel level, BlockPos centre, int radius, int height) {
        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            return;
        }

        if (FloraConfig.SETTLEMENT_VEGETATION_CLEANUP_ENABLED.get()) {
            WorldGenPlacement.clearVegetation(level, centre, radius, height);
        }

        // The cleared ground now belongs to the settlement, so its palette and its exclusion zones
        // both changed. Flag a little past the footprint so the fringe blends outward.
        markRegion(level, centre, radius + 32, FloraChunkSavedData.Transition.CONQUEST);
    }

    /**
     * Territory has changed hands. The chunks are flagged for a conquest transition, which clears
     * the previous holder's flora before the new palette is laid down — Imperial grass and memorial
     * blooms give way to trampled grass, fungus and gob moss, or the other way about.
     */
    public static void onTerritoryCaptured(ServerLevel level, BlockPos centre, int radius) {
        markRegion(level, centre, radius, FloraChunkSavedData.Transition.CONQUEST);
    }

    /**
     * Fields put to the torch. The affected chunks resolve to {@link FloraPalette#BURNT} for as
     * long as whatever caused the burning holds, which strips the crop-side flora and leaves burnt
     * stubble and ash behind.
     */
    public static void onFieldsBurned(ServerLevel level, BlockPos centre, int radius) {
        markRegion(level, centre, radius, FloraChunkSavedData.Transition.BURN);
    }

    /**
     * Ground retaken and being brought back. {@link FloraPalette#RECOVERING} is deliberately thin —
     * withered scrub and Imperial grass coming back through, with the occasional memorial bloom —
     * so a reclaimed region reads as convalescing rather than as instantly healthy.
     */
    public static void onTerritoryRecovered(ServerLevel level, BlockPos centre, int radius) {
        markRegion(level, centre, radius, FloraChunkSavedData.Transition.RECOVER);
    }

    /**
     * Marks or clears Chaos corruption over a region.
     *
     * <p><b>Nothing in the mod calls this yet</b> — there is no Chaos faction. It exists so that the
     * palette, the resolver rung and the transition are all in place and tested, and the Chaos phase
     * only has to decide <i>when</i> corruption spreads.
     */
    public static void markChaosCorruption(ServerLevel level, BlockPos centre, int radius, boolean corrupted) {
        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            return;
        }

        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);

        int chunkRadius = Math.min(MAX_TRANSITION_CHUNK_RADIUS, (radius >> 4) + 1);
        ChunkPos origin = new ChunkPos(centre);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                floraData.setChaosCorruption(new ChunkPos(origin.x + dx, origin.z + dz), corrupted);
            }
        }

        markRegion(level, centre, radius, FloraChunkSavedData.Transition.CORRUPT);
    }

    /**
     * Records that the war was fought here.
     *
     * <p>Called from the death handler. Marking is cheap — one map entry — but flagging the chunk
     * for redecoration is not, so a chunk that was already marked recently is only restamped. A
     * twenty-minute battle therefore causes one transformation, not one per casualty.
     *
     * <p>The scar ages on its own: the resolver reads a fresh battlefield for three in-game days,
     * an old one for a further twelve, and after that lets the surrounding territory take the
     * ground back. No ticking, no scheduled task — just a timestamp and a comparison.
     */
    public static void markBattlefield(ServerLevel level, BlockPos pos) {
        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            return;
        }

        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);
        ChunkPos chunkPos = new ChunkPos(pos);

        long now = level.getGameTime();
        long previous = floraData.battlefieldTime(chunkPos.toLong());

        floraData.markBattlefield(chunkPos, now);

        if (previous >= 0L && now - previous < BATTLEFIELD_REFRESH_TICKS) {
            return;
        }

        floraData.markDirty(chunkPos, FloraChunkSavedData.Transition.CONQUEST);

        if (level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) != null) {
            FloraChunkQueue.get(level).enqueueDirect(chunkPos);
        }
    }
}
