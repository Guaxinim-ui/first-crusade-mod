package com.example.examplemod.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;

/**
 * What the client is allowed to know about progression, and nothing more.
 *
 * <h2>Two different questions</h2>
 *
 * A client needs two unrelated things. It needs <b>its own</b> full profile, to draw the tree — that
 * is {@link #self()}, filled by the sync packet. And it needs <b>everyone's</b> stage, because a
 * player's size is visible to every client in render distance and the size event runs locally on
 * each of them — that is {@link #stageOf(Player)}.
 *
 * <p>The split matters: another player's doctrine points are none of this client's business, and
 * broadcasting them would be a privacy leak dressed up as a convenience. Only the stage travels
 * publicly, because only the stage is visible anyway.
 *
 * <h2>The client decides nothing</h2>
 *
 * Every value here arrives from the server. Nothing in this class computes a rank, spends a point or
 * chooses a size; it remembers what it was told and hands it to the screen and the size event.
 */
public final class PlayerProgressionClientView {
    private PlayerProgressionClientView() {
    }

    @Nullable
    private static PlayerProgressionProfile self;

    /** The side this client fights for, as the server last stated it. */
    private static com.example.examplemod.PlayerFaction faction =
            com.example.examplemod.PlayerFaction.UNCHOSEN;

    public static com.example.examplemod.PlayerFaction faction() {
        return faction;
    }

    public static void setFaction(com.example.examplemod.PlayerFaction value) {
        faction = value == null ? com.example.examplemod.PlayerFaction.UNCHOSEN : value;
    }

    /** True when this client should be shown the WAAAGH tree instead of the Astartes one. */
    public static boolean isOrk() {
        return faction == com.example.examplemod.PlayerFaction.ORKS;
    }

    /**
     * The global WAAAGH tier as the server last stated it, 0 to 4.
     *
     * <p>The only number on the WAAAGH screen that is not about the player. It is here rather than
     * read locally because there is nothing local to read: the tide lives in a SavedData on the
     * server, and a client guess would be a client inventing the Warboss gate's last requirement.
     */
    private static int globalWaaaghTier;

    public static int globalWaaaghTier() {
        return globalWaaaghTier;
    }

    public static void setGlobalWaaaghTier(int tier) {
        globalWaaaghTier = Math.max(0, tier);
    }

    /**
     * Public bodies, keyed by player id. Small, and cleared when the client leaves a world.
     *
     * <p>Bodies rather than stages: two different ladders can change a player's size now, and the
     * client has no business knowing which one applied. It is told the answer.
     */
    private static final Map<UUID, PlayerBody> BODIES = new HashMap<>();

    /** This client's own profile, or null before the first sync arrives. */
    @Nullable
    public static PlayerProgressionProfile self() {
        return self;
    }

    public static void setSelf(PlayerProgressionProfile profile) {
        self = profile;
    }

    /** The local player's stage — {@link PlayerEvolutionStage#ASTRA_RECRUIT} until told otherwise. */
    public static PlayerEvolutionStage stage() {
        return self == null ? PlayerEvolutionStage.ASTRA_RECRUIT : self.stage();
    }

    /** This player's body as the server last described it, or an ordinary human until told. */
    public static PlayerBody bodyOf(Player player) {
        return BODIES.getOrDefault(player.getUUID(), PlayerBody.VANILLA);
    }

    public static void putBody(UUID playerId, PlayerBody body) {
        BODIES.put(playerId, body);
    }

    /** Called when leaving a world: stale sizes must not survive into the next one. */
    public static void clear() {
        self = null;
        faction = com.example.examplemod.PlayerFaction.UNCHOSEN;
        globalWaaaghTier = 0;
        BODIES.clear();
    }
}
