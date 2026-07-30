package com.example.examplemod.flora.runtime;

import java.util.List;

import net.minecraft.world.level.ChunkPos;

/**
 * Everything one chunk decoration task needs to know about <i>where</i> it is, resolved once and
 * then answered with arithmetic only.
 *
 * <p>This is the object the performance rules are really about. Working out who holds a piece of
 * ground means walking the world war map, reading settlement records and checking city layouts —
 * far too expensive to redo for every plant. So {@link FloraRegionResolver} does all of it once,
 * flattens the answer into the small array of {@link Influence} below, and from then on
 * {@link #paletteAt} is a loop over a handful of records with no allocation, no world access and no
 * SavedData lookups.
 *
 * <h2>Why there are no straight chunk edges</h2>
 *
 * The chunk decides <i>what work happens</i>. It does not decide <i>what the ground looks like</i>.
 * Every column is resolved on its own, through world coordinates warped by
 * {@link FloraNoise}, so a territorial border wanders across the terrain and a plant standing one
 * block inside a chunk edge can perfectly well belong to the region next door.
 *
 * <p>Crucially this needs no neighbouring chunk to be loaded or even to exist: influences are
 * circles and squares around settlement positions read from persisted data, so the maths simply
 * continues past the chunk edge. A chunk decorated today and its neighbour decorated a week later
 * still meet seamlessly, because both evaluate the same continuous function.
 */
public final class FloraChunkContext {

    /**
     * One region's claim on the ground: a centre, a reach, the palette it imposes and how strongly
     * it imposes it.
     *
     * @param priority       lower wins; the ladder from {@link FloraRegionResolver}
     * @param square         true for Chebyshev reach (settlement footprints, which are square),
     *                       false for Euclidean (territory halos, which are not)
     * @param densityScale   multiplier on vegetation thickness inside this claim
     */
    public record Influence(
            int x,
            int z,
            int radius,
            FloraPalette palette,
            int priority,
            boolean square,
            float densityScale
    ) {
        /**
         * How far into this claim the given point sits, as {@code 0} at the very edge rising to
         * {@code 1} at the centre. Negative means outside.
         */
        public float penetration(int px, int pz) {
            int dx = Math.abs(px - this.x);
            int dz = Math.abs(pz - this.z);

            float distance = this.square
                    ? Math.max(dx, dz)
                    : (float) Math.sqrt((double) dx * dx + (double) dz * dz);

            if (this.radius <= 0) {
                return -1.0F;
            }

            return 1.0F - distance / this.radius;
        }
    }

    /**
     * A height band that overrides the horizontal answer. Only the Hive dimension uses these: its
     * districts are stacked, so which level a column sits on matters as much as where it is.
     * Bands are tested in order and the first whose {@code maxY} the position falls at or below
     * wins.
     */
    public record VerticalBand(int maxY, FloraPalette palette) {
    }

    private final ChunkPos chunkPos;
    private final long floraSeed;
    private final FloraPalette neutralPalette;
    private final FloraPalette dominantPalette;
    private final List<Influence> influences;
    private final List<VerticalBand> verticalBands;
    private final int blendWidth;
    private final float globalDensity;

    FloraChunkContext(
            ChunkPos chunkPos,
            long floraSeed,
            FloraPalette neutralPalette,
            List<Influence> influences,
            List<VerticalBand> verticalBands,
            int blendWidth,
            float globalDensity
    ) {
        this.chunkPos = chunkPos;
        this.floraSeed = floraSeed;
        this.neutralPalette = neutralPalette;
        this.influences = influences;
        this.verticalBands = verticalBands;
        this.blendWidth = Math.max(0, blendWidth);
        this.globalDensity = globalDensity;

        // The chunk's own identity is just the identity of its centre column. It is what gets
        // recorded in the SavedData and reported by the inspect command; the actual ground is
        // resolved column by column and may well contain two or three palettes near a border.
        this.dominantPalette = paletteAt(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ());
    }

    public ChunkPos chunkPos() {
        return this.chunkPos;
    }

    /** The palette recorded for the chunk as a whole. */
    public FloraPalette dominantPalette() {
        return this.dominantPalette;
    }

    /** What the vanilla biome suggests for ground nobody holds. */
    public FloraPalette neutralPalette() {
        return this.neutralPalette;
    }

    public long floraSeed() {
        return this.floraSeed;
    }

