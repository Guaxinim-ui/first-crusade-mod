package com.example.examplemod.crusade;

import java.util.Collection;

import com.example.examplemod.ExampleMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /fccrusade} — reading the roster before there is a screen to read it on.
 *
 * <p>The garrison list belongs in the Command Core's menu, and that is where it is going. Until
 * then this exists so the data can be checked in a live world rather than by opening a save file:
 * a roster that nobody can see is a roster nobody can tell is wrong.
 *
 * <p>Read-only except {@code name}. Everything is level 2 — this reports on a base the player may
 * not own, and in multiplayer that is a permission question, not a convenience.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrusadeCommands {
    private CrusadeCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fccrusade")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("roster")
                        .then(Commands.argument("core", BlockPosArgument.blockPos())
                                .executes(context -> roster(context,
                                        BlockPosArgument.getLoadedBlockPos(context, "core")))))

                .then(Commands.literal("fallen")
                        .then(Commands.argument("core", BlockPosArgument.blockPos())
                                .executes(context -> fallen(context,
                                        BlockPosArgument.getLoadedBlockPos(context, "core")))))

                .then(Commands.literal("bases")
                        .executes(CrusadeCommands::bases));

        event.getDispatcher().register(root);
    }

    private static int roster(CommandContext<CommandSourceStack> context, BlockPos corePos)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        ImperialSoldierRoster roster = ImperialCrusadeData.get(level).peek(corePos);
        if (roster == null) {
            source.sendFailure(Component.literal("No Imperial base recorded at " + corePos.toShortString()));
            return 0;
        }

        source.sendSuccess(() -> Component.empty()
                .append(roster.regiment().displayName())
                .append(Component.literal("  —  " + roster.servingCount() + " serving, "
                        + roster.totalFallen() + " fallen").withStyle(ChatFormatting.GRAY)), false);

        for (ImperialSoldierRecord record : roster.serving()) {
            source.sendSuccess(() -> line(record, level), false);
        }

        return roster.servingCount();
    }

    private static int fallen(CommandContext<CommandSourceStack> context, BlockPos corePos)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        ImperialSoldierRoster roster = ImperialCrusadeData.get(level).peek(corePos);
        if (roster == null) {
            source.sendFailure(Component.literal("No Imperial base recorded at " + corePos.toShortString()));
            return 0;
        }

        Collection<ImperialSoldierRecord> dead = roster.fallen();

        source.sendSuccess(() -> Component.translatable("msg.firstcrusade.crusade.fallen_header",
                roster.totalFallen()).withStyle(ChatFormatting.DARK_RED), false);

        for (ImperialSoldierRecord record : dead) {
            source.sendSuccess(() -> Component.empty()
                    .append(record.displayName().copy().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  ").append(
                            Component.translatable(record.fate()).withStyle(ChatFormatting.DARK_GRAY))),
                    false);
        }

        return dead.size();
    }

    private static int bases(CommandContext<CommandSourceStack> context) {
        ImperialCrusadeData data = ImperialCrusadeData.get(context.getSource().getLevel());

        context.getSource().sendSuccess(() -> Component.literal(
                "Crusade \"" + (data.crusadeName().isEmpty() ? "(unnamed)" : data.crusadeName())
                        + "\" — " + data.baseCount() + " base(s) on record"), false);

        return data.baseCount();
    }

    /** One roster row: title, name, grade, tally, service. */
    private static Component line(ImperialSoldierRecord record, ServerLevel level) {
        // Service in Minecraft days, which is the only unit a player has any feel for.
        long days = record.serviceTicks(level.getGameTime()) / 24000L;

        return Component.empty()
                .append(record.displayName().copy().withStyle(record.regiment().colour()))
                .append(Component.literal("  " + record.orkKills() + " Orks"
                        + (record.eliteKills() > 0 ? " (" + record.eliteKills() + " elite)" : "")
                        + "  ·  " + record.raidsJoined() + " raids"
                        + "  ·  " + days + "d").withStyle(ChatFormatting.DARK_GRAY));
    }
}
