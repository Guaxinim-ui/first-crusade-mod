package com.example.examplemod.hive;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;

import com.example.examplemod.ExampleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Carrega os módulos de data/&lt;ns&gt;/hive_modules/**.json (datapack-reloadável com /reload).
 *
 * Formato:
 * {
 *   "template": "firstcrusade:hive/street/industrial_street_01",
 *   "category": "street",
 *   "size": [64, 48, 64],
 *   "weight": 10,
 *   "sockets": { "north": "street", "south": "street",
 *                "west": "corridor_l2", "east": "corridor_l2",
 *                "up": "canopy", "down": "foundation" }
 * }
 * Faces omitidas em "sockets" viram "sealed". O id do módulo é o caminho do arquivo
 * (ex.: firstcrusade:street/industrial_street_01).
 *
 * Auto-registrado via @Mod.EventBusSubscriber — nenhuma alteração em classes existentes.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class HiveModuleManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    public static final HiveModuleManager INSTANCE = new HiveModuleManager();

    private volatile Map<ResourceLocation, HiveModule> modules = Map.of();

    private HiveModuleManager() {
        super(GSON, "hive_modules");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, HiveModule> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                loaded.put(id, parse(id, entry.getValue().getAsJsonObject()));
            } catch (Exception e) {
                LOGGER.error("[fchive] Módulo inválido {}: {}", id, e.toString());
            }
        }
        this.modules = Map.copyOf(loaded);
        LOGGER.info("[fchive] {} módulos de Hive carregados", loaded.size());
    }

    private static HiveModule parse(ResourceLocation id, JsonObject json) {
        ResourceLocation template = new ResourceLocation(json.get("template").getAsString());
        String category = json.has("category") ? json.get("category").getAsString() : "misc";
        JsonArray sizeArr = json.getAsJsonArray("size");
        Vec3i size = new Vec3i(sizeArr.get(0).getAsInt(), sizeArr.get(1).getAsInt(), sizeArr.get(2).getAsInt());
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;

        Map<Direction, String> sockets = new HashMap<>();
        if (json.has("sockets")) {
            JsonObject s = json.getAsJsonObject("sockets");
            for (Direction d : Direction.values()) {
                String key = d.getName();
                if (s.has(key)) {
                    sockets.put(d, s.get(key).getAsString());
                }
            }
        }
        return new HiveModule(id, template, category, size, weight, Map.copyOf(sockets));
    }

    // ------------------------------------------------------------------ consultas

    public static Optional<HiveModule> get(ResourceLocation id) {
        return Optional.ofNullable(INSTANCE.modules.get(id));
    }

    public static Collection<HiveModule> all() {
        return INSTANCE.modules.values();
    }

    public static List<HiveModule> byCategory(String category) {
        return INSTANCE.modules.values().stream()
                .filter(m -> m.category().equals(category))
                .toList();
    }

    public static Collection<ResourceLocation> ids() {
        return INSTANCE.modules.keySet();
    }
}
