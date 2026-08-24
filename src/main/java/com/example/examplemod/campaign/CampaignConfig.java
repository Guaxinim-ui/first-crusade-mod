package com.example.examplemod.campaign;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The balance knobs of the campaign layer.
 *
 * <h2>Few, and each one load-bearing</h2>
 *
 * Every value here changes how the war behaves, not how it looks, and each is one somebody tuning
 * the campaign would actually reach for: how often it thinks, how fast ground changes hands, how far
 * a settlement's influence reaches, whether the whole layer runs at all. Nothing is exposed merely
 * because it happens to be a number in the code — a config with two hundred entries is a config
 * nobody reads, and the ones that matter drown in it.
 *
 * <p>Registered onto the existing SERVER spec ({@code firstcrusade-server.toml}) rather than a file
 * of its own. The campaign decides captures, control and raids: it is simulation, so a client may
 * never have an opinion about it, and it belongs in the same file as the other simulation settings.
 */
public final class CampaignConfig {

    public static ForgeConfigSpec.BooleanValue ENABLED;
    public static ForgeConfigSpec.IntValue STRATEGIC_INTERVAL_TICKS;
    public static ForgeConfigSpec.IntValue SETTLEMENT_INFLUENCE_RADIUS;
    public static ForgeConfigSpec.IntValue PRESSURE_PER_SETTLEMENT;
    public static ForgeConfigSpec.DoubleValue WAR_SPEED;
    public static ForgeConfigSpec.BooleanValue AUTO_ACTIVATE_FRONTS;
    public static ForgeConfigSpec.IntValue NECRON_AWAKENING_PER_PASS;
    public static ForgeConfigSpec.IntValue MAX_OPERATIONS_PER_FRONT;
    public static ForgeConfigSpec.DoubleValue OPERATION_TIME_SCALE;
    public static ForgeConfigSpec.IntValue DEPLOYMENT_MUSTER_TICKS;
    public static ForgeConfigSpec.IntValue DEPLOYMENT_TRAVEL_TICKS;
    public static ForgeConfigSpec.IntValue DEPLOYMENT_MATERIALISE_CAP;
    public static ForgeConfigSpec.IntValue MAX_DEPLOYMENTS_PER_SIDE;
    public static ForgeConfigSpec.IntValue ORK_RAID_THRESHOLD;
    public static ForgeConfigSpec.IntValue ORK_RAID_BUILD_PER_CAMP;
    public static ForgeConfigSpec.IntValue MAX_LIVE_CONVOYS;
    public static ForgeConfigSpec.IntValue CONVOY_TRAVEL_TICKS;
    public static ForgeConfigSpec.IntValue CONVOY_ATTRITION_PER_PASS;
    public static ForgeConfigSpec.IntValue CONVOY_DEFENCE_PER_KILL;
    public static ForgeConfigSpec.IntValue CONVOY_CARGO_PASSES;
    public static ForgeConfigSpec.IntValue CONVOY_COOLDOWN_TICKS;
    public static ForgeConfigSpec.DoubleValue CONVOY_WAR_SUPPORT_PER_CARGO;

    private CampaignConfig() {
    }