    public List<Influence> influences() {
        return this.influences;
    }

    /**
     * The palette that governs one column, border blending included.
     *
     * <p>The point is warped before it is tested, which is the whole trick: a border that would
     * otherwise be a clean circle or a clean square instead wanders by up to
     * {@code borderBlendWidth} blocks, and does so as a continuous function of world position, so
     * neighbouring chunks agree along their shared edge without ever consulting each other.
     */
    public FloraPalette paletteAt(int worldX, int worldZ) {
        int sampleX = worldX;
        int sampleZ = worldZ;

        if (this.blendWidth > 0) {
            sampleX += Math.round(FloraNoise.warp(this.floraSeed, worldX, worldZ, 28, this.blendWidth, 4111));
            sampleZ += Math.round(FloraNoise.warp(this.floraSeed, worldX, worldZ, 28, this.blendWidth, 9187));
        }

        Influence best = null;
        float bestPenetration = 0.0F;

        for (int i = 0; i < this.influences.size(); i++) {
            Influence influence = this.influences.get(i);
            float penetration = influence.penetration(sampleX, sampleZ);

            if (penetration < 0.0F) {
                continue;
            }

            if (best == null
                    || influence.priority() < best.priority()
                    || (influence.priority() == best.priority() && penetration > bestPenetration)) {
                best = influence;
                bestPenetration = penetration;
            }
        }

        return best == null ? this.neutralPalette : best.palette();
    }

    /**
     * The palette for a column at a known height. Identical to {@link #paletteAt(int, int)} outside
     * the Hive, where no vertical bands are set; inside a Hive City the stacked districts mean the
     * Underhive sump and the hab levels above it are different places at the same coordinates.
     */
    public FloraPalette paletteAt(int worldX, int worldZ, int worldY) {
        for (int i = 0; i < this.verticalBands.size(); i++) {
            VerticalBand band = this.verticalBands.get(i);

            if (worldY <= band.maxY()) {
                return band.palette();
            }
        }

        return paletteAt(worldX, worldZ);
    }

    /**
     * How thickly to plant one column, in {@code [0, ~2]}.
     *
     * <p>Three things multiply together: the palette's own character, the config multipliers, and a
     * two-octave noise field. That last term is what produces clearings and thickets — without it a
     * region would be an even carpet, which reads as generated rather than grown. Territory also
     * thins towards its own edge, so a faction's vegetation fades out instead of stopping dead.
     */
    public float densityAt(int worldX, int worldZ, FloraPalette palette) {
        float patch = FloraNoise.patches(this.floraSeed, worldX, worldZ, 44, 2311);

        // Remap the noise so roughly a fifth of the ground is genuinely bare and the thickest
        // patches are clearly thicker than average, rather than everything hovering near the mean.
        float shaped = Math.max(0.0F, (patch - 0.10F) / 0.90F);

        float edgeFalloff = 1.0F;
        float scale = 1.0F;

        Influence governing = governingInfluence(worldX, worldZ, palette);

        if (governing != null) {
            scale = governing.densityScale();
            float penetration = governing.penetration(worldX, worldZ);

            // Fade over the outermost tenth of a claim.
            if (penetration >= 0.0F && penetration < 0.1F) {
                edgeFalloff = 0.35F + penetration * 6.5F;
            }
        }

        return palette.baseDensity() * this.globalDensity * shaped * scale * edgeFalloff;
    }

    private Influence governingInfluence(int worldX, int worldZ, FloraPalette palette) {
        Influence best = null;

        for (int i = 0; i < this.influences.size(); i++) {
            Influence influence = this.influences.get(i);

            if (influence.palette() != palette) {
                continue;
            }

            if (influence.penetration(worldX, worldZ) < 0.0F) {
                continue;
            }

            if (best == null || influence.priority() < best.priority()) {
                best = influence;
            }
        }

        return best;
    }

    /** True when more than one palette governs ground in this chunk — used by the inspect command. */
    public boolean isBorderChunk() {
        int minX = this.chunkPos.getMinBlockX();
        int minZ = this.chunkPos.getMinBlockZ();

        FloraPalette first = paletteAt(minX, minZ);

        return first != paletteAt(minX + 15, minZ)
                || first != paletteAt(minX, minZ + 15)
                || first != paletteAt(minX + 15, minZ + 15)
                || first != this.dominantPalette;
    }

}
