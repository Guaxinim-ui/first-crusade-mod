package com.example.examplemod.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * The Imperial Command tree: nine nodes, one spine and one wing.
 *
 * <h2>Shape</h2>
 *
 * The centre column is the only thing that raises how many soldiers a raid may call — Vox, then
 * Reinforced Squad, then Section, then Platoon — and each of the last two also asks for raids
 * actually won, so the ceiling is earned in the field and not only in the menu. Everything to the
 * sides changes <i>how</i> the call goes out: who leads it, how soon it may go out again, how close
 * it lands, and what the squad arrives with.
 *
 * <h2>Deliberately not validated by PlayerProgressionTree</h2>
 *
 * The Astartes tree's {@code validate()} asserts a very specific shape — fifty nodes, twelve cycles
 * of three-skills-then-an-organ — and would fail loudly on anything else. This tree is a different
 * shape on purpose, so it keeps its own table, its own lookup and its own ids. Nothing here can
 * appear in {@link PlayerProgressionTree#exists}, and nothing there can be bought with Command
 * Points.
 */
public final class PlayerCommanderTree {
    private PlayerCommanderTree() {
    }

    public static final String ROOT_ID = "imperial_authority";

    public static final String SQUAD_VOX = "squad_vox";
    public static final String REINFORCED_SQUAD = "reinforced_squad";
    public static final String COMBAT_SECTION = "combat_section";
    public static final String ASSAULT_PLATOON = "assault_platoon";
    public static final String FIELD_SERGEANT = "field_sergeant";
    public static final String PRIORITY_VOX = "priority_vox";
    public static final String FORWARD_INSERTION = "forward_insertion";
    public static final String COORDINATED_ASSAULT = "coordinated_assault";

    private static final int LEFT = -2;
    private static final int MIDDLE = 0;
    private static final int RIGHT = 2;

    private static final Map<String, PlayerCommanderNodeDefinition> NODES = new LinkedHashMap<>();
    private static final List<PlayerCommanderNodeDefinition> ORDERED = new ArrayList<>();

    static {
        // ---------------------------------------------------------------- the free first node
        add(new PlayerCommanderNodeDefinition(ROOT_ID, 0, List.of(), 0,
                PlayerCommanderEffect.AUTHORITY, 0, MIDDLE, 0,
                PlayerCommanderIcon.AUTHORITY_CAP));

        // ---------------------------------------------------------------- the reinforcement spine
        add(new PlayerCommanderNodeDefinition(SQUAD_VOX, 1, List.of(ROOT_ID), 0,
                PlayerCommanderEffect.REINFORCEMENT_LIMIT,
                PlayerCommanderBalance.REINFORCEMENTS_VOX, MIDDLE, 1,
                PlayerCommanderIcon.SQUAD_VOX));

        add(new PlayerCommanderNodeDefinition(REINFORCED_SQUAD, 2, List.of(SQUAD_VOX), 0,
                PlayerCommanderEffect.REINFORCEMENT_LIMIT,
                PlayerCommanderBalance.REINFORCEMENTS_REINFORCED, MIDDLE, 2,
                PlayerCommanderIcon.REINFORCED_SQUAD));

        add(new PlayerCommanderNodeDefinition(COMBAT_SECTION, 2, List.of(REINFORCED_SQUAD),
                PlayerCommanderBalance.WINS_FOR_SECTION,
                PlayerCommanderEffect.REINFORCEMENT_LIMIT,
                PlayerCommanderBalance.REINFORCEMENTS_SECTION, MIDDLE, 3,
                PlayerCommanderIcon.COMBAT_SECTION));

        add(new PlayerCommanderNodeDefinition(ASSAULT_PLATOON, 3, List.of(COMBAT_SECTION),
                PlayerCommanderBalance.WINS_FOR_PLATOON,
                PlayerCommanderEffect.REINFORCEMENT_LIMIT,
                PlayerCommanderBalance.REINFORCEMENTS_PLATOON, MIDDLE, 4,
                PlayerCommanderIcon.ASSAULT_PLATOON));

        // ---------------------------------------------------------------- the tactical wing
        add(new PlayerCommanderNodeDefinition(PRIORITY_VOX, 1, List.of(SQUAD_VOX), 0,
                PlayerCommanderEffect.COOLDOWN_CUT, 25, RIGHT, 2,
                PlayerCommanderIcon.PRIORITY_VOX));

        add(new PlayerCommanderNodeDefinition(FIELD_SERGEANT, 1, List.of(REINFORCED_SQUAD), 0,
                PlayerCommanderEffect.SERGEANT_PREFERENCE, 5, LEFT, 3,
                PlayerCommanderIcon.FIELD_SERGEANT));

        add(new PlayerCommanderNodeDefinition(FORWARD_INSERTION, 1, List.of(REINFORCED_SQUAD), 0,
                PlayerCommanderEffect.APPROACH_CUT,
                PlayerCommanderBalance.APPROACH_DISTANCE_ADVANCED, RIGHT, 3,
                PlayerCommanderIcon.FORWARD_INSERTION));

        add(new PlayerCommanderNodeDefinition(COORDINATED_ASSAULT, 2,
                List.of(COMBAT_SECTION, FIELD_SERGEANT), 0,
                PlayerCommanderEffect.ARRIVAL_BUFF, 0, LEFT, 4,
                PlayerCommanderIcon.COORDINATED_ASSAULT));
    }

    private static void add(PlayerCommanderNodeDefinition node) {
        NODES.put(node.id(), node);
        ORDERED.add(node);
    }

    public static List<PlayerCommanderNodeDefinition> all() {
        return List.copyOf(ORDERED);
    }

    @Nullable
    public static PlayerCommanderNodeDefinition node(String id) {
        return NODES.get(id);
    }

    public static boolean exists(String id) {
        return NODES.containsKey(id);
    }

    /** Rows in the strip, so the screen can lay the tab out without enumerating the nodes twice. */
    public static int rows() {
        int deepest = 0;
        for (PlayerCommanderNodeDefinition node : ORDERED) {
            deepest = Math.max(deepest, node.y());
        }
        return deepest + 1;
    }
}
