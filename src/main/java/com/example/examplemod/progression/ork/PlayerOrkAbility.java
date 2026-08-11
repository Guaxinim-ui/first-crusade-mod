package com.example.examplemod.progression.ork;

import net.minecraft.network.chat.Component;

/**
 * The four things an Ork can actively do.
 *
 * <h2>Each one is a node, not a stage</h2>
 *
 * Every ability names the tree node that grants it, and rank 0 means the player simply does not have
 * it. Nothing is handed out by growing: a Warboss who never bought 'EADBUTT cannot headbutt, which is
 * what keeps the tree the thing that decides what an Ork is rather than the ladder.
 *
 * <h2>The wire carries the name and nothing else</h2>
 *
 * A key press becomes {@code ORK_ABILITY} plus one of these names. Whether the player owns the node,
 * whether the cooldown has run out, whether there is a wall in the way and whether there is anything
 * worth hitting are all answered by {@link PlayerOrkAbilityManager} on the server.
 */
public enum PlayerOrkAbility {
    /** A short, hard headbutt at whatever is directly in front. */
    HEADBUTT("headbutt", "eadbutt"),

    /**
     * The shout. Costs the whole Fury bar and buffs every greenskin who can hear it.
     *
     * <p>The only ability with a resource cost, because it is the only one the Fury bar exists for.
     */
    WAAAGH_ROAR("waaagh", "waaagh_roar"),

    /**
     * Point at a git and the Boyz go for it.
     *
     * <p>With no valid target it falls through to calling them to him instead — which is a different
     * order, and needs its own node ({@code boyz_come_here}) before it will do anything.
     */
    BOSS_ORDER("order", "im_da_boss"),

    /** A run at something. Refused rather than clipped when there is a wall in the way. */
    CHARGE("charge", "krump_first");

    private final String key;
    private final String nodeId;

    PlayerOrkAbility(String key, String nodeId) {
        this.key = key;
        this.nodeId = nodeId;
    }

    /** The node that grants it; rank 0 means the player does not have this ability at all. */
    public String nodeId() {
        return this.nodeId;
    }

    public Component displayName() {
        return Component.translatable("ork.firstcrusade.ability." + this.key);
    }

    public String key() {
        return this.key;
    }

    /** Resolves a name off the wire, or null. A name nothing matches is a packet nobody sent. */
    public static PlayerOrkAbility byName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        for (PlayerOrkAbility ability : values()) {
            if (ability.name().equals(name)) {
                return ability;
            }
        }

        return null;
    }
}
