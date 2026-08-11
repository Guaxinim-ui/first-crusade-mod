package com.example.examplemod.progression;

import net.minecraft.network.chat.Component;

/**
 * The four things a progressed Imperial can actively do.
 *
 * <h2>One enum, one packet</h2>
 *
 * Four near-identical packet classes differing only in their name is four places to fix a bug in.
 * The wire carries this enum instead, and {@link UseProgressionAbilityPacket} is the single message —
 * which also means the server-side "may they, and are they off cooldown" check exists once rather
 * than four times.
 *
 * @see PlayerProgressionAbilityManager
 */
public enum PlayerProgressionAbility {
    /** Channelled. Absorption hearts, and whatever the faith branch has bolted onto it. */
    PRAYER("prayer", "prayer_to_the_emperor"),

    /** A short dash along the way you are already going. */
    ROLL("roll", "tactical_roll"),

    /** Betcher's gland: a short-ranged corrosive spit. */
    ACID("acid", "betcher_gland"),

    /** Sus-an stasis: stop, and slowly stop dying. */
    STASIS("stasis", "sus_an_sleep");

    private final String key;
    private final String nodeId;

    PlayerProgressionAbility(String key, String nodeId) {
        this.key = key;
        this.nodeId = nodeId;
    }

    /** The node that grants it; rank 0 means the player simply does not have this ability. */
    public String nodeId() {
        return this.nodeId;
    }

    public Component displayName() {
        return Component.translatable("ability.firstcrusade." + this.key);
    }

    public String key() {
        return this.key;
    }
}
