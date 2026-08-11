package com.example.examplemod.progression;

import net.minecraft.network.chat.Component;

/**
 * How far down the road from Guardsman to Astartes a player has walked, and how big that has made
 * them.
 *
 * <h2>The stage owns the body</h2>
 *
 * Height and width live here rather than in a table somewhere else, because they are not decoration:
 * {@link PlayerProgressionSizeManager} asks the stage for the player's dimensions on every size
 * event, and the surgery that advances the stage is the only thing that may change them. One value,
 * one owner — a second copy of "how tall is an Implant VII player" is a copy that will disagree.
 *
 * <h2>Twelve implants, eleven numbered stages</h2>
 *
 * {@code IMPLANT_STAGE_1..11} are the first eleven organs. The twelfth is the Black Carapace, and it
 * does not get a numbered stage of its own — receiving it <i>is</i> becoming a {@link #NEOPHYTE},
 * which is a different thing to be, not a twelfth notch on the same ruler.
 *
 * <h2>These are design numbers, and the body is clamped below them</h2>
 *
 * The heights past {@code IMPLANT_STAGE_3} run over two blocks, and a player whose collision box is
 * over two blocks tall gets ejected upward every time he stands out of a crouch — vanilla chooses
 * the pose using the <i>unscaled</i> box and only then applies the real one. See
 * {@link PlayerProgressionSizeManager#MAX_COLLISION_HEIGHT} for the full chain. The table is left
 * intact because it is the design's intent and because the ratios still drive the eye height and
 * everything read off {@link #height()}; the size event is where the physical limit is enforced.
 * Getting the visual giant back means scaling the <i>model</i> in the renderer, not the box.
 */
public enum PlayerEvolutionStage {
    ASTRA_RECRUIT(0, 0.60F, 1.80F),
    ASTRA_SOLDIER(0, 0.60F, 1.80F),
    ASTRA_VETERAN(0, 0.61F, 1.82F),
    ASTARTES_CANDIDATE(0, 0.62F, 1.82F),
    IMPLANT_STAGE_1(1, 0.62F, 1.84F),
    IMPLANT_STAGE_2(2, 0.64F, 1.90F),
    IMPLANT_STAGE_3(3, 0.66F, 1.96F),
    IMPLANT_STAGE_4(4, 0.68F, 2.02F),
    IMPLANT_STAGE_5(5, 0.70F, 2.06F),
    IMPLANT_STAGE_6(6, 0.72F, 2.10F),
    IMPLANT_STAGE_7(7, 0.74F, 2.13F),
    IMPLANT_STAGE_8(8, 0.76F, 2.16F),
    IMPLANT_STAGE_9(9, 0.78F, 2.20F),
    IMPLANT_STAGE_10(10, 0.80F, 2.24F),
    IMPLANT_STAGE_11(11, 0.82F, 2.28F),
    NEOPHYTE(12, 0.84F, 2.30F),
    SPACE_MARINE(12, 0.85F, 2.35F);

    /** Vanilla's own player box, and the baseline every scale is measured against. */
    public static final float VANILLA_WIDTH = 0.60F;
    public static final float VANILLA_HEIGHT = 1.80F;

    private final int implants;
    private final float width;
    private final float height;

    PlayerEvolutionStage(int implants, float width, float height) {
        this.implants = implants;
        this.width = width;
        this.height = height;
    }

    /** How many gene-seed organs this stage represents. Drives the "Implants: X/12" readout. */
    public int implants() {
        return this.implants;
    }

    public float width() {
        return this.width;
    }

    public float height() {
        return this.height;
    }

    /**
     * The stage a player reaches on completing their {@code index}-th implant (1-based).
     *
     * <p>The twelfth answers {@link #NEOPHYTE}, which is the whole reason this is a method and not
     * {@code values()[ordinal + 1]}.
     */
    public static PlayerEvolutionStage afterImplant(int index) {
        if (index >= 12) {
            return NEOPHYTE;
        }
        if (index <= 0) {
            return ASTRA_RECRUIT;
        }

        return values()[IMPLANT_STAGE_1.ordinal() + index - 1];
    }

    /**
     * True once the player stops counting as an ordinary human.
     *
     * <p>The line is the Haemastamen, the fourth organ — not the {@link #ASTARTES_CANDIDATE} name
     * further up the enum, which is only the title a soldier holds before the first incision.
     */
    public boolean isAstartesCandidate() {
        return ordinal() >= IMPLANT_STAGE_4.ordinal();
    }

    public boolean isAtLeast(PlayerEvolutionStage other) {
        return ordinal() >= other.ordinal();
    }

    public Component displayName() {
        return Component.translatable("stage.firstcrusade." + name().toLowerCase(java.util.Locale.ROOT));
    }

    public static PlayerEvolutionStage byName(String name) {
        for (PlayerEvolutionStage stage : values()) {
            if (stage.name().equalsIgnoreCase(name)) {
                return stage;
            }
        }
        return ASTRA_RECRUIT;
    }
}
