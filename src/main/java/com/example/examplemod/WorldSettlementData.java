package com.example.examplemod;

import java.util.HashSet;
import java.util.Set;

import com.example.examplemod.planet.FCPlanets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which worlds have already been populated with their starting settlements (Imperial cities + Ork
 * camps).
 *
 * <h2>Once per planet, not once per save</h2>
 *
 * This used to be a single boolean, {@code planetSeeded}, on a store that always resolves to the
 * overworld. The first traveller to land anywhere set it, and every other planet was then
 * permanently barren: Macragge being populated was what stopped Armageddon, Cadia and the rest from
 * ever generating a settlement. The flag is now a set of dimension ids, so each world answers for
 * itself.
 *
 * <p>The store stays on the overworld deliberately. It has to be readable while deciding whether to
 * seed a level that is only now being entered, and it is a handful of strings.
 *
 * <p><b>Old saves:</b> a {@code PlanetSeeded=true} with no set behind it is read as
 * "{@link FCPlanets#DEFAULT} was seeded" — the planet a pre-campaign save could actually have
 * populated. Everything else stays unseeded and will populate on first arrival, which is the
 * behaviour the owner of such a save was expecting all along.
 *
 * @see WorldSettlementSeeder
 */
public class WorldSettlementData extends SavedData {
    private static final String NAME = "firstcrusade_settlements";

    /** The overworld's own starting layout (the test war-pair), which is not a planet. */
    private boolean seeded = false;

    /** Dimension ids whose starting settlements have been planted. */
    private final Set<String> seededPlanets = new HashSet<>();

    public WorldSettlementData() {
    }

    public static WorldSettlementData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldSettlementData::load, WorldSettlementData::new, NAME);
    }

    public static WorldSettlementData load(CompoundTag tag) {
        WorldSettlementData data = new WorldSettlementData();
        data.seeded = tag.getBoolean("Seeded");

        ListTag planets = tag.getList("SeededPlanets", Tag.TAG_STRING);

        for (int i = 0; i < planets.size(); i++) {
            data.seededPlanets.add(planets.getString(i));
        }

        // Legacy single flag: it could only ever have meant the first planet anyone travelled to,
        // and the Crusade begins on Macragge.
        if (planets.isEmpty() && tag.getBoolean("PlanetSeeded")) {
            data.seededPlanets.add(FCPlanets.DEFAULT.location().toString());
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Seeded", this.seeded);

        ListTag planets = new ListTag();

        for (String id : this.seededPlanets) {
            planets.add(StringTag.valueOf(id));
        }

        tag.put("SeededPlanets", planets);

        return tag;
    }

    public boolean isSeeded() {
        return this.seeded;
    }

    public void markSeeded() {
        this.seeded = true;
        setDirty();
    }

    public boolean isPlanetSeeded(ResourceKey<Level> planet) {
        return this.seededPlanets.contains(planet.location().toString());
    }

    public void markPlanetSeeded(ResourceKey<Level> planet) {
        if (this.seededPlanets.add(planet.location().toString())) {
            setDirty();
        }
    }

    /**
     * Forgets that a planet was seeded, so the next arrival populates it again. For
     * {@code /fcstrategy planet reseed}: testing the seeder used to require a whole new world.
     */
    public boolean clearPlanetSeeded(ResourceKey<Level> planet) {
        if (this.seededPlanets.remove(planet.location().toString())) {
            setDirty();
            return true;
        }

        return false;
    }

    /** The dimensions populated so far, for the debug commands. */
    public Set<ResourceLocation> seededPlanets() {
        Set<ResourceLocation> ids = new HashSet<>();

        for (String raw : this.seededPlanets) {
            ResourceLocation parsed = ResourceLocation.tryParse(raw);
            if (parsed != null) {
                ids.add(parsed);
            }
        }

        return ids;
    }
}
