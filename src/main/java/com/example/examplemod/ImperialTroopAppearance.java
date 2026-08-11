package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Which PNG a given Imperial soldier is wearing.
 *
 * <h2>Why this is not a switch in each renderer</h2>
 *
 * Every troop renderer used to hold one {@code static final ResourceLocation} and hand it back for
 * every instance, which is why nine different units shipped the same placeholder file and nobody
 * noticed. Three axes actually decide a soldier's appearance — <b>what he is</b>, <b>which
 * regiment raised him</b> and <b>which soldier he is</b> — and a renderer is the wrong place to
 * answer any of them. They are answered once, here, and the renderers become one line.
 *
 * <h2>The lookup</h2>
 *
 * <pre>{@code texture(troopKey, regiment, variant, grade)}</pre>
 *
 * <ul>
 *   <li><b>troopKey</b> — the entity type's registry path ({@code kasrkin}, {@code feudal_knight}).
 *       Nothing has to be registered per unit: a new troop that ships
 *       {@code textures/entity/imperium/&lt;key&gt;/&lt;key&gt;_0.png} and declares itself below is done.</li>
 *   <li><b>regiment</b> — reserved and wired, with exactly one entry today ({@link #DEFAULT_REGIMENT}).
 *       A Cadian and a Krieg Guardsman share this mod's geometry and UV and differ only in the file
 *       this method returns, so the day those PNGs exist the change is a line in {@link #define}
 *       and nothing else.</li>
 *   <li><b>variant</b> — which individual. Rolled once when the soldier spawns and persisted, so a
 *       squad is a squad and not four copies of the same man.</li>
 *   <li><b>grade</b> — {@link ImperialTroopGrade}, derived from rank. A promotion changes the
 *       picture and nothing else: same entity, same UUID, same history.</li>
 * </ul>
 *
 * <h2>Cost</h2>
 *
 * Every {@link ResourceLocation} is built once, at class-load, into a flat array per
 * (troop, regiment, grade). A render call is two map lookups and an array index — no string
 * concatenation, no allocation, nothing that gets more expensive when the battlefield gets busier.
 *
 * <h2>Nothing renders purple</h2>
 *
 * An unknown troop, regiment or grade falls back to the line Guardsman rather than to a missing
 * texture, and says so in the log <b>once</b> per distinct miss — a renderer runs per entity per
 * frame, so a warning that did not deduplicate would be a warning that filled the disk.
 */
public final class ImperialTroopAppearance {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The regiment key used until named regiments ship their own art. */
    public static final String DEFAULT_REGIMENT = "default";

    /** The troop every failed lookup lands on. It is the one texture set guaranteed to exist. */
    public static final String FALLBACK_TROOP = "guardsman";

    private static final String ROOT = "textures/entity/imperium/";

    /** troop key -> regiment key -> grade -> the variants that exist for it. */
    private static final Map<String, Map<String, Map<ImperialTroopGrade, ResourceLocation[]>>> LOOKS =
            new HashMap<>();

    /** Misses already reported, so a per-frame call cannot become a per-frame log line. */
    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());

    static {
        // Mirrors tools/generate_troop_textures.py. If the two ever disagree, the fallback below
        // catches it at runtime and names the unit in the log.
        // Guardsman variant 3 and Kasrkin variant 2 are the owner's own hand-painted sheets, moved
        // here rather than replaced; the generator is told not to overwrite them.
        define("guardsman", 4, 2, 2);
        define("kasrkin", 3, 0, 0);
        define("skitarii_ranger", 2, 0, 0);
        define("sister_of_battle", 2, 0, 0);
        define("penal_legionnaire", 2, 0, 0);
        define("jungle_fighter", 2, 0, 0);
        define("mine_guard", 2, 0, 0);
        define("feudal_knight", 2, 0, 0);
        define("agri_militia", 2, 0, 0);
        define("enforcer", 2, 0, 0);
        define("city_commander", 2, 0, 0);
    }

    private ImperialTroopAppearance() {
    }

    /**
     * Declares one troop's art for the default regiment.
     *
     * @param key       entity type registry path, and the folder name under {@code entity/imperium/}
     * @param line      how many line variants exist (at least one, or the unit cannot be drawn)
     * @param veteran   how many veteran variants exist; 0 means veterans keep the line look
     * @param sergeant  how many sergeant variants exist; 0 means sergeants keep the line look
     */
    private static void define(String key, int line, int veteran, int sergeant) {
        Map<ImperialTroopGrade, ResourceLocation[]> byGrade = new HashMap<>();

        byGrade.put(ImperialTroopGrade.LINE, build(key, ImperialTroopGrade.LINE, line));
        if (veteran > 0) {
            byGrade.put(ImperialTroopGrade.VETERAN, build(key, ImperialTroopGrade.VETERAN, veteran));
        }
        if (sergeant > 0) {
            byGrade.put(ImperialTroopGrade.SERGEANT, build(key, ImperialTroopGrade.SERGEANT, sergeant));
        }

        Map<String, Map<ImperialTroopGrade, ResourceLocation[]>> byRegiment = new HashMap<>();
        byRegiment.put(DEFAULT_REGIMENT, byGrade);
        LOOKS.put(key, byRegiment);
    }

    private static ResourceLocation[] build(String key, ImperialTroopGrade grade, int count) {
        String stem = grade.suffix().isEmpty() ? key : key + "_" + grade.suffix();
        ResourceLocation[] out = new ResourceLocation[count];

        for (int i = 0; i < count; i++) {
            out[i] = new ResourceLocation(ExampleMod.MODID, ROOT + key + "/" + stem + "_" + i + ".png");
        }

        return out;
    }

    /** The texture for one soldier. Never null, never a missing texture. */
    public static ResourceLocation texture(String troopKey, String regiment, int variant,
                                           ImperialTroopGrade grade) {
        ResourceLocation[] set = resolve(troopKey, regiment, grade);

        if (set == null || set.length == 0) {
            warnOnce(troopKey + "/" + regiment,
                    "Missing specialised texture for troop type " + troopKey
                            + " (regiment " + regiment + ") — falling back to " + FALLBACK_TROOP);
            set = resolve(FALLBACK_TROOP, DEFAULT_REGIMENT, ImperialTroopGrade.LINE);
        }

        // Math.floorMod, not %, so a corrupt negative variant wraps instead of throwing.
        return set[Math.floorMod(variant, set.length)];
    }

    /** Convenience for the common case: default regiment, line grade. */
    public static ResourceLocation texture(String troopKey, int variant) {
        return texture(troopKey, DEFAULT_REGIMENT, variant, ImperialTroopGrade.LINE);
    }

    /** The texture for anything implementing {@link ImperialTroopVisuals}. */
    public static ResourceLocation texture(ImperialTroopVisuals troop) {
        return texture(troop.appearanceKey(), troop.appearanceRegiment(),
                troop.getVisualVariant(), troop.getVisualGrade());
    }

    private static ResourceLocation[] resolve(String troopKey, String regiment,
                                              ImperialTroopGrade grade) {
        Map<String, Map<ImperialTroopGrade, ResourceLocation[]>> byRegiment = LOOKS.get(troopKey);
        if (byRegiment == null) {
            return null;
        }

        // An unnamed regiment is not an error — it is every regiment that has not been drawn yet,
        // and it silently wears the default kit rather than warning about art nobody promised.
        Map<ImperialTroopGrade, ResourceLocation[]> byGrade = byRegiment.get(regiment);
        if (byGrade == null) {
            byGrade = byRegiment.get(DEFAULT_REGIMENT);
        }
        if (byGrade == null) {
            return null;
        }

        // Same for grade: a unit with no sergeant art has sergeants in line kit, which is correct
        // behaviour rather than a hole. Only a missing LINE set is a real failure.
        ResourceLocation[] set = byGrade.get(grade);
        return set != null ? set : byGrade.get(ImperialTroopGrade.LINE);
    }

    /** How many line variants a troop ships. Used by the spawn roll so it never picks a gap. */
    public static int variantCount(String troopKey) {
        ResourceLocation[] set = resolve(troopKey, DEFAULT_REGIMENT, ImperialTroopGrade.LINE);
        return set == null || set.length == 0 ? 1 : set.length;
    }

    /** Every troop key with declared art. Used by the {@code /fctroop} suggestion provider. */
    public static List<String> knownTroops() {
        List<String> keys = new ArrayList<>(LOOKS.keySet());
        Collections.sort(keys);
        return keys;
    }

    private static void warnOnce(String token, String message) {
        if (WARNED.add(token)) {
            LOGGER.warn("First Crusade: {}", message);
        }
    }
}
