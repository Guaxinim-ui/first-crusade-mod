package com.example.examplemod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Server-side configuration for First Crusade. These were compile-time constants in {@link ExampleMod};
 * they are now per-world server settings (written to {@code serverconfig/firstcrusade-server.toml}) so a
 * server owner can flip the test sandbox, Ork waves, the world border, dimension sealing and initial
 * settlement seeding without recompiling.
 *
 * <p>On (re)load the values are mirrored into {@link ExampleMod}'s runtime fields so the many existing
 * call sites (e.g. {@code ExampleMod.TEST_FIXED_WORLD}) keep reading the same names on hot paths.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FirstCrusadeServerConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue TEST_FIXED_WORLD = BUILDER
            .comment("Test sandbox: seed a fixed war-pair layout, strip vanilla mobs, and cap each faction's warriors.")
            .define("testFixedWorld", true);

    public static final ForgeConfigSpec.IntValue TEST_WARRIOR_CAP = BUILDER
            .comment("Warriors allowed per faction while testFixedWorld is on.")
            .defineInRange("testWarriorCap", 50, 1, 5000);

    public static final ForgeConfigSpec.BooleanValue ORK_WAVES_ENABLED = BUILDER
            .comment("Enable Ork attack waves (both camp war parties and strategic offensives honour this).")
            .define("orkWavesEnabled", false);

    public static final ForgeConfigSpec.DoubleValue WORLD_BORDER_SIZE = BUILDER
            .comment("Overworld border size in blocks (a movement-only clamp; use 0 to leave the border untouched).")
            .defineInRange("worldBorderSize", 5000.0D, 0.0D, 60_000_000.0D);

    public static final ForgeConfigSpec.BooleanValue SEAL_NETHER_AND_END = BUILDER
            .comment("Seal the Nether and the End: travel to either dimension is cancelled.")
            .define("sealNetherAndEnd", true);

    public static final ForgeConfigSpec.BooleanValue PLANET_HAZARDS_ENABLED = BUILDER
            .comment("Planet environmental hazards (cold on Valhalla, ash on Armageddon/Forge World,",
                     "spores on the Ork world, toxic flora on Catachan, the tombs under Sekhet).",
                     "Players only: the worlds' own inhabitants are from there.")
            .define("planetHazardsEnabled", true);

    public static final ForgeConfigSpec.BooleanValue SEED_STARTING_SETTLEMENTS = BUILDER
            .comment("Seed starting settlements (Imperial cities + Ork camps) the first time a player joins a world.")
            .define("seedStartingSettlements", true);

    // The campaign layer's own section, appended before the spec is sealed. It lives in this file
    // rather than one of its own because it is simulation: fronts, sector capture and raid rates are
    // server decisions, and they belong beside the other server decisions.
    static {
        com.example.examplemod.campaign.CampaignConfig.build(BUILDER);
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private FirstCrusadeServerConfig() {
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
        ExampleMod.TEST_FIXED_WORLD = TEST_FIXED_WORLD.get();
        ExampleMod.TEST_WARRIOR_CAP = TEST_WARRIOR_CAP.get();
        ExampleMod.ORK_WAVES_ENABLED = ORK_WAVES_ENABLED.get();
        ExampleMod.WORLD_BORDER_SIZE = WORLD_BORDER_SIZE.get();
        ExampleMod.SEAL_NETHER_AND_END = SEAL_NETHER_AND_END.get();
        ExampleMod.SEED_STARTING_SETTLEMENTS = SEED_STARTING_SETTLEMENTS.get();
        ExampleMod.PLANET_HAZARDS_ENABLED = PLANET_HAZARDS_ENABLED.get();
    }
}
