package com.example.examplemod.animal;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Wildlife settings, written to {@code firstcrusade-animals-common.toml}.
 *
 * <p>Its own spec rather than a section of {@link com.example.examplemod.flora.FloraConfig}: an
 * operator turning down the animals is doing something unrelated to turning down the grass, and
 * mixing the two would make each file harder to read than either needs to be.
 *
 * <p>Only settings something actually reads are declared. The brief lists more
 * ({@code predatorPopulationLimit}, per-species switches for animals that do not exist yet), and
 * they arrive with the species — a switch that silently does nothing is worse than no switch.
 */
public final class FCAnimalConfig {
    private FCAnimalConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ANIMAL_SPAWN_ENABLED = BUILDER
            .comment("Master switch for natural spawning of the mod's wildlife.",
                     "Off leaves spawn eggs and commands working — it only stops the world",
                     "populating itself.")
            .define("animalSpawnEnabled", true);

    public static final ForgeConfigSpec.IntValue WILDLIFE_POPULATION_LIMIT = BUILDER
            .comment("Maximum animals of one species within 48 blocks before natural spawning of",
                     "that species stops there. Checked when the spawn is proposed, never by culling",
                     "afterwards: an animal the player has seen must not vanish.")
            .defineInRange("wildlifePopulationLimit", 12, 1, 128);

    public static final ForgeConfigSpec.BooleanValue FLEE_FROM_COMBAT = BUILDER
            .comment("Whether being hurt — by a shot, a blade or an explosion — makes an animal run,",
                     "and passes the alarm to its herd.")
            .define("animalFleeFromCombatEnabled", true);

    public static final ForgeConfigSpec.BooleanValue ANIMAL_ECOLOGY_ENABLED = BUILDER
            .comment("Whether animals graze, seek water and keep to a herd.",
                     "Off leaves them as ordinary wandering mobs, which is cheaper.")
            .define("animalEcologyEnabled", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
