package com.example.examplemod.performance.config;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeFaction;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Every number that decides how hard the server works, in one file.
 *
 * <h2>Server config, not common</h2>
 *
 * All of this changes <b>simulation</b>: how far a soldier can see an enemy, how often a squad looks
 * for one, when a distant unit stops thinking at full speed. That makes it the server's business and
 * nobody else's — a client must never be able to widen its own units' acquisition range, and in
 * multiplayer there is exactly one right answer per world. Purely visual settings (particles,
 * tracers, muzzle flashes) live in {@link FirstCrusadeClientConfig} and cannot touch any of this.
 *
 * <h2>Why the values are mirrored into static fields</h2>
 *
 * {@code ForgeConfigSpec.IntValue.get()} walks a config tree and boxes an Integer. That is fine
 * once per second and wrong several hundred times per tick, which is exactly how often the AI hot
 * paths ask these questions. The spec is the source of truth; {@link #apply()} copies it into plain
 * fields on load and reload, and the hot paths read the fields. Same arrangement
 * {@code FirstCrusadeServerConfig} already uses for {@code ExampleMod.TEST_FIXED_WORLD}.
 *
 * <h2>The defaults are the audit's findings</h2>
 *
 * {@link #followRangeCap} defaults to 40 rather than the 96 that shipped. The old value forced a
 * 192x8x192 search box onto every Imperial and Ork unit — roughly 295,000 cubic metres, 144 chunk
 * columns — twice a second each, because {@code NearestAttackableTargetGoal} inflates its search
 * area by {@code FOLLOW_RANGE} and collects <i>every</i> living entity inside before filtering. A
 * server owner who wants the old behaviour can still set 96 here; the point is that it is a
 * decision with a number attached rather than a constant nobody could find.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FirstCrusadePerformanceConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ==================================================================== acquisition

    public static final ForgeConfigSpec.DoubleValue FOLLOW_RANGE_CAP = BUILDER
            .comment("Maximum FOLLOW_RANGE any Imperial/Ork combat unit may hold, in blocks.",
                    "This is the single biggest performance dial in the mod: a target scan collects",
                    "every living entity in a box inflated by this value, so doubling it multiplies",
                    "the work by roughly eight. Units that ask for less than this keep their own",
                    "value; only units asking for more are clamped down to it.",
                    "Was an unconditional 96 before 2026-08-12. 40 is a long sightline that still",
                    "costs about a twentieth of what 96 did.",
                    "This value is also the radius of the world region every pathfind copies:",
                    "PathNavigation#createPath sizes its PathNavigationRegion as FOLLOW_RANGE + 16",
                    "in each direction. At 96 that was a 112-block cube per path; at 40 it is a",
                    "56-block cube, about an eighth of the volume. So this dial pays twice, in",
                    "target acquisition and in pathfinding, which is why it is the first one to try.")
            .defineInRange("ai.followRangeCap", 40.0D, 8.0D, 128.0D);

    public static final ForgeConfigSpec.DoubleValue FOLLOW_RANGE_FLOOR = BUILDER
            .comment("Minimum FOLLOW_RANGE for a combat unit, in blocks.",
                    "Stops a unit that declares a tiny range from being blind on an open field.",
                    "Applied only to Imperial/Ork combatants, never to animals or civilians.")
            .defineInRange("ai.followRangeFloor", 24.0D, 4.0D, 64.0D);

    // ==================================================================== AI level of detail

    public static final ForgeConfigSpec.BooleanValue AI_LOD_ENABLED = BUILDER
            .comment("Scale AI work by distance to the nearest player.",
                    "Off means every unit thinks at full speed no matter how far away it is.")
            .define("ai.lod.enabled", true);

    public static final ForgeConfigSpec.IntValue FULL_AI_DISTANCE = BUILDER
            .comment("Within this distance of a player, a unit runs full AI (20 updates/s).")
            .defineInRange("ai.lod.fullDistance", 32, 8, 256);

    public static final ForgeConfigSpec.IntValue MEDIUM_AI_DISTANCE = BUILDER
            .comment("Within this distance, a unit runs medium AI (about 10 updates/s).")
            .defineInRange("ai.lod.mediumDistance", 64, 16, 384);

    public static final ForgeConfigSpec.IntValue LOW_AI_DISTANCE = BUILDER
            .comment("Within this distance, a unit runs low AI (about 4 updates/s).",
                    "Beyond it, strategic AI (about 1 update/s).")
            .defineInRange("ai.lod.lowDistance", 128, 32, 512);

    public static final ForgeConfigSpec.IntValue MEDIUM_AI_MULTIPLIER = BUILDER
            .comment("How much longer a MEDIUM-detail unit waits between expensive decisions.",
                    "2 means half rate: about ten decisions a second instead of twenty.")
            .defineInRange("ai.lod.mediumMultiplier", 2, 1, 100);

    public static final ForgeConfigSpec.IntValue LOW_AI_MULTIPLIER = BUILDER
            .comment("Interval multiplier for LOW detail. 5 is about four decisions a second.")
            .defineInRange("ai.lod.lowMultiplier", 5, 1, 100);

    public static final ForgeConfigSpec.IntValue STRATEGIC_AI_MULTIPLIER = BUILDER
            .comment("Interval multiplier for STRATEGIC detail — units with no player within",
                    "lowDistance. 20 is about one decision a second.",
                    "TUNING NOTE, measured 2026-08-12: at 20 a battle nobody is watching barely",
                    "advances — 100 v 100 produced 8 casualties in 30 seconds instead of about a",
                    "hundred. That is correct if no player will ever see the result, and wrong if",
                    "off-screen battles are supposed to decide territory on their own. Until the",
                    "strategic combat simulation exists, lower this to 6-8 if you want distant wars",
                    "to actually resolve; raise it if you only care about what is on screen.")
            .defineInRange("ai.lod.strategicMultiplier", 20, 1, 200);

    public static final ForgeConfigSpec.DoubleValue VERTICAL_TARGET_RANGE = BUILDER
            .comment("How far above or below itself a unit may acquire a target, in blocks.",
                    "Vanilla's target box is only inflated by 4 on the Y axis, which keeps fights",
                    "flat. This is deliberately a little more forgiving so troops on a rampart or a",
                    "hab-block stairway still engage, without a soldier locking onto somebody forty",
                    "levels up a Hive spire that it could never reach.")
            .defineInRange("ai.verticalTargetRange", 8.0D, 2.0D, 64.0D);

    // ==================================================================== scan scheduling

    public static final ForgeConfigSpec.IntValue TARGET_SCAN_INTERVAL = BUILDER
            .comment("Ticks between one unit's search for a new target.",
                    "Vanilla rolls a 1-in-10 chance each time it evaluates the goal, and it only",
                    "evaluates on every other tick, so the real average was about 20 ticks with a",
                    "long tail: some units rescanned twice in a row, others waited three seconds.",
                    "20 keeps the same average and removes the variance, which is what actually",
                    "spikes a server when three hundred units roll at once.")
            .defineInRange("ai.targetScanIntervalTicks", 20, 2, 200);

    public static final ForgeConfigSpec.IntValue ENEMY_INDEX_REFRESH = BUILDER
            .comment("Ticks between rebuilds of the per-level combatant index.",
                    "Only the faction sorting is cached; positions are always read live, so this",
                    "never makes a unit shoot at where an enemy used to be. The cost of a lower",
                    "value is one pass over the roster; the cost of a higher one is that a freshly",
                    "spawned unit takes that long to become visible to the enemy.")
            .defineInRange("ai.enemyIndexRefreshTicks", 10, 1, 100);

    public static final ForgeConfigSpec.IntValue MAX_TARGET_CANDIDATE_CHECKS = BUILDER
            .comment("How many candidates a unit may line-of-sight test in one scan.",
                    "Candidates are tested nearest-first, so the first one that passes is the same",
                    "enemy vanilla would have chosen. This only bounds the pathological case: a unit",
                    "walled off from an entire army, which would otherwise raycast to every single",
                    "enemy on the field and find nobody.")
            .defineInRange("ai.maxTargetCandidateChecks", 12, 1, 64);

    public static final ForgeConfigSpec.IntValue REPATH_INTERVAL = BUILDER
            .comment("Ticks a chasing unit keeps its current path before asking for a new one.",
                    "Every call to Navigation#moveTo runs a fresh A* — there is no cache underneath",
                    "it — so a goal that repaths each tick pays a full pathfind per unit per tick.",
                    "The target has not moved far in half a second, and the unit is already walking",
                    "towards it, so the staleness is invisible while the saving is not.")
            .defineInRange("ai.repathIntervalTicks", 10, 1, 100);

    public static final ForgeConfigSpec.IntValue SQUAD_SCAN_INTERVAL = BUILDER
            .comment("Ticks between a squad's shared enemy scan at full AI.",
                    "One scan per squad replaces one scan per soldier; AI level of detail and the",
                    "squad's own state stretch this further.")
            .defineInRange("ai.squadScanIntervalTicks", 20, 4, 200);

    // ==================================================================== squads
    //
    // These were constants scattered across FCLeaderGoal and FCFormationGoal. Gathering them is not
    // tidiness for its own sake: they interact, and interacting numbers that live in different files
    // get tuned against each other by accident. The cohesion radius has to stay comfortably larger
    // than the recruit radius, for instance, or a squad drops members it cannot re-recruit and the
    // soldier bounces between squads forever.

    public static final ForgeConfigSpec.IntValue SQUAD_RECRUIT_INTERVAL = BUILDER
            .comment("Ticks between a leader's sweep for new followers.",
                    "Recruitment scans an area, so this is the leader's most expensive routine job.")
            .defineInRange("ai.squad.recruitIntervalTicks", 40, 5, 400);

    public static final ForgeConfigSpec.DoubleValue SQUAD_RECRUIT_RADIUS = BUILDER
            .comment("How far a leader looks for new followers, in blocks.")
            .defineInRange("ai.squad.recruitRadius", 12.0D, 4.0D, 64.0D);

    public static final ForgeConfigSpec.DoubleValue SQUAD_COHESION_RADIUS = BUILDER
            .comment("How far a follower may stray before it is dropped from the squad, in blocks.",
                    "Keep this well above recruitRadius. If they are close together a soldier that",
                    "steps out of the recruit radius is immediately dropped and cannot be picked up",
                    "again until it walks back, which reads in game as squads constantly falling",
                    "apart for no visible reason.")
            .defineInRange("ai.squad.cohesionRadius", 28.0D, 8.0D, 128.0D);

    public static final ForgeConfigSpec.IntValue SQUAD_FORMATION_INTERVAL = BUILDER
            .comment("Ticks between a follower's attempts to correct its formation position.",
                    "A firing line does not need its shape recomputed twenty times a second; the",
                    "squad state stretches this further when nothing is happening.")
            .defineInRange("ai.squad.formationIntervalTicks", 10, 1, 100);

    public static final ForgeConfigSpec.IntValue SQUAD_ENEMY_COUNT_INTERVAL = BUILDER
            .comment("Goal evaluations between a leader's head-count of nearby enemies.",
                    "Only the counting is throttled: the formation is re-decided every tick from the",
                    "cached number, so a squad still reacts the instant its leader acquires a target.")
            .defineInRange("ai.squad.enemyCountIntervalTicks", 5, 1, 100);

    public static final ForgeConfigSpec.IntValue SQUAD_MAX_IMPERIAL = BUILDER
            .comment("Most followers an Imperial Guard squad may hold.",
                    "A unit's own combat profile may ask for fewer; this is the ceiling, so that a",
                    "single sergeant cannot end up leading a hundred men and turning one entity's",
                    "tick into the whole battle's cost.")
            .defineInRange("ai.squad.maxImperialFollowers", 11, 1, 60);

    public static final ForgeConfigSpec.IntValue SQUAD_MAX_ELITE = BUILDER
            .comment("Most followers an elite squad may hold — Kasrkin, Space Marines, Custodes.",
                    "Smaller than a line squad on purpose: elites fight in small formations.")
            .defineInRange("ai.squad.maxEliteFollowers", 9, 1, 40);

    public static final ForgeConfigSpec.IntValue SQUAD_MAX_ORK = BUILDER
            .comment("Most followers an Ork mob may hold. Larger than an Imperial squad by design.")
            .defineInRange("ai.squad.maxOrkFollowers", 19, 1, 80);


    // ==================================================================== strategic combat

    public static final ForgeConfigSpec.BooleanValue STRATEGIC_ENABLED = BUILDER
            .comment("Resolve battles far from every player as arithmetic instead of as entities.",
                    "Off means distant battles stay physical and merely think slowly (see ai.lod).")
            .define("strategic.enabled", true);

    public static final ForgeConfigSpec.IntValue STRATEGIC_MATERIALISE_DISTANCE = BUILDER
            .comment("A player this close, in blocks, turns a strategic battle back into troops.")
            .defineInRange("strategic.materialiseDistance", 160, 32, 512);

    public static final ForgeConfigSpec.IntValue STRATEGIC_DEMATERIALISE_DISTANCE = BUILDER
            .comment("No player within this distance is what allows a battle to be absorbed.",
                    "Deliberately larger than materialiseDistance: the gap between the two is the",
                    "hysteresis that stops a player pacing the boundary from spawning and despawning",
                    "an army over and over.")
            .defineInRange("strategic.dematerialiseDistance", 220, 48, 768);

    public static final ForgeConfigSpec.IntValue STRATEGIC_DEMATERIALISE_DELAY = BUILDER
            .comment("Ticks a battle must go unwatched before it is absorbed. The second half of the",
                    "hysteresis: distance alone would still flicker for a player circling at range.")
            .defineInRange("strategic.dematerialiseDelayTicks", 200, 20, 6000);

    public static final ForgeConfigSpec.IntValue STRATEGIC_MIN_UNITS = BUILDER
            .comment("Fewest units in one place before it is worth abstracting into a battle.",
                    "Below this the entities are cheap enough that abstracting them buys nothing and",
                    "only risks losing individuality.")
            .defineInRange("strategic.minUnits", 20, 2, 500);

    public static final ForgeConfigSpec.IntValue STRATEGIC_CLUSTER_RADIUS = BUILDER
            .comment("How far apart, in blocks, units may be and still count as one battle.")
            .defineInRange("strategic.clusterRadius", 48, 8, 256);

    public static final ForgeConfigSpec.IntValue STRATEGIC_SCAN_INTERVAL = BUILDER
            .comment("Ticks between sweeps looking for battles to absorb. This is a whole-level pass,",
                    "so it is deliberately infrequent.")
            .defineInRange("strategic.scanIntervalTicks", 100, 20, 6000);

    public static final ForgeConfigSpec.IntValue STRATEGIC_STEP_TICKS = BUILDER
            .comment("Ticks between resolution steps of an abstracted battle.")
            .defineInRange("strategic.stepIntervalTicks", 20, 1, 200);

    public static final ForgeConfigSpec.DoubleValue STRATEGIC_DAMAGE_RATE = BUILDER
            .comment("Damage multiplier per resolution step. Higher resolves battles faster.",
                    "The calibration target is the only one that matters: walking away from a battle",
                    "must not change who wins it, so an abstracted fight should reach roughly the",
                    "same outcome in roughly the same time as the physical one it replaced.")
            .defineInRange("strategic.damageRate", 0.35D, 0.01D, 10.0D);

    public static final ForgeConfigSpec.DoubleValue STRATEGIC_DEFENCE_ADVANTAGE = BUILDER
            .comment("Damage multiplier applied to a side that holds the ground. Below 1 is a bonus.")
            .defineInRange("strategic.defenceAdvantage", 0.75D, 0.1D, 1.0D);

    public static final ForgeConfigSpec.IntValue STRATEGIC_BATCH_SIZE = BUILDER
            .comment("Units spawned per tick while a battle materialises.",
                    "The whole reason materialisation is batched: dropping three hundred entities",
                    "into one tick is a guaranteed stall exactly when the player is arriving to look.")
            .defineInRange("strategic.materialiseBatchSize", 10, 1, 200);

    public static final ForgeConfigSpec.IntValue STRATEGIC_SPAWN_SPREAD = BUILDER
            .comment("Radius, in blocks, that materialising units are scattered over.")
            .defineInRange("strategic.spawnSpread", 12, 1, 64);

    public static final ForgeConfigSpec.BooleanValue STRATEGIC_ABSORB_NAMED = BUILDER
            .comment("Allow units that carry a custom name to be abstracted into a strategic battle.",
                    "READ THIS BEFORE TURNING IT OFF. It sounds like the safe setting and on this mod",
                    "it is drastic: around fifteen unit classes -- Agri Militia, Custodes, Enforcer,",
                    "Feudal Knight, Jungle Fighter, Kasrkin, Penal Legionnaire, Mine Guard and others",
                    "-- call setCustomName in their own constructor, using the name as a TYPE LABEL",
                    "rather than as individuality. Setting this to false therefore excludes most of",
                    "the army from the strategic layer, and large distant battles go back to being",
                    "simulated entity by entity.",
                    "A constructor label and a player's name tag leave an entity in an identical",
                    "state, so the code cannot tell them apart -- which is why this is your decision",
                    "and not a guess. Units that are genuinely irreplaceable do not rely on this at",
                    "all: they implement FCStrategicExempt and are never absorbed either way.",
                    "true  = abstract named units, carrying the name through and restoring it.",
                    "false = keep every named unit physical, at a real cost in distant-battle TPS.")
            .define("strategic.absorbNamedUnits", true);

    public static final ForgeConfigSpec.IntValue STRATEGIC_MAX_SPAWN_ATTEMPTS = BUILDER
            .comment("Placement attempts a materialising battle may make in one tick.",
                    "A unit that lands somewhere solid is retried rather than written off, so this",
                    "is what stops a battle whose centre fell inside a wall from spending the whole",
                    "tick looking for room. Attempts are cheap — a position test, no entity added.")
            .defineInRange("strategic.maxSpawnAttemptsPerTick", 40, 4, 500);

    public static final ForgeConfigSpec.IntValue STRATEGIC_MAX_SPAWN_FAILURES = BUILDER
            .comment("Consecutive failed placements before a battle gives up for now.",
                    "Giving up means going back to the strategic list to be tried again later. It",
                    "never means the units died: a technical failure to find floor space is not a",
                    "casualty, and treating it as one is how an army disappears into a hillside.")
            .defineInRange("strategic.maxSpawnFailures", 240, 10, 5000);

    public static final ForgeConfigSpec.IntValue STRATEGIC_SPAWN_RADIUS_EXPANSION = BUILDER
            .comment("Blocks added to the scatter radius per consecutive failed placement.",
                    "The battle centre is wherever the fight was, which can be inside a building or",
                    "on a cliff. Widening the search after each miss is what lets an army arrive",
                    "next to an obstacle instead of refusing to arrive at all.")
            .defineInRange("strategic.spawnRadiusExpansion", 1, 0, 16);

    public static final ForgeConfigSpec.IntValue STRATEGIC_MAX_SPAWN_SPREAD = BUILDER
            .comment("Ceiling, in blocks, that the expanding scatter radius may reach.",
                    "Without a cap a battle that cannot place anybody would keep widening until it",
                    "was spawning reinforcements in the next valley.")
            .defineInRange("strategic.maxSpawnSpread", 64, 8, 256);

    // ==================================================================== broadcast particles
    //
    // These govern ServerLevel#sendParticles: the server building a packet and pushing it to every
    // player in range. That is why they are here and not in the client config — one player's
    // graphics preset cannot decide what the server broadcasts to everybody else, which is exactly
    // why visuals.explosionEffects and visuals.debrisAmount over there were marked NOT WIRED. These
    // are the dials they were waiting for.
    //
    // Only the nine COMBAT call sites are routed through FCServerParticles: muzzle flash, casing
    // eject, las-bolt micro-detonation, Sentinel missile trail and impact, Sentinel stomp. Those are
    // the ones whose cost scales with the size of a battle. Deliberately NOT routed, and it is not
    // an oversight:
    //   - the /fchive debug markers and the governor's location marker, because a visualisation you
    //     asked for by typing a command should appear exactly as requested;
    //   - progression and Ork ability feedback, because it fires on a player's own input a few times
    //     a second at most, and thinning it steals the confirmation that the ability went off.
    // Neither of those grows with army size, so neither is worth a dial.
    //
    // Defaults are 100 everywhere: out of the box the mod broadcasts exactly what it did before this
    // section existed. See docs/PERFORMANCE.md for the values worth trying on a large battle.

    public static final ForgeConfigSpec.IntValue PARTICLE_MASTER_DENSITY = BUILDER
            .comment("Master percentage for every particle effect the SERVER broadcasts.",
                    "0 stops the server sending mod particles entirely; 100 sends all of them.",
                    "Combined with each channel below by minimum, not by multiplication: a master of",
                    "70 and a channel of 70 means 70%, not 49%. Purely visual — damage, blast radius",
                    "and hit registration never read this.")
            .defineInRange("particles.masterDensity", 100, 0, 100);

    public static final ForgeConfigSpec.IntValue PARTICLE_MUZZLE_FLASH_DENSITY = BUILDER
            .comment("Percentage of muzzle flash particles broadcast when a weapon fires.",
                    "The highest-frequency effect in the mod: one send per shot per trooper, so two",
                    "hundred soldiers in a firefight is where this dial earns its keep. Try 40-50 if",
                    "a large battle is costing you frames.")
            .defineInRange("particles.muzzleFlashDensity", 100, 0, 100);

    public static final ForgeConfigSpec.IntValue PARTICLE_EXPLOSION_DENSITY = BUILDER
            .comment("Percentage of detonation fireball and flame particles broadcast.",
                    "Covers the bolter micro-detonation, the Sentinel missile impact and the Sentinel",
                    "stomp. Blast damage and radius are decided elsewhere and do not change with it.")
            .defineInRange("particles.explosionDensity", 100, 0, 100);

    public static final ForgeConfigSpec.IntValue PARTICLE_SMOKE_DENSITY = BUILDER
            .comment("Percentage of smoke particles broadcast by detonations, stomps and trails.",
                    "The Sentinel missile is the one that sends smoke every tick of its flight; the",
                    "rest are one-off impacts.")
            .defineInRange("particles.smokeDensity", 100, 0, 100);

    public static final ForgeConfigSpec.IntValue PARTICLE_DEBRIS_DENSITY = BUILDER
            .comment("Percentage of spent casings and impact fragments broadcast.",
                    "Like the muzzle flash, one send per shot fired. The cheapest thing to turn down",
                    "first, because a missing casing is the least missed effect in a firefight.")
            .defineInRange("particles.debrisDensity", 100, 0, 100);

    public static final ForgeConfigSpec.IntValue PARTICLE_MAX_SEND_DISTANCE = BUILDER
            .comment("Skip broadcasting an effect with no player within this many blocks.",
                    "32 is the ceiling on purpose, not an arbitrary cap: vanilla's own sendParticles",
                    "already refuses to deliver a non-forced particle past 32 blocks, so a larger",
                    "number here would be a dial that cannot do anything. At 32 this changes nothing",
                    "a player could see and only skips building a packet destined for nobody; below",
                    "32 it becomes a real visual range cut.")
            .defineInRange("particles.maxSendDistance", 32, 8, 32);

    public static final ForgeConfigSpec.IntValue PARTICLE_MAX_SENDS_PER_TICK = BUILDER
            .comment("Hard ceiling on mod particle broadcasts in one server tick.",
                    "A safety valve rather than a tuning dial: the percentages above scale with how",
                    "much is happening, and this one catches the case they cannot — three hundred",
                    "soldiers whose reload timers have drifted into sync all firing on the same tick.",
                    "Run '/fc perf' to see the busiest tick so far and whether this has ever bitten.",
                    "If it never has, it is doing its job by being invisible; if it bites constantly,",
                    "the percentages above are the correct place to fix it, not this.")
            .defineInRange("particles.maxSendsPerTick", 256, 16, 4096);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private FirstCrusadePerformanceConfig() {
    }

    // ==================================================================== mirrored values

    private static double followRangeCap = 40.0D;
    private static double followRangeFloor = 24.0D;
    private static double verticalTargetRange = 8.0D;
    private static boolean aiLodEnabled = true;
    private static int fullAiDistance = 32;
    private static int mediumAiDistance = 64;
    private static int lowAiDistance = 128;
    private static int mediumAiMultiplier = 2;
    private static int lowAiMultiplier = 5;
    private static int strategicAiMultiplier = 20;
    private static int targetScanInterval = 20;
    private static int enemyIndexRefreshTicks = 10;
    private static int maxTargetCandidateChecks = 12;
    private static int repathInterval = 10;
    private static int squadScanInterval = 20;
    private static int squadRecruitInterval = 40;
    private static double squadRecruitRadius = 12.0D;
    private static double squadCohesionRadius = 28.0D;
    private static int squadFormationInterval = 10;
    private static int squadEnemyCountInterval = 5;
    private static int squadMaxImperial = 11;
    private static int squadMaxElite = 9;
    private static int squadMaxOrk = 19;
    private static boolean strategicEnabled = true;
    private static int strategicMaterialiseDistance = 160;
    private static int strategicDematerialiseDistance = 220;
    private static int strategicDematerialiseDelay = 200;
    private static int strategicMinUnits = 20;
    private static int strategicClusterRadius = 48;
    private static int strategicScanInterval = 100;
    private static int strategicStepTicks = 20;
    private static double strategicDamageRate = 0.35D;
    private static double strategicDefenceAdvantage = 0.75D;
    private static int strategicBatchSize = 10;
    private static int strategicSpawnSpread = 12;
    private static boolean strategicAbsorbNamed = true;
    private static int strategicMaxSpawnAttempts = 40;
    private static int strategicMaxSpawnFailures = 240;
    private static int strategicSpawnRadiusExpansion = 1;
    private static int strategicMaxSpawnSpread = 64;
    private static int masterParticleDensity = 100;
    private static int muzzleFlashParticleDensity = 100;
    private static int explosionParticleDensity = 100;
    private static int smokeParticleDensity = 100;
    private static int debrisParticleDensity = 100;
    private static int maxParticleSendDistance = 32;
    private static int maxParticleSendsPerTick = 256;

    public static double followRangeCap() {
        return followRangeCap;
    }

    public static double followRangeFloor() {
        return followRangeFloor;
    }

    public static boolean aiLodEnabled() {
        return aiLodEnabled;
    }

    public static int fullAiDistance() {
        return fullAiDistance;
    }

    public static int mediumAiDistance() {
        return mediumAiDistance;
    }

    public static int lowAiDistance() {
        return lowAiDistance;
    }

    public static int mediumAiMultiplier() {
        return mediumAiMultiplier;
    }

    public static int lowAiMultiplier() {
        return lowAiMultiplier;
    }

    public static int strategicAiMultiplier() {
        return strategicAiMultiplier;
    }

    public static double verticalTargetRange() {
        return verticalTargetRange;
    }

    /** Ticks between one unit's target search. See {@code FirstCrusadeNearestEnemyTargetGoal}. */
    public static int targetScanInterval() {
        return targetScanInterval;
    }

    public static int enemyIndexRefreshTicks() {
        return enemyIndexRefreshTicks;
    }

    public static int maxTargetCandidateChecks() {
        return maxTargetCandidateChecks;
    }

    /** Ticks a chasing unit keeps its path before recomputing. See ImperialLasgunAttackGoal. */
    public static int repathInterval() {
        return repathInterval;
    }

    public static int squadScanInterval() {
        return squadScanInterval;
    }

    // ---------------------------------------------------------------- squads

    public static int squadRecruitInterval() {
        return squadRecruitInterval;
    }

    public static double squadRecruitRadius() {
        return squadRecruitRadius;
    }

    public static double squadCohesionRadius() {
        return squadCohesionRadius;
    }

    public static int squadFormationInterval() {
        return squadFormationInterval;
    }

    public static int squadEnemyCountInterval() {
        return squadEnemyCountInterval;
    }

    /**
     * The ceiling on followers for a unit of this faction.
     *
     * <p>A unit's own {@code FCCombatProfile} may ask for fewer and keeps that number; this only
     * caps it, the same way {@link #clampFollowRange} caps sight range. Elite units are recognised
     * by asking for a small squad in the first place, so they get the elite ceiling rather than the
     * line-infantry one.
     */
    public static int squadFollowerCap(FirstCrusadeFaction faction, int declared) {
        int cap = switch (faction) {
            case ORKS -> squadMaxOrk;
            case IMPERIUM -> declared <= squadMaxElite ? squadMaxElite : squadMaxImperial;
            default -> squadMaxImperial;
        };

        return Math.max(0, Math.min(cap, declared));
    }

    public static boolean strategicEnabled() {
        return strategicEnabled;
    }

    public static int strategicMaterialiseDistance() {
        return strategicMaterialiseDistance;
    }

    public static int strategicDematerialiseDistance() {
        return strategicDematerialiseDistance;
    }

    public static int strategicDematerialiseDelay() {
        return strategicDematerialiseDelay;
    }

    public static int strategicMinUnits() {
        return strategicMinUnits;
    }

    public static int strategicClusterRadius() {
        return strategicClusterRadius;
    }

    public static int strategicScanInterval() {
        return strategicScanInterval;
    }

    public static int strategicStepTicks() {
        return strategicStepTicks;
    }

    public static double strategicDamageRate() {
        return strategicDamageRate;
    }

    public static double strategicDefenceAdvantage() {
        return strategicDefenceAdvantage;
    }

    public static int strategicBatchSize() {
        return strategicBatchSize;
    }

    public static int strategicSpawnSpread() {
        return strategicSpawnSpread;
    }

    /** Whether a custom-named unit may be abstracted. See the config comment before changing it. */
    public static boolean absorbNamedUnits() {
        return strategicAbsorbNamed;
    }

    public static int strategicMaxSpawnAttempts() {
        return strategicMaxSpawnAttempts;
    }

    public static int strategicMaxSpawnFailures() {
        return strategicMaxSpawnFailures;
    }

    public static int strategicSpawnRadiusExpansion() {
        return strategicSpawnRadiusExpansion;
    }

    public static int strategicMaxSpawnSpread() {
        return strategicMaxSpawnSpread;
    }

    // ---------------------------------------------------------------- broadcast particles
    //
    // Read by FCServerParticles once per effect. Plain fields, no .get(): a firefight asks these
    // questions several hundred times a second and ForgeConfigSpec.IntValue#get walks a config tree
    // and boxes an Integer every time.

    public static int masterParticleDensity() {
        return masterParticleDensity;
    }

    public static int muzzleFlashParticleDensity() {
        return muzzleFlashParticleDensity;
    }

    public static int explosionParticleDensity() {
        return explosionParticleDensity;
    }

    public static int smokeParticleDensity() {
        return smokeParticleDensity;
    }

    public static int debrisParticleDensity() {
        return debrisParticleDensity;
    }

    public static int maxParticleSendDistance() {
        return maxParticleSendDistance;
    }

    public static int maxParticleSendsPerTick() {
        return maxParticleSendsPerTick;
    }

    /**
     * Clamps a unit's declared follow range into the configured band.
     *
     * <p>Clamped rather than assigned, which is the whole correction: the old code overwrote every
     * unit's range with one number, so a Sump Rat that asked for 12 and a Valkyrie that asked for 72
     * both ended up at 96. A unit that wants less than the cap knows something about itself worth
     * keeping.
     */
    public static double clampFollowRange(double declared) {
        double floor = Math.min(followRangeFloor, followRangeCap);
        return Math.max(floor, Math.min(followRangeCap, declared));
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
        followRangeCap = FOLLOW_RANGE_CAP.get();
        followRangeFloor = FOLLOW_RANGE_FLOOR.get();
        verticalTargetRange = VERTICAL_TARGET_RANGE.get();
        targetScanInterval = TARGET_SCAN_INTERVAL.get();
        enemyIndexRefreshTicks = ENEMY_INDEX_REFRESH.get();
        maxTargetCandidateChecks = MAX_TARGET_CANDIDATE_CHECKS.get();
        repathInterval = REPATH_INTERVAL.get();
        aiLodEnabled = AI_LOD_ENABLED.get();
        fullAiDistance = FULL_AI_DISTANCE.get();
        mediumAiDistance = MEDIUM_AI_DISTANCE.get();
        lowAiDistance = LOW_AI_DISTANCE.get();
        mediumAiMultiplier = MEDIUM_AI_MULTIPLIER.get();
        lowAiMultiplier = LOW_AI_MULTIPLIER.get();
        strategicAiMultiplier = STRATEGIC_AI_MULTIPLIER.get();
        squadScanInterval = SQUAD_SCAN_INTERVAL.get();
        squadRecruitInterval = SQUAD_RECRUIT_INTERVAL.get();
        squadRecruitRadius = SQUAD_RECRUIT_RADIUS.get();
        squadCohesionRadius = SQUAD_COHESION_RADIUS.get();
        squadFormationInterval = SQUAD_FORMATION_INTERVAL.get();
        squadEnemyCountInterval = SQUAD_ENEMY_COUNT_INTERVAL.get();
        squadMaxImperial = SQUAD_MAX_IMPERIAL.get();
        squadMaxElite = SQUAD_MAX_ELITE.get();
        squadMaxOrk = SQUAD_MAX_ORK.get();
        strategicEnabled = STRATEGIC_ENABLED.get();
        strategicMaterialiseDistance = STRATEGIC_MATERIALISE_DISTANCE.get();
        strategicDematerialiseDistance = STRATEGIC_DEMATERIALISE_DISTANCE.get();
        strategicDematerialiseDelay = STRATEGIC_DEMATERIALISE_DELAY.get();
        strategicMinUnits = STRATEGIC_MIN_UNITS.get();
        strategicClusterRadius = STRATEGIC_CLUSTER_RADIUS.get();
        strategicScanInterval = STRATEGIC_SCAN_INTERVAL.get();
        strategicStepTicks = STRATEGIC_STEP_TICKS.get();
        strategicDamageRate = STRATEGIC_DAMAGE_RATE.get();
        strategicDefenceAdvantage = STRATEGIC_DEFENCE_ADVANTAGE.get();
        strategicBatchSize = STRATEGIC_BATCH_SIZE.get();
        strategicSpawnSpread = STRATEGIC_SPAWN_SPREAD.get();
        strategicAbsorbNamed = STRATEGIC_ABSORB_NAMED.get();
        strategicMaxSpawnAttempts = STRATEGIC_MAX_SPAWN_ATTEMPTS.get();
        strategicMaxSpawnFailures = STRATEGIC_MAX_SPAWN_FAILURES.get();
        strategicSpawnRadiusExpansion = STRATEGIC_SPAWN_RADIUS_EXPANSION.get();
        strategicMaxSpawnSpread = STRATEGIC_MAX_SPAWN_SPREAD.get();
        masterParticleDensity = PARTICLE_MASTER_DENSITY.get();
        muzzleFlashParticleDensity = PARTICLE_MUZZLE_FLASH_DENSITY.get();
        explosionParticleDensity = PARTICLE_EXPLOSION_DENSITY.get();
        smokeParticleDensity = PARTICLE_SMOKE_DENSITY.get();
        debrisParticleDensity = PARTICLE_DEBRIS_DENSITY.get();
        maxParticleSendDistance = PARTICLE_MAX_SEND_DISTANCE.get();
        maxParticleSendsPerTick = PARTICLE_MAX_SENDS_PER_TICK.get();
    }
}
