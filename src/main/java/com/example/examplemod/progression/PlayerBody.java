package com.example.examplemod.progression;

/**
 * How big a player is, resolved.
 *
 * <h2>Why the body travels instead of the stage</h2>
 *
 * There are now two ladders that change a player's size — the Astartes implants and the Ork
 * krumpings — and they share nothing but the effect. Sending a stage name would mean the client has
 * to know which ladder to read it against, which means the client has to know the player's faction,
 * which means one more thing to sync and one more way for the two sides to disagree about a
 * hitbox. Sending the answer instead removes the question: the server works out the body once, and
 * every reader on both sides gets the same two numbers.
 *
 * <p>That matters more here than almost anywhere else in the mod. A client and a server that
 * disagree about a player's box do not produce a cosmetic bug — they produce a player who cannot
 * place blocks and gets ejected through ceilings, which is exactly what happened when the stage
 * broadcast was missing.
 */
public record PlayerBody(float width, float height) {

    /** An ordinary human player, and what everything falls back to. */
    public static final PlayerBody VANILLA =
            new PlayerBody(PlayerEvolutionStage.VANILLA_WIDTH, PlayerEvolutionStage.VANILLA_HEIGHT);

    /** True when this body needs no work at all — the common case, and worth an early return. */
    public boolean isVanilla() {
        return this.width == VANILLA.width && this.height == VANILLA.height;
    }

    public float widthScale() {
        return this.width / VANILLA.width;
    }

    public float heightScale() {
        return this.height / VANILLA.height;
    }
}
