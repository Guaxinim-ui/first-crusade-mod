package com.example.examplemod.hive;

import java.util.ArrayList;
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

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Layouts de distrito (FASE 5): listas de módulos com offset e rotação, carregadas de
 * data/&lt;ns&gt;/hive_districts/*.json e coladas de uma vez por /fchive district place.
 * É a versão manual/dev do que a FASE 10 fará proceduralmente por seed.
 *
 * Formato:
 * { "description": "...",
 *   "modules": [ {"module":"firstcrusade:cargo/cargo_yard_01","offset":[64,0,0],"rotation":0}, ... ] }
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class HiveDistricts extends SimpleJsonResourceReloadListener {

    public record Entry(ResourceLocation module, Vec3i offset, int rotation) {
    }

    public record District(ResourceLocation id, String description, List<Entry> entries) {
    }

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    public static final HiveDistricts INSTANCE = new HiveDistricts();

    private volatile Map<ResourceLocation, District> districts = Map.of();

    private HiveDistricts() {
        super(GSON, "hive_districts");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, District> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : jsons.entrySet()) {
            try {
                JsonObject json = e.getValue().getAsJsonObject();
                String desc = json.has("description") ? json.get("description").getAsString() : "";
                List<Entry> entries = new ArrayList<>();
                for (JsonElement el : json.getAsJsonArray("modules")) {
                    JsonObject m = el.getAsJsonObject();
                    JsonArray off = m.getAsJsonArray("offset");
                    entries.add(new Entry(
                            new ResourceLocation(m.get("module").getAsString()),
                            new Vec3i(off.get(0).getAsInt(), off.get(1).getAsInt(), off.get(2).getAsInt()),
                            m.has("rotation") ? m.get("rotation").getAsInt() : 0));
                }
                loaded.put(e.getKey(), new District(e.getKey(), desc, List.copyOf(entries)));
            } catch (Exception ex) {
                LOGGER.error("[fchive] Distrito inválido {}: {}", e.getKey(), ex.toString());
            }
        }
        this.districts = Map.copyOf(loaded);
        LOGGER.info("[fchive] {} distritos de Hive carregados", loaded.size());
    }

    public static Optional<District> get(ResourceLocation id) {
        return Optional.ofNullable(INSTANCE.districts.get(id));
    }

    public static Collection<ResourceLocation> ids() {
        return INSTANCE.districts.keySet();
    }
}
