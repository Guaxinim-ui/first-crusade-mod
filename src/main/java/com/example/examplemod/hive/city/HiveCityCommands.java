package com.example.examplemod.hive.city;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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

                // Spec §18. Riding a lift needs somebody to click it, which is the one thing a
                // headless test cannot do — so this asks the block's own question from a position.
                .then(Commands.literal("lift")
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates
                                .BlockPosArgument.blockPos())
                                .executes(ctx -> cmdLift(ctx, true))
                                .then(Commands.literal("up").executes(ctx -> cmdLift(ctx, true)))
                                .then(Commands.literal("down").executes(ctx -> cmdLift(ctx, false)))))
                .then(Commands.literal("status").executes(HiveCityCommands::cmdStatus))
                .then(Commands.literal("cancel").executes(HiveCityCommands::cmdCancel))
                .then(Commands.literal("preview").executes(HiveCityCommands::cmdPreview))
                .then(Commands.literal("generate")
                        .executes(ctx -> cmdGenerate(ctx, ctx.getSource().getLevel().getSeed()))
                        .then(arg("seed", LongArgumentType.longArg())
                                .executes(ctx -> cmdGenerate(ctx,
                                        LongArgumentType.getLong(ctx, "seed")))))

                // FASE 8/13 — cidade completa de teste (validação integrada).
                .then(Commands.literal("build_full_test").executes(HiveCityCommands::cmdBuildFullTest))
                .then(Commands.literal("full_test_status").executes(HiveCityCommands::cmdStatus))
                .then(Commands.literal("full_test_cancel").executes(HiveCityCommands::cmdCancel))
                .then(Commands.literal("full_test_clear").executes(HiveCityCommands::cmdFullTestClear))
                .then(Commands.literal("full_test_tp")
                        .executes(ctx -> cmdFullTestTp(ctx, "aerial"))
                        .then(Commands.argument("point", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        HiveFullCityTest.TP_POINTS, b))
                                .executes(ctx -> cmdFullTestTp(ctx,
                                        StringArgumentType.getString(ctx, "point")))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Long> arg(
            String name, LongArgumentType type) {
        return Commands.argument(name, type);
    }

    // ---- tp ----
    /**
     * Answers what a transit lift at this position would do, without anyone riding it.
     *
     * <p>Prints the level the position is on, the level the ride would target, and the landing the
     * block's own search returns — or says which check refused. Calls
     * {@link com.example.examplemod.hive.HiveTransitLiftBlock#findLanding} rather than repeating the
     * search, so what this reports is what a player would actually get.
     */
    private static int cmdLift(CommandContext<CommandSourceStack> ctx, boolean up) {
        CommandSourceStack src = ctx.getSource();
        BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument
                .getBlockPos(ctx, "pos");

        ServerLevel hive = src.getServer().getLevel(HiveWorld.LEVEL);

        if (hive == null) {
            src.sendFailure(Component.literal("hive_world dimension not found — datapack not loaded."));
            return 0;
        }

        HiveTier from = HiveTier.of(pos.getY());
        HiveTier to = up ? from.above() : from.below();

        src.sendSuccess(() -> Component.literal("y=" + pos.getY() + " -> nivel " + from.key()
                + " (piso y=" + from.baseY() + ", zona " + from.zoneKey() + ")"), false);

        if (to == null) {
            src.sendFailure(Component.literal("Sem nivel " + (up ? "acima" : "abaixo")
                    + " de " + from.key() + " — e o " + (up ? "topo" : "fundo") + " da Colmeia."));
            return 0;
        }

        BlockPos landing = com.example.examplemod.hive.HiveTransitLiftBlock
                .findLanding(hive, pos, to);

        if (landing == null) {
            src.sendFailure(Component.literal("Destino " + to.key() + " (piso y=" + to.baseY()
                    + ") sem desembarque livre em x=" + pos.getX() + " z=" + pos.getZ() + "."));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("Desembarque em " + to.key() + ": "
                + landing.getX() + " " + landing.getY() + " " + landing.getZ()
                + "  (desvio " + (landing.getY() - to.baseY()) + " do piso)"), false);

        return 1;
    }

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

        // Reset the persistent marker store (spec §11 process step 6) so a fresh build starts
        // clean — otherwise a re-generate with a new seed would leave stale instances/markers from
        // whatever city was here before.
        int[] box = HiveCityLayout.planBoundingBox(plan);
        BlockPos center = new BlockPos((box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2);
        HiveCityMarkerData.get(hive).resetForNewCity(seed, center, box);

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

    // ---- FASE 8/13 — cidade completa de teste ----

    private static int cmdBuildFullTest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel hive = src.getServer().getLevel(HiveWorld.LEVEL);
        if (hive == null) {
            src.sendFailure(Component.literal("hive_world dimension not found — datapack not loaded."));
            return 0;
        }
        HiveGenerationQueue queue = HiveGenerationQueue.get(hive);
        if (!queue.isEmpty()) {
            src.sendFailure(Component.literal("Já há uma cidade sendo construída ("
                    + queue.percent() + "%). Use /fchive city full_test_cancel primeiro."));
            return 0;
        }
        if (HiveClearQueue.get(hive).isActive()) {
            src.sendFailure(Component.literal("Um clear está em andamento. Aguarde ou use full_test_cancel."));
            return 0;
        }

        List<HiveCityLayout.PlacedDistrict> plan = HiveFullCityTest.plan();
        int[] box = HiveFullCityTest.cityBox();
        // Dry-run: registra o plano inteiro antes de colocar qualquer bloco (spec: não esconder erros).
        for (HiveCityLayout.PlacedDistrict pd : plan) {
            src.sendSystemMessage(Component.literal("  " + pd));
        }

        // Reset the persistent marker store (spec §11) for this fresh test-city build.
        BlockPos center = new BlockPos((box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2);
        HiveCityMarkerData.get(hive).resetForNewCity(HiveFullCityTest.TEST_SEED, center, box);

        HiveFullCityTest.enqueue(hive);

        // Teleporta para a plataforma de observação aérea.
        tpTo(src, hive, HiveFullCityTest.tpPoint("aerial"));

        int wx = box[3] - box[0] + 1, hy = box[4] - box[1] + 1, wz = box[5] - box[2] + 1;
        src.sendSuccess(() -> Component.literal(
                "Cidade completa de teste enfileirada: " + plan.size() + " distritos (seed "
                + HiveFullCityTest.TEST_SEED + ", raio " + HiveFullCityTest.TEST_RADIUS + ")."
                + " Área " + wx + "×" + hy + "×" + wz + " em [" + box[0] + "," + box[1] + "," + box[2]
                + "]..[" + box[3] + "," + box[4] + "," + box[5] + "]."
                + " Construindo " + HiveCityTicker.DISTRICTS_PER_TICK + " distrito/tick —"
                + " /fchive city full_test_status para progresso."), true);
        return 1;
    }

    private static int cmdFullTestClear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel hive = src.getServer().getLevel(HiveWorld.LEVEL);
        if (hive == null) {
            src.sendFailure(Component.literal("hive_world dimension not found."));
            return 0;
        }
        if (!HiveGenerationQueue.get(hive).isEmpty()) {
            src.sendFailure(Component.literal(
                    "Uma construção está em andamento. Use full_test_cancel antes de limpar."));
            return 0;
        }
        HiveClearQueue clear = HiveClearQueue.get(hive);
        if (clear.isActive()) {
            src.sendFailure(Component.literal("Já há um clear em andamento (" + clear.percent() + "%)."));
            return 0;
        }
        int[] b = HiveFullCityTest.startClear(hive);
        src.sendSuccess(() -> Component.literal(
                "Limpando a cidade de teste em lotes: box [" + b[0] + "," + b[1] + "," + b[2]
                + "]..[" + b[3] + "," + b[4] + "," + b[5] + "] a "
                + HiveClearQueue.BLOCKS_PER_TICK + " blocos/tick."), true);
        return 1;
    }

    private static int cmdFullTestTp(CommandContext<CommandSourceStack> ctx, String point) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel hive = src.getServer().getLevel(HiveWorld.LEVEL);
        if (hive == null) {
            src.sendFailure(Component.literal("hive_world dimension not found."));
            return 0;
        }
        float[] p = HiveFullCityTest.tpPoint(point);
        if (p == null) {
            src.sendFailure(Component.literal("Ponto desconhecido: " + point
                    + ". Válidos: " + String.join(", ", HiveFullCityTest.TP_POINTS)));
            return 0;
        }
        if (!tpTo(src, hive, p)) {
            src.sendFailure(Component.literal("Apenas um jogador pode teleportar."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Teleportado para: " + point), false);
        return 1;
    }

    /** Teleporta o jogador (se houver) para {x,y,z,yRot,xRot}. Retorna false sem jogador. */
    private static boolean tpTo(CommandSourceStack src, ServerLevel hive, float[] p) {
        if (p == null) return false;
        try {
            ServerPlayer player = src.getPlayerOrException();
            player.teleportTo(hive, p[0] + 0.5, p[1], p[2] + 0.5, p[3], p[4]);
            return true;
        } catch (Exception e) {
            return false;
        }
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