    /** Appends the campaign section to a spec being built. Called from {@code FirstCrusadeServerConfig}. */
    public static void build(ForgeConfigSpec.Builder builder) {
        builder.comment("Campanha planetaria: estado de guerra por planeta, setores e captura.")
                .push("campaign");

        ENABLED = builder
                .comment("Master switch. Off leaves every front frozen at whatever it last computed;",
                        "nothing is deleted and turning it back on resumes from there.")
                .define("enabled", true);

        STRATEGIC_INTERVAL_TICKS = builder
                .comment("Ticks between strategic passes. One pass recomputes control for every",
                        "activated front, so this is the campaign's entire per-tick cost. 200 = 10s.")
                .defineInRange("strategicIntervalTicks", 200, 20, 12_000);

        SETTLEMENT_INFLUENCE_RADIUS = builder
                .comment("How far (blocks) a city or camp presses on a sector. Beyond this it does",
                        "not contest it at all.")
                .defineInRange("settlementInfluenceRadius", 400, 32, 4000);

        PRESSURE_PER_SETTLEMENT = builder
                .comment("Contest points one settlement contributes per pass at point-blank range,",
                        "before the sector's own defence divides it down.")
                .defineInRange("pressurePerSettlement", 12, 1, 100);

        WAR_SPEED = builder
                .comment("Global multiplier on how fast ground changes hands. 0.5 halves it, 2.0",
                        "doubles it. Affects only the rate, never who wins.")
                .defineInRange("warSpeed", 1.0D, 0.05D, 10.0D);

        AUTO_ACTIVATE_FRONTS = builder
                .comment("Lay a planet's sectors out automatically the first time a player is on it.",
                        "Off means fronts only activate through /fcstrategy planet activate.")
                .define("autoActivateFronts", true);

        NECRON_AWAKENING_PER_PASS = builder
                .comment("Awakening a tomb world gains per pass while players are present. The",
                        "Necron faction has no entities yet: this only advances the number.")
                .defineInRange("necronAwakeningPerPass", 1, 0, 100);

        MAX_OPERATIONS_PER_FRONT = builder
                .comment("How many orders may stand on one planet at once. 0 turns operations off",
                        "without disturbing anything else in the campaign.")
                .defineInRange("maxOperationsPerFront", 2, 0, 10);

        OPERATION_TIME_SCALE = builder
                .comment("Multiplier on how long an order stands before it lapses. 2.0 gives twice",
                        "the deadline; it does not change what an order asks for.")
                .defineInRange("operationTimeScale", 1.0D, 0.1D, 10.0D);

        DEPLOYMENT_MUSTER_TICKS = builder
                .comment("Ticks a force spends gathering before it sets out. This is the window a",
                        "player gets to react to an order or an enemy build-up. 600 = 30s.")
                .defineInRange("deploymentMusterTicks", 600, 20, 24000);

        DEPLOYMENT_TRAVEL_TICKS = builder
                .comment("Ticks a force spends on the march before making contact. Nothing walks:",
                        "this is a timer, not pathfinding.")
                .defineInRange("deploymentTravelTicks", 1200, 20, 24000);

        DEPLOYMENT_MATERIALISE_CAP = builder
                .comment("Most units one deployment may become while a player is watching. The rest",
                        "stays arithmetic. 0 keeps every deployment fully abstract.")
                .defineInRange("deploymentMaterialiseCap", 12, 0, 100);

        MAX_DEPLOYMENTS_PER_SIDE = builder
                .comment("Live deployments one faction may have on one planet at once.")
                .defineInRange("maxDeploymentsPerSide", 3, 0, 20);

        ORK_RAID_THRESHOLD = builder
                .comment("WAAAGH! a planet must build up before the Orks launch an offensive.")
                .defineInRange("orkRaidThreshold", 100, 10, 10000);

        ORK_RAID_BUILD_PER_CAMP = builder
                .comment("WAAAGH! each Ork camp on a planet contributes per strategic pass, scaled",
                        "by that planet war intensity.")
                .defineInRange("orkRaidBuildPerCamp", 3, 0, 1000);

        MAX_LIVE_CONVOYS = builder
                .comment("Relief convoys that may be in the air across the whole Crusade at once.",
                        "0 turns convoys and their ESCORT orders off without disturbing the ordinary",
                        "supply lanes, which keep delivering exactly as before.")
                .defineInRange("maxLiveConvoys", 2, 0, 10);

        CONVOY_TRAVEL_TICKS = builder
                .comment("Ticks a convoy spends on the run. Nothing travels: this is a timer, not",
                        "pathfinding. 2400 = 2 minutes = 12 strategic passes at the default interval.")
                .defineInRange("convoyTravelTicks", 2400, 200, 48_000);

        CONVOY_ATTRITION_PER_PASS = builder
                .comment("Integrity (out of 100) a convoy loses per pass on a CONTESTED front, before",
                        "the destination war intensity scales it. A player standing on the front halves",
                        "it. Raise this to make convoys harder to bring in undefended.")
                .defineInRange("convoyAttritionPerPass", 4, 0, 50);

        CONVOY_DEFENCE_PER_KILL = builder
                .comment("Integrity a convoy recovers for each enemy killed on the front it is heading",
                        "for. This is what escorting actually is; 0 leaves presence as the only lever.")
                .defineInRange("convoyDefencePerKill", 3, 0, 50);

        CONVOY_CARGO_PASSES = builder
                .comment("How many passes of the lane rated throughput one convoy carries. The point of",
                        "the multiple is that a convoy has to be worth defending; at 1 it would be a",
                        "rounding error a player could rationally ignore.")
                .defineInRange("convoyCargoPasses", 6, 1, 50);

        CONVOY_COOLDOWN_TICKS = builder
                .comment("How long after a convoy finishes before that same lane may send another.",
                        "Doubles as how long a finished convoy stays visible on the War Table and in",
                        "/fcstrategy convoy list - the record IS the cooldown.")
                .defineInRange("convoyCooldownTicks", 6000, 0, 72_000);

        CONVOY_WAR_SUPPORT_PER_CARGO = builder
                .comment("War Support paid into the destination Command Core per unit of cargo that",
                        "actually landed. Cargo that arrives is already scaled by surviving integrity,",
                        "so this is the rate on what got through, not on what set out.")
                .defineInRange("convoyWarSupportPerCargo", 0.25D, 0.0D, 10.0D);

        builder.pop();
    }

