package com.example.examplemod.flora.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.example.examplemod.flora.runtime.FloraTreePlan;
import com.example.examplemod.flora.runtime.FloraTreeSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.RegistryObject;

/**
 * One tree species, expressed as parameters rather than as a subclass.
 *
 * <p>Seven species share this one class; what differs between an Imperial pine and an Ork fungal
 * tower is a {@link Shape}, a height range and two blocks. That is deliberate — a class per tree
 * would be seven copies of the same careful placement logic, and the careful part is what matters.
 *
 * <h2>Canopies spill into neighbouring chunks</h2>
 *
 * A tree wide enough to look like a tree is wider than the margin any single chunk can spare, so
 * canopies are allowed to cross the chunk border into <b>already-loaded</b> neighbours. The
 * alternative — keeping every tree entirely inside its own chunk — sounds tidy and looks terrible
 * the moment trees get dense: it carves a permanent grid of clearings along every chunk line.
 *
 * <p>The rule that still holds absolutely is that no chunk is ever <i>loaded</i> to plant a tree. A
 * block whose chunk is not in memory is simply not written, and the tree reports how many it had to
 * leave out; the decorator marks that chunk incomplete and finishes the job on a later pass, once
 * the neighbour is there. Because the plan is deterministic, that later pass writes exactly the
 * missing blocks and changes nothing else.
 *
 * <h2>Why the leaves do not rot away</h2>
 *
 * Decoration writes with {@code UPDATE_CLIENTS} and deliberately fires no neighbour updates, which
 * means vanilla's leaf-distance propagation never runs. Leaves placed at the default distance of 7
 * would quietly decay on their first random tick and the canopy would vanish overnight.
 *
 * <p>So the distance is computed here, by a breadth-first walk out from the trunk through the
 * planned canopy — the same rule {@link LeavesBlock} would have arrived at, just calculated up
 * front. Any leaf further than six steps from wood is dropped rather than placed, because vanilla
 * would delete it anyway.
 */
public final class FCTree implements FloraTreeSpec {

    /** Returned by {@link #place} when the tree could not be planted at all. */
    public static final int REFUSED = -1;

    /** The silhouette. Everything else about a species is colour. */
    public enum Shape {
        /** Narrow, pointed, layered — a conifer. */
        CONIFER,
        /** A broad rounded mass on a bare trunk: the ordinary shape of a tree. */
        ROUND,
        /** A flat cap on a stalk, with a drooping rim. Not a tree, biologically. */
        FUNGAL,
        /** Dead standing wood with a couple of stub branches. No canopy at all. */
        SNAG,
        /** Short trunk, small tidy crown — something somebody planted. */
        ORCHARD
    }

    private final RegistryObject<Block> log;

    @Nullable
    private final RegistryObject<Block> foliage;

    private final Shape shape;
    private final int minHeight;
    private final int maxHeight;
    private final int canopyRadius;
    private final float perChunk;

    public FCTree(RegistryObject<Block> log, @Nullable RegistryObject<Block> foliage, Shape shape,
                  int minHeight, int maxHeight, int canopyRadius, float perChunk) {
        this.log = log;
        this.foliage = foliage;
        this.shape = shape;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.canopyRadius = canopyRadius;
        this.perChunk = perChunk;
    }

    @Override
    public float treesPerChunk() {
        return this.perChunk;
    }

    @Override
    public int canopyRadius() {
        return this.canopyRadius;
    }

    @Override
    public FloraTreePlan plan(int worldX, int worldZ, RandomSource random) {
        int height = this.minHeight + random.nextInt(this.maxHeight - this.minHeight + 1);

        // Y is relative to the trunk base, so the same plan drops onto any ground height.
        BlockPos origin = new BlockPos(worldX, 0, worldZ);

        List<BlockPos> trunk = new ArrayList<>(height);
        Set<BlockPos> leaves = new HashSet<>();

        layOutTrunk(trunk, origin, height, random);
        layOutCanopy(leaves, origin, height, random);
        leaves.removeAll(trunk);

        return new FloraTreePlan(trunk, leaves, height);
    }

