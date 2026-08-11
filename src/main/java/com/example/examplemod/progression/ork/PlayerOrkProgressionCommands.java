package com.example.examplemod.progression.ork;

import java.util.Collection;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.OrkClan;
import com.example.examplemod.progression.PlayerProgressionManager;
import com.example.examplemod.progression.PlayerProgressionNetwork;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /fcorkprogress} — the bench for the WAAAGH.
 *
 * <p>Krumpagem takes a long time to earn on purpose, and the evolution gates sit at 40, 150, 400 and
 * 900. Waiting that out to find out whether a gate reads the right field is not testing, it is
 * hoping. Everything that changes state is level 2; {@code status} is open, because it only reports
 * and a player asking about his own numbers is not an administrative act.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOrkProgressionCommands {
    private PlayerOrkProgressionCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fcorkprogress")

                .then(Commands.literal("status")
                        .executes(context -> status(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> status(context,
                                        EntityArgument.getPlayer(context, "target")))))

                .then(Commands.literal("add_krump")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(context -> addKrump(context,
                                                IntegerArgumentType.getInteger(context, "amount"))))))

                .then(Commands.literal("add_teef")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(context -> addTeef(context,
                                                IntegerArgumentType.getInteger(context, "amount"))))))

                .then(Commands.literal("set_fury")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount",
                                                IntegerArgumentType.integer(0, PlayerOrkProgressionBalance.FURY_MAX))
                                        .executes(context -> setFury(context,
                                                IntegerArgumentType.getInteger(context, "amount"))))))

                .then(Commands.literal("set_stage")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (PlayerOrkEvolutionStage stage : PlayerOrkEvolutionStage.values()) {
                                                builder.suggest(stage.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> setStage(context,
                                                StringArgumentType.getString(context, "stage"))))))

                .then(Commands.literal("set_clan")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("clan", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (OrkClan clan : OrkClan.values()) {
                                                builder.suggest(clan.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> setClan(context,
                                                StringArgumentType.getString(context, "clan"))))))

                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(PlayerOrkProgressionCommands::reset)));

        event.getDispatcher().register(root);
    }

    // ==================================================================== reading

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        PlayerOrkProgressionProfile ork = PlayerProgressionManager.profile(player).ork();
        long now = player.level().getGameTime();
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName())
                .withStyle(ChatFormatting.GREEN), false);

        source.sendSuccess(() -> Component.literal(
                "TAMANHU: " + ork.stage().id()
                        + "   KLAN: " + (ork.hasClan() ? ork.clan().name() : "-")), false);

        source.sendSuccess(() -> Component.literal(
                "KRUMPADO: " + ork.krumpScore()
                        + "   DENTU: " + ork.teef()
                        + "   FURIA: " + ork.fury(now) + "/" + PlayerOrkProgressionBalance.FURY_MAX), false);

        source.sendSuccess(() -> Component.literal(
                "kills " + ork.validKills()
                        + "   elites " + ork.eliteKills()
                        + "   imperiais " + ork.imperialKills()
                        + "   cores " + ork.imperialCoresDestroyed()
                        + "   ranks " + ork.totalRanks()
                        + "   dentu gasto " + ork.totalTeefSpent()), false);

        return 1;
    }

    // ==================================================================== writing

    private static int addKrump(CommandContext<CommandSourceStack> context, int amount)
            throws CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            PlayerProgressionManager.profile(player).ork().addKrump(amount);
            commit(player);
        }
        return 1;
    }

    private static int addTeef(CommandContext<CommandSourceStack> context, int amount)
            throws CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            PlayerOrkProgressionProfile ork = PlayerProgressionManager.profile(player).ork();

            // A negative amount spends rather than adds, so the balance still cannot go below zero.
            if (amount >= 0) {
                ork.addTeef(amount);
            } else {
                ork.spendTeef(-amount);
            }

            commit(player);
        }
        return 1;
    }

    private static int setFury(CommandContext<CommandSourceStack> context, int amount)
            throws CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            PlayerProgressionManager.profile(player).ork()
                    .setFury(amount, player.level().getGameTime());
            commit(player);
        }
        return 1;
    }

    /**
     * Sets the stage, and reshapes the player.
     *
     * <p>{@code recalculate} is what pushes the new body onto the wire and moves him out of a
     * ceiling if he just grew into one — setting the field alone would leave a Warboss with a Boy's
     * hitbox until something else happened to refresh him.
     */
    private static int setStage(CommandContext<CommandSourceStack> context, String stageId)
            throws CommandSyntaxException {
        PlayerOrkEvolutionStage stage = PlayerOrkEvolutionStage.byId(stageId);

        for (ServerPlayer player : targets(context)) {
            PlayerProgressionManager.profile(player).ork().setStage(stage);
            commit(player);
            PlayerProgressionManager.recalculate(player);
        }

        context.getSource().sendSuccess(
                () -> Component.literal("TAMANHU: " + stage.id()), false);
        return 1;
    }

    private static int setClan(CommandContext<CommandSourceStack> context, String clanName)
            throws CommandSyntaxException {
        OrkClan clan = OrkClan.fromName(clanName.toUpperCase(java.util.Locale.ROOT));

        for (ServerPlayer player : targets(context)) {
            PlayerProgressionManager.profile(player).ork().setClan(clan);
            commit(player);
            // A klan is worth attributes, and attributes only move when something reapplies them.
            PlayerProgressionManager.recalculate(player);
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            // One method, so a reset can never miss a field the profile grows later.
            PlayerProgressionManager.profile(player).ork().reset();

            // The rally is not in the profile — it is transient state in the ability manager, and a
            // reset that left Boyz walking towards a player who no longer has the node would be a
            // reset that did not reset.
            PlayerOrkAbilityManager.forget(player.getUUID());

            commit(player);
            PlayerProgressionManager.recalculate(player);
        }
        return 1;
    }

    private static void commit(ServerPlayer player) {
        PlayerProgressionManager.data(player.serverLevel()).markChanged();
        PlayerProgressionNetwork.sync(player);
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return EntityArgument.getPlayers(context, "targets");
    }
}
