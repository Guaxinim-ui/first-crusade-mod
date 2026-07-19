package com.example.examplemod.hive;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.examplemod.ExampleMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * FASE 4 — comandos de desenvolvimento da Hive City (raiz /fchive, permissão 2).
 *
 *   /fchive place <template> [rot 0-3]        cola template; marcadores viram ar + capturados
 *   /fchive place_raw <template> [rot 0-3]    cola SEM processar marcadores (edição de módulo)
 *   /fchive module list [categoria]           módulos registrados (data/ns/hive_modules)
 *   /fchive module info <módulo>              template, tamanho, sockets por face
 *   /fchive module place <módulo> [rot 0-3]   cola via registro + relata sockets no mundo
 *   /fchive markers                           re-lista a última captura, com partículas
 *   /fchive show_bounds <módulo> [rot 0-3]    contorno de partículas da bounding box (não coloca)
 *   /fchive save <template> <from> <to>       captura região de QUALQUER tamanho (sem limite 48³)
 *   /fchive clear <from> <to>                 limpa região (guarda de volume 64³)
 *
 * Auto-registrado via @Mod.EventBusSubscriber — nenhuma classe existente é alterada.
 * Colocação instantânea = modo desenvolvimento (spec §8); geração escalonada é FASE 10.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class HiveCommands {

    private static final int CLEAR_MAX_VOLUME = 64 * 64 * 64;

    private HiveCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fchive")
                .requires(source -> source.hasPermission(2))

                // FASE 10 — city generation subtree (/fchive city generate|status|cancel|tp).
                .then(com.example.examplemod.hive.city.HiveCityCommands.build())

                .then(Commands.literal("place")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .executes(ctx -> placeTemplate(ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "template"), 0, true))
                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> placeTemplate(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "template"),
                                                IntegerArgumentType.getInteger(ctx, "rotation"), true)))))

                .then(Commands.literal("place_raw")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .executes(ctx -> placeTemplate(ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "template"), 0, false))
                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> placeTemplate(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "template"),
                                                IntegerArgumentType.getInteger(ctx, "rotation"), false)))))

                .then(Commands.literal("module")
                        .then(Commands.literal("list")
                                .executes(ctx -> moduleList(ctx.getSource(), null))
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .executes(ctx -> moduleList(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "category")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("module", ResourceLocationArgument.id())
                                        .suggests((ctx, b) -> SharedSuggestionProvider
                                                .suggestResource(HiveModuleManager.ids(), b))
                                        .executes(ctx -> moduleInfo(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "module")))))
                        .then(Commands.literal("place")
                                .then(Commands.argument("module", ResourceLocationArgument.id())
                                        .suggests((ctx, b) -> SharedSuggestionProvider
                                                .suggestResource(HiveModuleManager.ids(), b))
                                        .executes(ctx -> modulePlace(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "module"), 0))
                                        .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                                .executes(ctx -> modulePlace(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "module"),
                                                        IntegerArgumentType.getInteger(ctx, "rotation")))))))

                .then(Commands.literal("district")
                        .then(Commands.literal("list")
                                .executes(ctx -> districtList(ctx.getSource())))
                        .then(Commands.literal("place")
                                .then(Commands.argument("district", ResourceLocationArgument.id())
                                        .suggests((ctx, b) -> SharedSuggestionProvider
                                                .suggestResource(HiveDistricts.ids(), b))
                                        .executes(ctx -> districtPlace(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "district"))))))

                .then(Commands.literal("markers")
                        .executes(ctx -> listMarkers(ctx.getSource())))

                .then(Commands.literal("show_bounds")
                        .then(Commands.argument("module", ResourceLocationArgument.id())
                                .suggests((ctx, b) -> SharedSuggestionProvider
                                        .suggestResource(HiveModuleManager.ids(), b))
                                .executes(ctx -> showBounds(ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "module"), 0))
                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> showBounds(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "module"),
                                                IntegerArgumentType.getInteger(ctx, "rotation"))))))

                .then(Commands.literal("save")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(ctx -> save(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "template"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to")))))))

                .then(Commands.literal("clear")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> clear(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "to")))))));
    }

    // ------------------------------------------------------------------ núcleo de colocação

    private static Rotation rotation(int index) {
        return switch (index) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * Minecraft rotates structure coordinates around local (0,0,0). For 90/180-degree rotations
     * that pushes part of the template into negative X/Z. This converts a desired world minimum
     * corner into the placement origin required to keep the rotated template inside that minimum.
     */
    private static BlockPos placementOriginForMinimum(BlockPos minimum, Vec3i templateSize,
                                                       Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> minimum.offset(templateSize.getZ() - 1, 0, 0);
            case CLOCKWISE_180 -> minimum.offset(templateSize.getX() - 1, 0,
                    templateSize.getZ() - 1);
            case COUNTERCLOCKWISE_90 -> minimum.offset(0, 0, templateSize.getX() - 1);
            default -> minimum;
        };
    }

    private record DistrictFootprint(int width, int depth) {
    }

    /** Footprint before the optional whole-district rotation. */
    private static DistrictFootprint districtFootprint(HiveDistricts.District district) {
        int width = 0;
        int depth = 0;
        for (HiveDistricts.Entry entry : district.entries()) {
            Optional<HiveModule> module = HiveModuleManager.get(entry.module());
            if (module.isEmpty()) continue;
            Vec3i size = module.get().rotatedSize(rotation(entry.rotation()));
            width = Math.max(width, entry.offset().getX() + size.getX());
            depth = Math.max(depth, entry.offset().getZ() + size.getZ());
        }
        return new DistrictFootprint(width, depth);
    }

    /**
     * Rotate one module bounding box inside the normalized district footprint. Unlike rotating only
     * the offset vector, this keeps every rotated district in positive local coordinates.
     */
    private static BlockPos rotatedEntryMinimum(HiveDistricts.Entry entry, HiveModule module,
                                                 DistrictFootprint footprint, Rotation districtRot) {
        Vec3i localSize = module.rotatedSize(rotation(entry.rotation()));
        int x = entry.offset().getX();
        int y = entry.offset().getY();
        int z = entry.offset().getZ();
        return switch (districtRot) {
            case CLOCKWISE_90 -> new BlockPos(footprint.depth() - (z + localSize.getZ()), y, x);
            case CLOCKWISE_180 -> new BlockPos(
                    footprint.width() - (x + localSize.getX()), y,
                    footprint.depth() - (z + localSize.getZ()));
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y,
                    footprint.width() - (x + localSize.getX()));
            default -> new BlockPos(x, y, z);
        };
    }

    private static int placeTemplate(CommandSourceStack source, ResourceLocation templateId,
                                     int rotationIndex, boolean processMarkers) {
        ServerLevel level = source.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> template = manager.get(templateId);
        if (template.isEmpty()) {
            source.sendFailure(Component.literal("[fchive] Template não encontrado: " + templateId));
            return 0;
        }

        BlockPos minimum = BlockPos.containing(source.getPosition());
        Rotation rot = rotation(rotationIndex);
        BlockPos origin = placementOriginForMinimum(minimum, template.get().getSize(), rot);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rot)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(true);
        if (processMarkers) {
            settings.addProcessor(HiveMarkerProcessor.INSTANCE);
            HiveMarkers.begin();
        }

        boolean ok = template.get().placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        List<HiveMarkers.Captured> captured = processMarkers ? HiveMarkers.end() : List.of();
        if (!ok) {
            source.sendFailure(Component.literal("[fchive] Falha ao colocar " + templateId));
            return 0;
        }

        Vec3i size = template.get().getSize();
        source.sendSuccess(() -> Component.literal(
                "[fchive] " + templateId + " (" + size.getX() + "x" + size.getY() + "x" + size.getZ()
                        + ") em " + minimum.toShortString() + ", rot " + (rotationIndex * 90) + "°"
                        + (processMarkers ? " | " + summarizeMarkers(captured) : " | marcadores mantidos")),
                true);
        return 1;
    }

    private static String summarizeMarkers(List<HiveMarkers.Captured> captured) {
        if (captured.isEmpty()) {
            return "0 marcadores";
        }
        Map<HiveMarkers.MarkerType, Integer> counts = new EnumMap<>(HiveMarkers.MarkerType.class);
        for (HiveMarkers.Captured c : captured) {
            counts.merge(c.type(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder(captured.size() + " marcadores (");
        boolean first = true;
        for (Map.Entry<HiveMarkers.MarkerType, Integer> e : counts.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getValue()).append("x ").append(e.getKey().display());
            first = false;
        }
        return sb.append(") — detalhe: /fchive markers").toString();
    }

    // ------------------------------------------------------------------ módulos

    private static int moduleList(CommandSourceStack source, String category) {
        var mods = category == null ? HiveModuleManager.all() : HiveModuleManager.byCategory(category);
        if (mods.isEmpty()) {
            source.sendFailure(Component.literal("[fchive] Nenhum módulo"
                    + (category == null ? " registrado" : " na categoria '" + category + "'")));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[fchive] " + mods.size() + " módulo(s):"), false);
        for (HiveModule m : mods) {
            source.sendSuccess(() -> Component.literal("  " + m.id() + "  [" + m.category() + "]  "
                    + m.size().getX() + "x" + m.size().getY() + "x" + m.size().getZ()
                    + "  peso " + m.weight()), false);
        }
        return mods.size();
    }

    private static int moduleInfo(CommandSourceStack source, ResourceLocation id) {
        Optional<HiveModule> module = HiveModuleManager.get(id);
        if (module.isEmpty()) {
            source.sendFailure(Component.literal("[fchive] Módulo não encontrado: " + id));
            return 0;
        }
        HiveModule m = module.get();
        source.sendSuccess(() -> Component.literal("[fchive] " + m.id()
                + "\n  template: " + m.template()
                + "\n  categoria: " + m.category() + " | tamanho: " + m.size().getX() + "x"
                + m.size().getY() + "x" + m.size().getZ() + " | peso: " + m.weight()), false);
        for (Direction d : Direction.values()) {
            String socket = m.sockets().getOrDefault(d, HiveModule.SOCKET_SEALED);
            source.sendSuccess(() -> Component.literal("  socket " + d.getName() + ": " + socket), false);
        }
        return 1;
    }

    private static int modulePlace(CommandSourceStack source, ResourceLocation id, int rotationIndex) {
        Optional<HiveModule> module = HiveModuleManager.get(id);
        if (module.isEmpty()) {
            source.sendFailure(Component.literal("[fchive] Módulo não encontrado: " + id));
            return 0;
        }
        HiveModule m = module.get();
        int result = placeTemplate(source, m.template(), rotationIndex, true);
        if (result > 0) {
            Rotation rot = rotation(rotationIndex);
            StringBuilder sb = new StringBuilder("[fchive] sockets no mundo:");
            for (Direction d : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST, Direction.UP, Direction.DOWN)) {
                sb.append(" ").append(d.getName()).append("=").append(m.socketAt(d, rot));
            }
            source.sendSuccess(() -> Component.literal(sb.toString()), false);
        }
        return result;
    }

    // ------------------------------------------------------------------ marcadores / bounds

    private static int listMarkers(CommandSourceStack source) {
        List<HiveMarkers.Captured> last = HiveMarkers.last();
        if (last.isEmpty()) {
            source.sendFailure(Component.literal("[fchive] Nenhuma captura de marcadores ainda"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        int shown = 0;
        for (HiveMarkers.Captured c : last) {
            level.sendParticles(ParticleTypes.END_ROD,
                    c.pos().getX() + 0.5, c.pos().getY() + 0.5, c.pos().getZ() + 0.5,
                    8, 0.1, 0.1, 0.1, 0.0);
            if (shown < 24) {
                source.sendSuccess(() -> Component.literal("  " + c.type().display()
                        + " @ " + c.pos().toShortString()), false);
            }
            shown++;
        }
        int total = shown;
        source.sendSuccess(() -> Component.literal("[fchive] " + total + " marcadores da última colocação"
                + (total > 24 ? " (listados 24)" : "")), true);
        return total;
    }

    private static int showBounds(CommandSourceStack source, ResourceLocation id, int rotationIndex) {
        Optional<HiveModule> module = HiveModuleManager.get(id);
        if (module.isEmpty()) {
            source.sendFailure(Component.literal("[fchive] Módulo não encontrado: " + id));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos min = BlockPos.containing(source.getPosition());
        Vec3i size = module.get().rotatedSize(rotation(rotationIndex));
        double x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
        double x1 = x0 + size.getX(), y1 = y0 + size.getY(), z1 = z0 + size.getZ();

        for (double x = x0; x <= x1; x += 1.0) {
            for (double y : new double[]{y0, y1}) {
                for (double z : new double[]{z0, z1}) {
                    level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
        for (double y = y0; y <= y1; y += 1.0) {
            for (double x : new double[]{x0, x1}) {
                for (double z : new double[]{z0, z1}) {
                    level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
        for (double z = z0; z <= z1; z += 1.0) {
            for (double x : new double[]{x0, x1}) {
                for (double y : new double[]{y0, y1}) {
                    level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("[fchive] bounds " + id + " rot " + (rotationIndex * 90)
                + "°: min " + min.toShortString() + ", tamanho " + size.getX() + "x" + size.getY()
                + "x" + size.getZ() + " (partículas por alguns segundos)"), false);
        return 1;
    }

    // ------------------------------------------------------------------ distritos

    private static int districtList(CommandSourceStack source) {
        var ids = HiveDistricts.ids();
        if (ids.isEmpty()) {
            source.sendFailure(Component.literal("[fchive] Nenhum distrito registrado"));
            return 0;
        }
        for (ResourceLocation id : ids) {
            HiveDistricts.get(id).ifPresent(d -> source.sendSuccess(() -> Component.literal(
                    "  " + id + " — " + d.entries().size() + " módulos. " + d.description()), false));
        }
        return ids.size();
    }

    private record Placed(HiveModule module, Rotation rot, BlockPos min, Vec3i size) {
    }

    private static int districtPlace(CommandSourceStack source, ResourceLocation id) {
        Optional<HiveDistricts.District> district = HiveDistricts.get(id);
        if (district.isEmpty()) {
            source.sendFailure(Component.literal("[fchive] Distrito não encontrado: " + id));
            return 0;
        }
        ServerLevel level = source.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        BlockPos origin = BlockPos.containing(source.getPosition());

        HiveMarkers.begin();
        List<Placed> placed = new java.util.ArrayList<>();
        int okCount = 0;
        for (HiveDistricts.Entry entry : district.get().entries()) {
            Optional<HiveModule> module = HiveModuleManager.get(entry.module());
            if (module.isEmpty()) {
                source.sendFailure(Component.literal("[fchive] módulo ausente: " + entry.module()));
                continue;
            }
            Optional<StructureTemplate> template = manager.get(module.get().template());
            if (template.isEmpty()) {
                source.sendFailure(Component.literal("[fchive] template ausente: " + module.get().template()));
                continue;
            }
            Rotation rot = rotation(entry.rotation());
            BlockPos at = origin.offset(entry.offset().getX(), entry.offset().getY(), entry.offset().getZ());
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rot).setMirror(Mirror.NONE).setIgnoreEntities(true)
                    .addProcessor(HiveMarkerProcessor.INSTANCE);
            if (template.get().placeInWorld(level, at, at, settings, level.getRandom(), 2)) {
                placed.add(new Placed(module.get(), rot, at, module.get().rotatedSize(rot)));
                okCount++;
            }
        }
        List<HiveMarkers.Captured> captured = HiveMarkers.end();

        // validação de costuras entre módulos adjacentes
        int seams = 0, mismatches = 0;
        for (int i = 0; i < placed.size(); i++) {
            for (int j = 0; j < placed.size(); j++) {
                if (i == j) continue;
                Placed a = placed.get(i), b = placed.get(j);
                Direction face = touchingFace(a, b);
                if (face == null || face == Direction.WEST || face == Direction.NORTH
                        || face == Direction.DOWN) continue;   // conta cada costura uma vez
                seams++;
                String sa = a.module().socketAt(face, a.rot());
                String sb = b.module().socketAt(face.getOpposite(), b.rot());
                boolean fit = HiveModule.fits(sa, sb);
                if (!fit) mismatches++;
                final String line = (fit ? "  ✔ " : "  ✖ ") + a.module().id() + " " + face.getName()
                        + " [" + sa + "] ↔ [" + sb + "] " + b.module().id();
                if (!fit) source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        final int fOk = okCount, fSeams = seams, fMis = mismatches;
        source.sendSuccess(() -> Component.literal("[fchive] Distrito " + id + ": " + fOk
                + " módulos colocados em " + origin.toShortString() + " | costuras: " + fSeams
                + " (" + (fMis == 0 ? "todas encaixam" : fMis + " incompatíveis") + ") | "
                + summarizeMarkers(captured)), true);
        return okCount;
    }

    /**
     * Reusable district placement, shared by {@code /fchive district place} and the FASE 10 city
     * generator ({@link com.example.examplemod.hive.city.HiveCityPlacer}). Places every module of the
     * district at {@code origin} (offsets rotated by {@code districtRotation}), each rotated and run
     * through {@link HiveMarkerProcessor}. Returns how many modules were placed. No chat output.
     */
    public static int placeDistrict(ServerLevel level, ResourceLocation id, BlockPos origin, int districtRotation) {
        Optional<HiveDistricts.District> district = HiveDistricts.get(id);
        if (district.isEmpty()) {
            return 0;
        }
        StructureTemplateManager manager = level.getStructureManager();
        Rotation districtRot = rotation(districtRotation);
        DistrictFootprint footprint = districtFootprint(district.get());
        HiveMarkers.begin();
        int okCount = 0;
        for (HiveDistricts.Entry entry : district.get().entries()) {
            Optional<HiveModule> module = HiveModuleManager.get(entry.module());
            if (module.isEmpty()) {
                continue;
            }
            Optional<StructureTemplate> template = manager.get(module.get().template());
            if (template.isEmpty()) {
                continue;
            }

            Rotation finalRot = districtRot.getRotated(rotation(entry.rotation()));
            BlockPos localMinimum = rotatedEntryMinimum(entry, module.get(), footprint, districtRot);
            BlockPos worldMinimum = origin.offset(
                    localMinimum.getX(), localMinimum.getY(), localMinimum.getZ());
            BlockPos placementOrigin = placementOriginForMinimum(
                    worldMinimum, template.get().getSize(), finalRot);

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(finalRot).setMirror(Mirror.NONE).setIgnoreEntities(true)
                    .addProcessor(HiveMarkerProcessor.INSTANCE);
            if (template.get().placeInWorld(level, placementOrigin, placementOrigin,
                    settings, level.getRandom(), 2)) {
                okCount++;
            }
        }
        HiveMarkers.end();
        return okCount;
    }

    /** Face de A que encosta em B (ou null). Exige alinhamento de intervalo nos outros eixos. */
    private static Direction touchingFace(Placed a, Placed b) {
        int ax0 = a.min().getX(), ax1 = ax0 + a.size().getX();
        int ay0 = a.min().getY(), ay1 = ay0 + a.size().getY();
        int az0 = a.min().getZ(), az1 = az0 + a.size().getZ();
        int bx0 = b.min().getX(), bx1 = bx0 + b.size().getX();
        int by0 = b.min().getY(), by1 = by0 + b.size().getY();
        int bz0 = b.min().getZ(), bz1 = bz0 + b.size().getZ();
        boolean xo = ax0 < bx1 && bx0 < ax1, yo = ay0 < by1 && by0 < ay1, zo = az0 < bz1 && bz0 < az1;
        if (ax1 == bx0 && yo && zo) return Direction.EAST;
        if (bx1 == ax0 && yo && zo) return Direction.WEST;
        if (az1 == bz0 && xo && yo) return Direction.SOUTH;
        if (bz1 == az0 && xo && yo) return Direction.NORTH;
        if (ay1 == by0 && xo && zo) return Direction.UP;
        if (by1 == ay0 && xo && zo) return Direction.DOWN;
        return null;
    }

    // ------------------------------------------------------------------ save / clear

    private static int save(CommandSourceStack source, ResourceLocation id, BlockPos from, BlockPos to) {
        ServerLevel level = source.getLevel();
        StructureTemplateManager manager = level.getStructureManager();

        BoundingBox box = BoundingBox.fromCorners(from, to);
        BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
        Vec3i size = new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan());

        StructureTemplate template = manager.getOrCreate(id);
        template.fillFromWorld(level, min, size, false, Blocks.STRUCTURE_VOID);

        if (!manager.save(id)) {
            source.sendFailure(Component.literal("[fchive] Falha ao salvar " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[fchive] Salvo " + id + " ("
                + size.getX() + "x" + size.getY() + "x" + size.getZ()
                + ") em (mundo)/generated/" + id.getNamespace() + "/structures/" + id.getPath()
                + ".nbt — structure_void é ignorado."), true);
        return 1;
    }

    private static int clear(CommandSourceStack source, BlockPos from, BlockPos to) {
        BoundingBox box = BoundingBox.fromCorners(from, to);
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > CLEAR_MAX_VOLUME) {
            source.sendFailure(Component.literal("[fchive] Região grande demais (" + volume
                    + " > " + CLEAR_MAX_VOLUME + "). Divida em partes."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        var air = Blocks.AIR.defaultBlockState();
        int cleared = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (level.setBlock(new BlockPos(x, y, z), air, 2)) {
                        cleared++;
                    }
                }
            }
        }
        int total = cleared;
        source.sendSuccess(() -> Component.literal("[fchive] " + total + " blocos limpos em "
                + box.getXSpan() + "x" + box.getYSpan() + "x" + box.getZSpan()), true);
        return total;
    }
}
