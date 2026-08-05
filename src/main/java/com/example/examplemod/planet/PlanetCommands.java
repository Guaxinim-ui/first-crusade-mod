package com.example.examplemod.planet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.example.examplemod.ExampleMod;
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
 * {@code /firstcrusade planet …} — the operator's view of the destination system.
 *
 * <pre>
 *   /firstcrusade planet list                     every destination and this player's state
 *   /firstcrusade planet info &lt;planet&gt;            one destination in full
 *   /firstcrusade planet unlock &lt;player&gt; &lt;planet&gt; grant it
 *   /firstcrusade planet lock &lt;player&gt; &lt;planet&gt;   take it back
 *   /firstcrusade planet travel &lt;player&gt; &lt;planet&gt; send them, skipping the terminal
 * </pre>
 *
 * <p>{@code list} and {@code info} are readable by anyone — they show what the terminal already
 * shows. The three that change something require permission level 2, the same bar the mod's other
 * administrative commands use.
 *
 * <p>{@code travel} deliberately bypasses the Spaceport and cost checks (that is what makes it
 * useful for testing) but still refuses a destination with no dimension, because that is not a
 * policy to override — it is a teleport that cannot happen.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class PlanetCommands {
    private PlanetCommands() {
    }

    /** Completes planet ids from the registry, so no operator has to remember them. */
    private static final SuggestionProvider<CommandSourceStack> PLANETS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    PlanetRegistry.all().stream().map(planet -> planet.id().toString()).toList(),
                    builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> planet = Commands.literal("planet")
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("planet", StringArgumentType.string())
                                .suggests(PLANETS)
                                .executes(context -> info(context.getSource(),
                                        StringArgumentType.getString(context, "planet")))))
                .then(Commands.literal("unlock")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("planet", StringArgumentType.string())
                                        .suggests(PLANETS)
                                        .executes(context -> setLocked(context, false)))))
                .then(Commands.literal("lock")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("planet", StringArgumentType.string())
                                        .suggests(PLANETS)
                                        .executes(context -> setLocked(context, true)))))
                .then(Commands.literal("travel")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("planet", StringArgumentType.string())
                                        .suggests(PLANETS)
                                        .executes(PlanetCommands::travel))));

        event.getDispatcher().register(Commands.literal("firstcrusade").then(planet));
    }

    // ------------------------------------------------------------------ list

    private static int list(CommandSourceStack source) {
        List<PlanetDefinition> planets = PlanetRegistry.all();

        source.sendSuccess(() -> Component.translatable("command.firstcrusade.planet.list.header",
                planets.size()).withStyle(ChatFormatting.GOLD), false);

        ServerPlayer player = source.getPlayer();

        for (PlanetDefinition definition : planets) {
            PlanetTravelState state = player == null
                    ? PlanetTravelManager.intrinsicState(source.getServer(), definition)
                    : PlanetTravelManager.stateOf(player, definition);

            ChatFormatting colour = switch (state) {
                case AVAILABLE -> ChatFormatting.GREEN;
                case CURRENT -> ChatFormatting.AQUA;
                case DISCOVERED_NOT_DEPLOYABLE -> ChatFormatting.YELLOW;
                case LOCKED -> ChatFormatting.DARK_GRAY;
            };

            source.sendSuccess(() -> Component.literal(" " + definition.id() + " - ")
                    .append(definition.displayName())
                    .append(Component.literal(" ["))
                    .append(state.displayName())
                    .append(Component.literal("]"))
                    .withStyle(colour), false);
        }

        return planets.size();
    }

    // ------------------------------------------------------------------ info

    private static int info(CommandSourceStack source, String raw) {
        Optional<PlanetDefinition> found = PlanetRegistry.parse(raw);

        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("command.firstcrusade.planet.unknown", raw));
            return 0;
        }

        PlanetDefinition planet = found.get();

        source.sendSuccess(() -> planet.displayName().copy().withStyle(ChatFormatting.GOLD), false);
        line(source, "command.firstcrusade.planet.info.type", planet.worldType().displayName());
        line(source, "command.firstcrusade.planet.info.faction", planet.dominantFaction().displayName());
        line(source, "command.firstcrusade.planet.info.danger", planet.dangerLevel().displayName());
        line(source, "command.firstcrusade.planet.info.military", planet.militaryStatus().displayName());
        line(source, "command.firstcrusade.planet.info.resources", planet.resources());

        Component destination = planet.isDeployable()
                ? Component.literal(planet.destinationDimension().location().toString())
                : Component.translatable("command.firstcrusade.planet.info.no_dimension");
        line(source, "command.firstcrusade.planet.info.destination", destination);

        if (planet.unlockRequirements().isEmpty()) {
            line(source, "command.firstcrusade.planet.info.requirements",
                    Component.translatable("command.firstcrusade.planet.info.none"));
        } else {
            for (PlanetUnlockRequirement requirement : planet.unlockRequirements()) {
                source.sendSuccess(() -> Component.literal("  - ")
                        .append(requirement.description())
                        .append(Component.literal(" (" + requirement.trigger() + ")"))
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }

    private static void line(CommandSourceStack source, String key, Component value) {
        source.sendSuccess(() -> Component.translatable(key)
                .append(Component.literal(": "))
                .append(value)
                .withStyle(ChatFormatting.GRAY), false);
    }

    // ------------------------------------------------------------------ unlock / lock

    private static int setLocked(CommandContext<CommandSourceStack> context, boolean lock)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "planet");
        Optional<PlanetDefinition> found = PlanetRegistry.parse(raw);

        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("command.firstcrusade.planet.unknown", raw));
            return 0;
        }

        PlanetDefinition planet = found.get();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int changed = 0;

        for (ServerPlayer target : targets) {
            PlanetUnlockData data = PlanetUnlockData.get(target.serverLevel());

            boolean applied = lock
                    ? data.lock(target.getUUID(), planet.id())
                    : data.unlock(target.getUUID(), planet.id());

            if (applied) {
                changed++;
                if (!lock) {
                    target.sendSystemMessage(Component.translatable(
                            "msg.firstcrusade.planet.unlocked", planet.displayName()));
                }
            }
        }

        int total = changed;
        source.sendSuccess(() -> Component.translatable(
                lock ? "command.firstcrusade.planet.locked" : "command.firstcrusade.planet.unlocked",
                planet.displayName(), total), true);

        return changed;
    }

    // ------------------------------------------------------------------ travel

    private static int travel(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "planet");
        Optional<PlanetDefinition> found = PlanetRegistry.parse(raw);

        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("command.firstcrusade.planet.unknown", raw));
            return 0;
        }

        PlanetDefinition planet = found.get();

        // The one check an operator cannot wave away: there has to be somewhere to arrive.
        if (!planet.isDeployable() || source.getServer().getLevel(planet.destinationDimension()) == null) {
            source.sendFailure(PlanetTravelManager.Blocker.NOT_DEPLOYABLE.message());
            return 0;
        }

        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int moved = 0;

        for (ServerPlayer target : targets) {
            PlanetTravelManager.adminTravel(target, planet);
            moved++;
        }

        int total = moved;
        source.sendSuccess(() -> Component.translatable("command.firstcrusade.planet.travelled",
                planet.displayName(), total), true);

        return moved;
    }
}
