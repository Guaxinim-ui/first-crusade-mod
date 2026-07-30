package com.example.examplemod.flora.runtime;

/**
 * Deterministic 2D value noise in world coordinates, with no state and no allocation.
 *
 * <p>This is what stops the vegetation from ending in straight sixteen-block lines. The decorator
 * works chunk by chunk, but every density lookup and every territorial edge is warped through this
 * noise using <b>world</b> coordinates, so nothing in the result knows where a chunk boundary was.
 *
 * <p>Everything here is a pure function of (seed, x, z): the same coordinate always yields the same
 * value, in any order, on any thread, on a server restart, in a different session. That is a hard
 * requirement — the same chunk decorated twice under the same palette and decorator version has to
 * come out identical, or redecoration would keep piling new plants onto old ones.
 *
 * <p>Implementation is a 64-bit integer hash lattice with smoothstep interpolation. No generator
 * object is ever constructed, so calling this a few thousand times inside a chunk task costs
 * arithmetic only — which is why the performance rules forbid building a noise generator per
 * attempt.
 */
public final class FloraNoise {
    private FloraNoise() {
    }

    // Odd 64-bit constants from the SplitMix64 / MurmurHash3 finaliser family. Their only job is to
    // scramble bits well; nothing depends on these exact numbers beyond determinism.
    private static final long MIX_A = 0x9E3779B97F4A7C15L;
    private static final long MIX_B = 0xBF58476D1CE4E5B9L;
    private static final long MIX_C = 0x94D049BB133111EBL;

    /**
     * Uniform hash of a coordinate pair plus a salt, as a full-range long. Use this when you want a
     * decision rather than a gradient — "does this column get a clearing?".
     */
    public static long hash(long seed, int x, int z, int salt) {
        long value = seed;

        value ^= (long) x * 0x8DA6B343L;
        value ^= (long) z * 0xD8163841L;
        value ^= (long) salt * 0xCB1AB31FL;

        value ^= value >>> 30;
        value *= MIX_B;
        value ^= value >>> 27;
        value *= MIX_C;
        value ^= value >>> 31;
        value += MIX_A;

        return value;
    }

    /** The hash as a float in {@code [0, 1)}. */
    public static float hashFloat(long seed, int x, int z, int salt) {
        return (float) ((hash(seed, x, z, salt) >>> 40) / (double) (1 << 24));
    }

    /**
     * Smooth value noise in {@code [0, 1]} sampled at world coordinates.
     *
     * @param cellSize edge of one noise cell in blocks — larger means broader, slower features.
     *                 Must be at least 1.
     */
    public static float value(long seed, int worldX, int worldZ, int cellSize, int salt) {
        int size = Math.max(1, cellSize);

        int cellX = Math.floorDiv(worldX, size);
        int cellZ = Math.floorDiv(worldZ, size);

        float fx = smoothstep((worldX - (float) cellX * size) / size);
        float fz = smoothstep((worldZ - (float) cellZ * size) / size);

        float c00 = hashFloat(seed, cellX, cellZ, salt);
        float c10 = hashFloat(seed, cellX + 1, cellZ, salt);
        float c01 = hashFloat(seed, cellX, cellZ + 1, salt);
        float c11 = hashFloat(seed, cellX + 1, cellZ + 1, salt);

        float top = c00 + (c10 - c00) * fx;
        float bottom = c01 + (c11 - c01) * fx;

        return top + (bottom - top) * fz;
    }

    /**
     * Two octaves of {@link #value}: broad patches with a finer grain laid over them. Enough
     * structure to make clearings and thickets read as natural, cheap enough to call per placement
     * attempt.
     */
    public static float patches(long seed, int worldX, int worldZ, int cellSize, int salt) {
        float coarse = value(seed, worldX, worldZ, cellSize, salt);
        float fine = value(seed, worldX, worldZ, Math.max(1, cellSize / 3), salt + 7717);

        return coarse * 0.68F + fine * 0.32F;
    }

    /**
     * Signed offset in {@code [-amplitude, +amplitude]} used to bend a territorial border off the
     * straight line it would otherwise follow. Call once per axis with different salts.
     */
    public static float warp(long seed, int worldX, int worldZ, int cellSize, float amplitude, int salt) {
        return (value(seed, worldX, worldZ, cellSize, salt) * 2.0F - 1.0F) * amplitude;
    }

    private static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
