package com.example.examplemod.campaign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.examplemod.hive.city.HiveWorld;
import com.example.examplemod.planet.FCPlanets;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The list of theatres the Crusade is fought in, and the single place that answers "is this level a
 * front?".
 *
 * <p>Built from {@link FCPlanets#ALL} rather than repeating it, so a planet added there is a front
 * without anyone remembering to add it here. The Hive World is appended separately because
 * {@code FCPlanets.ALL} deliberately excludes it — it is a dimension nobody should be dropped onto —
 * but it is still ground with an owner, and the campaign has to be able to say who holds it.
 *
 * <p>Order is travel order, which is the order the Spaceport and the navigation terminal already
 * use, so the War Table lists worlds the way the player met them.
 */
public final class CampaignFronts {

    private static final Map<ResourceLocation, CampaignFront> BY_ID = new LinkedHashMap<>();
    private static final List<CampaignFront> ORDERED = new ArrayList<>();

    static {
        for (ResourceKey<Level> planet : FCPlanets.ALL) {
            register(CampaignFront.planet(planet));
        }

        register(CampaignFront.of(HiveWorld.LEVEL, CampaignFrontType.HIVE));
    }

    private CampaignFronts() {
    }

    private static void register(CampaignFront front) {
        BY_ID.put(front.id(), front);
        ORDERED.add(front);
    }

    /** Every front, in travel order. */
    public static List<CampaignFront> all() {
        return List.copyOf(ORDERED);
    }

    public static Optional<CampaignFront> byId(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Optional<CampaignFront> byDimension(ResourceKey<Level> dimension) {
        return dimension == null ? Optional.empty() : byId(dimension.location());
    }

    /**
     * Accepts what a player typed: a bare path ({@code armageddon}) or a full id
     * ({@code firstcrusade:armageddon}). The bare form is what anyone types at a console, so the
     * commands accept it rather than making the namespace mandatory.
     */
    public static Optional<CampaignFront> byName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }

        ResourceLocation parsed = raw.indexOf(':') >= 0
                ? ResourceLocation.tryParse(raw)
                : new ResourceLocation(com.example.examplemod.ExampleMod.MODID, raw);

        return parsed == null ? Optional.empty() : byId(parsed);
    }

    public static boolean isFront(ResourceKey<Level> dimension) {
        return dimension != null && BY_ID.containsKey(dimension.location());
    }

    /** The names the command's tab-completion offers. */
    public static List<String> names() {
        List<String> names = new ArrayList<>(ORDERED.size());

        for (CampaignFront front : ORDERED) {
            names.add(front.path());
        }

        return names;
    }
}
