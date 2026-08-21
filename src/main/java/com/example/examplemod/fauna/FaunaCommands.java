package com.example.examplemod.fauna;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.examplemod.ExampleMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /fauna} — o comando de teste da fauna.
 *
 * <h2>Existe para responder tres perguntas, e so tres</h2>
 *
 * <ul>
 *   <li>{@code /fauna spawn <especie> [quantos]} — o modelo carregou? a animacao toca? a caixa
 *       bate com o corpo? as patas encostam no chao? Sem isto, ver um Catachan Devil exige achar
 *       um em 900 chunks.</li>
 *   <li>{@code /fauna list} — quais sao os nomes, para nao ter de abrir o registro.</li>
 *   <li>{@code /fauna count} — quantos de cada especie existem no mundo carregado, que e a unica
 *       forma barata de flagrar spawn descontrolado.</li>
 * </ul>
 *
 * <p>Nivel 2 de permissao, como todo comando de operador do mod. E nao imprime nada no console
 * sozinho: o briefing pede para nao deixar spam, e um comando que so fala quando chamado cumpre
 * isso por construcao.
 *
 * <h2>Raiz propria, e nao {@code /fc fauna}</h2>
 *
 * {@code /fc} e o comando de performance e de estado do exercito. Pendurar a fauna la juntaria dois
 * assuntos que nunca sao investigados juntos, e o autocomplete de {@code /fc} ja tem quatro ramos.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FaunaCommands {

    private FaunaCommands() {
    }

    /** Quantos um {@code spawn} sem numero cria. */
    private static final int DEFAULT_COUNT = 1;

    /** Teto por comando. Alto o bastante para testar manada, baixo o bastante para nao travar. */
    private static final int MAX_COUNT = 32;

    /**
     * Nome curto -> tipo. Ordem de insercao preservada, entao {@code /fauna list} sai agrupado por
     * ambiente em vez de em ordem alfabetica — que e a ordem em que alguem procura um bicho.
     */
    private static Map<String, EntityType<?>> species() {
        Map<String, EntityType<?>> map = new LinkedHashMap<>();

        // Fase E
        map.put("grox", com.example.examplemod.animal.FCAnimals.GROX.get());
        map.put("cyber_mastiff", com.example.examplemod.animal.FCAnimals.CYBER_MASTIFF.get());
        map.put("squig", com.example.examplemod.animal.FCAnimals.SQUIG.get());
        map.put("sump_rat", com.example.examplemod.animal.FCAnimals.SUMP_RAT.get());
        map.put("ash_strider", com.example.examplemod.animal.FCAnimals.ASH_STRIDER.get());
        map.put("ambull", com.example.examplemod.animal.FCAnimals.AMBULL.get());

        // Fauna dos modelos do Blockbench
        map.put("fenrisian_wolf", FirstCrusadeFaunaRegistry.FENRISIAN_WOLF.get());
        map.put("duneskuttler", FirstCrusadeFaunaRegistry.DUNESKUTTLER.get());
        map.put("dustback_helamite", FirstCrusadeFaunaRegistry.DUSTBACK_HELAMITE.get());
        map.put("cudbear", FirstCrusadeFaunaRegistry.CTHELLEAN_CUDBEAR.get());
        map.put("duskhorn", FirstCrusadeFaunaRegistry.DUSKHORN.get());
        map.put("knarloc", FirstCrusadeFaunaRegistry.KNARLOC.get());
        map.put("constrictor", FirstCrusadeFaunaRegistry.CONSTRICTOR.get());
        map.put("barking_toad", FirstCrusadeFaunaRegistry.BARKING_TOAD.get());
        map.put("catachan_devil", FirstCrusadeFaunaRegistry.CATACHAN_DEVIL.get());

        return map;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fauna")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("list")
                .executes(context -> list(context.getSource())));

        root.then(Commands.literal("count")
                .executes(context -> count(context.getSource())));

        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");

        for (Map.Entry<String, EntityType<?>> entry : species().entrySet()) {
            spawn.then(Commands.literal(entry.getKey())
                    .executes(context ->
                            spawn(context.getSource(), entry.getValue(), DEFAULT_COUNT))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                            .executes(context -> spawn(context.getSource(), entry.getValue(),
                                    IntegerArgumentType.getInteger(context, "count")))));
        }

        root.then(spawn);

        event.getDispatcher().register(root);
    }

    // ==================================================================== spawn

    /**
     * Cria bichos a frente de quem chamou.
     *
     * <p>{@code MobSpawnType.COMMAND} de proposito: as regras de spawn da fauna deixam passar spawn
     * deliberado sem aplicar bioma, luz nem teto de populacao. Um comando de teste que recusasse o
     * bicho por causa da regra que se esta testando seria inutil justamente quando importa.
     */
    private static int spawn(CommandSourceStack source, EntityType<?> type, int count) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();

        // Uma manada de cinco empilhada num ponto so se empurraria para fora do mundo no primeiro
        // tick de colisao. O espalhamento cresce com a quantidade pedida.
        double spread = count == 1 ? 0.0D : 1.0D + count * 0.25D;
        int placed = 0;

        for (int i = 0; i < count; i++) {
            Entity entity = type.create(level);
            if (entity == null) {
                source.sendFailure(Component.literal("tipo invalido: " + type));
                return 0;
            }

            double x = origin.x + (level.random.nextDouble() - 0.5D) * spread * 2.0D;
            double z = origin.z + (level.random.nextDouble() - 0.5D) * spread * 2.0D;

            entity.moveTo(x, origin.y, z, level.random.nextFloat() * 360.0F, 0.0F);

            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x,
                        origin.y, z)), MobSpawnType.COMMAND, null, null);
            }

            if (level.addFreshEntity(entity)) {
                placed++;
            }
        }

        int total = placed;
        source.sendSuccess(() -> Component.literal(
                "fauna: " + total + " x " + type.getDescriptionId()), true);
        return placed;
    }

    // ==================================================================== relatorios

    private static int list(CommandSourceStack source) {
        StringBuilder text = new StringBuilder("especies (" + species().size() + "):");

        for (String name : species().keySet()) {
            text.append("\n  ").append(name);
        }

        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return species().size();
    }

    /**
     * Conta cada especie no mundo carregado.
     *
     * <p>Uma varredura de {@code getAllEntities}, e so quando alguem digita o comando. Isto e um
     * instrumento de diagnostico, nao um sistema: o custo dele existe uma vez por chamada, e a
     * alternativa (um contador mantido por evento de spawn e morte) seria estado permanente para
     * responder uma pergunta que so se faz durante o desenvolvimento.
     */
    private static int count(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Map<String, Integer> tally = new LinkedHashMap<>();
        Map<String, EntityType<?>> known = species();

        for (String name : known.keySet()) {
            tally.put(name, 0);
        }

        int total = 0;

        for (Entity entity : level.getAllEntities()) {
            for (Map.Entry<String, EntityType<?>> entry : known.entrySet()) {
                if (entity.getType() == entry.getValue()) {
                    tally.merge(entry.getKey(), 1, Integer::sum);
                    total++;
                    break;
                }
            }
        }

        StringBuilder text = new StringBuilder("fauna carregada em "
                + level.dimension().location() + ": " + total);

        for (Map.Entry<String, Integer> entry : tally.entrySet()) {
            if (entry.getValue() > 0) {
                text.append("\n  ").append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }

        if (total == 0) {
            text.append("\n  (nenhuma)");
        }

        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return total;
    }
}
