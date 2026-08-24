package com.example.examplemod.performance;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.ai.formation.FCSquad;
import com.example.examplemod.ai.formation.FCSquadLeader;
import com.example.examplemod.performance.ai.FirstCrusadeAiLod;
import com.example.examplemod.performance.compat.FirstCrusadeModCompat;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;
import com.example.examplemod.performance.graphics.FCServerParticles;
import com.example.examplemod.performance.strategic.FCStrategicBattle;
import com.example.examplemod.performance.strategic.FCStrategicBattleData;
import com.example.examplemod.unit.profile.FCUnit;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /fc perf} and {@code /fc strategic} — what the server is actually carrying right now.
 *
 * <p>Written because the alternative is guessing. Every performance decision in this package was
 * argued from a number, and a number nobody can read in a live world is a number that will be wrong
 * within a month of new content. This is deliberately a report, not a control panel: the one thing
 * it can change is forcing a strategic sweep, which is how the absorption path gets tested without
 * waiting for its interval.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstCrusadePerformanceCommand {

    private FirstCrusadePerformanceCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fc")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("perf")
                .executes(context -> reportPerformance(context.getSource()))
                .then(Commands.literal("entities")
                        .executes(context -> reportEntities(context.getSource()))));

        root.then(Commands.literal("compat")
                .executes(context -> reportCompat(context.getSource())));

        root.then(Commands.literal("squad")
                .executes(context -> reportSquads(context.getSource())));

        // §21/§22 tem 2500 linhas que nunca foram observadas. Supressao e um numero que decai na
        // LEITURA, entao nao ha log a emitir e nada no mundo que a mostre — sem isto a unica forma
        // de saber se funciona e confiar que funciona.
        root.then(Commands.literal("suppression")
                .executes(context -> reportSuppression(context.getSource())));

        root.then(Commands.literal("strategic")
                .executes(context -> reportStrategic(context.getSource()))
                .then(Commands.literal("sweep")
                        .executes(context -> forceSweep(context.getSource()))));

        event.getDispatcher().register(root);
    }

    // ==================================================================== /fc suppression

    /**
     * Who is suppressed, and by how much.
     *
     * <p>Suppression decays lazily — the value is computed when read and nothing ticks — so there is
     * no log line it could have emitted and no state in the world that shows it. Without this the
     * only way to know §22 works is to trust that it does.
     */
    private static int reportSuppression(CommandSourceStack source) {
        ServerLevel level = source.getLevel();

        int suppressed = 0;
        int pinned = 0;
        int seekingCover = 0;
        int total = 0;
        int highest = 0;
        String worst = "-";

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || !isModCombatant(mob)) {
                continue;
            }

            total++;

            int value = com.example.examplemod.ai.combat.FCSuppression.level(mob);

            if (value <= 0) {
                continue;
            }

            suppressed++;

            if (com.example.examplemod.ai.combat.FCSuppression.isPinned(mob)) {
                pinned++;
            }

            if (com.example.examplemod.ai.combat.FCSuppression.wantsCover(mob)) {
                seekingCover++;
            }

            if (value > highest) {
                highest = value;
                worst = mob.getType().toShortString() + " @ " + mob.blockPosition().toShortString();
            }
        }

        header(source, "First Crusade — supressao (" + level.dimension().location() + ")");
        line(source, "Combatentes", Integer.toString(total));
        line(source, "Sob supressao", suppressed + " (>0)");
        line(source, "  Presos (>=" + com.example.examplemod.ai.combat.FCSuppression.PINNED_LEVEL + ")",
                Integer.toString(pinned));
        line(source, "  Buscando cobertura (>=" + com.example.examplemod.ai.combat.FCSuppression.COVER_LEVEL + ")",
                Integer.toString(seekingCover));
        line(source, "Maior nivel", highest + "  " + worst);

        return 1;
    }

    // ==================================================================== /fc perf

    private static int reportPerformance(CommandSourceStack source) {
        ServerLevel level = source.getLevel();

        int units = 0;
        int projectiles = 0;
        EnumMap<FirstCrusadeAiLod, Integer> byDetail = new EnumMap<>(FirstCrusadeAiLod.class);

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Projectile) {
                projectiles++;
                continue;
            }

            if (!(entity instanceof Mob mob) || !isModCombatant(mob)) {
                continue;
            }

            units++;
            byDetail.merge(FirstCrusadeAiLod.forEntity(mob), 1, Integer::sum);
        }

        FCStrategicBattleData data = FCStrategicBattleData.get(level);

        header(source, "First Crusade — desempenho (" + level.dimension().location() + ")");
        line(source, "Unidades do mod", Integer.toString(units));
        line(source, "  IA completa", Integer.toString(byDetail.getOrDefault(FirstCrusadeAiLod.FULL, 0)));
        line(source, "  IA media", Integer.toString(byDetail.getOrDefault(FirstCrusadeAiLod.MEDIUM, 0)));
        line(source, "  IA baixa", Integer.toString(byDetail.getOrDefault(FirstCrusadeAiLod.LOW, 0)));
        line(source, "  IA estrategica",
                Integer.toString(byDetail.getOrDefault(FirstCrusadeAiLod.STRATEGIC, 0)));
        line(source, "Projeteis", Integer.toString(projectiles));
        line(source, "Batalhas abstraidas", Integer.toString(data.battles().size()));
        line(source, "Materializando", Integer.toString(data.materialisingCount()));

        reportParticles(source);

        return units;
    }

    /**
     * What the server is broadcasting in particles, and whether the ceiling has ever bitten.
     *
     * <p>The clipped count is the line that matters. A ceiling nobody can observe is worth exactly
     * as much as a ceiling that does nothing, and the busiest tick tells the owner whether
     * {@code particles.maxSendsPerTick} is anywhere near being reached before they touch it.
     */
    private static void reportParticles(CommandSourceStack source) {
        int ceiling = FirstCrusadePerformanceConfig.maxParticleSendsPerTick();
        long clipped = FCServerParticles.clippedTotal();

        line(source, "Particulas enviadas (ultimo tick)", Integer.toString(FCServerParticles.lastTickSent()));
        line(source, "  pico por tick", FCServerParticles.peakSentInTick() + " de " + ceiling);

        String clippedText = Long.toString(clipped);
        source.sendSuccess(() -> Component.literal("  cortadas pelo teto: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(clippedText)
                        .withStyle(clipped > 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE)), false);

        line(source, "  densidade mestre", FirstCrusadePerformanceConfig.masterParticleDensity() + "%");
    }

    private static int reportEntities(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Map<FirstCrusadeFaction, Integer> byFaction = new EnumMap<>(FirstCrusadeFaction.class);
        Map<String, Integer> byType = new TreeMap<>();

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || !isModCombatant(mob)) {
                continue;
            }

            byFaction.merge(FirstCrusadeFactionManager.getFaction(mob), 1, Integer::sum);

            String name = mob.getType().getDescriptionId();
            byType.merge(name.substring(name.lastIndexOf('.') + 1), 1, Integer::sum);
        }

        header(source, "First Crusade — entidades");
        for (Map.Entry<FirstCrusadeFaction, Integer> entry : byFaction.entrySet()) {
            line(source, entry.getKey().name(), Integer.toString(entry.getValue()));
        }

        source.sendSuccess(() -> Component.literal("  --").withStyle(ChatFormatting.DARK_GRAY), false);

        for (Map.Entry<String, Integer> entry : byType.entrySet()) {
            line(source, entry.getKey(), Integer.toString(entry.getValue()));
        }

        return byFaction.size();
    }

    // ==================================================================== /fc compat

    /**
     * Which known performance mods are installed. Detection only — First Crusade requires none of
     * them and never references their classes.
     */
    private static int reportCompat(CommandSourceStack source) {
        header(source, "First Crusade — mods de performance");

        int present = 0;

        for (FirstCrusadeModCompat.KnownMod known : FirstCrusadeModCompat.KNOWN) {
            boolean loaded = known.isLoaded();
            if (loaded) {
                present++;
            }

            String text = String.format("  %s %-16s %-12s %s",
                    loaded ? "[x]" : "[ ]",
                    known.displayName(),
                    known.recommendation().name(),
                    known.side().name().replace('_', ' '));

            source.sendSuccess(() -> Component.literal(text)
                    .withStyle(loaded ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), false);
        }

        line(source, "Instalados", present + " de " + FirstCrusadeModCompat.KNOWN.size());
        source.sendSuccess(() -> Component.literal("  Nenhum e obrigatorio. Ver docs/PERFORMANCE.md")
                .withStyle(ChatFormatting.DARK_GRAY), false);

        return present;
    }

    // ==================================================================== /fc squad

    /**
     * Every live squad: who leads it, how many follow, what it is shooting at.
     *
     * <p>The shared target is the line that matters. If it is empty while the sergeant clearly has
     * an enemy, the members are all still scanning individually and the saving is not happening.
     */
    private static int reportSquads(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int squads = 0;
        int followers = 0;

        header(source, "First Crusade — esquadroes");

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || !(mob instanceof FCSquadLeader leader)) {
                continue;
            }

            FCSquad squad = leader.getSquad();
            squads++;
            followers += squad.size();

            LivingEntity shared = squad.getSharedTarget();
            BlockPos destination = squad.getDestination();

            // Two lines per squad: identity and roster on the first, what it is doing on the
            // second. One line held everything until state and orders existed, and a line that
            // wraps in a terminal is a line nobody reads.
            String head = String.format("  #%d %s [%s] %s — %d/%d membros, %s",
                    mob.getId(),
                    mob.getName().getString(),
                    squad.getId().toString().substring(0, 8),
                    FirstCrusadeFactionManager.getFaction(mob).name(),
                    squad.size(),
                    followerCapOf(mob),
                    squad.getFormation().name());

            // Suppression on the leader's line, because the leader is the one steadying everyone
            // else: a squad whose sergeant is pinned is a squad about to break, and that is not
            // visible from any other number here.
            int suppression = com.example.examplemod.ai.combat.FCSuppression.level(mob);

            String body = String.format(
                    "      estado %s, ordem %s, alvo %s, destino %s, supressao %d, LOD %s, scan %d ticks",
                    squad.getState().name(),
                    squad.getOrder().name(),
                    shared == null ? "nenhum" : shared.getName().getString(),
                    destination == null ? "nenhum" : destination.toShortString(),
                    suppression,
                    FirstCrusadeAiLod.forEntity(mob).name(),
                    squad.intervalFor(FirstCrusadePerformanceConfig.squadScanInterval()));

            source.sendSuccess(() -> Component.literal(head).withStyle(ChatFormatting.WHITE), false);
            source.sendSuccess(() -> Component.literal(body).withStyle(ChatFormatting.GRAY), false);
        }

        if (squads == 0) {
            source.sendSuccess(() -> Component.literal("  nenhum")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 0;
        }

        line(source, "Total", squads + " esquadroes, " + followers + " seguidores");
        return squads;
    }

    // ==================================================================== /fc strategic

    private static int reportStrategic(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        FCStrategicBattleData data = FCStrategicBattleData.get(level);

        header(source, "First Crusade — batalhas estrategicas");

        if (data.battles().isEmpty() && data.materialisingCount() == 0) {
            source.sendSuccess(() -> Component.literal("  nenhuma")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 0;
        }

        for (FCStrategicBattle battle : data.battles()) {
            String text = battle.describe();
            source.sendSuccess(() -> Component.literal("  " + text)
                    .withStyle(ChatFormatting.GRAY), false);
        }

        line(source, "Materializando", Integer.toString(data.materialisingCount()));

        // A non-zero value here is the only outward sign that a battle is landing somewhere its
        // units cannot be placed. It used to be unobservable for the worst reason: the units were
        // deleted instead of returned, so there was nothing left to count.
        int failed = data.failedMaterialisations();
        source.sendSuccess(() -> Component.literal("  Materializacoes adiadas: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Integer.toString(failed))
                        .withStyle(failed > 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE)), false);

        return data.battles().size();
    }

    private static int forceSweep(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        FCStrategicBattleData data = FCStrategicBattleData.get(level);

        int before = data.battles().size();
        data.forceSweep(level);
        int after = data.battles().size();

        header(source, "Varredura estrategica forcada");
        line(source, "Batalhas absorvidas", Integer.toString(after - before));

        String report = data.lastSweepReport();
        source.sendSuccess(() -> Component.literal("  " + report)
                .withStyle(ChatFormatting.DARK_GRAY), false);

        return after - before;
    }

    // ==================================================================== helpers

    /** The follower ceiling this leader is actually operating under, profile clamped by config. */
    private static int followerCapOf(Mob leader) {
        if (!(leader instanceof FCUnit unit)) {
            return 0;
        }

        return FirstCrusadePerformanceConfig.squadFollowerCap(
                unit.getUnitFaction(), unit.getCombatProfile().maxFollowers());
    }

    private static boolean isModCombatant(Mob mob) {
        FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(mob);
        return faction != FirstCrusadeFaction.NEUTRAL && faction != FirstCrusadeFaction.PLAYER;
    }

    private static void header(CommandSourceStack source, String title) {
        source.sendSuccess(() -> Component.literal(title)
                .withStyle(ChatFormatting.GOLD), false);
    }

    private static void line(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal("  " + label + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }
}