    // ====================================================================================
    // Reads, each null-safe before config load
    // ====================================================================================
    //
    // Class-loading order puts a few of these in front of the config being read (a level tick can
    // fire during startup). Every getter therefore answers its own default rather than throwing on
    // a null spec value.

    public static boolean enabled() {
        return ENABLED == null || ENABLED.get();
    }

    public static int strategicIntervalTicks() {
        return STRATEGIC_INTERVAL_TICKS == null ? 200 : STRATEGIC_INTERVAL_TICKS.get();
    }

    public static int settlementInfluenceRadius() {
        return SETTLEMENT_INFLUENCE_RADIUS == null ? 400 : SETTLEMENT_INFLUENCE_RADIUS.get();
    }

    public static int pressurePerSettlement() {
        return PRESSURE_PER_SETTLEMENT == null ? 12 : PRESSURE_PER_SETTLEMENT.get();
    }

    public static double warSpeed() {
        return WAR_SPEED == null ? 1.0D : WAR_SPEED.get();
    }

    public static boolean autoActivateFronts() {
        return AUTO_ACTIVATE_FRONTS == null || AUTO_ACTIVATE_FRONTS.get();
    }

    public static int necronAwakeningPerPass() {
        return NECRON_AWAKENING_PER_PASS == null ? 1 : NECRON_AWAKENING_PER_PASS.get();
    }

    public static int maxOperationsPerFront() {
        return MAX_OPERATIONS_PER_FRONT == null ? 2 : MAX_OPERATIONS_PER_FRONT.get();
    }

    public static double operationTimeScale() {
        return OPERATION_TIME_SCALE == null ? 1.0D : OPERATION_TIME_SCALE.get();
    }

    public static int deploymentMusterTicks() {
        return DEPLOYMENT_MUSTER_TICKS == null ? 600 : DEPLOYMENT_MUSTER_TICKS.get();
    }

    public static int deploymentTravelTicks() {
        return DEPLOYMENT_TRAVEL_TICKS == null ? 1200 : DEPLOYMENT_TRAVEL_TICKS.get();
    }

    public static int deploymentMaterialiseCap() {
        return DEPLOYMENT_MATERIALISE_CAP == null ? 12 : DEPLOYMENT_MATERIALISE_CAP.get();
    }

    public static int maxDeploymentsPerSide() {
        return MAX_DEPLOYMENTS_PER_SIDE == null ? 3 : MAX_DEPLOYMENTS_PER_SIDE.get();
    }

    public static int orkRaidThreshold() {
        return ORK_RAID_THRESHOLD == null ? 100 : ORK_RAID_THRESHOLD.get();
    }

    public static int orkRaidBuildPerCamp() {
        return ORK_RAID_BUILD_PER_CAMP == null ? 3 : ORK_RAID_BUILD_PER_CAMP.get();
    }

    public static int maxLiveConvoys() {
        return MAX_LIVE_CONVOYS == null ? 2 : MAX_LIVE_CONVOYS.get();
    }

    public static int convoyTravelTicks() {
        return CONVOY_TRAVEL_TICKS == null ? 2400 : CONVOY_TRAVEL_TICKS.get();
    }

    public static int convoyAttritionPerPass() {
        return CONVOY_ATTRITION_PER_PASS == null ? 4 : CONVOY_ATTRITION_PER_PASS.get();
    }

    public static int convoyDefencePerKill() {
        return CONVOY_DEFENCE_PER_KILL == null ? 3 : CONVOY_DEFENCE_PER_KILL.get();
    }

    public static int convoyCargoPasses() {
        return CONVOY_CARGO_PASSES == null ? 6 : CONVOY_CARGO_PASSES.get();
    }

    public static int convoyCooldownTicks() {
        return CONVOY_COOLDOWN_TICKS == null ? 6000 : CONVOY_COOLDOWN_TICKS.get();
    }

    public static double convoyWarSupportPerCargo() {
        return CONVOY_WAR_SUPPORT_PER_CARGO == null ? 0.25D : CONVOY_WAR_SUPPORT_PER_CARGO.get();
    }
}
