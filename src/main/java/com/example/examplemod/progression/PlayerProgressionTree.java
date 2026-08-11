package com.example.examplemod.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.examplemod.StrategicAge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The Imperial progression tree: fifty nodes, twelve cycles, one shape.
 *
 * <h2>The shape is the rule</h2>
 *
 * Every cycle is three skills and then one surgery, and the surgery is the only door to the next
 * cycle. That is not enforced by a special case somewhere — it falls out of the prerequisites written
 * here: each skill of cycle N lists the implant of cycle N-1, and each implant lists its own three
 * skills. {@link PlayerProgressionRequirements} only ever has to ask "is every prerequisite at rank
 * 1", and there is no path around an organ because no node outside a cycle points into it.
 *
 * <h2>Coordinates</h2>
 *
 * {@link PlayerSkillNodeDefinition#x()} and {@code y()} are in tree units, not pixels: three columns
 * at {@code -2, 0, +2} and one row per half-cycle, growing downward. The screen multiplies by its
 * own zoom, so the layout survives any GUI scale and any window.
 *
 * <h2>Adding to it</h2>
 *
 * A new node is one {@code skill(...)} line plus two lang keys. Nothing else in the mod enumerates
 * nodes — the screen, the tooltips, the save format and the commands all read this table.
 */
public final class PlayerProgressionTree {
    private PlayerProgressionTree() {
    }

    public static final String ROOT_ID = "astra_recruit";
    public static final String ASCENSION_ID = "adeptus_astartes";

    private static final Map<String, PlayerSkillNodeDefinition> NODES = new LinkedHashMap<>();
    private static final Map<String, PlayerEvolutionNodeDefinition> SURGERIES = new LinkedHashMap<>();
    private static final List<PlayerSkillNodeDefinition> ORDERED = new ArrayList<>();

    /** Column positions of the three skills of a cycle, and of the organ underneath them. */
    private static final int LEFT = -2;
    private static final int MIDDLE = 0;
    private static final int RIGHT = 2;

    static {
        // ------------------------------------------------------------------ the free first node
        add(new PlayerSkillNodeDefinition(ROOT_ID, 0, PlayerSkillBranch.FAITH, 1,
                List.of(), PlayerProgressionEffect.IMPLANT, 0.0D, MIDDLE, 0, false, false,
                PlayerProgressionIcon.GUARDSMAN_HELMET));

        // ------------------------------------------------------------------ cycle 1
        cycle(1, "secondary_heart", PlayerEvolutionStage.IMPLANT_STAGE_1, 1, 1, StrategicAge.OUTPOST,
                PlayerProgressionIcon.SECONDARY_HEART,
                skill("military_conditioning", 1, PlayerSkillBranch.VITALITY,
                        PlayerProgressionEffect.MAX_HEALTH, 1.0D, LEFT),
                skill("flak_armour_training", 1, PlayerSkillBranch.RESILIENCE,
                        PlayerProgressionEffect.ARMOR, 0.4D, MIDDLE),
                skill("lasgun_training", 1, PlayerSkillBranch.DAMAGE,
                        PlayerProgressionEffect.RANGED_DAMAGE, 0.03D, RIGHT));

        // ------------------------------------------------------------------ cycle 2
        cycle(2, "ossmodula", PlayerEvolutionStage.IMPLANT_STAGE_2, 2, 1, StrategicAge.OUTPOST,
                PlayerProgressionIcon.OSSMODULA_BONE,
                skill("forced_march", 2, PlayerSkillBranch.MOBILITY,
                        PlayerProgressionEffect.MOVEMENT_SPEED, 0.02D, LEFT),
                skill("prayer_to_the_emperor", 2, PlayerSkillBranch.FAITH,
                        PlayerProgressionEffect.PRAYER, 1.0D, MIDDLE),
                skill("pain_tolerance", 2, PlayerSkillBranch.RESILIENCE,
                        PlayerProgressionEffect.DAMAGE_REDUCTION, 0.015D, RIGHT));

        // ------------------------------------------------------------------ cycle 3
        cycle(3, "biscopea", PlayerEvolutionStage.IMPLANT_STAGE_3, 3, 1,
                StrategicAge.FORTIFIED_SETTLEMENT,
                PlayerProgressionIcon.BISCOPEA_MUSCLE,
                skill("close_combat", 3, PlayerSkillBranch.DAMAGE,
                        PlayerProgressionEffect.MELEE_DAMAGE, 0.4D, LEFT),
                skill("field_recovery", 3, PlayerSkillBranch.VITALITY,
                        PlayerProgressionEffect.MEDKIT_POWER, 0.08D, MIDDLE),
                skill("assault_step", 3, PlayerSkillBranch.MOBILITY,
                        PlayerProgressionEffect.FALL_DAMAGE, 0.08D, RIGHT));

        // ------------------------------------------------------------------ cycle 4
        cycle(4, "haemastamen", PlayerEvolutionStage.IMPLANT_STAGE_4, 4, 2,
                StrategicAge.FORTIFIED_SETTLEMENT,
                PlayerProgressionIcon.HAEMASTAMEN_BLOOD,
                skill("haemic_reserve", 4, PlayerSkillBranch.VITALITY,
                        PlayerProgressionEffect.MAX_HEALTH, 1.0D, LEFT),
                skill("psalm_of_protection", 4, PlayerSkillBranch.FAITH,
                        PlayerProgressionEffect.PRAYER_LITANY, 3.0D, MIDDLE),
                skill("xenos_hunter", 4, PlayerSkillBranch.DAMAGE,
                        PlayerProgressionEffect.ANTI_ORK_DAMAGE, 0.03D, RIGHT));

        // ------------------------------------------------------------------ cycle 5
        cycle(5, "larraman_organ", PlayerEvolutionStage.IMPLANT_STAGE_5, 5, 2,
                StrategicAge.MANUFACTORUM_AGE,
                PlayerProgressionIcon.LARRAMAN_CLOT,
                skill("out_of_combat_recovery", 5, PlayerSkillBranch.VITALITY,
                        PlayerProgressionEffect.OUT_OF_COMBAT_REGEN, 0.2D, LEFT),
                skill("tactical_roll", 5, PlayerSkillBranch.MOBILITY,
                        PlayerProgressionEffect.TACTICAL_ROLL, 0.06D, MIDDLE),
                skill("iron_liturgy", 5, PlayerSkillBranch.FAITH,
                        PlayerProgressionEffect.PRAYER_RESISTANCE, 3.0D, RIGHT));

        // ------------------------------------------------------------------ cycle 6
        cycle(6, "catalepsean_node", PlayerEvolutionStage.IMPLANT_STAGE_6, 6, 2,
                StrategicAge.MANUFACTORUM_AGE,
                PlayerProgressionIcon.CATALEPSEAN_BRAIN,
                skill("fire_discipline", 6, PlayerSkillBranch.DAMAGE,
                        PlayerProgressionEffect.WEAPON_COOLDOWN, 0.02D, LEFT),
                skill("unshakeable_stance", 6, PlayerSkillBranch.RESILIENCE,
                        PlayerProgressionEffect.KNOCKBACK_RESISTANCE, 0.03D, MIDDLE),
                skill("emperors_vigil", 6, PlayerSkillBranch.FAITH,
                        PlayerProgressionEffect.DEBUFF_DURATION, 0.05D, RIGHT));

        // ------------------------------------------------------------------ cycle 7
        cycle(7, "preomnor_omophagea", PlayerEvolutionStage.IMPLANT_STAGE_7, 7, 2,
                StrategicAge.MANUFACTORUM_AGE,
                PlayerProgressionIcon.PREOMNOR_STOMACH,
                skill("enhanced_metabolism", 7, PlayerSkillBranch.VITALITY,
                        PlayerProgressionEffect.FOOD_EFFICIENCY, 0.06D, LEFT),
                skill("battle_memory", 7, PlayerSkillBranch.FAITH,
                        PlayerProgressionEffect.COMBAT_MEMORY, 0.05D, MIDDLE),
                skill("xenos_executioner", 7, PlayerSkillBranch.DAMAGE,
                        PlayerProgressionEffect.ANTI_ELITE_DAMAGE, 0.04D, RIGHT));

        // ------------------------------------------------------------------ cycle 8
        cycle(8, "multi_lung", PlayerEvolutionStage.IMPLANT_STAGE_8, 8, 3,
                StrategicAge.ASTARTES_AGE,
                PlayerProgressionIcon.MULTI_LUNG,
                skill("assault_breath", 8, PlayerSkillBranch.MOBILITY,
                        PlayerProgressionEffect.SPRINT_HUNGER, 0.08D, LEFT),
                skill("trained_lungs", 8, PlayerSkillBranch.RESILIENCE,
                        PlayerProgressionEffect.BREATH_CONTROL, 0.2D, MIDDLE),
                skill("moving_fire", 8, PlayerSkillBranch.DAMAGE,
                        PlayerProgressionEffect.MOVING_FIRE, 0.1D, RIGHT));

        // ------------------------------------------------------------------ cycle 9
        cycle(9, "occulobe_lyman", PlayerEvolutionStage.IMPLANT_STAGE_9, 9, 3,
                StrategicAge.ASTARTES_AGE,
                PlayerProgressionIcon.OCCULOBE_LYMAN,
                skill("marksmans_eye", 9, PlayerSkillBranch.DAMAGE,
                        PlayerProgressionEffect.WEAPON_ACCURACY, 0.02D, LEFT),
                skill("combat_balance", 9, PlayerSkillBranch.MOBILITY,
                        PlayerProgressionEffect.AIM_STABILITY, 0.1D, MIDDLE),
                skill("psalm_of_vigilance", 9, PlayerSkillBranch.FAITH,
                        PlayerProgressionEffect.PRAYER_VIGIL, 2.0D, RIGHT));

        // ------------------------------------------------------------------ cycle 10
        cycle(10, "susan_melanochrome_oolitic", PlayerEvolutionStage.IMPLANT_STAGE_10, 10, 3,
                StrategicAge.ASTARTES_AGE,
                PlayerProgressionIcon.SUSAN_MELANOCHROME_OOLITIC,
                skill("sus_an_sleep", 10, PlayerSkillBranch.VITALITY,
                        PlayerProgressionEffect.SUS_AN, 1.0D, LEFT),
                skill("war_skin", 10, PlayerSkillBranch.RESILIENCE,
                        PlayerProgressionEffect.ENVIRONMENTAL_RESISTANCE, 0.05D, MIDDLE),
                skill("armoured_advance", 10, PlayerSkillBranch.MOBILITY,
                        PlayerProgressionEffect.HEAVY_ARMOUR_MOVEMENT, 0.04D, RIGHT));

        // ------------------------------------------------------------------ cycle 11
        cycle(11, "chemical_maturity", PlayerEvolutionStage.IMPLANT_STAGE_11, 11, 3,
                StrategicAge.ASTARTES_AGE,
                PlayerProgressionIcon.CHEMICAL_MATURITY,
                skill("neuroglottis", 11, PlayerSkillBranch.FAITH,
                        PlayerProgressionEffect.TOXIN_SENSE, 1.0D, LEFT),
                skill("betcher_gland", 11, PlayerSkillBranch.DAMAGE,
                        PlayerProgressionEffect.ACID_SPIT, 1.0D, MIDDLE),
                skill("mucranoid", 11, PlayerSkillBranch.RESILIENCE,
                        PlayerProgressionEffect.PRAYER_FIRE_WARD, 3.0D, RIGHT));

        // ------------------------------------------------------------------ cycle 12
        cycle(12, "black_carapace", PlayerEvolutionStage.NEOPHYTE, 12, 3,
                StrategicAge.ASTARTES_AGE,
                PlayerProgressionIcon.BLACK_CARAPACE,
                skill("neural_interface", 12, PlayerSkillBranch.RESILIENCE,
                        PlayerProgressionEffect.NEURAL_INTERFACE, 0.2D, LEFT),
                skill("death_angel_march", 12, PlayerSkillBranch.MOBILITY,
                        PlayerProgressionEffect.POWER_ARMOUR_MARCH, 0.02D, MIDDLE),
                skill("death_angel_litany", 12, PlayerSkillBranch.FAITH,
                        PlayerProgressionEffect.PRAYER_CHOIR, 1.0D, RIGHT));

        // ------------------------------------------------------------------ the last node
        add(new PlayerSkillNodeDefinition(ASCENSION_ID, 0, PlayerSkillBranch.ASCENSION, 1,
                List.of("black_carapace"), PlayerProgressionEffect.IMPLANT, 0.0D,
                MIDDLE, rowOfImplant(12) + 2, false, true,
                PlayerProgressionIcon.SPACE_MARINE_HELMET));
    }

    // ==================================================================== construction helpers

    /**
     * A skill node of a cycle, positioned on the cycle's skill row.
     *
     * <p>No icon argument: a skill wears its discipline's, and that is the whole point of having
     * disciplines. Passing null here is how {@link PlayerSkillNodeDefinition#icon()} knows to fall
     * back rather than having thirty-six call sites repeat the same five constants.
     */
    private static PlayerSkillNodeDefinition skill(String id, int cycle, PlayerSkillBranch branch,
                                                   PlayerProgressionEffect effect, double perRank,
                                                   int column) {
        return new PlayerSkillNodeDefinition(id, cycle, branch, PlayerProgressionBalance.SKILL_RANKS,
                List.of(gateOf(cycle)), effect, perRank, column, rowOfSkills(cycle), false, false,
                null);
    }

    /**
     * Registers one cycle: its three skills, then the organ that closes it.
     *
     * <p>The organ's prerequisites are exactly those three skills, which is what makes "three at
     * rank 1 unlocks the surgery" true by construction rather than by a rule written somewhere else.
     */
    private static void cycle(int index, String implantId, PlayerEvolutionStage stage,
                              int organIndex, int minCoreLevel, StrategicAge age,
                              PlayerProgressionIcon icon,
                              PlayerSkillNodeDefinition... skills) {
        List<String> ids = new ArrayList<>(skills.length);

        for (PlayerSkillNodeDefinition node : skills) {
            add(node);
            ids.add(node.id());
        }

        add(new PlayerSkillNodeDefinition(implantId, index, PlayerSkillBranch.IMPLANT, 1,
                List.copyOf(ids), PlayerProgressionEffect.IMPLANT, 0.0D,
                MIDDLE, rowOfImplant(index), true, false, icon));

        SURGERIES.put(implantId, new PlayerEvolutionNodeDefinition(implantId, organIndex, stage,
                PlayerProgressionBalance.SURGERY_GENE_SEED_COST, PlayerProgressionBalance.SURGERY_TICKS,
                minCoreLevel, age));
    }

    /** What a cycle's skills hang from: the previous organ, or the root for the first cycle. */
    private static String gateOf(int cycle) {
        return cycle <= 1 ? ROOT_ID : implantIdOfCycle(cycle - 1);
    }

    private static int rowOfSkills(int cycle) {
        return cycle * 2 - 1;
    }

    private static int rowOfImplant(int cycle) {
        return cycle * 2;
    }

    private static void add(PlayerSkillNodeDefinition node) {
        NODES.put(node.id(), node);
        ORDERED.add(node);
    }

    // ==================================================================== access

    public static List<PlayerSkillNodeDefinition> all() {
        return List.copyOf(ORDERED);
    }

    public static PlayerSkillNodeDefinition node(String id) {
        return NODES.get(id);
    }

    public static boolean exists(String id) {
        return NODES.containsKey(id);
    }

    public static PlayerEvolutionNodeDefinition surgery(String nodeId) {
        return SURGERIES.get(nodeId);
    }

    public static List<PlayerEvolutionNodeDefinition> surgeries() {
        return List.copyOf(SURGERIES.values());
    }

    /** The organ that closes a cycle. */
    public static String implantIdOfCycle(int cycle) {
        for (Map.Entry<String, PlayerEvolutionNodeDefinition> entry : SURGERIES.entrySet()) {
            if (entry.getValue().index() == cycle) {
                return entry.getKey();
            }
        }
        return ROOT_ID;
    }

    /** The organ of a given 1-based index, or null past the twelfth. */
    public static PlayerEvolutionNodeDefinition surgeryOfIndex(int index) {
        for (PlayerEvolutionNodeDefinition surgery : SURGERIES.values()) {
            if (surgery.index() == index) {
                return surgery;
            }
        }
        return null;
    }

    public static List<PlayerSkillNodeDefinition> skillsOfCycle(int cycle) {
        List<PlayerSkillNodeDefinition> out = new ArrayList<>(3);
        for (PlayerSkillNodeDefinition node : ORDERED) {
            if (node.cycle() == cycle && !node.implant() && !node.isRoot() && !node.ascension()) {
                out.add(node);
            }
        }
        return out;
    }

    // ==================================================================== description

    /**
     * The one place a node's numbers become a sentence.
     *
     * <p>Both the tooltip and the chat feedback call it, so a value can never be described two
     * different ways. The prayer is the single node whose printed value is not {@code perRank ×
     * rank} — it starts at two hearts — and that exception lives here rather than in the screen.
     */
    public static Component describe(PlayerSkillNodeDefinition node, int rank) {
        if (rank <= 0) {
            return Component.translatable("gui.firstcrusade.progression.no_rank")
                    .withStyle(ChatFormatting.DARK_GRAY);
        }

        if (node.effect() == PlayerProgressionEffect.PRAYER) {
            return value(Component.translatable("effect.firstcrusade.prayer_hearts",
                    format(rank + 1.0D)));
        }

        double total = node.valuePerRank() * rank;

        return switch (node.effect().format()) {
            case FLAT -> value(Component.translatable("effect.firstcrusade." + key(node),
                    format(total)));
            case PERCENT -> value(Component.translatable("effect.firstcrusade." + key(node),
                    format(total * 100.0D)));
            case SECONDS -> value(Component.translatable("effect.firstcrusade." + key(node),
                    format(total)));
            case HEARTS -> value(Component.translatable("effect.firstcrusade." + key(node),
                    format(total)));
            case NONE -> value(Component.translatable("effect.firstcrusade." + key(node)));
        };
    }

    private static Component value(Component text) {
        return text.copy().withStyle(ChatFormatting.GRAY);
    }

    private static String key(PlayerSkillNodeDefinition node) {
        return node.effect().name().toLowerCase(Locale.ROOT);
    }

    /** Trims a trailing ".0" so a tooltip says "+2 armour" rather than "+2.0 armour". */
    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001D) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // ==================================================================== self-check

    /**
     * Asserts the tree is the shape the design promises. Called once at mod setup.
     *
     * <p>Cheap, and it fails loudly at load rather than quietly at play: a miscounted cycle would
     * otherwise show up as a player who cannot reach the ascension, which is a bug report, not a
     * crash.
     */
    public static void validate() {
        int skills = 0;
        int implants = 0;

        for (PlayerSkillNodeDefinition node : ORDERED) {
            if (node.implant()) {
                implants++;
            } else if (!node.isRoot() && !node.ascension()) {
                skills++;
            }

            for (String prerequisite : node.prerequisites()) {
                if (!NODES.containsKey(prerequisite)) {
                    throw new IllegalStateException(
                            "progression node " + node.id() + " requires unknown " + prerequisite);
                }
            }
        }

        if (skills != 36 || implants != PlayerProgressionBalance.IMPLANT_COUNT) {
            throw new IllegalStateException(
                    "progression tree is " + skills + " skills and " + implants + " implants");
        }

        for (int cycle = 1; cycle <= PlayerProgressionBalance.CYCLE_COUNT; cycle++) {
            if (skillsOfCycle(cycle).size() != 3) {
                throw new IllegalStateException("cycle " + cycle + " is not three skills");
            }
        }
    }
}
