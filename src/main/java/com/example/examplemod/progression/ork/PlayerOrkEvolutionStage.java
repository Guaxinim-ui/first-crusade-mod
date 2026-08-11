package com.example.examplemod.progression.ork;

import net.minecraft.network.chat.Component;

/**
 * How big an Ork the player has krumped his way into being.
 *
 * <h2>Not the Astartes ladder with green paint</h2>
 *
 * {@code PlayerEvolutionStage} counts implants: twelve surgeries, each one a step, each one bought
 * with gene-seed and doctrine. This counts nothing of the sort. An Ork does not get bigger because
 * a surgeon opened him up — he gets bigger because he won, and the game's only measure of that is
 * what he has krumped. So a stage here has no cost, no prerequisite tree of its own and no
 * consumable behind it; it is earned outright and it is the one thing about him everyone can see
 * from across a field.
 *
 * <h2>The sizes are the mob sizes</h2>
 *
 * Each stage matches the entity the fiction says the player has become, so a player Nob standing
 * next to an {@code OrkNobEntity} is the same animal. Warboss deliberately overshoots every human
 * silhouette in the mod: a Space Marine tops out at 2.35 and a Warboss stands at 2.60, which is the
 * whole point of the sentence "olha o tamanhu desse git".
 */
public enum PlayerOrkEvolutionStage {

    /** Where every Ork starts. Same footprint as an {@code OrkBoyEntity}. */
    ORK_BOY("ork_boy", 0.70F, 2.05F),

    /** Ate well, won often. Bigger than a Boy, not yet anybody's boss. */
    BIG_BOY("big_boy", 0.76F, 2.12F),

    /** A Nob: the Boyz listen, sometimes. Matches {@code OrkNobEntity}. */
    ORK_NOB("ork_nob", 0.85F, 2.25F),

    /** Past a Meganob's bulk, and still growing. */
    BIG_NOB("big_nob", 0.92F, 2.38F),

    /** The biggest and therefore the boss. Matches {@code WarbossEntity}. */
    WARBOSS("warboss", 1.00F, 2.60F);

    private final String id;
    private final float width;
    private final float height;

    PlayerOrkEvolutionStage(String id, float width, float height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public String id() {
        return this.id;
    }

    public float width() {
        return this.width;
    }

    public float height() {
        return this.height;
    }

    /** The next rung, or {@code null} at Warboss — there is nothing bigger to become. */
    public PlayerOrkEvolutionStage next() {
        int index = this.ordinal() + 1;
        return index < values().length ? values()[index] : null;
    }

    public boolean isAtLeast(PlayerOrkEvolutionStage other) {
        return this.ordinal() >= other.ordinal();
    }

    /** Shown in the header: "TAMANHU: NOB". */
    public Component displayName() {
        return Component.translatable("ork.firstcrusade.stage." + this.id);
    }

    /**
     * Looks a stage up by its stored id.
     *
     * <p>Falls back to {@link #ORK_BOY} rather than throwing: an id from a newer build, or a
     * corrupted tag, should cost a player his size and not his save.
     */
    public static PlayerOrkEvolutionStage byId(String id) {
        for (PlayerOrkEvolutionStage stage : values()) {
            if (stage.id.equals(id) || stage.name().equals(id)) {
                return stage;
            }
        }

        return ORK_BOY;
    }
}
