package com.example.examplemod.performance.config;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.performance.graphics.FirstCrusadeGraphicsPreset;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * What the player sees, and nothing else.
 *
 * <h2>Why this is a separate spec from FirstCrusadePerformanceConfig</h2>
 *
 * {@link FirstCrusadePerformanceConfig} decides how hard the server thinks: acquisition range, scan
 * intervals, AI level of detail. This file decides how much the client draws. They are deliberately
 * two files with two specs and no reference between them, because the moment a client value can
 * reach a simulation value, a player can improve their own units by editing a text file. Nothing
 * here is read on the logical server, and nothing here changes damage, accuracy, range, hit
 * registration or AI — a tracer that was not drawn was still fired, still travelled and still hurt
 * whatever it hit.
 *
 * <h2>How the preset and the individual fields fit together</h2>
 *
 * {@link #PRESET} is the master switch. On PERFORMANCE, GRIMDARK or EXTERMINATUS the individual
 * fields below are ignored and the answers come from {@link FirstCrusadeGraphicsPreset}; on CUSTOM
 * the individual fields are used as written. The code never writes the preset's numbers back into
 * the file: a config that rewrites itself fights the file watcher, and it would silently destroy a
 * player's hand-tuned CUSTOM values the first time they tried a preset. Switching to a preset and
 * back leaves the CUSTOM block exactly as it was.
 *
 * <h2>Honest state of these values, 2026-08-13</h2>
 *
 * Four of these actually do something today: {@code particleDensity}, {@code tracerDensity},
 * {@code maxVisualCombatDistance} and {@code maxParticlesPerTick}, all of them through the las-bolt
 * trail, which is the only client-side particle the mod draws. The rest are wired to the config and
 * to a channel, and no call site requests that channel yet — so turning them does nothing. Each one
 * says so in its own comment.
 *
 * <p>That is recorded rather than quietly left because a dial that does nothing is worse than a
 * missing dial: it costs somebody an afternoon of tuning before they work out it was never
 * connected. The places that call {@code ServerLevel#sendParticles} are a separate problem with a
 * separate owner — they cost network bandwidth for every player in range, so they belong to the
 * server config, not to this file.
 *
 * <p>That separate owner now exists. The {@code particles} section of
 * {@link FirstCrusadePerformanceConfig}, applied by {@code FCServerParticles}, is the dial for the
 * broadcast effects: muzzle flashes, explosions, smoke and casings. Four of the fields below name
 * the server key that actually governs their effect. They stay here rather than being deleted
 * because the client still needs its own answer to the same question for anything it draws itself,
 * and the las-bolt trail already is exactly that.
 *
 * <h2>Mirrored static fields</h2>
 *
 * Same reason as the server config: {@code ForgeConfigSpec.IntValue.get()} walks a config tree and
 * boxes an Integer, which is unacceptable in a particle path that can be asked hundreds of times per
 * frame. {@link #apply()} copies the spec into plain static fields on load and reload.
 *
 * <p>Those fields also carry preset defaults, which matters on a dedicated server: a CLIENT config
 * is never loaded there, so {@code .get()} would throw. Nothing on a server should be asking these
 * questions, but if some future common-side code does, it reads a sane GRIMDARK number instead of
 * crashing the server.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FirstCrusadeClientConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ==================================================================== preset

    public static final ForgeConfigSpec.EnumValue<FirstCrusadeGraphicsPreset> PRESET = BUILDER
            .comment("First Crusade graphics preset.",
                    "PERFORMANCE   - thinned effects, short visual range. Best frames in a large battle.",
                    "GRIMDARK      - the recommended default; the look the mod is built around.",
                    "EXTERMINATUS  - everything at full strength. For strong machines.",
                    "CUSTOM        - use the individual values in the [visuals] section below.",
                    "On any preset other than CUSTOM the [visuals] values are ignored, and this file",
                    "is never rewritten, so your CUSTOM tuning survives a trip through the presets.")
            .defineEnum("graphics.preset", FirstCrusadeGraphicsPreset.GRIMDARK);

    // ==================================================================== visuals (CUSTOM only)

    public static final ForgeConfigSpec.IntValue PARTICLE_DENSITY = BUILDER
            .comment("Master particle percentage, applied on top of every channel below.",
                    "0 disables mod particles entirely; 100 draws all of them.")
            .defineInRange("visuals.particleDensity", 70, 0, 100);

    public static final ForgeConfigSpec.IntValue TRACER_DENSITY = BUILDER
            .comment("Percentage of weapon tracers and projectile trails that are drawn.",
                    "Purely visual: a shot whose tracer was skipped was still fired and still hits.")
            .defineInRange("visuals.tracerDensity", 75, 0, 100);

    public static final ForgeConfigSpec.IntValue SMOKE_DENSITY = BUILDER
            .comment("Percentage of smoke drawn by explosions, fires and engines.",
                    "NOT WIRED HERE: no smoke the client draws for itself goes through the particle",
                    "budget, because every smoke effect in the mod is broadcast by the server.",
                    "The dial that governs them is particles.smokeDensity in the SERVER config.")
            .defineInRange("visuals.smokeDensity", 70, 0, 100);

    public static final ForgeConfigSpec.IntValue MUZZLE_FLASH_QUALITY = BUILDER
            .comment("Percentage of muzzle flashes drawn at a weapon's barrel.",
                    "NOT WIRED HERE: the muzzle flash is broadcast by the server, not drawn by the",
                    "client on its own. Use particles.muzzleFlashDensity in the SERVER config.")
            .defineInRange("visuals.muzzleFlashQuality", 80, 0, 100);

    public static final ForgeConfigSpec.IntValue DISTANT_ANIMATION_QUALITY = BUILDER
            .comment("Animation detail for units beyond the full-detail radius.",
                    "Lower values would let distant soldiers animate at a reduced rate, leaving AI,",
                    "shooting and hitboxes untouched.",
                    "NOT WIRED, and investigated properly on 2026-08-13 rather than left as a maybe.",
                    "It cannot be done from FCGeoRenderer, and the reason is in GeckoLib's own",
                    "architecture: GeckoLibCache.MODELS is a static map holding ONE BakedGeoModel per",
                    "model file, so every Guardsman in the world shares a single set of bones.",
                    "GeoEntityRenderer#actuallyRender re-poses those shared bones for each entity",
                    "immediately before drawing it. Skipping that step for a distant soldier would",
                    "not freeze it in its own last pose -- it would draw it in whichever pose the",
                    "previously rendered soldier left behind, and since that neighbour changes every",
                    "frame, distant troops would twitch between unrelated poses. Worse than the cost",
                    "it saves. The mod adds no GeoRenderLayers either, so there is no additive detail",
                    "to trim at distance the way an armour or emissive layer could be.",
                    "Doing this properly needs either a mixin into GeckoLib or a per-entity baked",
                    "model, and the second one trades frame time for exactly the kind of duplicated",
                    "memory FerriteCore exists to remove. Left off deliberately.")
            .defineInRange("visuals.distantAnimationQuality", 70, 0, 100);

    public static final ForgeConfigSpec.IntValue VEHICLE_EFFECTS = BUILDER
            .comment("PLACEHOLDER: there is currently nothing for this to thin. Changing it does",
                    "nothing, and that is a fact about the vehicles rather than a missing hook.",
                    "Percentage of exhaust, dust and spark effects thrown by vehicles.",
                    "Checked 2026-08-13 and again 2026-08-14: no vehicle in the mod emits a per-tick",
                    "effect at all. The Sentinel only throws particles on its stomp impact, which is",
                    "an event and already goes through particles.explosionDensity and",
                    "particles.smokeDensity in the SERVER config; the tank and the Valkyrie throw",
                    "none. When a vehicle grows an exhaust plume, this is where it gets its dial.")
            .defineInRange("visuals.vehicleEffects", 70, 0, 100);

    public static final ForgeConfigSpec.IntValue EXPLOSION_EFFECTS = BUILDER
            .comment("Percentage of fireball, shockwave and ember effects on detonation.",
                    "Blast damage and radius are server-side and do not change with this value.",
                    "NOT WIRED HERE, and it never can be: the mod's explosions use",
                    "ServerLevel#sendParticles, which leaves the server and cannot obey one client's",
                    "config. The equivalent dial now exists as particles.explosionDensity in the",
                    "SERVER config; that is the one that works.")
            .defineInRange("visuals.explosionEffects", 70, 0, 100);

    public static final ForgeConfigSpec.IntValue DEBRIS_AMOUNT = BUILDER
            .comment("Percentage of impact fragments and kicked-up dirt that is drawn.",
                    "NOT WIRED HERE: same as explosions, these leave via sendParticles.",
                    "Use particles.debrisDensity in the SERVER config.")
            .defineInRange("visuals.debrisAmount", 60, 0, 100);

    public static final ForgeConfigSpec.IntValue MAX_VISUAL_COMBAT_DISTANCE = BUILDER
            .comment("Beyond this distance from you, in blocks, combat visuals are not drawn.",
                    "The fighting continues; you simply stop being sent the sparks for it.")
            .defineInRange("visuals.maxVisualCombatDistance", 96, 16, 512);

    public static final ForgeConfigSpec.IntValue CORPSE_RENDER_DISTANCE = BUILDER
            .comment("PLACEHOLDER FOR A SYSTEM THAT DOES NOT EXIST. Changing this does nothing.",
                    "Beyond this distance from you, in blocks, bodies would stop being drawn.",
                    "Kept as a declared intention rather than deleted so the key does not appear and",
                    "disappear between versions, but it controls nothing today: the mod has no corpse",
                    "system at all, so there is no body to stop drawing.")
            .defineInRange("visuals.corpseRenderDistance", 48, 8, 256);

    public static final ForgeConfigSpec.IntValue MAX_PARTICLES_PER_TICK = BUILDER
            .comment("Ceiling on First Crusade particles spawned in one client tick.",
                    "A hard stop for the case the density percentages cannot cover: three hundred",
                    "soldiers all firing on the same tick. Nearby effects are served first.")
            .defineInRange("visuals.maxParticlesPerTick", 600, 50, 5000);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private FirstCrusadeClientConfig() {
    }

    // ==================================================================== mirrored values

    private static FirstCrusadeGraphicsPreset preset = FirstCrusadeGraphicsPreset.GRIMDARK;
    private static int particleDensity = 70;
    private static int tracerDensity = 75;
    private static int smokeDensity = 70;
    private static int muzzleFlashQuality = 80;
    private static int distantAnimationQuality = 70;
    private static int vehicleEffects = 70;
    private static int explosionEffects = 70;
    private static int debrisAmount = 60;
    private static int maxVisualCombatDistance = 96;
    private static int corpseRenderDistance = 48;
    private static int maxParticlesPerTick = 600;

    /** The active preset. CUSTOM means the resolved values below come from the config fields. */
    public static FirstCrusadeGraphicsPreset preset() {
        return preset;
    }

    private static boolean custom() {
        return preset == FirstCrusadeGraphicsPreset.CUSTOM;
    }

    // ==================================================================== resolved values
    //
    // Every accessor answers the same question: "preset, or hand-tuned?". Callers never need to know
    // which, and never need to branch on the preset themselves.

    public static int particleDensity() {
        return custom() ? particleDensity : preset.particleDensity();
    }

    public static int tracerDensity() {
        return custom() ? tracerDensity : preset.tracerDensity();
    }

    public static int smokeDensity() {
        return custom() ? smokeDensity : preset.smokeDensity();
    }

    public static int muzzleFlashQuality() {
        return custom() ? muzzleFlashQuality : preset.muzzleFlashQuality();
    }

    public static int distantAnimationQuality() {
        return custom() ? distantAnimationQuality : preset.distantAnimationQuality();
    }

    public static int vehicleEffects() {
        return custom() ? vehicleEffects : preset.vehicleEffects();
    }

    public static int explosionEffects() {
        return custom() ? explosionEffects : preset.explosionEffects();
    }

    public static int debrisAmount() {
        return custom() ? debrisAmount : preset.debrisAmount();
    }

    public static int maxVisualCombatDistance() {
        return custom() ? maxVisualCombatDistance : preset.maxVisualCombatDistance();
    }

    public static int corpseRenderDistance() {
        return custom() ? corpseRenderDistance : preset.corpseRenderDistance();
    }

    public static int maxParticlesPerTick() {
        return custom() ? maxParticlesPerTick : preset.maxParticlesPerTick();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            apply();
        }
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            apply();
        }
    }

    private static void apply() {
        preset = PRESET.get();
        particleDensity = PARTICLE_DENSITY.get();
        tracerDensity = TRACER_DENSITY.get();
        smokeDensity = SMOKE_DENSITY.get();
        muzzleFlashQuality = MUZZLE_FLASH_QUALITY.get();
        distantAnimationQuality = DISTANT_ANIMATION_QUALITY.get();
        vehicleEffects = VEHICLE_EFFECTS.get();
        explosionEffects = EXPLOSION_EFFECTS.get();
        debrisAmount = DEBRIS_AMOUNT.get();
        maxVisualCombatDistance = MAX_VISUAL_COMBAT_DISTANCE.get();
        corpseRenderDistance = CORPSE_RENDER_DISTANCE.get();
        maxParticlesPerTick = MAX_PARTICLES_PER_TICK.get();
    }
}
