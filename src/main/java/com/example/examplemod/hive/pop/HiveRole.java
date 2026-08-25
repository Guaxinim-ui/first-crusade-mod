package com.example.examplemod.hive.pop;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.hive.HiveMarkers.MarkerType;

import net.minecraft.resources.ResourceLocation;

/**
 * Who lives in the Hive (spec §19).
 *
 * <h2>One entity with a role, not five near-identical mobs</h2>
 *
 * The spec names eight kinds of Hive dweller. Three already existed as entities — the citizen, the
 * guardsman and the enforcer — and the remaining five are the same problem with different clothes:
 * a person who stands somewhere, wanders a little, and runs when shot at. Five classes for that
 * would be five copies of the same goal list, and the architecture rule in {@code STATUS.md} §5 says
 * to reuse an existing entity before adding one.
 *
 * <p>So the five new dwellers are one {@link HiveDwellerEntity} carrying a role, and this enum is
 * everything that differs: what they look like, what they are called, how tough they are, and
 * whether they are hostile. It is the same shape {@code ImperialTroopAppearance} already uses for
 * the eleven Imperial troops — a resolver keyed by type rather than a class per type.
 *
 * <h2>Textures come from the generator, never by hand</h2>
 *
 * The first attempt pointed these at vanilla villager profession textures — mason for the worker,
 * cleric for the priest. That does not work: those files are <b>overlays</b> the vanilla villager
 * renderer draws on top of a base, and used alone they render a mostly-transparent person.
 *
 * <p>So the five are painted by {@code tools/generate_hive_dweller_textures.py}, which
 * <b>imports</b> the painter from {@code generate_troop_textures.py} rather than repeating it —
 * same Canvas, same UV layout, same palette helpers. That is the house rule for every texture in
 * this mod: edit the generator and re-run it, never the PNG.
 */
public enum HiveRole {

    /**
     * Hab-block labour. The Hive's actual purpose: bodies that feed the manufactorum.
     *
     * <p>Separate from the citizen for a reason that was a real bug — {@code CIVIL_SPAWN} and
     * {@code WORKER_SPAWN} both resolved to {@code imperial_citizen}, so sixteen worker markers
     * across the city produced the same person the twenty-six civil markers did, and the distinction
     * the templates went to the trouble of marking was invisible in play.
     */
    WORKER("worker", MarkerType.WORKER_SPAWN, 20.0D, 0.26D, 0.0D, false),

    /** Trade points. Heavy coat with gold trim — the one dweller who chose their clothes. */
    MERCHANT("merchant", MarkerType.TRADE_POINT, 20.0D, 0.28D, 0.0D, false),

    /** Construction points. The Machine God's hands, and the reason anything still runs. */
    MECHANICUS_WORKER("mechanicus_worker", MarkerType.CONSTRUCTION_POINT,
            24.0D, 0.25D, 2.0D, false),

    /** The Administratum's cathedral. See {@link #marker()} — nothing plants one of these yet. */
    PRIEST("priest", null, 20.0D, 0.24D, 0.0D, false),

    /**
     * The Underhive's own. The one hostile role, and the only reason {@code ENEMY_SPAWN} means
     * anything down there.
     */
    GANG_MEMBER("gang_member", MarkerType.ENEMY_SPAWN, 24.0D, 0.30D, 2.0D, true);

    private final String key;
    private final MarkerType marker;
    private final ResourceLocation texture;
    private final double health;
    private final double speed;
    private final double armour;
    private final boolean hostile;

    HiveRole(String key, MarkerType marker,
             double health, double speed, double armour, boolean hostile) {
        this.key = key;
        this.marker = marker;
        // Built once at class-load, so a render call is a field read. Mirrors exactly what
        // tools/generate_hive_dweller_textures.py writes; if the two ever disagree the texture
        // simply fails to bind and Minecraft draws the missing-texture checkerboard, which is a
        // loud failure rather than a silent wrong-skin one.
        this.texture = new ResourceLocation(ExampleMod.MODID, "textures/entity/hive/" + key + ".png");
        this.health = health;
        this.speed = speed;
        this.armour = armour;
        this.hostile = hostile;
    }

    public String key() {
        return this.key;
    }

    /**
     * The city marker this role spawns from, or null for one nothing plants yet.
     *
     * <p>{@link #PRIEST} is the null. The district generators place twelve kinds of marker and none
     * of them is a shrine — the cathedral in the Administratum district has no marker of its own. The
     * role is registered anyway, with a spawn egg, so the priest exists and can be placed by hand;
     * wiring it up is a change to the district generators, which is somebody else's file.
     */
    public MarkerType marker() {
        return this.marker;
    }

    public ResourceLocation texture() {
        return this.texture;
    }

    public double health() {
        return this.health;
    }

    public double speed() {
        return this.speed;
    }

    public double armour() {
        return this.armour;
    }

    /** True for a role that attacks the Imperium on sight. Only the gangs. */
    public boolean hostile() {
        return this.hostile;
    }

    /** {@code hive_worker}, {@code hive_gang_member} — the registry name of this role's entity. */
    public String entityName() {
        return "hive_" + this.key;
    }

    public String translationKey() {
        return "entity." + ExampleMod.MODID + "." + entityName();
    }

    /** The role a marker spawns, or null when that marker is not one of ours. */
    public static HiveRole forMarker(MarkerType type) {
        for (HiveRole role : values()) {
            if (role.marker == type) {
                return role;
            }
        }

        return null;
    }
}