    @Override
    public int place(ServerLevel level, LevelChunk chunk, FloraTreePlan plan, int baseY) {
        if (baseY + plan.height() + 3 >= chunk.getMaxBuildHeight()) {
            return REFUSED;
        }

        BlockPos first = plan.trunk().get(0);

        if (!hasSolidFooting(level, chunk, new BlockPos(first.getX(), baseY - 1, first.getZ()))) {
            return REFUSED;
        }

        // The trunk must be entirely free, and entirely in memory. A trunk is only a block or two
        // wide, so this is nearly always satisfied by the chunk being decorated.
        List<BlockPos> trunk = new ArrayList<>(plan.trunk().size());

        for (BlockPos rel : plan.trunk()) {
            BlockPos pos = rel.above(baseY);

            if (!canOccupy(level, chunk, pos, true)) {
                return REFUSED;
            }
            trunk.add(pos);
        }

        Set<BlockPos> leaves = new HashSet<>();
        int deferred = 0;

        for (BlockPos rel : plan.leaves()) {
            BlockPos pos = rel.above(baseY);
            LevelChunk owner = chunkFor(level, chunk, pos);

            if (owner == null) {
                // Neighbour not in memory. Leave it for a later pass rather than load it.
                deferred++;
                continue;
            }

            if (canOccupy(level, chunk, pos, false)) {
                leaves.add(pos);
            }
        }

        BlockState logState = this.log.get().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);

        for (BlockPos pos : trunk) {
            level.setBlock(pos, logState, Block.UPDATE_CLIENTS);
        }

        if (this.foliage != null && !leaves.isEmpty()) {
            placeCanopy(level, this.foliage.get(), trunk, leaves);
        }

