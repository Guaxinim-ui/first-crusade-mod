package com.example.examplemod.progression;

import java.util.Collection;

import com.example.examplemod.ExampleMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /fccommand} — the operator's window onto a player's command career.
 *
 * <h2>Read is free, write is level 2</h2>
 *
 * Same split as {@code /fcprogress}: a player asking about their own commander is not an
 * administrative act, and everything that changes a profile can hand somebody a platoon.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class PlayerCommanderCommands {
    private PlayerCommanderCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> NODES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    PlayerCommanderTree.all().stream()
                            .map(PlayerCommanderNodeDefinition::id).toList(), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fccommand")

                .then(Commands.literal("status")
                        .executes(context -> status(context,
                                context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> status(context,
                                        EntityArgument.getPlayer(context, "player")))))

                .then(Commands.literal("add_xp").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(PlayerCommanderCommands::addXp))))

                .then(Commands.literal("add_points").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(PlayerCommanderCommands::addPoints))))

                .then(Commands.literal("unlock").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("node", StringArgumentType.string())
                                        .suggests(NODES)
                                        .executes(PlayerCommanderCommands::unlock))))

                .then(Commands.literal("reset").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(PlayerCommanderCommands::reset)));

        event.getDispatcher().register(root);
    }

    private static java.util.function.Predicate<CommandSourceStack> admin() {
        return source -> source.hasPermission(2);
    }

    // ==================================================================== status

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        PlayerCommanderProfile profile = PlayerCommanderManager.profile(player);
        CommandSourceStack source = context.getSource();

        line(source, "command.firstcrusade.command.level",
                profile.level(), profile.xp(), profile.xpForNextLevel());
        line(source, "command.firstcrusade.command.points",
                profile.points(), profile.pointsEarned(), profile.pointsSpent());
        line(source, "command.firstcrusade.command.raids",
                profile.startedRaids(), profile.successfulRaids(), profile.failedRaids());
        line(source, "command.firstcrusade.command.troops", profile.totalTroopsCalled());
        line(source, "command.firstcrusade.command.limit",
                PlayerCommanderManager.reinforcementLimit(profile),
                PlayerCommanderManager.approachDistance(profile));
        line(source, "command.firstcrusade.command.cooldown",
                PlayerCommanderManager.cooldownRemaining(profile,
                        player.level().getGameTime()) / 20L);

        return 1;
    }

    private static void line(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args).withStyle(ChatFormatting.GRAY),
                false);
    }

    // ==================================================================== mutations

    private static int addXp(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");

        for (ServerPlayer player : targets(context)) {
            PlayerCommanderManager.awardXp(player, amount);
        }

        return 1;
    }

    private static int addPoints(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");

        for (ServerPlayer player : targets(context)) {
            PlayerCommanderManager.awardPoints(player, amount);
        }

        return 1;
    }

    /**
     * Grants a command node outright, skipping cost and prerequisites.
     *
     * <p>An operator testing the platoon call should not have to win four raids first. It still
     * refuses an unknown id, because that is a typo rather than a policy to override.
     */
    private static int unlock(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "node");

        if (PlayerCommanderTree.node(id) == null) {
            context.getSource().sendFailure(
                    Component.translatable("command.firstcrusade.command.unknown_node", id));
            return 0;
        }

        for (ServerPlayer player : targets(context)) {
            PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
            data.profile(player.getUUID()).commander().take(id);
            data.markChanged();
            PlayerProgressionNetwork.sync(player);
        }

        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
            data.profile(player.getUUID()).commander().reset();
            data.markChanged();
            PlayerProgressionNetwork.sync(player);
        }

        return 1;
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return EntityArgument.getPlayers(context, "targets");
    }
}
