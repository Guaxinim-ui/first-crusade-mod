package com.example.examplemod.flora;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Vegetation settings, written to {@code firstcrusade-flora-common.toml}.
 *
 * <p>Same shape as {@link com.example.examplemod.FirstCrusadeServerConfig}, but a COMMON spec
 * rather than SERVER: the particle and sound budgets are read by client code in a later phase,
 * and a SERVER spec is not loaded on a client that has not joined a world.
 *
 * <h2>Status of each block of settings</h2>
 *
 * <ul>
 *   <li><b>Density</b> — live. The per-chunk decorator reads these on every chunk task
 *       ({@code vegetationDensity}, {@code smallPlantDensity}, {@code hiveDecorationDensity},
 *       {@code orkFungusFrequency}).</li>
 *   <li><b>{@code treeFrequency}</b> — <b>declared but inert.</b> Phase 2 ships no custom trees, so
 *       this multiplies a tree budget that is always zero. The decorator reads it already, and it
 *       will start doing something the moment
 *       {@link com.example.examplemod.flora.runtime.FloraPalette#treeEntry()} returns a spec
 *       (Phase 3). It is declared now so worlds created between phases do not see the default
 *       shift under them.</li>
 *   <li><b>Runtime</b> — live. Budgets, caps and switches for the per-chunk decorator.</li>
 *   <li><b>Effects</b> — reserved for Phase 5 ({@code particleFrequency},
 *       {@code ambientSoundFrequency}, {@code ambienceQuality}, {@code dangerousFloraEnabled}).
 *       Nothing reads them yet, and the phase brief keeps them that way.</li>
 * </ul>
 */
public final class FloraConfig {
    private FloraConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ------------------------------------------------------------------ density (live)

    public static final ForgeConfigSpec.DoubleValue VEGETATION_DENSITY = BUILDER
            .comment("Global multiplier on every vegetation placement. 0 disables natural flora entirely.")
            .defineInRange("vegetationDensity", 1.0D, 0.0D, 4.0D);

    public static final ForgeConfigSpec.DoubleValue SMALL_PLANT_DENSITY = BUILDER
            .comment("Multiplier for grasses, flowers, fungi and ground detail.")
            .defineInRange("smallPlantDensity", 1.0D, 0.0D, 4.0D);

    public static final ForgeConfigSpec.DoubleValue TREE_FREQUENCY = BUILDER
            .comment("Multiplier on how many trees a region plants.",
                     "1.0 gives woodland just under vanilla forest density; 0 disables trees entirely.")
            .defineInRange("treeFrequency", 1.0D, 0.0D, 4.0D);

    public static final ForgeConfigSpec.DoubleValue HIVE_DECORATION_DENSITY = BUILDER
            .comment("Multiplier for decoration inside Hive Cities, where block count is already high.")
            .defineInRange("hiveDecorationDensity", 1.0D, 0.0D, 4.0D);

    // ------------------------------------------------------------------ ork spread (live)

    public static final ForgeConfigSpec.DoubleValue ORK_FUNGUS_FREQUENCY = BUILDER
            .comment("Multiplier on Ork vegetation inside the camp corruption halo.",
                     "The camp's own spore radius (OrkSporeManager) still caps the reach — this only thins it out.")
            .defineInRange("orkFungusFrequency", 1.0D, 0.0D, 4.0D);

    // ------------------------------------------------------------------ effects (reserved, phase 5)

    public static final ForgeConfigSpec.DoubleValue PARTICLE_FREQUENCY = BUILDER
            .comment("Multiplier for ambient particles (spores, ash, pollen). 0 disables them.",
                     "RESERVED for phase 5 — nothing reads this yet.")
            .defineInRange("particleFrequency", 1.0D, 0.0D, 4.0D);

    public static final ForgeConfigSpec.DoubleValue AMBIENT_SOUND_FREQUENCY = BUILDER
            .comment("Multiplier for ambient vegetation sounds. 0 disables them.",
                     "RESERVED for phase 5 — nothing reads this yet.")
            .defineInRange("ambientSoundFrequency", 1.0D, 0.0D, 4.0D);

    public static final ForgeConfigSpec.EnumValue<Quality> AMBIENCE_QUALITY = BUILDER
            .comment("Overall ambience budget. LOW drops particles and localised fog and keeps blocks only.",
                     "RESERVED for phase 5 — nothing reads this yet.")
            .defineEnum("ambienceQuality", Quality.HIGH);

    // ------------------------------------------------------------------ fruit (live)
    //
    // Honest scope note. Of the five settings the brief lists, these two are the ones a running
    // game can obey, because growth happens at runtime. The other three — how many nodes a tree
    // carries, and how often fruit trees are generated — are decided when the chunk is generated,
    // by the tree feature's attached_to_leaves decorator and the biome's feature list. A config
    // cannot move them without regenerating the world, so declaring them here would be a switch
    // that silently does nothing. They live in the datapack, where they are honest.

    public static final ForgeConfigSpec.BooleanValue FRUIT_REGROWTH_ENABLED = BUILDER
            .comment("Whether a picked fruit node grows back.",
                     "Off makes every fruit tree a one-time harvest, which is a legitimate way to run",
                     "a scarcity server — the nodes stay, they simply never ripen again.")
            .define("fruitRegrowthEnabled", true);

    public static final ForgeConfigSpec.DoubleValue FRUIT_REGROWTH_CHANCE = BUILDER
            .comment("Chance per random tick that a node advances one growth stage.",
                     "Four stages, so the default works out at roughly one crop per node per",
                     "in-game day or two on a chunk the player keeps loaded.")
            .defineInRange("fruitRegrowthChance", 0.12D, 0.0D, 1.0D);

    public static final ForgeConfigSpec.BooleanValue DANGEROUS_FLORA_ENABLED = BUILDER
            .comment("Allow Death World plants to poison, slow or damage entities that touch them.",
                     "Turning this off leaves the plants in place as pure decoration.",
                     "RESERVED for phase 5 — the plants are inert today.")
            .define("dangerousFloraEnabled", true);

    // ------------------------------------------------------------------ runtime decorator (live)

    public static final ForgeConfigSpec.BooleanValue CHUNK_DECORATION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DYNAMIC_REDECORATION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue NEUTRAL_CHUNK_DECORATION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SETTLEMENT_VEGETATION_CLEANUP_ENABLED;

    public static final ForgeConfigSpec.IntValue CHUNKS_PROCESSED_PER_TICK;
    public static final ForgeConfigSpec.IntValue PLACEMENT_ATTEMPTS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAXIMUM_CUSTOM_FLORA_PER_CHUNK;
    public static final ForgeConfigSpec.IntValue MAXIMUM_LICHEN_PER_CHUNK;
    public static final ForgeConfigSpec.IntValue MAXIMUM_TALL_PLANTS_PER_CHUNK;
    public static final ForgeConfigSpec.IntValue BORDER_BLEND_WIDTH;
    public static final ForgeConfigSpec.IntValue QUEUE_CAPACITY;
    public static final ForgeConfigSpec.IntValue STRUCTURE_MARGIN;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Per-chunk runtime decorator: budgets, caps and switches.").push("runtime");

        CHUNK_DECORATION_ENABLED = BUILDER
                .comment("Master switch for the per-chunk vegetation decorator.",
                         "Off means no chunk is ever queued; flora already placed stays where it is.")
                .define("chunkDecorationEnabled", true);

        DYNAMIC_REDECORATION_ENABLED = BUILDER
                .comment("Allow already-decorated chunks to be redecorated when the war changes who holds them.",
                         "Off freezes vegetation at whatever it looked like when first decorated.")
                .define("dynamicRedecorationEnabled", true);

        NEUTRAL_CHUNK_DECORATION_ENABLED = BUILDER
                .comment("Decorate chunks that belong to nobody. Off restricts custom flora to territory",
                         "actually held by a faction, which is much cheaper on a freshly explored world.")
                .define("neutralChunkDecorationEnabled", true);

        SETTLEMENT_VEGETATION_CLEANUP_ENABLED = BUILDER
                .comment("Clear vanilla trees and grass out of a settlement's footprint when it is founded",
                         "or expanded, so a Forge City does not sit in the middle of an untouched forest.")
                .define("settlementVegetationCleanupEnabled", true);

        CHUNKS_PROCESSED_PER_TICK = BUILDER
                .comment("How many queued chunks may be decorated in a single server tick.")
                .defineInRange("chunksProcessedPerTick", 2, 0, 64);

        PLACEMENT_ATTEMPTS_PER_TICK = BUILDER
                .comment("Hard ceiling on placement attempts across all chunks in a single server tick.",
                         "This is the setting that keeps a hundred chunks loading at once from stalling the",
                         "server: work simply spills over into the following ticks.")
                .defineInRange("placementAttemptsPerTick", 900, 16, 8192);

        MAXIMUM_CUSTOM_FLORA_PER_CHUNK = BUILDER
                .comment("Ceiling on custom flora blocks placed in one chunk by one decoration pass.")
                .defineInRange("maximumCustomFloraPerChunk", 340, 0, 2048);

        MAXIMUM_LICHEN_PER_CHUNK = BUILDER
                .comment("Ceiling on wall-clinging lichen per chunk. Kept low: lichen lands on structures,",
                         "and a wall crusted in moss reads as neglect rather than atmosphere.")
                .defineInRange("maximumLichenPerChunk", 28, 0, 512);

        MAXIMUM_TALL_PLANTS_PER_CHUNK = BUILDER
                .comment("Ceiling on two-block-tall plants per chunk. Each one costs two block writes.")
                .defineInRange("maximumTallPlantsPerChunk", 34, 0, 512);

        BORDER_BLEND_WIDTH = BUILDER
                .comment("How far, in blocks, one region's vegetation bleeds into its neighbour, and how hard",
                         "the noise bends a territorial border off a straight line.",
                         "0 gives hard edges that follow the territory maths exactly.")
                .defineInRange("borderBlendWidth", 12, 0, 48);

        QUEUE_CAPACITY = BUILDER
                .comment("Maximum chunks waiting for decoration. Beyond this, newly loaded chunks are skipped",
                         "and picked up next time they load, rather than growing the queue without bound.")
                .defineInRange("queueCapacity", 4096, 64, 65536);

        STRUCTURE_MARGIN = BUILDER
                .comment("Extra blocks of clearance kept around gates, roads, rails and building walls, on top",
                         "of each structure's own margin. Vegetation never grows inside this band.")
                .defineInRange("structureMargin", 2, 0, 16);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public enum Quality {
        LOW,
        MEDIUM,
        HIGH
    }
}
