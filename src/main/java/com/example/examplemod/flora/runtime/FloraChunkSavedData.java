package com.example.examplemod.flora.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What the flora system remembers about ground it has already touched.
 *
 * <h2>Sparse by construction</h2>
 *
 * A Minecraft world has more chunks than anyone wants to enumerate, so this never pre-fills
 * anything. A chunk appears here the first time it is actually decorated or explicitly marked, and
 * a chunk nobody has ever visited costs exactly zero bytes. Keys are {@link ChunkPos#toLong()}.
 *
 * <h2>What is stored, and what deliberately is not</h2>
 *
 * Per chunk: the palette that was applied, the decorator version that applied it, the territorial
 * revision it was applied under, and three flags. That is four small numbers — it is <b>not</b> a
 * list of where each plant went. Individual plant positions are not stored and never need to be,
 * because placement is a pure function of (world seed, chunk, palette, version): the decorator can
 * always re-derive exactly what it put down. See {@link FloraChunkDecorator}.
 *
 * <p>Palettes go in as their small stable {@link FloraPalette#id()}, not as names, so the on-disk
 * form stays compact and survives palettes being reordered in the enum.
 *
 * <h2>Storage form</h2>
 *
 * The map is written as three parallel arrays rather than a list of compounds: one {@code long[]}
 * of keys, one {@code int[]} of packed state (palette, version, flags, pending transition, one byte
 * each) and one {@code int[]} of revisions. A hundred thousand decorated chunks is about 1.6 MB
 * instead of the tens of megabytes a compound-per-chunk list would cost.
 *
 * <h2>Territorial marks</h2>
 *
 * Two extra sparse sets live here because the mod has no other home for them yet:
 * battlefield timestamps (ground where the war actually happened) and Chaos corruption. Both are
 * inputs the resolver reads; both are documented at their accessors, and both are small, explicit
 * sets rather than a second copy of anyone's faction.
 *
 * <p>Every mutator calls {@link #setDirty()}. The in-memory work queue is <b>not</b> stored: on
 * reload, chunks flagged incomplete or dirty are simply re-queued when they next load.
 */
public class FloraChunkSavedData extends SavedData {
    private static final String NAME = "firstcrusade_flora_chunks";

    /**
     * On-disk format version. Bump when the packing below changes; {@link #load} migrates or
     * discards older data rather than misreading it.
     */
    public static final int FORMAT_VERSION = 1;

    /** The chunk has been fully decorated under the recorded palette/version. */
    public static final int FLAG_DECORATED = 1;
    /** Decoration ran out of budget partway through and must resume. */
    public static final int FLAG_INCOMPLETE = 1 << 1;
    /** The territory changed under this chunk; it needs re-resolving and redecorating. */
    public static final int FLAG_DIRTY = 1 << 2;

    /**
     * What kind of change is waiting for this chunk. Ordinals are persisted, so append only.
     */
    public enum Transition {
        /** Nothing pending; a plain (re)decoration. */
        NONE,
        /** Territory changed hands — clear the old palette's flora, then apply the new one. */
        CONQUEST,
        /** Fields put to the torch — strip most flora, lay down stubble and ash. */
        BURN,
        /** Retaken ground coming back to life — thin the previous palette in, gradually. */
        RECOVER,
        /** Chaos corruption taking hold. */
        CORRUPT,
        /**
         * Filling in a chunk that predates the worldgen vegetation.
         *
         * <p>The one case where the runtime decorator is allowed to plant a natural palette: an
         * old save has chunks that were generated before the biome features existed, and only the
         * decorator can add to them now. Unlike a conquest this clears nothing first — it is
         * adding what was never there, not replacing what was.
         */
        RETROGEN;

        public static Transition byOrdinal(int ordinal) {
            Transition[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
        }
    }

    /**
     * One chunk's record. Immutable — updates replace the whole entry, which keeps the "did
     * anything actually change?" check that guards {@link #setDirty()} trivial.
     */
    public record ChunkState(int paletteId, int decoratorVersion, int flags, Transition transition, int revision) {

        public FloraPalette palette() {
            return FloraPalette.byId(this.paletteId);
        }

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public ChunkState with(int flag) {
            return new ChunkState(this.paletteId, this.decoratorVersion, this.flags | flag, this.transition, this.revision);
        }

        public ChunkState without(int flag) {
            return new ChunkState(this.paletteId, this.decoratorVersion, this.flags & ~flag, this.transition, this.revision);
        }
    }

    private final Map<Long, ChunkState> states = new HashMap<>();

    /** Chunk -> game time of the last combat heavy enough to scar the ground. */
    private final Map<Long, Long> battlefields = new HashMap<>();

    /** Chunks under Chaos corruption. Nothing in the mod sets this yet — see the class javadoc. */
    private final Set<Long> chaosChunks = new HashSet<>();

    public FloraChunkSavedData() {
    }

    /**
     * Bound to the level it is asked for, not forced onto the overworld: the Hive dimension keeps
     * its own decoration record, which is what lets an Underhive chunk and an overworld chunk at
     * the same coordinates hold different palettes.
     */
    public static FloraChunkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FloraChunkSavedData::load, FloraChunkSavedData::new, NAME);
    }

    public static FloraChunkSavedData load(CompoundTag tag) {
        FloraChunkSavedData data = new FloraChunkSavedData();

        int version = tag.getInt("Version");

        // An unknown (newer, or corrupt) format is dropped rather than misread: the worst case is
        // that decorated chunks get decorated again, which is idempotent by design.
        if (version != FORMAT_VERSION) {
            return data;
        }

        long[] keys = tag.getLongArray("Keys");
        int[] packed = tag.getIntArray("Packed");
        int[] revisions = tag.getIntArray("Revisions");

        int count = Math.min(keys.length, Math.min(packed.length, revisions.length));

        for (int i = 0; i < count; i++) {
            data.states.put(keys[i], unpack(packed[i], revisions[i]));
        }

        long[] battlefieldKeys = tag.getLongArray("BattlefieldKeys");
        long[] battlefieldTimes = tag.getLongArray("BattlefieldTimes");

        int battlefieldCount = Math.min(battlefieldKeys.length, battlefieldTimes.length);

        for (int i = 0; i < battlefieldCount; i++) {
            data.battlefields.put(battlefieldKeys[i], battlefieldTimes[i]);
        }

        for (long key : tag.getLongArray("Chaos")) {
            data.chaosChunks.add(key);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", FORMAT_VERSION);

        int count = this.states.size();

        long[] keys = new long[count];
        int[] packed = new int[count];
        int[] revisions = new int[count];

        int index = 0;

        for (Map.Entry<Long, ChunkState> entry : this.states.entrySet()) {
            keys[index] = entry.getKey();
            packed[index] = pack(entry.getValue());
            revisions[index] = entry.getValue().revision();
            index++;
        }

        tag.putLongArray("Keys", keys);
        tag.putIntArray("Packed", packed);
        tag.putIntArray("Revisions", revisions);

        long[] battlefieldKeys = new long[this.battlefields.size()];
        long[] battlefieldTimes = new long[this.battlefields.size()];

        index = 0;

        for (Map.Entry<Long, Long> entry : this.battlefields.entrySet()) {
            battlefieldKeys[index] = entry.getKey();
            battlefieldTimes[index] = entry.getValue();
            index++;
        }

        tag.putLongArray("BattlefieldKeys", battlefieldKeys);
        tag.putLongArray("BattlefieldTimes", battlefieldTimes);

        tag.putLongArray("Chaos", this.chaosChunks.stream().mapToLong(Long::longValue).toArray());

        return tag;
    }

    private static int pack(ChunkState state) {
        return (state.paletteId() & 0xFF) << 24
                | (state.decoratorVersion() & 0xFF) << 16
                | (state.flags() & 0xFF) << 8
                | (state.transition().ordinal() & 0xFF);
    }

    private static ChunkState unpack(int packed, int revision) {
        return new ChunkState(
                (packed >>> 24) & 0xFF,
                (packed >>> 16) & 0xFF,
                (packed >>> 8) & 0xFF,
                Transition.byOrdinal(packed & 0xFF),
                revision
        );
    }

    // ------------------------------------------------------------------ chunk state

    @Nullable
    public ChunkState getState(ChunkPos pos) {
        return this.states.get(pos.toLong());
    }

    @Nullable
    public ChunkState getState(long packedChunk) {
        return this.states.get(packedChunk);
    }

    /** Records a completed decoration pass. Clears the dirty/incomplete flags and the transition. */
    public void recordDecorated(ChunkPos pos, FloraPalette palette, int decoratorVersion, int revision) {
        put(pos.toLong(), new ChunkState(palette.id(), decoratorVersion, FLAG_DECORATED, Transition.NONE, revision));
    }

    /** Records a pass that ran out of budget: same data, but flagged to be resumed. */
    public void recordIncomplete(ChunkPos pos, FloraPalette palette, int decoratorVersion, int revision,
                                 Transition transition) {
        put(pos.toLong(), new ChunkState(palette.id(), decoratorVersion, FLAG_INCOMPLETE, transition, revision));
    }

    /**
     * Flags a chunk for redecoration under the given kind of change. A chunk with no record yet
     * gets a placeholder entry so the mark survives a restart — it will be resolved and decorated
     * the next time it loads.
     */
    public void markDirty(ChunkPos pos, Transition transition) {
        long key = pos.toLong();
        ChunkState existing = this.states.get(key);

        if (existing == null) {
            put(key, new ChunkState(FloraPalette.NEUTRAL_DARK.id(), 0, FLAG_DIRTY, transition, -1));
            return;
        }

        put(key, new ChunkState(existing.paletteId(), existing.decoratorVersion(),
                existing.flags() | FLAG_DIRTY, transition, existing.revision()));
    }

    /** Drops a chunk's record entirely, so it is treated as never decorated. */
    public void forget(ChunkPos pos) {
        if (this.states.remove(pos.toLong()) != null) {
            setDirty();
        }
    }

    private void put(long key, ChunkState state) {
        ChunkState previous = this.states.put(key, state);

        if (!state.equals(previous)) {
            setDirty();
        }
    }

    public int decoratedChunkCount() {
        return this.states.size();
    }

    // ------------------------------------------------------------------ battlefields

    /**
     * Marks a chunk as ground the war has been fought over, stamped with the current game time.
     * Read back by the resolver, which ages a fresh battlefield into an old one and finally lets
     * the surrounding territory reclaim it.
     */
    public void markBattlefield(ChunkPos pos, long gameTime) {
        Long previous = this.battlefields.put(pos.toLong(), gameTime);

        if (previous == null || previous != gameTime) {
            setDirty();
        }
    }

    /** Game time of the last combat in this chunk, or {@code -1} if it has seen none. */
    public long battlefieldTime(long packedChunk) {
        Long time = this.battlefields.get(packedChunk);
        return time == null ? -1L : time;
    }

    /** Drops battlefield marks older than {@code cutoff}, so the map does not grow without bound. */
    public void pruneBattlefields(long cutoff) {
        if (this.battlefields.values().removeIf(time -> time < cutoff)) {
            setDirty();
        }
    }

    public int battlefieldCount() {
        return this.battlefields.size();
    }

    // ------------------------------------------------------------------ chaos

    public void setChaosCorruption(ChunkPos pos, boolean corrupted) {
        boolean changed = corrupted
                ? this.chaosChunks.add(pos.toLong())
                : this.chaosChunks.remove(pos.toLong());

        if (changed) {
            setDirty();
        }
    }

    public boolean isChaosCorrupted(long packedChunk) {
        return this.chaosChunks.contains(packedChunk);
    }

    public int chaosCount() {
        return this.chaosChunks.size();
    }
}
