package com.example.examplemod.progression;

import java.util.Collection;

import com.example.examplemod.ExampleMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
 * {@code /fcprogress} — the operator's window onto a player's progression.
 *
 * <h2>Read is free, write is level 2</h2>
 *
 * {@code status} is open because a player asking about their own tree is not an administrative act.
 * Everything that changes a profile is gated at permission level 2, because everything that changes
 * a profile can hand somebody a Space Marine.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class PlayerProgressionCommands {
    private PlayerProgressionCommands() {
    }

    /** Completes node ids from the tree, so nobody has to remember thirty-seven of them. */
    private static final SuggestionProvider<CommandSourceStack> NODES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    PlayerProgressionTree.all().stream()
                            .map(PlayerSkillNodeDefinition::id).toList(), builder);

    private static final SuggestionProvider<CommandSourceStack> STAGES =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(PlayerEvolutionStage.values())
                            .map(Enum::name).toList(), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fcprogress")
                .then(Commands.literal("status")
                        .executes(context -> status(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> status(context,
                                        EntityArgument.getPlayer(context, "player")))))

                .then(Commands.literal("add_xp").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(PlayerProgressionCommands::addXp))))

                .then(Commands.literal("add_points").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(PlayerProgressionCommands::addPoints))))

                .then(Commands.literal("unlock").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("node", StringArgumentType.string())
                                        .suggests(NODES)
                                        .executes(context -> unlock(context, 1))
                                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, 5))
                                                .executes(context -> unlock(context,
                                                        IntegerArgumentType.getInteger(context, "rank")))))))

                .then(Commands.literal("set_stage").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("stage", StringArgumentType.string())
                                        .suggests(STAGES)
                                        .executes(PlayerProgressionCommands::setStage))))

                .then(Commands.literal("complete_implant").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(PlayerProgressionCommands::completeImplant)))

                .then(Commands.literal("complete_blood_trial").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(PlayerProgressionCommands::completeTrial)))

                .then(Commands.literal("reset").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(PlayerProgressionCommands::reset)))

                .then(Commands.literal("refresh_size").requires(admin())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(PlayerProgressionCommands::refreshSize)));

        event.getDispatcher().register(root);
    }

    private static java.util.function.Predicate<CommandSourceStack> admin() {
        return source -> source.hasPermission(2);
    }

    // ==================================================================== status

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        PlayerProgressionProfile profile = PlayerProgressionManager.profile(player);
        CommandSourceStack source = context.getSource();

        line(source, "command.firstcrusade.progress.stage", profile.stage().displayName());
        line(source, "command.firstcrusade.progress.level", profile.level(),
                profile.xp(), profile.xpForNextLevel());
        line(source, "command.firstcrusade.progress.points", profile.points(),
                profile.pointsEarned(), profile.pointsSpent());
        line(source, "command.firstcrusade.progress.implants", profile.implantCount(),
                PlayerProgressionBalance.IMPLANT_COUNT);
        line(source, "command.firstcrusade.progress.height",
                PlayerProgressionTree.format(profile.stage().height()));
        line(source, "command.firstcrusade.progress.trial", profile.trialOrkKills(),
                PlayerProgressionBalance.TRIAL_ORK_KILLS, profile.trialEliteKills(),
                PlayerProgressionBalance.TRIAL_ELITE_KILLS, profile.trialWarbossKills(),
                PlayerProgressionBalance.TRIAL_WARBOSS_KILLS);

        if (profile.isInSurgery()) {
            line(source, "command.firstcrusade.progress.surgery",
                    Component.literal(String.valueOf(profile.surgeryNodeId())),
                    Math.round(profile.surgeryProgress(player.level().getGameTime()) * 100.0F));
        }

        return 1;
    }

    private static void line(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args).withStyle(ChatFormatting.GRAY),
                false);
    }

    // ==================================================================== mutations

    private static int addXp(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");

        for (ServerPlayer player : targets(context)) {
            PlayerProgressionManager.awardXp(player, amount);
        }

        return 1;
    }

    private static int addPoints(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");

        for (ServerPlayer player : targets(context)) {
            PlayerProgressionManager.awardPoints(player, amount);
        }

        return 1;
    }

    /**
     * Grants a rank outright, skipping cost and prerequisites.
     *
     * <p>An operator testing the fifth cycle should not have to buy their way there. It still
     * refuses an unknown id, because that is a typo rather than a policy to override.
     */
    private static int unlock(CommandContext<CommandSourceStack> context, int rank)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id = StringArgumentType.getString(context, "node");
        PlayerSkillNodeDefinition node = PlayerProgressionTree.node(id);

        if (node == null) {
            context.getSource().sendFailure(
                    Component.translatable("command.firstcrusade.progress.unknown_node", id));
            return 0;
        }

        for (ServerPlayer player : targets(context)) {
            PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
            PlayerProgressionProfile profile = data.profile(player.getUUID());

            if (node.implant()) {
                PlayerEvolutionNodeDefinition surgery = PlayerProgressionTree.surgery(id);
                profile.addImplant(id);
                if (surgery != null) {
                    profile.setStage(surgery.stage());
                }
            } else {
                profile.setRank(id, Math.min(rank, node.maxRanks()));
            }

            data.markChanged();
            PlayerProgressionManager.recalculate(player);
        }

        return 1;
    }

    private static int setStage(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerEvolutionStage stage = PlayerEvolutionStage.byName(
                StringArgumentType.getString(context, "stage"));

        for (ServerPlayer player : targets(context)) {
            PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
            data.profile(player.getUUID()).setStage(stage);
            data.markChanged();
            PlayerProgressionManager.recalculate(player);
        }

        return 1;
    }

    /** Finishes whatever is on the table right now, without waiting out the clock. */
    private static int completeImplant(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
            PlayerProgressionProfile profile = data.profile(player.getUUID());

            if (!profile.isInSurgery()) {
                continue;
            }

            // Bring the deadline forward and let the ordinary path do the rest, so a commanded
            // implant and a waited-out one end in exactly the same state.
            profile.beginSurgery(profile.surgeryNodeId(), player.level().getGameTime() - 1, 0);
            data.markChanged();
            PlayerProgressionManager.tickSurgery(player);
        }

        return 1;
    }

    private static int completeTrial(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
            data.profile(player.getUUID()).setTrialComplete(true);
            data.markChanged();
            PlayerProgressionNetwork.sync(player);
        }

        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
            data.profile(player.getUUID()).reset();
            data.markChanged();

            PlayerProgressionAbilityManager.forget(player.getUUID());
            PlayerProgressionAttributes.clear(player);
            PlayerProgressionManager.recalculate(player);
        }

        return 1;
    }

    private static int refreshSize(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        for (ServerPlayer player : targets(context)) {
            PlayerProgressionSizeManager.refresh(player);
            PlayerProgressionNetwork.sync(player);
        }

        return 1;
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayers(context, "targets");
    }
}
