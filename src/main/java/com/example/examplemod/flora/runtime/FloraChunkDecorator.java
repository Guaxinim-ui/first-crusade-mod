package com.example.examplemod.flora.runtime;

import javax.annotation.Nullable;

import com.example.examplemod.flora.FloraConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Puts the vegetation of one chunk on the ground.
 *
 * <h2>Determinism, and why it matters more than it sounds</h2>
 *
 * Placement is driven by a {@link RandomSource} seeded from the world seed, the chunk coordinates,
 * the palette id and {@link #DECORATOR_VERSION} — never from {@code level.random}. The same chunk
 * decorated twice under the same palette therefore produces the <i>identical</i> set of positions.
 *
 * <p>That single property is what makes redecoration safe. Re-running a pass writes the same plants
 * into the same blocks, so vegetation cannot pile up pass after pass, and a run that is interrupted
 * halfway through can simply start again from the beginning instead of having to persist how far it
 * got. When the palette does change, the old flora is cleared first (tag-limited — see
 * {@link FloraPlacementRules#clearCustomFlora}) and the new palette lays down its own deterministic
 * set. Nothing accumulates.
 *
 * <h2>The chunk decides the work, not the appearance</h2>
 *
 * The chunk is the unit of scheduling, budget and bookkeeping. It is deliberately <i>not</i> the
 * unit of appearance: every candidate column asks {@link FloraChunkContext#paletteAt} for itself,
 * so a chunk that straddles a border grows two regions' plants, mixed along a line that the noise
 * has bent well away from the sixteen-block grid.
 */
public final class FloraChunkDecorator {
    private FloraChunkDecorator() {
    }

    /**
     * Bumped whenever a change to this class would make previously decorated chunks look wrong.
     * Chunks recorded under an older version are redecorated when they next load. It is stored in
     * one byte, so it must stay below 256.
     */
    public static final int DECORATOR_VERSION = 4;

    /** Placement attempts for a full-density chunk, before any density multiplier. */
    private static final int BASE_ATTEMPTS = 340;

    /** How far outside the chunk exclusion geometry is gathered from. */
    private static final int EXCLUSION_SEARCH_RADIUS = 24;

    /** Blocks above the ground a lichen patch will search for a wall face to attach to. */
    private static final int LICHEN_CLIMB = 4;

    /** Keeps the tree generator's stream disjoint from the plant generator's. */
    private static final long TREE_SEED_SALT = 0x5F3A9C21B7E4D18DL;

    /**
     * How far inside the chunk a trunk must start. Only the trunk is constrained — a trunk leans by
     * at most one block, so two is enough — while canopies reach freely into loaded neighbours.
     */
    private static final int TRUNK_INSET = 2;

    /**
     * Outcome of one decoration pass.
     *
     * @param attemptsUsed charged against the tick budget
     * @param placed       blocks actually written
     * @param removed      blocks cleared before planting
     * @param complete     false when the pass ran out of budget and must be re-queued
     */
    public record Result(int attemptsUsed, int placed, int removed, boolean complete) {
    }

    /**
     * Runs one decoration pass over a loaded chunk.
     *
     * @param budget maximum attempts this pass may spend, from the tick's global allowance
     */
    public static Result decorate(
            ServerLevel level,
            LevelChunk chunk,
            FloraChunkContext context,
            FloraChunkSavedData.Transition transition,
            @Nullable FloraPalette previousPalette,
            int previousVersion,
            int budget
    ) {
        if (budget <= 0) {
            return new Result(0, 0, 0, false);
        }

        ChunkPos chunkPos = chunk.getPos();
        FloraPalette palette = context.dominantPalette();

        // Natural regions are dressed by worldgen, not here. Running both would lay a second
        // forest on top of the first. The exception is a retrogen, which exists precisely to fill
        // in chunks that were generated before those worldgen features did.
        boolean retrogen = transition == FloraChunkSavedData.Transition.RETROGEN;

        if (palette.isNatural() && !retrogen && transition == FloraChunkSavedData.Transition.NONE) {
            return new Result(0, 0, 0, true);
        }

        int spent = 0;
        int removed = 0;

        FloraExclusionZones exclusions = FloraExclusionZones.forChunk(level, chunkPos, EXCLUSION_SEARCH_RADIUS);

        // A change of ownership clears the previous region's plants first. Ordinary decoration of a
        // chunk that has never been touched skips this entirely — the phase brief is explicit that
        // a plain chunk load must not go around deleting things.
        if (transition != FloraChunkSavedData.Transition.NONE && !retrogen) {
            int clearBudget = Math.min(budget, FloraConfig.MAXIMUM_CUSTOM_FLORA_PER_CHUNK.get());
            removed = FloraPlacementRules.clearCustomFlora(level, chunk, clearBudget);
            spent += removed;

            // Trees are logs and leaves, not flora, so the tag-limited clear above cannot see them.
            // They are removed by reconstructing the previous pass instead — precise enough that a
            // player's building made of the same logs is never at risk.
            if (previousPalette != null && previousPalette.treeEntries().length > 0) {
                removed += clearPreviousTrees(level, chunk, context, previousPalette, previousVersion, exclusions);
            }

            if (spent >= budget) {
                return new Result(spent, 0, removed, false);
            }
        }

        RandomSource random = RandomSource.create(
                placementSeed(context.floraSeed(), chunkPos, palette, DECORATOR_VERSION));

        double smallPlantDensity = FloraConfig.SMALL_PLANT_DENSITY.get();

        int attempts = (int) Math.round(BASE_ATTEMPTS * Math.min(2.0D, Math.max(0.0D, smallPlantDensity)));
        attempts = Math.min(attempts, budget - spent);

        int maxFlora = FloraConfig.MAXIMUM_CUSTOM_FLORA_PER_CHUNK.get();
        int maxLichen = FloraConfig.MAXIMUM_LICHEN_PER_CHUNK.get();
        int maxTall = FloraConfig.MAXIMUM_TALL_PLANTS_PER_CHUNK.get();

        int placed = 0;
        int lichenPlaced = 0;
        int tallPlaced = 0;

        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos groundCursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos plantCursor = new BlockPos.MutableBlockPos();

        for (int attempt = 0; attempt < attempts && placed < maxFlora; attempt++) {
            spent++;

            int worldX = minX + random.nextInt(16);
            int worldZ = minZ + random.nextInt(16);

            if (exclusions.blocks(worldX, worldZ)) {
                continue;
            }

            int groundY = FloraPlacementRules.groundY(chunk, worldX, worldZ);

            if (groundY == FloraPlacementRules.NO_GROUND) {
                continue;
            }

            groundCursor.set(worldX, groundY, worldZ);
            plantCursor.set(worldX, groundY + 1, worldZ);

            BlockState groundState = chunk.getBlockState(groundCursor);

            if (FloraPlacementRules.isUnusableGround(chunk, groundCursor, groundState)) {
                continue;
            }

            // The column resolves its own identity. Near a border this is routinely not the
            // chunk's own palette, which is the entire point.
            FloraPalette columnPalette = context.paletteAt(worldX, worldZ, groundY + 1);

            // A region that changes the ground itself — the ash waste — recolours the surface where
            // the decorator is already sampling. Doing it here costs no extra scanning, and because
            // the sampling is noisy the grey creeps in unevenly instead of arriving as a slab.
            placed += applyGroundOverride(level, chunk, columnPalette, groundCursor, groundState, random);

            float density = context.densityAt(worldX, worldZ, columnPalette);

            if (random.nextFloat() >= density) {
                continue;
            }

            FloraPalette.Entry entry = columnPalette.pick(random.nextInt(Math.max(1, columnPalette.totalWeight())));

            if (random.nextFloat() >= entry.density()) {
                continue;
            }

            if (entry.form() == FloraPalette.Form.LICHEN && lichenPlaced >= maxLichen) {
                continue;
            }

            if (entry.form() == FloraPalette.Form.TALL && tallPlaced >= maxTall) {
                continue;
            }

            // Plants arrive in clumps. A clump is what separates a meadow from a sprinkle, and it
            // costs one column resolution instead of one per plant.
            int groupSize = entry.minGroup() + random.nextInt(Math.max(1, entry.maxGroup() - entry.minGroup() + 1));

            for (int member = 0; member < groupSize && placed < maxFlora && spent < budget; member++) {
                int offsetX = member == 0 ? 0 : random.nextInt(5) - 2;
                int offsetZ = member == 0 ? 0 : random.nextInt(5) - 2;

                int memberX = worldX + offsetX;
                int memberZ = worldZ + offsetZ;

                // Clump members that fall outside the chunk are dropped rather than chased: the
                // neighbouring chunk will grow its own, and reaching across would load it.
                if ((memberX >> 4) != chunkPos.x || (memberZ >> 4) != chunkPos.z) {
                    continue;
                }

                if (member > 0) {
                    spent++;

                    if (exclusions.blocks(memberX, memberZ)) {
                        continue;
                    }
                }

                int memberGroundY = member == 0 ? groundY : FloraPlacementRules.groundY(chunk, memberX, memberZ);

                if (memberGroundY == FloraPlacementRules.NO_GROUND) {
                    continue;
                }

                // A clump must not crawl up a cliff face.
                if (Math.abs(memberGroundY - groundY) > 2) {
                    continue;
                }

                groundCursor.set(memberX, memberGroundY, memberZ);
                plantCursor.set(memberX, memberGroundY + 1, memberZ);

                BlockState memberGround = chunk.getBlockState(groundCursor);

                if (member > 0 && FloraPlacementRules.isUnusableGround(chunk, groundCursor, memberGround)) {
                    continue;
                }

                if (!placeOne(level, chunk, entry, plantCursor, groundCursor, memberGround, random)) {
                    continue;
                }

                placed++;

                if (entry.form() == FloraPalette.Form.LICHEN) {
                    lichenPlaced++;
                } else if (entry.form() == FloraPalette.Form.TALL) {
                    tallPlaced++;
                }
            }
        }

        TreeResult trees = placeTrees(level, chunk, context, palette, exclusions);
        placed += trees.planted();

        // A canopy that reached into a chunk which was not in memory leaves the pass unfinished:
        // the chunk goes back in the queue and the missing blocks are written once the neighbour
        // arrives. Replaying is free — the plan is identical, so only the gaps get filled.
        boolean complete = (spent < budget || placed >= maxFlora) && trees.deferred() == 0;

        return new Result(spent, placed, removed, complete);
    }

    /**
     * One plant, with the shared vetoes applied first. Lichen is the exception to the ground rules:
     * it clings to walls and ceilings, so it is not required to stand on anything.
     */
    private static boolean placeOne(
            ServerLevel level,
            LevelChunk chunk,
            FloraPalette.Entry entry,
            BlockPos.MutableBlockPos plantPos,
            BlockPos.MutableBlockPos groundPos,
            BlockState groundState,
            RandomSource random
    ) {
        if (!FloraPlacementRules.matchesEnvironment(level, chunk, entry.environment(), plantPos, groundPos, groundState)) {
            return false;
        }

        // Lichen belongs on a wall, and a wall is rarely at ankle height. Rather than sampling the
        // ground and giving up, walk a short way up the column looking for a face to cling to —
        // the difference between moss appearing on Hive walls and moss appearing almost never.
        if (entry.form() == FloraPalette.Form.LICHEN) {
            int baseY = plantPos.getY();

            for (int offset = 0; offset < LICHEN_CLIMB; offset++) {
                plantPos.setY(baseY + offset);

                if (plantPos.getY() >= chunk.getMaxBuildHeight()) {
                    break;
                }

                if (FloraPlacementRules.nearFunctionalBlock(chunk, plantPos)) {
                    continue;
                }

                if (FloraPlacementRules.place(level, chunk, entry, plantPos.immutable(), groundPos, groundState, random)) {
                    return true;
                }
            }

            plantPos.setY(baseY);
            return false;
        }

        if (!FloraPlacementRules.isFreeSpace(chunk, plantPos)) {
            return false;
        }

        if (FloraPlacementRules.nearFunctionalBlock(chunk, plantPos)) {
            return false;
        }

        return FloraPlacementRules.place(level, chunk, entry, plantPos.immutable(), groundPos, groundState, random);
    }

    /**
     * The tree step.
     *
     * <p>Trees run inside the same per-chunk pass as everything else: same deterministic
     * {@link RandomSource}, same exclusion zones, same budget. What is different is containment — a
     * canopy is several blocks wide, so candidate trunks are <b>inset</b> from the chunk edge by
     * {@link FloraTreeSpec#canopyRadius()}. A tree whose branches would cross the border is simply
     * not planted there, because planting it would mean writing into a neighbouring chunk and
     * pulling it into memory. Trees are sparse enough that the lost band is invisible; a canopy
     * chopped off flat at a chunk line would not be.
     */

    /**
     * Turns the surface block into whatever the region imposes, if it imposes one.
     *
     * <p>Only natural ground is converted — grass, dirt and podzol. Anything a player laid down, any
     * structure block, any ore is left exactly where it is: a region turning to ash should scour the
     * landscape, not eat a floor somebody built.
     *
     * @return 1 when a block was changed, 0 otherwise
     */
    private static int applyGroundOverride(
            ServerLevel level,
            LevelChunk chunk,
            FloraPalette palette,
            BlockPos.MutableBlockPos groundPos,
            BlockState groundState,
            RandomSource random
    ) {
        BlockState replacement = palette.groundOverride(random.nextFloat());

        if (replacement == null || groundState.is(replacement.getBlock())) {
            return 0;
        }

        if (!groundState.is(Blocks.GRASS_BLOCK)
                && !groundState.is(Blocks.DIRT)
                && !groundState.is(Blocks.PODZOL)
                && !groundState.is(Blocks.COARSE_DIRT)
                && !groundState.is(Blocks.MYCELIUM)) {
            return 0;
        }

        level.setBlock(groundPos.immutable(), replacement, Block.UPDATE_CLIENTS);
        return 1;
    }

    /** Outcome of the tree step: how many were planted, and how many blocks await a neighbour. */
    private record TreeResult(int planted, int deferred) {
    }

    private static TreeResult placeTrees(
            ServerLevel level,
            LevelChunk chunk,
            FloraChunkContext context,
            FloraPalette palette,
            FloraExclusionZones exclusions
    ) {
        return walkTrees(level, chunk, context, palette, DECORATOR_VERSION, exclusions, false);
    }

    /**
     * Reconstructs what an earlier pass planted here and takes it back out.
     *
     * <p>This is the step that lets a conquered region actually change hands: without it, Imperial
     * pines would still be standing among Ork fungal towers, because the ordinary flora clear is
     * limited to the {@code firstcrusade:flora} tag and trees are logs and leaves, not flora.
     *
     * <p>It works by replaying the previous pass's seed. Same palette, same version, same
     * coordinates give the same plan, so the decorator knows precisely which blocks it put down —
     * and removes only those, and only when the block still sitting there is the right species.
     * Nothing a player built is at risk, because nothing a player built is in the plan.
     */
    private static int clearPreviousTrees(
            ServerLevel level,
            LevelChunk chunk,
            FloraChunkContext context,
            FloraPalette previousPalette,
            int previousVersion,
            FloraExclusionZones exclusions
    ) {
        return walkTrees(level, chunk, context, previousPalette, previousVersion, exclusions, true).planted();
    }

    /**
     * One walk over a palette's trees, used for both planting and unplanting.
     *
     * <p>Sharing the walk is what makes the two operations line up: identical generator seed,
     * identical sequence of calls, identical positions. The world is only consulted <i>after</i>
     * each plan has been drawn, so a refusal — no ground, wrong palette, inside a settlement —
     * costs the same randomness as a success and cannot shift the trees that follow it.
     *
     * <p>Trunks are kept a couple of blocks inside the chunk, but canopies are free to reach over
     * the border into loaded neighbours. Insetting by the full canopy radius, which is what an
     * earlier version did, carves a visible grid of clearings along every chunk line as soon as
     * trees get dense.
     */
    private static TreeResult walkTrees(
            ServerLevel level,
            LevelChunk chunk,
            FloraChunkContext context,
            FloraPalette palette,
            int decoratorVersion,
            FloraExclusionZones exclusions,
            boolean clearing
    ) {
        FloraTreeSpec[] specs = palette.treeEntries();

        if (specs.length == 0) {
            return new TreeResult(0, 0);
        }

        ChunkPos chunkPos = chunk.getPos();

        RandomSource random = RandomSource.create(
                placementSeed(context.floraSeed(), chunkPos, palette, decoratorVersion) ^ TREE_SEED_SALT);

        float density = (float) (double) FloraConfig.TREE_FREQUENCY.get()
                * (float) (double) FloraConfig.VEGETATION_DENSITY.get();

        float totalWeight = 0.0F;
        for (FloraTreeSpec spec : specs) {
            totalWeight += spec.treesPerChunk();
        }

        float expected = totalWeight * density;
        int count = (int) expected;

        if (random.nextFloat() < expected - count) {
            count++;
        }

        if (count <= 0) {
            return new TreeResult(0, 0);
        }

        int span = 16 - TRUNK_INSET * 2;
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();

        int touched = 0;
        int deferred = 0;

        for (int i = 0; i < count; i++) {
            // Species first, so the generator is consumed the same way whatever happens next.
            FloraTreeSpec spec = pick(specs, random.nextFloat() * totalWeight);

            int worldX = minX + TRUNK_INSET + random.nextInt(span);
            int worldZ = minZ + TRUNK_INSET + random.nextInt(span);

            FloraTreePlan plan = spec.plan(worldX, worldZ, random);

            int groundY = FloraPlacementRules.groundY(chunk, worldX, worldZ);

            if (groundY == FloraPlacementRules.NO_GROUND) {
                continue;
            }

            if (clearing) {
                touched += spec.clear(level, chunk, plan, groundY + 1);
                continue;
            }

            if (exclusions.blocks(worldX, worldZ)) {
                continue;
            }

            // A tree is a big commitment, so it only goes where the column really belongs to this
            // palette — never in the blended band along a border.
            if (context.paletteAt(worldX, worldZ, groundY + 1) != palette) {
                continue;
            }

            int result = spec.place(level, chunk, plan, groundY + 1);

            if (result >= 0) {
                touched++;
                deferred += result;
            }
        }

        return new TreeResult(touched, deferred);
    }

    /** Weighted pick over the species of one palette. */
    private static FloraTreeSpec pick(FloraTreeSpec[] specs, float roll) {
        float remaining = roll;

        for (FloraTreeSpec spec : specs) {
            remaining -= spec.treesPerChunk();

            if (remaining < 0.0F) {
                return spec;
            }
        }

        return specs[specs.length - 1];
    }

    /**
     * The seed every placement in a chunk derives from.
     *
     * <p>World seed, chunk coordinates, palette and decorator version all go in. Changing any of
     * them changes the layout — which is exactly what should happen when a region changes hands or
     * the decorator's rules are revised — and changing none of them cannot.
     */
    public static long placementSeed(long floraSeed, ChunkPos chunkPos, FloraPalette palette,
                                     int decoratorVersion) {
        long seed = floraSeed;

        seed = seed * 6364136223846793005L + (chunkPos.x * 0x4F1BBCDDL);
        seed = seed * 6364136223846793005L + (chunkPos.z * 0x9E3779B9L);
        seed = seed * 6364136223846793005L + (palette.id() * 0x2545F491L);
        seed = seed * 6364136223846793005L + decoratorVersion;

        return seed;
    }
}
