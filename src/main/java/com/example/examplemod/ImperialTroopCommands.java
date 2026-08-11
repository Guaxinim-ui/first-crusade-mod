package com.example.examplemod;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /fctroop} — the bench for looking at soldiers.
 *
 * <p>Comparing eleven units means getting eleven units standing still, side by side, in daylight,
 * and then flipping one of them between variants and career grades without hunting for a promotion.
 * Doing that through the game's own systems takes a barracks, a regiment and half an hour; doing it
 * here takes one line, which is the difference between checking the art and assuming it is fine.
 *
 * <p>Everything is permission level 2 and nothing here is reachable in normal play: {@code line}
 * spawns a rank of troops, {@code variant} and {@code career} repaint a soldier you are looking at.
 * The career subcommand deliberately goes through {@link GuardsmanEntity#setRank}, the same path a
 * real promotion takes, so what you are checking is the real behaviour and not a preview of it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ImperialTroopCommands {
    private ImperialTroopCommands() {
    }

    /** Gap between units in {@code /fctroop line}, in blocks. Wide enough not to shove each other. */
    private static final int LINE_SPACING = 2;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fctroop")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("spawn")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String key : ImperialTroopAppearance.knownTroops()) {
                                        builder.suggest(key);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> spawn(context,
                                        StringArgumentType.getString(context, "type")))))

                .then(Commands.literal("line")
                        .executes(ImperialTroopCommands::line))

                .then(Commands.literal("variant")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("variant", IntegerArgumentType.integer(0, 7))
                                        .executes(context -> variant(context,
                                                IntegerArgumentType.getInteger(context, "variant"))))))

                .then(Commands.literal("career")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (GuardsmanRank rank : GuardsmanRank.values()) {
                                                builder.suggest(rank.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> career(context,
                                                StringArgumentType.getString(context, "rank"))))))

                .then(Commands.literal("info")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(ImperialTroopCommands::info)));

        event.getDispatcher().register(root);
    }

    // ==================================================================== spawning

    private static int spawn(CommandContext<CommandSourceStack> context, String key)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Vec3 at = source.getPosition();

        Entity spawned = spawnAt(source.getLevel(), key, at.add(0.0D, 0.0D, 2.0D));
        if (spawned == null) {
            source.sendFailure(Component.literal("No entity type firstcrusade:" + key));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Spawned " + key), false);
        return 1;
    }

    /**
     * One of every troop that has art, in a rank facing the caller.
     *
     * <p>This is the twenty-point visual check in a single command: if two units in that line are
     * hard to tell apart, the art is not done.
     */
    private static int line(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();

        List<String> keys = ImperialTroopAppearance.knownTroops();
        int spawned = 0;
        int index = 0;

        for (String key : keys) {
            // Laid out left to right around the caller, four blocks out, so the whole rank is in
            // one screenshot.
            double offset = (index - (keys.size() - 1) / 2.0D) * LINE_SPACING;
            if (spawnAt(level, key, at.add(offset, 0.0D, 4.0D)) != null) {
                spawned++;
            }
            index++;
        }

        int total = spawned;
        source.sendSuccess(() -> Component.literal(
                "Spawned " + total + " of " + keys.size() + " troop types"), false);
        return total;
    }

    private static Entity spawnAt(ServerLevel level, String key, Vec3 at) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation(ExampleMod.MODID, key));

        if (type == null) {
            return null;
        }

        return type.spawn(level, BlockPos.containing(at), MobSpawnType.COMMAND);
    }

    // ==================================================================== repainting

    private static int variant(CommandContext<CommandSourceStack> context, int variant)
            throws CommandSyntaxException {
        int changed = 0;

        for (Entity entity : targets(context)) {
            if (entity instanceof AbstractImperialTroopEntity troop) {
                troop.setVisualVariant(variant);
                changed++;
            } else if (entity instanceof GuardsmanEntity guardsman) {
                guardsman.setVisualVariant(variant);
                changed++;
            }
        }

        int total = changed;
        context.getSource().sendSuccess(
                () -> Component.literal("Set variant " + variant + " on " + total + " troop(s)"), false);
        return total;
    }

    /**
     * Promotes through the real path, so this checks the promotion and not just the picture.
     *
     * <p>The soldier is not replaced: same entity, same UUID, same name, same merit and Ork tally,
     * same Command Core. Only {@link ImperialTroopGrade} — and therefore the texture — moves.
     */
    private static int career(CommandContext<CommandSourceStack> context, String rankName)
            throws CommandSyntaxException {
        GuardsmanRank rank = GuardsmanRank.fromName(rankName.toUpperCase(java.util.Locale.ROOT));
        int changed = 0;

        for (Entity entity : targets(context)) {
            if (entity instanceof GuardsmanEntity guardsman) {
                guardsman.setRank(rank, false);
                changed++;
            }
        }

        int total = changed;
        context.getSource().sendSuccess(() -> Component.literal(
                "Set rank " + rank.name() + " on " + total + " Guardsman/-men"), false);
        return total;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int reported = 0;

        for (Entity entity : targets(context)) {
            if (!(entity instanceof ImperialTroopVisuals troop)) {
                continue;
            }

            ResourceLocation texture = ImperialTroopAppearance.texture(troop);
            String line = entity.getName().getString()
                    + " — key=" + troop.appearanceKey()
                    + " regiment=" + troop.appearanceRegiment()
                    + " variant=" + troop.getVisualVariant()
                    + " grade=" + troop.getVisualGrade().name()
                    + " -> " + texture;

            source.sendSuccess(() -> Component.literal(line), false);
            reported++;
        }

        return reported;
    }

    private static Collection<? extends Entity> targets(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return EntityArgument.getEntities(context, "targets");
    }
}