        return deferred;
    }

    /**
     * Takes back a tree an earlier pass planted here, and nothing else.
     *
     * <p>This is what makes a conquered region actually change: an Imperial pine should not still
     * be standing among Ork fungal towers. The safety comes from being doubly specific — only
     * positions this species' own plan covers, and only when the block actually sitting there is
     * this species' log or foliage. A player's cabin built out of the same logs a few blocks away
     * is untouched, because those positions are not in the plan.
     */
    @Override
    public int clear(ServerLevel level, LevelChunk chunk, FloraTreePlan plan, int baseY) {
        Block logBlock = this.log.get();
        Block foliageBlock = this.foliage == null ? null : this.foliage.get();
        BlockState air = Blocks.AIR.defaultBlockState();

        int removed = 0;

        for (BlockPos rel : plan.trunk()) {
            removed += removeIfMatches(level, chunk, rel.above(baseY), logBlock, air);
        }

        if (foliageBlock != null) {
            for (BlockPos rel : plan.leaves()) {
                removed += removeIfMatches(level, chunk, rel.above(baseY), foliageBlock, air);
            }
        }

        return removed;
    }

    private int removeIfMatches(ServerLevel level, LevelChunk chunk, BlockPos pos, Block expected, BlockState air) {
        LevelChunk owner = chunkFor(level, chunk, pos);

        if (owner == null || !owner.getBlockState(pos).is(expected)) {
            return 0;
        }

        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
        return 1;
    }

    // ------------------------------------------------------------------ chunk access

    /**
     * The chunk that owns a position, or null when it is not in memory.
     *
     * <p>{@code getChunkNow} never loads anything: it returns what is already there or nothing,
     * which is exactly the semantics the decorator needs.
     */
    @Nullable
    private LevelChunk chunkFor(ServerLevel level, LevelChunk own, BlockPos pos) {
        if (pos.getY() < own.getMinBuildHeight() || pos.getY() >= own.getMaxBuildHeight()) {
            return null;
        }

        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;

        if (cx == own.getPos().x && cz == own.getPos().z) {
            return own;
        }

        return level.getChunkSource().getChunkNow(cx, cz);
    }

    private boolean canOccupy(ServerLevel level, LevelChunk own, BlockPos pos, boolean strict) {
        LevelChunk owner = chunkFor(level, own, pos);

        if (owner == null) {
            return false;
        }

        BlockState state = owner.getBlockState(pos);

        if (state.isAir()) {
            return true;
        }

        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        // Grass and the mod's own small flora are fine to grow through; anything solid is not.
        if (!state.canBeReplaced()) {
            return !strict && state.is(BlockTags.LEAVES);
        }

        return true;
    }

    private boolean hasSolidFooting(ServerLevel level, LevelChunk own, BlockPos ground) {
        LevelChunk owner = chunkFor(level, own, ground);

        if (owner == null) {
            return false;
        }

        BlockState state = owner.getBlockState(ground);

        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.MUD)
                || state.is(Blocks.CLAY);
    }

    // ------------------------------------------------------------------ layout

    private void layOutTrunk(List<BlockPos> into, BlockPos origin, int height, RandomSource random) {
        // A very slight lean, decided once, keeps a stand of trees from looking like fence posts.
        int leanAt = this.shape == Shape.SNAG || this.shape == Shape.ROUND
                ? height / 2 + random.nextInt(Math.max(1, height / 3))
                : Integer.MAX_VALUE;

        int dx = 0;
        int dz = 0;

        for (int i = 0; i < height; i++) {
            if (i == leanAt) {
                if (random.nextBoolean()) {
                    dx += random.nextBoolean() ? 1 : -1;
                } else {
                    dz += random.nextBoolean() ? 1 : -1;
                }
            }
            into.add(origin.offset(dx, i, dz));
        }

        if (this.shape == Shape.SNAG) {
            // Two broken stubs, which is most of what makes deadwood read as deadwood.
            for (int s = 0; s < 2; s++) {
                int y = height / 2 + s * 2 + random.nextInt(2);
                if (y >= height) {
                    continue;
                }
                Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                into.add(origin.offset(dx + dir.getStepX(), y, dz + dir.getStepZ()));
            }
        }
    }

    /**
     * The canopy.
     *
     * <p>Two rules hold for every shape, and both exist because breaking them looked exactly like
     * the bug they caused:
     *
     * <ul>
     *   <li><b>No bare trunk above the foliage.</b> The canopy is built downwards from a cap that
     *       sits directly on the trunk's top block, so there is never a gap between the two.</li>
     *   <li><b>No one-block-wide leaf spike.</b> A layer never collapses to a single block in the
     *       trunk column — that block is either inside the trunk (and dropped as a duplicate) or
     *       floating above it. Tips are drawn as an explicit cap plus a cross, never as a
     *       degenerate disc.</li>
     * </ul>
     */
    /**
     * The lowest block a canopy may occupy, counted from the trunk's base.
     *
     * <p>Not a style choice. Measured in a generated world, the canopies here reached a median of
     * <b>three blocks below the trunk base</b> and a worst case of eleven — foliage buried in the
     * ground, and no gap a player or a squad could walk through. Every shape below is clamped so
     * the crown starts at least this high, which is what turns a wood from a wall into terrain you
     * can fight across.
     */
    private static final int MIN_CLEARANCE = 5;

    private void layOutCanopy(Set<BlockPos> into, BlockPos origin, int height, RandomSource random) {
        if (this.foliage == null) {
            return;
        }

        int trunkTop = height - 1;

        // A short specimen cannot honour the full clearance and still have a crown; give it what
        // is left over rather than dropping the canopy onto the floor.
        int floor = Math.min(MIN_CLEARANCE, Math.max(1, trunkTop - 2));

        switch (this.shape) {
            case CONIFER -> {
                // Skirts, widening as they descend, over the top of the trunk.
                int layers = Math.max(4, (int) (height * 0.55F));
                int bottom = Math.max(floor, trunkTop - layers + 1);

                for (int y = trunkTop; y >= bottom; y--) {
                    int i = trunkTop - y;

                    // 1,1,2,2,3,3,... capped at the species radius
                    int r = Math.min(this.canopyRadius, 1 + i / 2);

                    // every third layer pulls in, which is what reads as a spruce's stepped skirt
                    if (i >= 3 && i % 3 == 0) {
                        r = Math.max(1, r - 1);
                    }

                    disc(into, origin, y, r, random, 0.10F);
                }

                // Tip: a cross on the trunk top and a single block above it. No gap, no spike.
                cross(into, origin, trunkTop, 1);
                into.add(origin.above(trunkTop + 1));
            }
            case ROUND -> {
                int r = this.canopyRadius;

                // The centre stays tied to the trunk top — lifting it to make room would leave a
                // gap between crown and trunk, which is the one thing this method must never do.
                // The clearance is bought by truncating the ball's underside instead (below), and
                // by the species being tall enough in the first place: FCTrees keeps every ROUND
                // species at minHeight >= 2 * canopyRadius + MIN_CLEARANCE.
                int centre = trunkTop - Math.max(1, r - 1);

                for (int dy = -r; dy <= r; dy++) {
                    float v = dy / (float) (r + 0.6F);
                    int rowRadius = Math.round(r * (float) Math.sqrt(Math.max(0.0F, 1.0F - v * v)));

                    if (rowRadius <= 0 || centre + dy < floor) {
                        continue;
                    }

                    disc(into, origin, centre + dy, rowRadius, random, 0.16F);
                }

                cross(into, origin, trunkTop, 1);
                into.add(origin.above(trunkTop + 1));
            }
            case FUNGAL -> {
                // Flat cap sitting on the stalk, with the rim drooping a block.
                disc(into, origin, trunkTop, this.canopyRadius, random, 0.04F);
                disc(into, origin, trunkTop + 1, Math.max(1, this.canopyRadius - 1), random, 0.08F);
                ring(into, origin, trunkTop - 1, this.canopyRadius, random, 0.40F);
            }
            case ORCHARD -> {
                for (int dy = -1; dy <= 1; dy++) {
                    int r = dy == 0 ? this.canopyRadius : Math.max(1, this.canopyRadius - 1);
                    disc(into, origin, trunkTop + dy, r, random, 0.18F);
                }
                into.add(origin.above(trunkTop + 2));
            }
            case SNAG -> {
            }
        }
    }

    /** A filled disc of leaves at one height, with the outermost ring nibbled away. */
    private void disc(Set<BlockPos> into, BlockPos origin, int y, int radius,
                      RandomSource random, float ragged) {
        if (radius <= 0) {
            return;
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = dx * dx + dz * dz;

                if (distSq > radius * radius) {
                    continue;
                }

                // Only the outer ring is thinned, and gently. Nibbling the middle is what turned
                // the first canopies into see-through static.
                boolean edge = distSq > (radius - 1) * (radius - 1);

                if (edge && random.nextFloat() < ragged + 0.12F) {
                    continue;
                }

                into.add(origin.offset(dx, y, dz));
            }
        }
    }

    /** A plus shape — used for tips, where a disc would collapse to a single block. */
    private void cross(Set<BlockPos> into, BlockPos origin, int y, int arm) {
        for (int d = 1; d <= arm; d++) {
            into.add(origin.offset(d, y, 0));
            into.add(origin.offset(-d, y, 0));
            into.add(origin.offset(0, y, d));
            into.add(origin.offset(0, y, -d));
        }
    }

    private void ring(Set<BlockPos> into, BlockPos origin, int y, int radius,
                      RandomSource random, float gaps) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = dx * dx + dz * dz;

                if (distSq > radius * radius || distSq <= (radius - 1) * (radius - 1)) {
                    continue;
                }
                if (random.nextFloat() < gaps) {
                    continue;
                }

                into.add(origin.offset(dx, y, dz));
            }
        }
    }

    // ------------------------------------------------------------------ commit

    /**
     * Writes the canopy with the leaf distance vanilla would have computed.
     *
     * <p>Breadth-first from every trunk block, stepping only through planned canopy positions, so a
     * leaf's distance is the true number of steps back to wood — not a straight-line guess, which
     * would be wrong for anything shaped like a fungal cap.
     */
    private void placeCanopy(ServerLevel level, Block foliageBlock, List<BlockPos> trunk, Set<BlockPos> leaves) {
        Map<BlockPos, Integer> distance = new HashMap<>(leaves.size());
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        for (BlockPos wood : trunk) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = wood.relative(direction);

                if (leaves.contains(neighbour) && !distance.containsKey(neighbour)) {
                    distance.put(neighbour, 1);
                    queue.add(neighbour);
                }
            }
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int next = distance.get(current) + 1;

            if (next > LeavesBlock.DECAY_DISTANCE) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighbour = current.relative(direction);

                if (leaves.contains(neighbour) && !distance.containsKey(neighbour)) {
                    distance.put(neighbour, next);
                    queue.add(neighbour);
                }
            }
        }

        BlockState base = foliageBlock.defaultBlockState();

        for (Map.Entry<BlockPos, Integer> entry : distance.entrySet()) {
            level.setBlock(entry.getKey(),
                    base.setValue(LeavesBlock.DISTANCE, entry.getValue())
                            .setValue(LeavesBlock.PERSISTENT, Boolean.FALSE),
                    Block.UPDATE_CLIENTS);
        }
    }
}
