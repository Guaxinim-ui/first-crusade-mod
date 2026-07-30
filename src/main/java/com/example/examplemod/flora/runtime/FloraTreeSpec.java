package com.example.examplemod.flora.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A tree a palette can plant.
 *
 * <p>Trees go through the <b>same</b> pipeline as every other plant: the same per-chunk budget, the
 * same deterministic seed, the same exclusion zones. There is no second, parallel placement system
 * for them — {@link FloraChunkDecorator} asks {@link FloraPalette#treeEntry()} once per chunk task
 * and plants whatever comes back.
 *
 * <p>The implementation that ships is {@link com.example.examplemod.flora.tree.FCTree}, which is
 * parameterised rather than subclassed: a species is a row of numbers (log, foliage, height range,
 * canopy shape) rather than a new class.
 *
 * <h2>The chunk containment rule</h2>
 *
 * A tree is several blocks wide, so unlike a grass blade it can easily reach past the chunk being
 * decorated. Writing there would load the neighbour — the one thing the decorator must never do. So
 * the decorator insets its candidate positions by {@link #canopyRadius()} and only plants trees
 * whose whole footprint fits inside the chunk. Trees are sparse enough that losing the outermost
 * few columns of each chunk is invisible; a canopy sliced off at a chunk border would not be.
 */
public interface FloraTreeSpec {

    /**
     * Lays out one tree at a column, consuming the generator and touching nothing.
     *
     * <p>Must be a pure function of {@code (worldX, worldZ, random)} — see {@link FloraTreePlan}
     * for why that is not optional.
     */
    FloraTreePlan plan(int worldX, int worldZ, RandomSource random);

    /**
     * Plants a planned tree with its trunk base at {@code baseY}.
     *
     * <p>A canopy may reach into a neighbouring chunk. That is allowed when the neighbour is
     * already in memory and forbidden otherwise — no implementation may ever <i>load</i> a chunk to
     * finish a tree. Blocks that fall in an absent neighbour are reported rather than written, and
     * the decorator finishes them on a later pass.
     *
     * @return {@code -1} when nothing was planted, otherwise the number of blocks left for later
     *         ({@code 0} meaning the tree is complete)
     */
    int place(ServerLevel level, LevelChunk chunk, FloraTreePlan plan, int baseY);

    /**
     * Removes a tree this species planted here, and nothing else — only positions the plan covers,
     * and only when the block found there is actually this species'.
     *
     * @return how many blocks were removed
     */
    int clear(ServerLevel level, LevelChunk chunk, FloraTreePlan plan, int baseY);

    /**
     * Expected trees per chunk at density 1.0, before {@code FloraConfig.TREE_FREQUENCY} and the
     * palette's own density are applied. May be fractional — the decorator treats the remainder as
     * a probability.
     */
    float treesPerChunk();

    /**
     * How far the widest part of this tree reaches from its trunk, in blocks. The decorator keeps
     * trunks at least this far inside the chunk so no canopy block ever lands outside it.
     */
    int canopyRadius();

}
