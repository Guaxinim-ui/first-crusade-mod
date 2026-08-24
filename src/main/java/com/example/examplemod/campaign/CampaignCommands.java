package com.example.examplemod.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.examplemod.WorldSettlementData;
import com.example.examplemod.StrategicResourceType;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.campaign.force.StrategicDeployment;
import com.example.examplemod.campaign.operation.Operation;
import com.example.examplemod.campaign.operation.OperationManager;
import com.example.examplemod.campaign.operation.OperationType;
import com.example.examplemod.campaign.planet.PlanetWarState;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.supply.SupplyNetwork;
import com.example.examplemod.campaign.supply.SupplyRoute;
import com.example.examplemod.campaign.war.CrusadeScore;
import com.example.examplemod.campaign.war.WarFaction;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * The campaign's half of {@code /fcstrategy}.
 *
 * <h2>Built as branches, not registered separately</h2>
 *
 * These are returned as {@link LiteralArgumentBuilder}s and attached to the existing
 * {@code /fcstrategy} tree by {@code StrategicWarAIEvents}, rather than registering a command of
 * their own. Two roots for one subject is how a debug surface becomes something you have to
 * remember rather than something you can explore.
 *
 * <h2>Every one of these answers a question the log cannot</h2>
 *
 * A debug command that only prints what a log line already printed is a command nobody runs. Each of
 * these exists because something about the campaign is otherwise invisible: what a front's control
 * actually is between passes, why a sector has not flipped ({@code contest} is the answer, and it is
 * on every sector line), and whether a planet was ever seeded at all.
 */
public final class CampaignCommands {

    private CampaignCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> FRONT_NAMES =
            (context, builder) -> SharedSuggestionProvider.suggest(CampaignFronts.names(), builder);

    private static final SuggestionProvider<CommandSourceStack> FACTION_NAMES =
            (context, builder) -> {
                List<String> names = new ArrayList<>();
                for (WarFaction faction : WarFaction.values()) {
                    names.add(faction.name().toLowerCase(java.util.Locale.ROOT));
                }
                return SharedSuggestionProvider.suggest(names, builder);
            };

    // ====================================================================================
    // /fcstrategy planet ...
    // ====================================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> planet() {
        return Commands.literal("planet")

                .then(Commands.literal("list")
                        .executes(context -> listPlanets(context.getSource())))

                .then(Commands.literal("status")
                        // No argument: the world the caller is standing on, which is what somebody
                        // typing this in the middle of a war actually wants.
                        .executes(context -> planetStatus(context.getSource(),
                                context.getSource().getLevel()))
                        .then(Commands.argument("front", StringArgumentType.word())
                                .suggests(FRONT_NAMES)
                                .executes(context -> {
                                    CampaignFront front = requireFront(context);
                                    if (front == null) {
                                        return 0;
                                    }
                                    return planetStatusOf(context.getSource(), front);
                                })))

                .then(Commands.literal("activate")
                        .executes(context -> activate(context.getSource(),
                                context.getSource().getLevel()))
                        .then(Commands.argument("front", StringArgumentType.word())
                                .suggests(FRONT_NAMES)
                                .executes(context -> {
                                    CampaignFront front = requireFront(context);
                                    if (front == null) {
                                        return 0;
                                    }

                                    ServerLevel level = context.getSource().getServer()
                                            .getLevel(front.dimension());

                                    if (level == null) {
                                        context.getSource().sendFailure(Component.literal(
                                                "Dimensão " + front.path() + " não existe nesta instalação."));
                                        return 0;
                                    }

                                    return activate(context.getSource(), level);
                                })))

