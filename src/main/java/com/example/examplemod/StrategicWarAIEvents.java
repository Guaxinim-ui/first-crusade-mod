package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The war's server-tick entry point — now a much quieter one.
 *
 * <h2>What stopped running</h2>
 *
 * This class used to drive three loops per planet: {@link StrategicConstructionBuilder} every 20
 * ticks laying blocks for queued city projects, {@link CityMilitaryManager} every 60 mustering
 * squads and marching them, and the strategic AI every 100 choosing what each city should build
 * next. All three served the city builder, and none of them is called any more. What is left is a
 * light bookkeeping pass — sync the war map, keep the Ork side ticking — at a tenth of the old rate.
 *
 * <h2>Once every 600 ticks</h2>
 *
 * Half a minute. The pass does no per-base entity scan and places no blocks, so its cost is a walk
 * over two small maps; running it faster would buy nothing.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class StrategicWarAIEvents {
    private static final int STRATEGIC_AI_INTERVAL = 600;

    private StrategicWarAIEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // A guerra acontece nos planetas do mod. Era o overworld enquanto o mod era dono dele;
        // hoje o overworld e vanilla e nao deve receber cidade nem acampamento nenhum.
        for (net.minecraft.resources.ResourceKey<Level> planet
                : com.example.examplemod.planet.FCPlanets.ALL) {
            ServerLevel level = event.getServer().getLevel(planet);
            if (level != null) {
                tickWorld(level);
            }
        }
    }

    private static void tickWorld(ServerLevel overworld) {
        long gameTime = overworld.getGameTime();

        if (!ExampleMod.TEST_FIXED_WORLD) {
            VanillaVillageImperializer.serverTick(overworld);
        }
        FactionResearchManager.tick(overworld);

        if (gameTime % STRATEGIC_AI_INTERVAL == 0) {
            StrategicWarAIManager.lightStrategicTick(overworld);
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

                                    StrategicWarAIManager.lightStrategicTick(level);

                                    source.sendSuccess(
                                            () -> Component.literal("IA estratégica executada manualmente."),
                                            true
                                    );

                                    return 1;
                                }))

                        // "buildtick" is gone with the construction system it drove. Nothing queues
                        // city projects any more, so there is nothing to step by hand.

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

                        // Plants an autonomous Imperial walled village ~48 blocks in the direction
                        // the player is looking. Works in any world (ignores the once-per-world
                        // seeding flag), so tests don't require creating a new world.
                        .then(Commands.literal("seedcity")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getLevel();

                                    BlockPos spot = spotInFrontOf(source, 48);
                                    BlockPos core = WorldSettlementSeeder.foundCity(level, spot);

                                    source.sendSuccess(
                                            () -> Component.literal(
                                                    "Vila imperial fundada em ["
                                                            + core.getX() + ", "
                                                            + core.getY() + ", "
                                                            + core.getZ() + "]."),
                                            true
                                    );

                                    return 1;
                                }))

                        // Plants an Ork city ~48 blocks in the direction the player is looking,
                        // targeting the nearest Imperial city (natural target for attack squads).
                        .then(Commands.literal("seedcamp")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getLevel();

                                    BlockPos spot = spotInFrontOf(source, 48);

                                    BlockPos target = StrategicWarAIManager.findNearestCity(
                                            WorldWarMapData.get(level),
                                            spot
                                    );

                                    BlockPos camp = OrkCampManager.seedWorldCamp(level, spot, target);

                                    if (camp == null) {
                                        source.sendFailure(Component.literal(
                                                "Não foi possível plantar o núcleo Ork aqui."));
                                        return 0;
                                    }

                                    if (level.getBlockEntity(camp) instanceof OrkCampBlockEntity orkCity) {
                                        orkCity.seedAsCity(level, 3);
                                    }

                                    source.sendSuccess(
                                            () -> Component.literal(
                                                    "Cidade Ork plantada em ["
                                                            + camp.getX() + ", "
                                                            + camp.getY() + ", "
                                                            + camp.getZ() + "]."),
                                            true
                                    );

                                    return 1;
                                }))
        );
    }

    // A surface spot the given distance ahead of where the command source is looking (horizontal
    // yaw only), so seeded settlements never land on top of the player.
    private static BlockPos spotInFrontOf(CommandSourceStack source, int distance) {
        Vec3 position = source.getPosition();
        Vec2 rotation = source.getRotation();

        double yawRadians = Math.toRadians(rotation.y);

        return BlockPos.containing(
                position.x - Math.sin(yawRadians) * distance,
                position.y,
                position.z + Math.cos(yawRadians) * distance
        );
    }
}