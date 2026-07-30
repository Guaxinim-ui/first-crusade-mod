package com.example.examplemod.flora.runtime;

import com.example.examplemod.flora.FloraTags;
import com.example.examplemod.flora.block.FloraPlantBlock;
import com.example.examplemod.flora.block.FloraTallPlantBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Whether one particular block may become one particular plant, and how to actually put it there.
 *
 * <p>Everything in this class reads from the {@link LevelChunk} being decorated rather than from
 * the level. That is not a micro-optimisation, it is the rule that keeps decoration from cascading:
 * asking a {@code Level} for a block just outside the chunk silently loads the neighbour, which
 * loads its neighbours, and a routine that runs a few hundred times per chunk turns into a chunk
 * loading storm. Any candidate whose support would fall outside the chunk is simply skipped —
 * that column belongs to the neighbour's own decoration pass anyway.
 *
 * <p>Placements use {@link Block#UPDATE_CLIENTS} only. Decoration must not trigger neighbour
 * updates: several hundred of those per chunk would wake redstone, re-check supports and generally
 * cost more than the placement itself.
 */
public final class FloraPlacementRules {
    private FloraPlacementRules() {
    }

    /** Blocks written with clients notified, but no neighbour updates and no block-update chain. */
    private static final int DECORATION_FLAGS = Block.UPDATE_CLIENTS;

    /** How far below the heightmap to look for real ground before giving up on a column. */
    private static final int MAX_GROUND_DESCENT = 6;

    /** Returned by {@link #groundY} when a column has no usable surface. */
    public static final int NO_GROUND = Integer.MIN_VALUE;

    // ------------------------------------------------------------------ finding the surface

    /**
     * The Y of the solid ground block in a column, or {@link #NO_GROUND}.
     *
     * <p>Starts from the chunk's own heightmap and walks down past trees, snow and existing plants,
     * because the vanilla heightmap happily reports a forest canopy as "surface" — the same trap
     * {@code WorldGenPlacement.groundPlacement} exists to avoid, solved here without forcing the
     * chunk to generate, since by definition it already has.
     */
    public static int groundY(LevelChunk chunk, int worldX, int worldZ) {
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);

        int floor = Math.max(chunk.getMinBuildHeight() + 1, top - MAX_GROUND_DESCENT);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(worldX, top, worldZ);

        for (int y = top; y >= floor; y--) {
            cursor.setY(y);
            BlockState state = chunk.getBlockState(cursor);

            if (isGround(state)) {
                return y;
            }
        }

        return NO_GROUND;
    }

    /** Solid terrain only: not air, not fluid, not a tree, not something already replaceable. */
    private static boolean isGround(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }

        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return false;
        }

        return !state.canBeReplaced();
    }

    // ------------------------------------------------------------------ column-level vetoes

    /**
     * Reasons a column is unusable regardless of which plant was picked.
     *
     * <p>Farmland is refused outright. It is in {@code flora_ground_natural} so that a future
     * settlement-run agri district can plant on it deliberately, but the ambient decorator must
     * never touch it: farmland with nothing on it is almost always a player's field waiting to be
     * sown, and dropping a thistle in it is vandalism.
     */
    public static boolean isUnusableGround(LevelChunk chunk, BlockPos groundPos, BlockState groundState) {
        if (groundState.is(Blocks.FARMLAND)) {
            return true;
        }

        // A block with a block entity is a machine, a chest, a barracks — functional, and not to be
        // grown over. getBlockEntity on the owning chunk is a map lookup, not a world query.
        return chunk.getBlockEntity(groundPos) != null;
    }

    /** True when the target space is free for a plant to occupy. */
    public static boolean isFreeSpace(LevelChunk chunk, BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);

        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }

        // Never place into water, lava or the mod's toxic sludge.
        return state.getFluidState().isEmpty();
    }

    /**
     * Functional blocks that vegetation must not sit on or next to: rails, plates, beds and doors.
     * Checked over the target and its four horizontal neighbours so a plant never ends up in a
     * doorway. Neighbours outside the chunk are treated as clear — the far side is that chunk's
     * responsibility, and the exclusion rectangles already cover real structures from both sides.
     */
    public static boolean nearFunctionalBlock(LevelChunk chunk, BlockPos pos) {
        if (isFunctional(chunk.getBlockState(pos)) || isFunctional(chunk.getBlockState(pos.below()))) {
            return true;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            cursor.set(pos).move(direction);

            if (!inChunk(chunk, cursor)) {
                continue;
            }

            if (chunk.getBlockState(cursor).is(BlockTags.DOORS)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isFunctional(BlockState state) {
        return state.is(BlockTags.RAILS)
                || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.BUTTONS)
                || state.is(Blocks.LEVER);
    }

    /** True when the position lies inside the chunk being decorated. */
    public static boolean inChunk(LevelChunk chunk, BlockPos pos) {
        return (pos.getX() >> 4) == chunk.getPos().x
                && (pos.getZ() >> 4) == chunk.getPos().z
                && pos.getY() >= chunk.getMinBuildHeight()
                && pos.getY() < chunk.getMaxBuildHeight();
    }

    // ------------------------------------------------------------------ environment rules

    /**
     * Whether the column satisfies a palette entry's environment rule. Everything measured here is
     * local to the column already in hand — no extra chunk reads, no structure queries.
     */
    public static boolean matchesEnvironment(
            ServerLevel level,
            LevelChunk chunk,
            FloraPalette.Environment environment,
            BlockPos plantPos,
            BlockPos groundPos,
            BlockState groundState
    ) {
        return switch (environment) {
            case ANY -> true;
            case WET -> isDamp(chunk, groundPos, groundState);
            case DRY -> !isDamp(chunk, groundPos, groundState);
            case SHADED -> skyLight(level, plantPos) < FloraPalette.Environment.SHADE_THRESHOLD;
            case OPEN -> skyLight(level, plantPos) >= FloraPalette.Environment.SHADE_THRESHOLD;
        };
    }

    private static int skyLight(ServerLevel level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos);
    }

    /** Damp ground: soft wet terrain, or water in one of the four horizontal neighbours. */
    private static boolean isDamp(LevelChunk chunk, BlockPos groundPos, BlockState groundState) {
        if (groundState.is(Blocks.MUD)
                || groundState.is(Blocks.CLAY)
                || groundState.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || groundState.is(Blocks.MOSS_BLOCK)) {
            return true;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            cursor.set(groundPos).move(direction);

            if (!inChunk(chunk, cursor)) {
                continue;
            }

            if (!chunk.getBlockState(cursor).getFluidState().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------ placement

    /**
     * Attempts one plant. Every form checks its own survival conditions before writing anything, so
     * a refusal costs a few reads and nothing else.
     *
     * @return true when a block was actually placed
     */
    public static boolean place(
            ServerLevel level,
            LevelChunk chunk,
            FloraPalette.Entry entry,
            BlockPos plantPos,
            BlockPos groundPos,
            BlockState groundState,
            RandomSource random
    ) {
        Block block = entry.block().get();

        return switch (entry.form()) {
            case SMALL -> placeSmall(level, chunk, block, plantPos, groundState);
            case TALL -> placeTall(level, chunk, block, plantPos, groundState);
            case LICHEN -> placeLichen(level, chunk, block, plantPos, random);
            case CARPET -> placeCarpet(level, chunk, block, plantPos);
        };
    }

    private static boolean placeSmall(ServerLevel level, LevelChunk chunk, Block block,
                                      BlockPos plantPos, BlockState groundState) {
        if (!(block instanceof FloraPlantBlock plant)) {
            return false;
        }

        // The plant's own ground tag is authoritative — it is what a datapack edits to let a
        // species take root somewhere new.
        if (!groundState.is(plant.groundTag())) {
            return false;
        }

        BlockState state = plant.defaultBlockState();

        if (!state.canSurvive(level, plantPos)) {
            return false;
        }

        return level.setBlock(plantPos, state, DECORATION_FLAGS);
    }

    /**
     * Two-block plants are placed with {@link DoublePlantBlock#placeAt}, which writes both halves
     * with the correct {@code HALF} property. Placing only a lower half would leave a plant that
     * breaks itself the first time anything touches it, so both spaces are confirmed free first and
     * the upper space is confirmed to be inside the world.
     */
    private static boolean placeTall(ServerLevel level, LevelChunk chunk, Block block,
                                     BlockPos plantPos, BlockState groundState) {
        if (!(block instanceof FloraTallPlantBlock plant)) {
            return false;
        }

        if (!groundState.is(plant.groundTag())) {
            return false;
        }

        BlockPos upper = plantPos.above();

        if (upper.getY() >= chunk.getMaxBuildHeight()) {
            return false;
        }

        if (!isFreeSpace(chunk, plantPos) || !isFreeSpace(chunk, upper)) {
            return false;
        }

        BlockState state = plant.defaultBlockState();

        if (!state.canSurvive(level, plantPos)) {
            return false;
        }

        DoublePlantBlock.placeAt(level, state, plantPos, DECORATION_FLAGS);

        // Confirm both halves actually landed; a mod or a protection plugin may have vetoed one.
        return level.getBlockState(plantPos).is(plant) && level.getBlockState(upper).is(plant);
    }

    /**
     * Lichen clings to whichever face actually has something behind it. The faces are tried in a
     * rotated order so a wall does not end up with every patch facing the same way, and the
     * multiface state is built through the block's own {@code getStateForPlacement}, which is what
     * guarantees the face property matches the support that was found.
     *
     * <p>The spreader is never invoked: these blocks do not creep, by design.
     */
    private static boolean placeLichen(ServerLevel level, LevelChunk chunk, Block block,
                                       BlockPos plantPos, RandomSource random) {
        if (!(block instanceof MultifaceBlock lichen)) {
            return false;
        }

        BlockState current = chunk.getBlockState(plantPos);

        if (!current.isAir() && !current.canBeReplaced()) {
            return false;
        }

        int offset = random.nextInt(Direction.values().length);
        BlockPos.MutableBlockPos support = new BlockPos.MutableBlockPos();

        for (int i = 0; i < Direction.values().length; i++) {
            Direction direction = Direction.values()[(i + offset) % Direction.values().length];

            support.set(plantPos).move(direction);

            // A support outside this chunk would mean reading the neighbour. Skip: that face can be
            // grown from the other side, when that chunk is decorated.
            if (!inChunk(chunk, support)) {
                continue;
            }

            BlockState state = lichen.getStateForPlacement(current, chunk, plantPos, direction);

            if (state == null) {
                continue;
            }

            if (!state.canSurvive(level, plantPos)) {
                continue;
            }

            return level.setBlock(plantPos, state, DECORATION_FLAGS);
        }

        return false;
    }

    /**
     * Carpets need something solid directly underneath and nothing functional there. Vanilla's own
     * rule is only "the block below is not empty", which would happily let a carpet sit on a rail
     * or float over water, so the check here is stricter.
     */
    private static boolean placeCarpet(ServerLevel level, LevelChunk chunk, Block block, BlockPos plantPos) {
        if (!(block instanceof CarpetBlock carpet)) {
            return false;
        }

        BlockPos below = plantPos.below();

        if (!inChunk(chunk, below)) {
            return false;
        }

        BlockState belowState = chunk.getBlockState(below);

        if (belowState.isAir() || !belowState.getFluidState().isEmpty()) {
            return false;
        }

        if (isFunctional(belowState)) {
            return false;
        }

        // A sturdy top face, so the carpet is not floating over a fence post or a slab edge.
        if (!belowState.isFaceSturdy(chunk, below, Direction.UP)) {
            return false;
        }

        BlockState state = carpet.defaultBlockState();

        if (!state.canSurvive(level, plantPos)) {
            return false;
        }

        return level.setBlock(plantPos, state, DECORATION_FLAGS);
    }

    // ------------------------------------------------------------------ removal

    /**
     * Removes this mod's own flora from a chunk, on a budget, and touches nothing else.
     *
     * <p>The rule the phase brief insists on, and the reason this is tag-driven: only blocks in
     * {@code firstcrusade:flora} are removed. Player buildings, vanilla trees, vanilla crops and
     * anything solid are all invisible to this method — a region changing hands must never cost
     * anyone their house or their orchard.
     *
     * <p>Tall plants are cleared from their lower half so {@code DoublePlantBlock} takes the upper
     * half with it; an upper half met on its own is cleared directly, which covers the odd orphan
     * left by an earlier crash.
     *
     * @param budget maximum blocks to remove; the caller charges these against the tick budget
     * @return how many were removed
     */
    public static int clearCustomFlora(ServerLevel level, LevelChunk chunk, int budget) {
        return clearMatching(level, chunk, budget, FloraTags.FLORA);
    }

    /**
     * As {@link #clearCustomFlora}, but limited to one tag — used by the transitions, which strip
     * (say) Ork fungus without disturbing the ground detail that was already there.
     */
    public static int clearMatching(ServerLevel level, LevelChunk chunk, int budget, TagKey<Block> tag) {
        if (budget <= 0) {
            return 0;
        }

        int removed = 0;

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16 && removed < budget; x++) {
            for (int z = 0; z < 16 && removed < budget; z++) {
                int worldX = minX + x;
                int worldZ = minZ + z;

                // Flora only ever sits at or just above the surface, so there is no reason to scan
                // the full column height — a handful of blocks around the surface is enough and
                // keeps this from becoming a world scan.
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
                int from = Math.min(chunk.getMaxBuildHeight() - 1, top + 3);
                int to = Math.max(chunk.getMinBuildHeight(), top - MAX_GROUND_DESCENT - 2);

                for (int y = from; y >= to && removed < budget; y--) {
                    cursor.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(cursor);

                    if (!state.is(tag)) {
                        continue;
                    }

                    if (state.getBlock() instanceof DoublePlantBlock
                            && state.getValue(DoublePlantBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                        // Leave it to the lower half if that is still there; otherwise clear the orphan.
                        BlockState lower = chunk.getBlockState(cursor.below());

                        if (lower.is(state.getBlock())) {
                            continue;
                        }
                    }

                    level.setBlock(cursor.immutable(), Blocks.AIR.defaultBlockState(), DECORATION_FLAGS);
                    removed++;
                }
            }
        }

        return removed;
    }
}