                // The awakening only climbs with a player standing on the tomb world, which makes
                // the one system with a hundred-point clock the hardest thing in the campaign to
                // watch. This winds it forward.
                .then(Commands.literal("awaken")
                        .then(Commands.argument("amount",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                .executes(context -> awaken(context.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                                .getInteger(context, "amount")))))

                .then(Commands.literal("reseed")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerLevel level = source.getLevel();

                            boolean cleared = WorldSettlementData.get(level)
                                    .clearPlanetSeeded(level.dimension());

                            source.sendSuccess(() -> Component.literal(cleared
                                    ? "Marca de povoamento de " + level.dimension().location().getPath()
                                            + " apagada: a próxima chegada gera assentamentos de novo."
                                    : "Este planeta ainda não estava marcado como povoado."), true);

                            return 1;
                        }))

                .then(Commands.literal("reset")
                        .then(Commands.argument("front", StringArgumentType.word())
                                .suggests(FRONT_NAMES)
                                .executes(context -> {
                                    CampaignFront front = requireFront(context);
                                    if (front == null) {
                                        return 0;
                                    }

                                    CommandSourceStack source = context.getSource();
                                    boolean removed = CampaignData.get(source.getLevel()).resetFront(front);

                                    source.sendSuccess(() -> Component.literal(removed
                                            ? "Frente " + front.path() + " apagada: será remontada na próxima ativação."
                                            : "Frente " + front.path() + " não tinha estado nenhum."), true);

                                    return 1;
                                })));
    }

    private static int listPlanets(CommandSourceStack source) {
        CampaignData campaign = CampaignData.get(source.getLevel());
        WorldSettlementData settlements = WorldSettlementData.get(source.getLevel());
        WorldWarMapData warMap = WorldWarMapData.get(source.getLevel());

        source.sendSuccess(() -> Component.literal("=== Frentes da Cruzada ===")
                .withStyle(ChatFormatting.GOLD), false);

        for (CampaignFront front : CampaignFronts.all()) {
            PlanetWarState state = campaign.existingState(front.id());

            boolean loaded = source.getServer().getLevel(front.dimension()) != null;
            boolean seeded = settlements.isPlanetSeeded(front.dimension());

            String control = state == null
                    ? "—"
                    : state.imperialControl() + "/" + state.enemyControl() + "/" + state.contestedControl();

            String line = String.format("%-20s %-16s IMP/INI/DIS %-12s cid %d camp %d%s%s",
                    front.path(),
                    state == null ? "AVAILABLE" : state.state().name(),
                    control,
                    warMap.countCities(front.dimension()),
                    warMap.countCamps(front.dimension()),
                    seeded ? "" : "  [não povoado]",
                    loaded ? "" : "  [dim ausente]");

            ChatFormatting colour = state == null ? ChatFormatting.DARK_GRAY : state.state().colour();
            source.sendSuccess(() -> Component.literal(line).withStyle(colour), false);
        }

        int score = CrusadeScore.targetFor(campaign);

        source.sendSuccess(() -> Component.literal(
                "Crusade Score: " + score + "  |  conquistados " + CrusadeScore.conqueredFronts(campaign)
                        + "  perdidos " + CrusadeScore.lostFronts(campaign)).withStyle(ChatFormatting.AQUA), false);

        return 1;
    }

    private static int planetStatus(CommandSourceStack source, ServerLevel level) {
        return CampaignFronts.byDimension(level.dimension())
                .map(front -> planetStatusOf(source, front))
                .orElseGet(() -> {
                    source.sendFailure(Component.literal(
                            "Este mundo não é uma frente da Cruzada. Use /fcstrategy planet status <frente>."));
                    return 0;
                });
    }

    private static int planetStatusOf(CommandSourceStack source, CampaignFront front) {
        CampaignData campaign = CampaignData.get(source.getLevel());
        PlanetWarState state = campaign.existingState(front.id());

        source.sendSuccess(() -> Component.literal("=== " + front.path().toUpperCase(java.util.Locale.ROOT)
                + " ===").withStyle(ChatFormatting.GOLD), false);

        if (state == null) {
            source.sendSuccess(() -> Component.literal(
                    "Frente ainda não iniciada. Ative com /fcstrategy planet activate "
                            + front.path() + "."), false);
            return 1;
        }

        List<StrategicSector> sectors = campaign.sectorsOn(front.dimension());

        source.sendSuccess(() -> Component.literal("Estado: " + state.state().name())
                .withStyle(state.state().colour()), false);
        source.sendSuccess(() -> Component.literal("Intensidade: " + state.intensity().name())
                .withStyle(state.intensity().colour()), false);
        source.sendSuccess(() -> Component.literal(
                "Imperium " + state.imperialControl() + "%  |  Orks " + state.orkControl()
                        + "%  |  Necrons " + state.necronControl() + "%  |  Contestado "
                        + state.contestedControl() + "%"), false);
        source.sendSuccess(() -> Component.literal("Setores: " + sectors.size()
                + "  (âncora " + state.anchor().getX() + ", " + state.anchor().getZ() + ")"), false);

        if (!state.objectiveKey().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Objetivo: ")
                    .append(state.objective()), false);
        }

        if (front.dimension().equals(com.example.examplemod.planet.FCPlanets.NECRON_TOMB_WORLD)) {
            source.sendSuccess(() -> Component.literal(
                    "Despertar Necron: " + state.necronAwakening() + "%")
                    .withStyle(ChatFormatting.AQUA), false);
        }

        if (!state.lastEvent().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Último evento: " + state.lastEvent())
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static int awaken(CommandSourceStack source, int amount) {
        CampaignFront tomb = CampaignFronts.byDimension(
                com.example.examplemod.planet.FCPlanets.NECRON_TOMB_WORLD).orElse(null);

        if (tomb == null) {
            source.sendFailure(Component.literal("O mundo-tumba não existe nesta instalação."));
            return 0;
        }

        CampaignData campaign = CampaignData.get(source.getLevel());
        PlanetWarState state = campaign.stateOf(tomb);

        List<PlanetWarState.NecronStage> crossed = state.raiseNecronAwakening(amount);
        campaign.setDirty();

        source.sendSuccess(() -> Component.literal("Despertar Necron: " + state.necronAwakening()
                + "%  (estágio " + state.necronStage().name() + ")"
                + (crossed.isEmpty() ? "" : "  cruzou " + crossed.size() + " estágio(s)"))
                .withStyle(ChatFormatting.AQUA), true);

        return 1;
    }

    private static int activate(CommandSourceStack source, ServerLevel level) {
        CampaignFront front = CampaignFronts.byDimension(level.dimension()).orElse(null);

        if (front == null) {
            source.sendFailure(Component.literal("Este mundo não é uma frente da Cruzada."));
            return 0;
        }

        CampaignData campaign = CampaignData.get(level);
        int created = campaign.activateFront(front, level.getSharedSpawnPos());

        List<StrategicSector> sectors = campaign.sectorsOn(front.dimension());
        PlanetWarState state = campaign.stateOf(front);
        state.recompute(sectors, front);
        state.refreshObjective(sectors);
        campaign.setDirty();

        source.sendSuccess(() -> Component.literal(created > 0
                ? front.path() + ": " + created + " setor(es) criados."
                : front.path() + ": já estava ativa (" + sectors.size() + " setores)."), true);

        return 1;
    }

    // ====================================================================================
    // /fcstrategy sector ...
    // ====================================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> sector() {
        return Commands.literal("sector")

                .then(Commands.literal("list")
                        .executes(context -> listSectors(context.getSource(),
                                context.getSource().getLevel()))
                        .then(Commands.argument("front", StringArgumentType.word())
                                .suggests(FRONT_NAMES)
                                .executes(context -> {
                                    CampaignFront front = requireFront(context);
                                    if (front == null) {
                                        return 0;
                                    }
                                    return listSectorsOf(context.getSource(), front);
                                })))

                .then(Commands.literal("capture")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        CampaignData.get(context.getSource().getLevel()).allSectors()
                                                .stream().map(StrategicSector::id).toList(),
                                        builder))
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests(FACTION_NAMES)
                                        .executes(context -> capture(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "faction"))))));
    }

    private static int listSectors(CommandSourceStack source, ServerLevel level) {
        return CampaignFronts.byDimension(level.dimension())
                .map(front -> listSectorsOf(source, front))
                .orElseGet(() -> {
                    source.sendFailure(Component.literal(
                            "Este mundo não é uma frente. Use /fcstrategy sector list <frente>."));
                    return 0;
                });
    }

    private static int listSectorsOf(CommandSourceStack source, CampaignFront front) {
        List<StrategicSector> sectors = CampaignData.get(source.getLevel())
                .sectorsOn(front.dimension());

        if (sectors.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    front.path() + " não tem setores. Ative com /fcstrategy planet activate "
                            + front.path() + "."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("=== Setores: " + front.path() + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        for (StrategicSector sector : sectors) {
            source.sendSuccess(() -> Component.literal(sector.id() + "  " + sector.shortText())
                    .withStyle(sector.owner().colour()), false);
        }

        return 1;
    }

    private static int capture(CommandSourceStack source, String id, String factionName) {
        ServerLevel level = source.getLevel();
        StrategicSector sector = CampaignData.get(level).sector(id);

        if (sector == null) {
            source.sendFailure(Component.literal("Setor desconhecido: " + id));
            return 0;
        }

        WarFaction faction = WarFaction.fromName(factionName);
        WarFaction before = sector.owner();

        if (!PlanetCampaignManager.captureSector(level, sector, faction)) {
            source.sendSuccess(() -> Component.literal(
                    id + " já pertence a " + faction.name() + "."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
                id + ": " + before.name() + " -> " + sector.owner().name()), true);

        return 1;
    }

    // ====================================================================================
    // /fcstrategy operation ...
    // ====================================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> operation() {
        return Commands.literal("operation")

                .then(Commands.literal("list")
                        .executes(context -> listOperations(context.getSource(),
                                context.getSource().getLevel()))
                        .then(Commands.argument("front", StringArgumentType.word())
                                .suggests(FRONT_NAMES)
                                .executes(context -> {
                                    CampaignFront front = requireFront(context);
                                    if (front == null) {
                                        return 0;
                                    }
                                    return listOperationsOf(context.getSource(), front);
                                })))

                // Forces an order out of the generator instead of waiting for the pass to choose
                // one. Without it, testing a type means standing on a planet until the campaign
                // happens to pick it.
                .then(Commands.literal("create")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    List<String> names = new ArrayList<>();
                                    for (OperationType type : OperationType.values()) {
                                        names.add(type.name().toLowerCase(java.util.Locale.ROOT));
                                    }
                                    return SharedSuggestionProvider.suggest(names, builder);
                                })
                                .executes(context -> createOperation(context.getSource(),
                                        OperationType.fromName(
                                                StringArgumentType.getString(context, "type"))))))

                .then(Commands.literal("complete")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        CampaignData.get(context.getSource().getLevel())
                                                .allOperations().stream()
                                                .filter(Operation::isActive)
                                                .map(Operation::id).toList(),
                                        builder))
                                .executes(context -> completeOperation(context.getSource(),
                                        StringArgumentType.getString(context, "id")))));
    }

    private static int listOperations(CommandSourceStack source, ServerLevel level) {
        return CampaignFronts.byDimension(level.dimension())
                .map(front -> listOperationsOf(source, front))
                .orElseGet(() -> {
                    source.sendFailure(Component.literal(
                            "Este mundo não é uma frente. Use /fcstrategy operation list <frente>."));
                    return 0;
                });
    }

    private static int listOperationsOf(CommandSourceStack source, CampaignFront front) {
        List<Operation> operations = CampaignData.get(source.getLevel()).operationsOn(front.id());

        if (operations.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    front.path() + " não tem operações no momento."), false);
            return 1;
        }

        long gameTime = source.getLevel().getGameTime();

        source.sendSuccess(() -> Component.literal("=== Operações: " + front.path() + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        for (Operation operation : operations) {
            long left = operation.ticksRemaining(gameTime);

            String clock = left < 0 ? "" : "  " + (left / 20) + "s";

            source.sendSuccess(() -> Component.literal(operation.id() + "  " + operation.shortText()
                    + clock + "  [" + operation.reward().shortText() + "]")
                    .withStyle(operation.state().colour()), false);
        }

        return 1;
    }

    private static int createOperation(CommandSourceStack source, OperationType type) {
        ServerLevel level = source.getLevel();
        CampaignFront front = CampaignFronts.byDimension(level.dimension()).orElse(null);

        if (front == null) {
            source.sendFailure(Component.literal("Este mundo não é uma frente da Cruzada."));
            return 0;
        }

        Operation created = OperationManager.issueByHand(level, front, type);

        if (created == null) {
            source.sendFailure(Component.literal(
                    "Não foi possível criar: a frente não tem setores, ou o tipo " + type.name()
                            + " ainda não tem gatilho ligado (ver OperationTrigger.MANUAL)."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Operação criada: " + created.id() + "  " + created.shortText()), true);

        return 1;
    }

    private static int completeOperation(CommandSourceStack source, String id) {
        ServerLevel level = source.getLevel();

        if (!OperationManager.completeByHand(level, id)) {
            source.sendFailure(Component.literal(
                    "Operação desconhecida ou já encerrada: " + id));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Operação " + id + " concluída e paga."), true);
        return 1;
    }

    // ====================================================================================
    // /fcstrategy supply ...
    // ====================================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> supply() {
        return Commands.literal("supply")

                .then(Commands.literal("list")
                        .executes(context -> listSupply(context.getSource(), null))
                        .then(Commands.argument("front", StringArgumentType.word())
                                .suggests(FRONT_NAMES)
                                .executes(context -> {
                                    CampaignFront front = requireFront(context);
                                    if (front == null) {
                                        return 0;
                                    }
                                    return listSupply(context.getSource(), front);
                                })))

                // What a front actually receives, which is the number that decides whether holding
                // it is paying for itself.
                .then(Commands.literal("income")
                        .executes(context -> supplyIncome(context.getSource(),
                                context.getSource().getLevel()))
                        .then(Commands.argument("front", StringArgumentType.word())
                                .suggests(FRONT_NAMES)
                                .executes(context -> {
                                    CampaignFront front = requireFront(context);
                                    if (front == null) {
                                        return 0;
                                    }
                                    return supplyIncomeOf(context.getSource(), front);
                                })));
    }

    private static int listSupply(CommandSourceStack source, CampaignFront front) {
        CampaignData campaign = CampaignData.get(source.getLevel());

        List<SupplyRoute> routes = front == null
                ? new ArrayList<>(campaign.supplyRoutes())
                : SupplyNetwork.routesTouching(campaign, front.id());

        if (routes.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "Nenhuma rota ainda. Rode /fcstrategy war tick para calcular a rede."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("=== Rotas logísticas"
                + (front == null ? "" : ": " + front.path()) + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        for (SupplyRoute route : routes) {
            source.sendSuccess(() -> route.shortText().copy()
                    .withStyle(route.state().colour()), false);
        }

        // Counted over what was printed, not over the whole network: with a front filter the two
        // differ, and the global number under a filtered list reads as a claim about the lines above.
        int broken = SupplyNetwork.brokenLanes(routes);

        source.sendSuccess(() -> Component.literal(routes.size() + " rota(s), " + broken
                + " sem entrega").withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    private static int supplyIncome(CommandSourceStack source, ServerLevel level) {
        return CampaignFronts.byDimension(level.dimension())
                .map(front -> supplyIncomeOf(source, front))
                .orElseGet(() -> {
                    source.sendFailure(Component.literal(
                            "Este mundo não é uma frente. Use /fcstrategy supply income <frente>."));
                    return 0;
                });
    }

    private static int supplyIncomeOf(CommandSourceStack source, CampaignFront front) {
        CampaignData campaign = CampaignData.get(source.getLevel());

        Map<StrategicResourceType, Integer> produced =
                SupplyNetwork.produceOn(campaign.sectorsOn(front.dimension()));
        Map<StrategicResourceType, Integer> received = SupplyNetwork.incoming(campaign, front.id());

        source.sendSuccess(() -> Component.literal("=== Logística: " + front.path() + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal("Produz: " + describe(produced)), false);
        source.sendSuccess(() -> Component.literal("Recebe: " + describe(received)), false);

        return 1;
    }

    private static String describe(Map<StrategicResourceType, Integer> amounts) {
        if (amounts.isEmpty()) {
            return "nada";
        }

        StringBuilder text = new StringBuilder();

        for (Map.Entry<StrategicResourceType, Integer> entry : amounts.entrySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(entry.getValue()).append(' ').append(entry.getKey().getDisplayName());
        }

        return text.toString();
    }

    // ====================================================================================
    // /fcstrategy raid ...
    // ====================================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> raid() {
        return Commands.literal("raid")

                // The forces on the move, both sides. The one view that answers "why is nothing
                // happening" — a deployment stuck MUSTERING reads very differently from none at all.
                .then(Commands.literal("list")
                        .executes(context -> listDeployments(context.getSource(),
                                context.getSource().getLevel())))

                // Fills the WAAAGH! pool so the next pass launches, instead of waiting out the
                // build-up to find out whether launching works.
                .then(Commands.literal("start")
                        .executes(context -> forceRaid(context.getSource())));
    }

    private static int listDeployments(CommandSourceStack source, ServerLevel level) {
        CampaignFront front = CampaignFronts.byDimension(level.dimension()).orElse(null);

        if (front == null) {
            source.sendFailure(Component.literal("Este mundo não é uma frente da Cruzada."));
            return 0;
        }

        List<StrategicDeployment> deployments =
                CampaignData.get(level).deploymentsOn(front.id());

        PlanetWarState state = CampaignData.get(level).existingState(front.id());

        source.sendSuccess(() -> Component.literal("=== Forças em movimento: " + front.path()
                + " ===").withStyle(ChatFormatting.GOLD), false);

        if (state != null) {
            source.sendSuccess(() -> Component.literal("WAAAGH! acumulado: "
                    + state.waaaghBuildUp() + "/" + CampaignConfig.orkRaidThreshold())
                    .withStyle(ChatFormatting.GREEN), false);
        }

        if (deployments.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nenhuma força em movimento."), false);
            return 1;
        }

        for (StrategicDeployment deployment : deployments) {
            source.sendSuccess(() -> Component.literal(deployment.shortText())
                    .withStyle(deployment.state().colour()), false);
        }

        return 1;
    }

    private static int forceRaid(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CampaignFront front = CampaignFronts.byDimension(level.dimension()).orElse(null);

        if (front == null) {
            source.sendFailure(Component.literal("Este mundo não é uma frente da Cruzada."));
            return 0;
        }

        CampaignData campaign = CampaignData.get(level);
        PlanetWarState state = campaign.stateOf(front);

        // Asked before the pool is filled, because every one of these makes the pass below do
        // nothing whatsoever. Reporting success anyway is the failure mode this command was most
        // able to produce: on a world where nobody has built a camp yet — which is every fresh save —
        // it filled the pool, announced a pass, and left an empty raid list with no reason given.
        String blocker = com.example.examplemod.campaign.war.OrkOffensiveManager.launchBlocker(
                campaign, WorldWarMapData.get(level), front, state,
                campaign.sectorsOn(front.dimension()));

        if (blocker != null) {
            source.sendFailure(Component.literal(
                    "Nada seria lançado em " + front.path() + ": " + blocker + "."));
            return 0;
        }

        int threshold = CampaignConfig.orkRaidThreshold();
        state.addWaaaghBuildUp(threshold, threshold * 2);
        campaign.setDirty();

        // Run the pass now rather than waiting for the interval, so the launch is visible in the
        // same breath as the command that asked for it.
        PlanetCampaignManager.runStrategicPass(source.getServer());

        source.sendSuccess(() -> Component.literal(
                "WAAAGH! de " + front.path() + " enchida e passe executado. Ver /fcstrategy raid list."),
                true);

        return 1;
    }

    // ====================================================================================
    // /fcstrategy war ...
    // ====================================================================================

    public static LiteralArgumentBuilder<CommandSourceStack> war() {
        return Commands.literal("war")

                .then(Commands.literal("tick")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            PlanetCampaignManager.runStrategicPass(source.getServer());

                            source.sendSuccess(() -> Component.literal(
                                    "Passe estratégico da campanha executado."), true);
                            return 1;
                        }))

                .then(Commands.literal("score")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            CampaignData campaign = CampaignData.get(source.getLevel());

                            source.sendSuccess(() -> Component.literal(
                                    "Crusade Score (alvo): " + CrusadeScore.targetFor(campaign)
                                            + "  |  Dominion atual: "
                                            + com.example.examplemod.WarDominionData
                                                    .get(source.getLevel()).getDominion()), false);
                            return 1;
                        }))

                .then(Commands.literal("reset")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            CampaignData.get(source.getLevel()).reset();

                            source.sendSuccess(() -> Component.literal(
                                    "Campanha apagada: todas as frentes serão remontadas."), true);
                            return 1;
                        }));
    }

    // ====================================================================================
    // Shared
    // ====================================================================================

    private static CampaignFront requireFront(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "front");
        CampaignFront front = CampaignFronts.byName(raw).orElse(null);

        if (front == null) {
            context.getSource().sendFailure(Component.literal(
                    "Frente desconhecida: " + raw + ". Conhecidas: "
                            + String.join(", ", CampaignFronts.names())));
        }

        return front;
    }
}
