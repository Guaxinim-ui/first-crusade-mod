package com.example.examplemod.progression;

import com.example.examplemod.StrategicAge;

/**
 * The surgical half of an implant node: what the operation costs, what it demands of the city, and
 * what the player becomes when it finishes.
 *
 * <p>Kept apart from {@link PlayerSkillNodeDefinition} because an implant answers questions a skill
 * never does — which organ, which stage, how long on the table, what the Command Core must be — and
 * folding those into every skill node would put ten empty fields on thirty-seven of them.
 *
 * @param nodeId       the tree node this surgery belongs to
 * @param index        1..12, the order of the organs
 * @param stage        the stage the player reaches when the surgery completes
 * @param geneSeed     units of Emperor's Gene Seed consumed, taken only once all checks pass
 * @param surgeryTicks time on the table
 * @param minCoreLevel the Command Core's city level required to attempt it
 * @param minimumAge   the strategic age the settlement must have reached
 */
public record PlayerEvolutionNodeDefinition(
        String nodeId,
        int index,
        PlayerEvolutionStage stage,
        int geneSeed,
        int surgeryTicks,
        int minCoreLevel,
        StrategicAge minimumAge) {

    /**
     * The organ's own name, shown on the surgery progress bar.
     *
     * <p>Reuses the node's translation key rather than adding a second one: the node <i>is</i> the
     * organ, and two keys for one thing is two things to keep in step.
     */
    public net.minecraft.network.chat.Component organName() {
        return net.minecraft.network.chat.Component.translatable("node.firstcrusade." + this.nodeId);
    }

    /** The last one. Black Carapace is the only surgery that ends somewhere other than a number. */
    public boolean isBlackCarapace() {
        return this.index == PlayerProgressionBalance.IMPLANT_COUNT;
    }
}
