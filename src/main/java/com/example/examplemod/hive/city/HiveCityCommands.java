package com.example.examplemod.hive.city;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The {@code /fchive city ...} command tree (permission level 2), self-contained so the existing
 * {@code HiveCommands} attaches it with a single line:
 *
 * <pre>
 *   // inside HiveCommands, where the /fchive root is built:
 *   root.then(HiveCityCommands.build());
 * </pre>
 *
 * Subcommands:
 * <ul>
 *   <li>{@code /fchive city tp} — teleport to the hive_world spawn.</li>
 *   <li>{@code /fchive city generate [seed]} — plan + enqueue the whole city. Prints the plan
 *       (dry-run log) and validates before a block is placed; the ticker then builds it staged.</li>
 *   <li>{@code /fchive city status} — placed / remaining / %.</li>
 *   <li>{@code /fchive city cancel} — clear the queue.</li>
 * </ul>
 */
public final class HiveCityCommands {
    private HiveCityCommands() {}

    /** Default city radius in rings (radius 2 → 5×5 = 25 districts; a solid test size). */
    public static final int DEFAULT_RADIUS = 2;

    /** Spawn position inside the hive: center street level, a couple blocks up. */
    private static final BlockPos HIVE_SPAWN = new BlockPos(0, HiveWorld.GROUND_Y + 4, 0);

    /** Isolated development pad for the 64x64 visual test sector. */
    private static final BlockPos PREVIEW_ORIGIN = new BlockPos(384, HiveWorld.GROUND_Y, 0);

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("city")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("tp").executes(HiveCityCommands::cmdTp))
                .then(Commands.literal("status").executes(HiveCityCommands::cmdStatus))
                .then(Commands.literal("cancel").executes(HiveCityCommands::cmdCancel))
                .then(Commands.literal("preview").executes(HiveCityCommands::cmdPreview))
                .then(Commands.literal("generate")
                        .executes(ctx -> cmdGenerate(ctx, ctx.getSource().getLevel().getSeed()))
                        .then(arg("seed", LongArgumentType.longArg())
                                .executes(ctx -> cmdGenerate(ctx,
                                        LongArgumentType.getLong(ctx, "seed")))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Long> arg(
            String name, LongArgumentType type) {
        return Commands.argument(name, type);
    }

    // ---- tp ----
    private static int cmdTp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        ServerLevel hive = server.getLevel(HiveWorld.LEVEL);
        if (hive == null) {
            src.sendFailure(Component.literal(
                    "hive_world dimension not found — is the datapack loaded? (data/firstcrusade/dimension/hive_world.json)"));
            return 0;
        }
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only a player can teleport."));
            return 0;
        }
        player.teleportTo(hive,
                HIVE_SPAWN.getX() + 0.5, HIVE_SPAWN.getY(), HIVE_SPAWN.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        src.sendSuccess(() -> Component.literal("Teleported to the Hive."), true);
        return 1;
    }


    // ---- visual test sector ----
    private static int cmdPreview(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel hive = src.getServer().getLevel(HiveWorld.LEVEL);
        if (hive == null) {
            src.sendFailure(Component.literal("hive_world dimension not found — datapack not loaded."));
            return 0;
        }

        boolean ok = HiveCityPlacer.place(hive, "firstcrusade:visual_test", PREVIEW_ORIGIN, 0);
        if (!ok) {
            src.sendFailure(Component.literal(
                    "Could not place firstcrusade:visual_test. Run /reload and check the module/template logs."));
            return 0;
        }

        try {
            ServerPlayer player = src.getPlayerOrException();
            player.teleportTo(hive,
                    PREVIEW_ORIGIN.getX() + 31.5,
                    PREVIEW_ORIGIN.getY() + 3,
                    PREVIEW_ORIGIN.getZ() - 10.5,
                    0.0F, 0.0F);
        } catch (Exception ignored) {
            // Console execution still places the sector; there is simply no player to teleport.
        }

        src.sendSuccess(() -> Component.literal(
                "Hive visual test placed at " + PREVIEW_ORIGIN.toShortString()
                + ". This sector uses all 48 new block concepts."), true);
        return 1;
    }

    // ---- generate ----
    private static int cmdGenerate(CommandContext<CommandSourceStack> ctx, long seed) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        ServerLevel hive = server.getLevel(HiveWorld.LEVEL);
        if (hive == null) {
            src.sendFailure(Component.literal("hive_world dimension not found — datapack not loaded."));
            return 0;
        }

        HiveGenerationQueue queue = HiveGenerationQueue.get(hive);
        if (!queue.isEmpty()) {
            src.sendFailure(Component.literal(
                    "A city is already being built (" + queue.percent()
                    + "%). Use /fchive city cancel first."));
            return 0;
        }

        // Query whether the optional spire district actually exists, so the layout can include it
        // only when present. The integrator points this at HiveDistricts' registry lookup.
        boolean spireRegistered = HiveCityPlacer_DistrictExists(HiveCityLayout.D_SPIRE);

        HiveCityLayout layout =
                new HiveCityLayout(seed, DEFAULT_RADIUS, true, spireRegistered);
        List<HiveCityLayout.PlacedDistrict> plan = layout.plan();

        // Step-2 dry-run: log the whole plan before placing anything.
        String desc = layout.describe(plan);
        for (String line : desc.split("\n")) {
            src.sendSystemMessage(Component.literal(line));
        }

        queue.enqueuePlan(seed, plan);
        src.sendSuccess(() -> Component.literal(
                "Queued " + plan.size() + " districts (seed " + seed
                + "). Building staged at " + HiveCityTicker.DISTRICTS_PER_TICK
                + " district(s)/tick. /fchive city status for progress."), true);
        return 1;
    }

    // ---- status ----
    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel hive = src.getServer().getLevel(HiveWorld.LEVEL);
        if (hive == null) {
            src.sendFailure(Component.literal("hive_world dimension not found."));
            return 0;
        }
        HiveGenerationQueue queue = HiveGenerationQueue.get(hive);
        if (queue.totalPlanned() == 0) {
            src.sendSuccess(() -> Component.literal("No city queued. /fchive city generate [seed]"), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal(
                "Hive City: " + queue.placedSoFar() + "/" + queue.totalPlanned()
                + " districts (" + queue.percent() + "%), "
                + queue.remaining() + " remaining, seed " + queue.seed()), false);
        return 1;
    }

    // ---- cancel ----
    private static int cmdCancel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel hive = src.getServer().getLevel(HiveWorld.LEVEL);
        if (hive == null) {
            src.sendFailure(Component.literal("hive_world dimension not found."));
            return 0;
        }
        HiveGenerationQueue queue = HiveGenerationQueue.get(hive);
        int remaining = queue.remaining();
        queue.cancel();
        src.sendSuccess(() -> Component.literal("Cancelled — cleared " + remaining
                + " pending districts. (Already-placed blocks remain.)"), true);
        return 1;
    }

    /**
     * INTEGRATOR: point this at HiveDistricts' "does this district id exist?" lookup so an optional
     * spire is only included when actually registered. Safe default returns false (spire skipped).
     */
    private static boolean HiveCityPlacer_DistrictExists(String districtId) {
        return com.example.examplemod.hive.HiveDistricts.get(
                new net.minecraft.resources.ResourceLocation(districtId)).isPresent();
    }
}
