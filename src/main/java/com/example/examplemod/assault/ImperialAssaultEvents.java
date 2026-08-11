package com.example.examplemod.assault;

import javax.annotation.Nullable;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.MeganobEntity;
import com.example.examplemod.OrkNobEntity;
import com.example.examplemod.WarbossEntity;
import com.example.examplemod.progression.PlayerCommanderBalance;
import com.example.examplemod.progression.PlayerCommanderManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the assault system meets the server: one tick hook, one kill hook, one command tree.
 *
 * <h2>The tick hook does nothing when nothing is happening</h2>
 *
 * It is one call into {@link ImperialAssaultManager#tick}, which returns on an {@code isEmpty()}.
 * A server with no raid running pays a map lookup a tick for the whole feature.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class ImperialAssaultEvents {
    private ImperialAssaultEvents() {
    }

    /** How near the camp an Ork must die for the kill to be part of the raid. */
    private static final double KILL_CREDIT_RANGE = 64.0D;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        ImperialAssaultManager.tick(event.getServer());
    }

    /**
     * Commander experience for the elites killed during the player's own raid.
     *
     * <p>Three guards, all of them necessary: the killer must be leading a live raid, the victim
     * must be one of the big Orks, and it must have died near the camp being raided. Without the
     * last, a commander could open a raid on the far side of the world and farm experience from
     * whatever they happened to be fighting.
     */
    @SubscribeEvent
    public static void onOrkDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer player
                ? player
                : null;

        if (killer == null) {
            return;
        }

        int xp = eliteXpFor(event.getEntity());
        if (xp <= 0) {
            return;
        }

        ImperialAssaultRecord record =
                ImperialAssaultData.get(level).byInitiator(killer.getUUID());

        if (record == null || !record.phase().isFighting()) {
            return;
        }

        if (event.getEntity().distanceToSqr(
                record.campPos().getX() + 0.5D,
                record.campPos().getY() + 0.5D,
                record.campPos().getZ() + 0.5D) > KILL_CREDIT_RANGE * KILL_CREDIT_RANGE) {
            return;
        }

        PlayerCommanderManager.awardXp(killer, xp);
    }

    private static int eliteXpFor(LivingEntity victim) {
        if (victim instanceof WarbossEntity) {
            return PlayerCommanderBalance.XP_KILL_WARBOSS;
        }
        if (victim instanceof MeganobEntity) {
            return PlayerCommanderBalance.XP_KILL_MEGANOB;
        }
        if (victim instanceof OrkNobEntity) {
            return PlayerCommanderBalance.XP_KILL_NOB;
        }
        return 0;
    }

    // ==================================================================== commands

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fcassault")

                // Reading the state of the raid you are standing in is not an administrative act.
                .then(Commands.literal("status")
                        .executes(ImperialAssaultEvents::status))

                .then(Commands.literal("start").requires(admin())
                        .executes(ImperialAssaultEvents::start))

                .then(Commands.literal("victory").requires(admin())
                        .executes(ImperialAssaultEvents::victory))

                .then(Commands.literal("abort").requires(admin())
                        .executes(ImperialAssaultEvents::abort))

                .then(Commands.literal("return_all").requires(admin())
                        .executes(ImperialAssaultEvents::returnAll))

                .then(Commands.literal("clear_orphans").requires(admin())
                        .executes(ImperialAssaultEvents::clearOrphans));

        event.getDispatcher().register(root);
    }

    private static java.util.function.Predicate<CommandSourceStack> admin() {
        return source -> source.hasPermission(2);
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ImperialAssaultData data = ImperialAssaultData.get(level);

        if (data.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.firstcrusade.assault.none").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        for (ImperialAssaultRecord record : data.snapshot()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.firstcrusade.assault.line",
                    record.campPos().getX(), record.campPos().getY(), record.campPos().getZ(),
                    record.phase().name(),
                    record.troops().size(),
                    record.initialDefenders()).withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.translatable("command.firstcrusade.assault.needs_player"));
            return 0;
        }

        // Exactly the path the R key takes, so the command tests the key rather than a twin of it.
        Component problem = ImperialAssaultManager.declareNearestRaid(player);

        if (!problem.getString().isEmpty()) {
            source.sendFailure(problem);
            return 0;
        }

        return 1;
    }

    private static int victory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ImperialAssaultRecord record = raidOfSource(source);

        if (record == null) {
            source.sendFailure(Component.translatable("command.firstcrusade.assault.none"));
            return 0;
        }

        ImperialAssaultManager.forceVictory(level, record);
        source.sendSuccess(() -> Component.translatable(
                "command.firstcrusade.assault.forced_victory"), true);
        return 1;
    }

    private static int abort(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ImperialAssaultRecord record = raidOfSource(source);

        if (record == null) {
            source.sendFailure(Component.translatable("command.firstcrusade.assault.none"));
            return 0;
        }

        ImperialAssaultManager.abort(level, ImperialAssaultData.get(level), record);
        source.sendSuccess(() -> Component.translatable(
                "command.firstcrusade.assault.aborted"), true);
        return 1;
    }

    private static int returnAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = ImperialAssaultManager.returnAll(source.getLevel());

        source.sendSuccess(() -> Component.translatable(
                "command.firstcrusade.assault.returned_all", count), true);
        return 1;
    }

    private static int clearOrphans(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int count = ImperialAssaultManager.clearOrphans(source.getLevel());

        source.sendSuccess(() -> Component.translatable(
                "command.firstcrusade.assault.orphans", count), true);
        return 1;
    }

    /** The raid the command source is standing in, or the one they started. */
    @Nullable
    private static ImperialAssaultRecord raidOfSource(CommandSourceStack source) {
        ImperialAssaultData data = ImperialAssaultData.get(source.getLevel());
        ServerPlayer player = source.getPlayer();

        if (player != null) {
            ImperialAssaultRecord own = data.byInitiator(player.getUUID());
            if (own != null) {
                return own;
            }
        }

        BlockPos here = BlockPos.containing(source.getPosition());
        ImperialAssaultRecord nearest = null;
        double bestDistance = Double.MAX_VALUE;

        for (ImperialAssaultRecord record : data.snapshot()) {
            double distance = record.campPos().distSqr(here);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = record;
            }
        }

        return nearest;
    }

}
