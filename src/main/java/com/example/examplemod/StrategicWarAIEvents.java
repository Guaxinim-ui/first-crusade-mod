package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class StrategicWarAIEvents {
    private static final int STRATEGIC_AI_INTERVAL = 100;
    private static final int CONSTRUCTION_AI_INTERVAL = 20;

    private StrategicWarAIEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);

        if (overworld == null) {
            return;
        }

        long gameTime = overworld.getGameTime();

        if (gameTime % CONSTRUCTION_AI_INTERVAL == 0) {
            StrategicWarAIData data = StrategicWarAIData.get(overworld);
            StrategicConstructionBuilder.tickConstruction(overworld, data);
        }

        if (gameTime % STRATEGIC_AI_INTERVAL == 0) {
            StrategicWarAIManager.forceStrategicTick(overworld);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("fcstrategy")
                        .requires(source -> source.hasPermission(2))

                        .then(Commands.literal("status")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);

                                    if (level == null) {
                                        source.sendFailure(Component.literal("Overworld não encontrado."));
                                        return 0;
                                    }

                                    for (Component line : StrategicWarAIManager.createStatusLines(level)) {
                                        source.sendSuccess(() -> line, false);
                                    }

                                    return 1;
                                }))

                        .then(Commands.literal("projects")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);

                                    if (level == null) {
                                        source.sendFailure(Component.literal("Overworld não encontrado."));
                                        return 0;
                                    }

                                    for (Component line : StrategicWarAIManager.createProjectLines(level)) {
                                        source.sendSuccess(() -> line, false);
                                    }

                                    return 1;
                                }))

                        .then(Commands.literal("tick")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);

                                    if (level == null) {
                                        source.sendFailure(Component.literal("Overworld não encontrado."));
                                        return 0;
                                    }

                                    StrategicWarAIManager.forceStrategicTick(level);

                                    source.sendSuccess(
                                            () -> Component.literal("IA estratégica executada manualmente."),
                                            true
                                    );

                                    return 1;
                                }))

                        .then(Commands.literal("buildtick")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);

                                    if (level == null) {
                                        source.sendFailure(Component.literal("Overworld não encontrado."));
                                        return 0;
                                    }

                                    StrategicWarAIData data = StrategicWarAIData.get(level);
                                    StrategicConstructionBuilder.tickConstruction(level, data);

                                    source.sendSuccess(
                                            () -> Component.literal("Construção estratégica executada manualmente."),
                                            true
                                    );

                                    return 1;
                                }))

                        .then(Commands.literal("reset")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);

                                    if (level == null) {
                                        source.sendFailure(Component.literal("Overworld não encontrado."));
                                        return 0;
                                    }

                                    StrategicWarAIManager.reset(level);

                                    source.sendSuccess(
                                            () -> Component.literal("IA estratégica resetada."),
                                            true
                                    );

                                    return 1;
                                }))
        );
    }
}