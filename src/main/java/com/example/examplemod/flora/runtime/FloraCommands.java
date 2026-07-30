package com.example.examplemod.flora.runtime;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.flora.FloraTags;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Administrative commands for the vegetation system, under {@code /firstcrusade flora}.
 *
 * <p>Registered the same way the mod's other command trees are ({@code /fchive},
 * {@code /fcstrategy}): a {@link RegisterCommandsEvent} listener on the Forge bus.
 *
 * <p>Read-only subcommands ({@code inspect}, {@code stats}) are open to anyone, because they only
 * report. Everything that changes blocks — {@code decorate}, {@code redecorate},
 * {@code clearcustom} — requires permission level 2.
 *
 * <p>Nothing here broadcasts. Output goes to the caller only; statistics are never pushed to
 * players on a timer.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FloraCommands {
    private FloraCommands() {
    }

    /** Ceiling on {@code redecorate radius}, so a slip of the keyboard cannot flag half a world. */
    private static final int MAX_RADIUS_CHUNKS = 16;

    private static final String[] TRANSITIONS = {"burn", "recover", "chaos", "uncorrupt"};

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> flora = Commands.literal("flora")
                .then(Commands.literal("inspect").executes(context -> inspect(context.getSource())))
                .then(Commands.literal("stats").executes(context -> stats(context.getSource())))
                .then(Commands.literal("decorate")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> decorate(context.getSource())))
                .then(Commands.literal("clearcustom")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> clearCustom(context.getSource())))
                .then(Commands.literal("cleartrees")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> clearTrees(context.getSource(), 0))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("chunks", IntegerArgumentType.integer(0, MAX_RADIUS_CHUNKS))
                                        .executes(context -> clearTrees(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "chunks"))))))
                .then(Commands.literal("transition")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(TRANSITIONS, builder))
                                .executes(context -> transition(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "kind"), 0))
                                .then(Commands.argument("chunks", IntegerArgumentType.integer(0, MAX_RADIUS_CHUNKS))
                                        .executes(context -> transition(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "kind"),
                                                IntegerArgumentType.getInteger(context, "chunks"))))))
                .then(Commands.literal("redecorate")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> redecorate(context.getSource(), 0))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("chunks", IntegerArgumentType.integer(0, MAX_RADIUS_CHUNKS))
                                        .executes(context -> redecorate(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "chunks"))))));

        // A arvore "environment" e a face administrativa das DUAS camadas: worldgen (retrogen,
        // para mundos que nasceram antes das features) e runtime (a fila territorial).
        LiteralArgumentBuilder<CommandSourceStack> environment = Commands.literal("environment")
                .then(Commands.literal("inspect").executes(context -> inspect(context.getSource())))
                .then(Commands.literal("stats").executes(context -> stats(context.getSource())))
                .then(Commands.literal("retrogen")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("chunk")
                                .executes(context -> retrogen(context.getSource(), 0)))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("chunks", IntegerArgumentType.integer(0, MAX_RADIUS_CHUNKS))
                                        .executes(context -> retrogen(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "chunks")))))
                        .then(Commands.literal("loaded")
                                .executes(context -> retrogenLoaded(context.getSource()))));

        event.getDispatcher().register(Commands.literal("firstcrusade").then(flora).then(environment));
    }

    // ------------------------------------------------------------------ inspect

    /**
     * Everything the system currently believes about the caller's chunk: what it resolves to now,
     * what was actually applied, under which decorator version and territorial revision, and
     * whether it is waiting in the queue.
     */
    private static int inspect(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkPos pos = new ChunkPos(BlockPos.containing(source.getPosition()));

        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            source.sendFailure(Component.literal(
                    "First Crusade flora does not decorate " + level.dimension().location() + "."));
            return 0;
        }

        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        FloraChunkContext context = FloraRegionResolver.buildContext(level, pos, chunk);

        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);
        FloraChunkSavedData.ChunkState state = floraData.getState(pos);
        FloraChunkQueue queue = FloraChunkQueue.get(level);

        int currentRevision = WorldWarMapData.get(level).getTerritoryRevision();

        source.sendSuccess(() -> Component.literal("--- First Crusade flora: chunk " + pos.x + ", " + pos.z + " ---"), false);
        source.sendSuccess(() -> Component.literal("  dimension:        " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("  resolves to:      " + context.dominantPalette().name()
                + (context.isBorderChunk() ? " (border chunk, mixed)" : "")), false);
        source.sendSuccess(() -> Component.literal("  neutral fallback: " + context.neutralPalette().name()), false);
        source.sendSuccess(() -> Component.literal("  claims:           " + FloraRegionResolver.describeInfluences(context)), false);

        if (state == null) {
            source.sendSuccess(() -> Component.literal("  applied:          never decorated"), false);
        } else {
            source.sendSuccess(() -> Component.literal("  applied:          " + state.palette().name()), false);
            source.sendSuccess(() -> Component.literal("  decorator ver:    " + state.decoratorVersion()
                    + " (current " + FloraChunkDecorator.DECORATOR_VERSION + ")"), false);
            source.sendSuccess(() -> Component.literal("  revision:         " + state.revision()
                    + " (current " + currentRevision + ")"), false);
            source.sendSuccess(() -> Component.literal("  flags:            " + describeFlags(state)), false);
            source.sendSuccess(() -> Component.literal("  pending change:   " + state.transition().name()), false);
        }

        long battlefield = floraData.battlefieldTime(pos.toLong());

        if (battlefield >= 0L) {
            long age = level.getGameTime() - battlefield;
            source.sendSuccess(() -> Component.literal("  battlefield:      " + (age / 24000L) + " day(s) ago"), false);
        }

        if (floraData.isChaosCorrupted(pos.toLong())) {
            source.sendSuccess(() -> Component.literal("  chaos:            corrupted"), false);
        }

        source.sendSuccess(() -> Component.literal("  queue:            "
                + (queue.isQueued(pos) ? "waiting" : "not queued")
                + " (" + queue.pendingCount() + " pending, " + queue.intakeCount() + " in intake)"), false);

        if (chunk == null) {
            source.sendSuccess(() -> Component.literal("  note:             chunk is not loaded; palette resolved from world data only"), false);
        }

        return 1;
    }

    private static String describeFlags(FloraChunkSavedData.ChunkState state) {
        StringBuilder builder = new StringBuilder();

        if (state.has(FloraChunkSavedData.FLAG_DECORATED)) {
            builder.append("decorated ");
        }

        if (state.has(FloraChunkSavedData.FLAG_INCOMPLETE)) {
            builder.append("incomplete ");
        }

        if (state.has(FloraChunkSavedData.FLAG_DIRTY)) {
            builder.append("dirty ");
        }

        return builder.length() == 0 ? "none" : builder.toString().trim();
    }

    // ------------------------------------------------------------------ decorate

    /** Queues the caller's chunk, leaving any existing record alone. */
    private static int decorate(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkPos pos = new ChunkPos(BlockPos.containing(source.getPosition()));

        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            source.sendFailure(Component.literal("Not a decorated dimension."));
            return 0;
        }

        boolean queued = FloraChunkQueue.get(level).enqueueDirect(pos);

        source.sendSuccess(() -> Component.literal(queued
                ? "Queued chunk " + pos.x + ", " + pos.z + " for decoration."
                : "Chunk " + pos.x + ", " + pos.z + " was already queued."), false);

        return 1;
    }

    /**
     * Flags the caller's chunk — or a square of chunks around it — as changed, so the decorator
     * clears the old palette's flora and applies the current one.
     */
    private static int redecorate(CommandSourceStack source, int radiusChunks) {
        ServerLevel level = source.getLevel();

        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            source.sendFailure(Component.literal("Not a decorated dimension."));
            return 0;
        }

        BlockPos centre = BlockPos.containing(source.getPosition());

        // markRegion works in blocks; a radius of 0 chunks still has to cover the caller's own one.
        int blockRadius = radiusChunks * 16;

        int flagged = FloraTransitionManager.markRegion(
                level, centre, blockRadius, FloraChunkSavedData.Transition.CONQUEST);

        source.sendSuccess(() -> Component.literal(
                "Flagged " + flagged + " chunk(s) for redecoration; loaded ones are queued."), false);

        return flagged;
    }

    /**
     * Removes this mod's flora from the caller's chunk and nothing else — the removal is limited to
     * the {@code firstcrusade:flora} tag, so buildings, vanilla plants and player blocks are
     * untouched. The chunk's record is forgotten, so it will be decorated again from scratch the
     * next time it is queued.
     */
    private static int clearCustom(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkPos pos = new ChunkPos(BlockPos.containing(source.getPosition()));

        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);

        if (chunk == null) {
            source.sendFailure(Component.literal("Chunk " + pos.x + ", " + pos.z + " is not loaded."));
            return 0;
        }

        int removed = FloraPlacementRules.clearCustomFlora(level, chunk, Integer.MAX_VALUE);

        FloraChunkSavedData.get(level).forget(pos);

        source.sendSuccess(() -> Component.literal(
                "Removed " + removed + " First Crusade flora block(s) from chunk " + pos.x + ", " + pos.z + "."), false);

        return removed;
    }

    /**
     * Removes this mod's trees from the caller's chunk, or a square of chunks around it.
     *
     * <p>Separate from {@code clearcustom} because it is a different promise. That command sweeps
     * the {@code firstcrusade:flora} tag, which is grass and detail nobody builds with. This one
     * removes logs and canopies — solid blocks a player may well have built with — so it is never
     * part of ordinary redecoration and has to be asked for by name.
     *
     * <p>It also removes the chunks' decoration records, so the next pass replants from scratch.
     * That is what makes it useful for cleaning up after a bad build: whatever strays are standing,
     * this takes them out and the decorator puts a clean, deterministic set back.
     */
    private static int clearTrees(CommandSourceStack source, int radiusChunks) {
        ServerLevel level = source.getLevel();

        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            source.sendFailure(Component.literal("Not a decorated dimension."));
            return 0;
        }

        ChunkPos origin = new ChunkPos(BlockPos.containing(source.getPosition()));
        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);

        int removed = 0;
        int chunks = 0;
        int skipped = 0;

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                ChunkPos pos = new ChunkPos(origin.x + dx, origin.z + dz);

                // Never force a load: an unloaded chunk is simply left alone.
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);

                if (chunk == null) {
                    skipped++;
                    continue;
                }

                removed += FloraPlacementRules.clearMatching(level, chunk, Integer.MAX_VALUE, FloraTags.FLORA_TREE);
                floraData.forget(pos);
                chunks++;
            }
        }

        int finalRemoved = removed;
        int finalChunks = chunks;
        int finalSkipped = skipped;

        source.sendSuccess(() -> Component.literal(
                "Removed " + finalRemoved + " tree block(s) from " + finalChunks + " chunk(s)"
                        + (finalSkipped > 0 ? " (" + finalSkipped + " not loaded, skipped)" : "")
                        + "; those chunks will be replanted from scratch."), false);

        return removed;
    }

    /**
     * Applies one of the war's vegetation transformations by hand, so they can be seen without
     * waiting for the war to produce them.
     *
     * <p>These are the transitions the phase brief describes — fields put to the torch, ground
     * being reclaimed, Chaos taking hold. {@code chaos} in particular has no other trigger in the
     * mod today: there is no Chaos faction yet, so this command and the API behind it are the only
     * ways to reach that palette.
     */
    private static int transition(CommandSourceStack source, String kind, int radiusChunks) {
        ServerLevel level = source.getLevel();

        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            source.sendFailure(Component.literal("Not a decorated dimension."));
            return 0;
        }

        BlockPos centre = BlockPos.containing(source.getPosition());
        int blockRadius = radiusChunks * 16;

        int flagged;
        String described;

        switch (kind.toLowerCase(java.util.Locale.ROOT)) {
            case "burn" -> {
                FloraTransitionManager.onFieldsBurned(level, centre, blockRadius);
                flagged = chunksIn(radiusChunks);
                described = "burnt";
            }
            case "recover" -> {
                FloraTransitionManager.onTerritoryRecovered(level, centre, blockRadius);
                flagged = chunksIn(radiusChunks);
                described = "recovering";
            }
            case "chaos" -> {
                FloraTransitionManager.markChaosCorruption(level, centre, blockRadius, true);
                flagged = chunksIn(radiusChunks);
                described = "Chaos-corrupted";
            }
            case "uncorrupt" -> {
                FloraTransitionManager.markChaosCorruption(level, centre, blockRadius, false);
                flagged = chunksIn(radiusChunks);
                described = "cleansed of Chaos";
            }
            default -> {
                source.sendFailure(Component.literal(
                        "Unknown transition '" + kind + "'. Expected one of: burn, recover, chaos, uncorrupt."));
                return 0;
            }
        }

        int finalFlagged = flagged;
        String finalDescribed = described;

        source.sendSuccess(() -> Component.literal(
                "Marked roughly " + finalFlagged + " chunk(s) as " + finalDescribed
                        + "; loaded ones are queued, the rest change when they next load."), false);

        return flagged;
    }

    private static int chunksIn(int radiusChunks) {
        int side = radiusChunks * 2 + 1;
        return side * side;
    }

    /**
     * Adds the natural vegetation to chunks that were generated before the worldgen features
     * existed.
     *
     * <p>Retrogen is additive on purpose: it never clears anything first. A chunk that already has
     * its vegetation is left looking identical, because placement is deterministic — the same seed
     * writes the same plants into the same blocks. That is what makes running this twice safe, and
     * what stops it from duplicating a forest.
     *
     * <p>It respects every exclusion the ordinary decorator does, so it will not put a tree through
     * a roof, a plant on a road, or anything at all inside a registered city footprint.
     */
    private static int retrogen(CommandSourceStack source, int radiusChunks) {
        ServerLevel level = source.getLevel();

        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            source.sendFailure(Component.literal("Not a decorated dimension."));
            return 0;
        }

        ChunkPos origin = new ChunkPos(BlockPos.containing(source.getPosition()));
        FloraChunkQueue queue = FloraChunkQueue.get(level);

        int queued = 0;
        int skipped = 0;

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                ChunkPos pos = new ChunkPos(origin.x + dx, origin.z + dz);

                // Never force a load: an unloaded chunk keeps its mark and is done when it returns.
                if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                    skipped++;
                    continue;
                }

                if (queue.enqueueRetrogen(level, pos)) {
                    queued++;
                }
            }
        }

        int finalQueued = queued;
        int finalSkipped = skipped;

        source.sendSuccess(() -> Component.literal(
                "Retrogen queued for " + finalQueued + " chunk(s)"
                        + (finalSkipped > 0 ? " (" + finalSkipped + " not loaded, skipped)" : "")
                        + "; nearest to a player is done first."), false);

        return queued;
    }

    /**
     * Retrogen over the chunks currently around the players — the usual way to update an old save.
     *
     * <p>Walks the view-distance square of each player rather than asking the chunk map for its
     * whole contents: that collection is not public API, and a player's surroundings are the part
     * anyone is going to look at anyway. Chunks that are not actually loaded are skipped, never
     * loaded.
     */
    private static int retrogenLoaded(CommandSourceStack source) {
        ServerLevel level = source.getLevel();

        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            source.sendFailure(Component.literal("Not a decorated dimension."));
            return 0;
        }

        FloraChunkQueue queue = FloraChunkQueue.get(level);
        int view = Math.min(16, level.getServer().getPlayerList().getViewDistance());

        java.util.Set<Long> seen = new java.util.HashSet<>();
        int queued = 0;

        for (ServerPlayer player : level.players()) {
            int px = player.getBlockX() >> 4;
            int pz = player.getBlockZ() >> 4;

            for (int dx = -view; dx <= view; dx++) {
                for (int dz = -view; dz <= view; dz++) {
                    ChunkPos pos = new ChunkPos(px + dx, pz + dz);

                    if (!seen.add(pos.toLong())) {
                        continue;
                    }

                    if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                        continue;
                    }

                    if (queue.enqueueRetrogen(level, pos)) {
                        queued++;
                    }
                }
            }
        }

        int finalQueued = queued;

        source.sendSuccess(() -> Component.literal(
                "Retrogen queued for " + finalQueued + " loaded chunk(s) around "
                        + level.players().size() + " player(s)."), false);

        return queued;
    }

    // ------------------------------------------------------------------ stats

    private static int stats(CommandSourceStack source) {
        ServerLevel level = source.getLevel();

        if (!FloraRegionResolver.isDecoratedDimension(level)) {
            source.sendFailure(Component.literal("Not a decorated dimension."));
            return 0;
        }

        FloraChunkQueue.Stats stats = FloraChunkQueue.get(level).stats();
        FloraChunkSavedData floraData = FloraChunkSavedData.get(level);

        source.sendSuccess(() -> Component.literal("--- First Crusade flora: " + level.dimension().location() + " ---"), false);
        source.sendSuccess(() -> Component.literal("  queue:      " + stats.pending() + " pending, " + stats.intake() + " in intake"), false);
        source.sendSuccess(() -> Component.literal("  queued:     " + stats.queued() + " (re-queued " + stats.requeued() + ")"), false);
        source.sendSuccess(() -> Component.literal("  processed:  " + stats.processed()), false);
        source.sendSuccess(() -> Component.literal("  skipped:    " + stats.skipped()), false);
        source.sendSuccess(() -> Component.literal("  placed:     " + stats.placed() + " blocks"), false);
        source.sendSuccess(() -> Component.literal("  removed:    " + stats.removed() + " blocks"), false);
        source.sendSuccess(() -> Component.literal("  failed:     " + stats.placementFailures() + " attempts found no valid spot"), false);
        source.sendSuccess(() -> Component.literal("  recorded:   " + floraData.decoratedChunkCount() + " chunk(s)"), false);
        source.sendSuccess(() -> Component.literal("  battlefields: " + floraData.battlefieldCount()
                + ", chaos: " + floraData.chaosCount()), false);
        source.sendSuccess(() -> Component.literal("  territory:  " + FloraRegionResolver.countTerritorialSources(level)
                + " settlement(s), revision " + WorldWarMapData.get(level).getTerritoryRevision()), false);

        return 1;
    }
}
