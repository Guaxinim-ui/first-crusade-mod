package com.example.examplemod.progression;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server: the four things a player can ask the progression to do.
 *
 * <h2>One packet, four verbs</h2>
 *
 * A separate class per action would be four encoders, four decoders and four handlers that differ
 * only in which method they call — and four places for the "is this player allowed" check to be
 * forgotten in one of them. The verb travels as an enum instead, and the guard rail runs once, here,
 * for all of them.
 *
 * <h2>The client is trusted with nothing</h2>
 *
 * The payload is a verb and a string. No rank, no cost, no cooldown, no stage. Every one of those is
 * looked up again on the server by {@link PlayerProgressionManager} and
 * {@link PlayerProgressionAbilityManager}, because this packet is the part of the system an
 * unfriendly client writes.
 */
public class ProgressionActionPacket {

    /** What the player is asking for. */
    public enum Action {
        /** Send me my profile — used when the screen opens. */
        REQUEST,
        /** Buy the next rank of {@code argument}. */
        UNLOCK,
        /** Begin the surgery for the implant node {@code argument}. */
        IMPLANT,
        /** Take the final node. */
        ASCEND,
        /** Fire the ability named by {@code argument}. */
        ABILITY,
        /**
         * Take the Imperial Command node {@code argument}.
         *
         * <p>A fifth verb rather than a second packet class: the guard rail below already runs once
         * for every verb, and the command tree is bought from the same screen by the same click.
         * What it must never be is {@link #UNLOCK} with a command id — the two vocabularies are
         * resolved against different tables, and keeping the verbs apart is what stops a command id
         * from ever being looked up in the Astartes tree.
         */
        COMMAND_UNLOCK,

        /**
         * Buy the next rank of the WAAAGH node {@code argument}.
         *
         * <p>A third vocabulary, and a third verb, for the same reason {@link #COMMAND_UNLOCK} is a
         * verb: Ork ids are resolved against the Ork tree and nothing else. An Ork id arriving as
         * {@link #UNLOCK} would be looked up in the Astartes table, and the day those tables share a
         * name is the day a Boy buys a Multi-lung.
         */
        ORK_UNLOCK,

        /** Take the WAAAGH evolution node {@code argument} — earned, never paid for. */
        ORK_EVOLVE,

        /** Pick a klan, once, at Nob. {@code argument} is the clan name. */
        ORK_SELECT_CLAN,

        /** Fire the Ork ability named by {@code argument}. */
        ORK_ABILITY
    }

    private final Action action;
    private final String argument;

    public ProgressionActionPacket(Action action, String argument) {
        this.action = action;
        this.argument = argument == null ? "" : argument;
    }

    public static ProgressionActionPacket request() {
        return new ProgressionActionPacket(Action.REQUEST, "");
    }

    public static ProgressionActionPacket unlock(String nodeId) {
        return new ProgressionActionPacket(Action.UNLOCK, nodeId);
    }

    public static ProgressionActionPacket implant(String nodeId) {
        return new ProgressionActionPacket(Action.IMPLANT, nodeId);
    }

    public static ProgressionActionPacket ascend() {
        return new ProgressionActionPacket(Action.ASCEND, "");
    }

    public static ProgressionActionPacket ability(PlayerProgressionAbility ability) {
        return new ProgressionActionPacket(Action.ABILITY, ability.name());
    }

    public static ProgressionActionPacket commandUnlock(String nodeId) {
        return new ProgressionActionPacket(Action.COMMAND_UNLOCK, nodeId);
    }

    public static ProgressionActionPacket orkUnlock(String nodeId) {
        return new ProgressionActionPacket(Action.ORK_UNLOCK, nodeId);
    }

    public static ProgressionActionPacket orkEvolve(String nodeId) {
        return new ProgressionActionPacket(Action.ORK_EVOLVE, nodeId);
    }

    public static ProgressionActionPacket orkSelectClan(String clanName) {
        return new ProgressionActionPacket(Action.ORK_SELECT_CLAN, clanName);
    }

    public static void encode(ProgressionActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUtf(packet.argument, 64);
    }

    public static ProgressionActionPacket decode(FriendlyByteBuf buffer) {
        return new ProgressionActionPacket(buffer.readEnum(Action.class), buffer.readUtf(64));
    }

    public static void handle(ProgressionActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            Component problem = switch (packet.action) {
                case REQUEST -> {
                    PlayerProgressionNetwork.sync(player);
                    yield Component.empty();
                }
                case UNLOCK -> PlayerProgressionManager.buyRank(player, packet.argument);
                case IMPLANT -> PlayerProgressionManager.beginSurgery(player, packet.argument);
                case ASCEND -> PlayerProgressionManager.ascend(player);
                case ABILITY -> useAbility(player, packet.argument);
                case COMMAND_UNLOCK -> PlayerCommanderManager.unlock(player, packet.argument);

                case ORK_UNLOCK -> com.example.examplemod.progression.ork
                        .PlayerOrkProgressionManager.buyRank(player, packet.argument);
                case ORK_EVOLVE -> com.example.examplemod.progression.ork
                        .PlayerOrkProgressionManager.evolve(player, packet.argument);
                case ORK_SELECT_CLAN -> com.example.examplemod.progression.ork
                        .PlayerOrkProgressionManager.selectClan(player, packet.argument);
                case ORK_ABILITY -> useOrkAbility(player, packet.argument);
            };

            // An empty component is the success case; anything else is the reason it did not happen.
            if (problem != null && !problem.getString().isEmpty()) {
                player.displayClientMessage(problem, true);
            }
        });

        context.setPacketHandled(true);
    }

    /**
     * Resolves an ability name against the enum rather than trusting it.
     *
     * <p>A name nothing matches is a client sending something the server never offered: dropped in
     * silence, because a malformed packet must not be a way to fill somebody's log.
     */
    private static Component useAbility(ServerPlayer player, String name) {
        for (PlayerProgressionAbility ability : PlayerProgressionAbility.values()) {
            if (ability.name().equals(name)) {
                return PlayerProgressionAbilityManager.use(player, ability);
            }
        }

        return Component.empty();
    }

    /**
     * The same treatment for the WAAAGH's four buttons: resolve the name against the enum, and drop
     * anything that does not match without a word.
     */
    private static Component useOrkAbility(ServerPlayer player, String name) {
        com.example.examplemod.progression.ork.PlayerOrkAbility ability =
                com.example.examplemod.progression.ork.PlayerOrkAbility.byName(name);

        if (ability == null) {
            return Component.empty();
        }

        return com.example.examplemod.progression.ork.PlayerOrkAbilityManager.use(player, ability);
    }

    public static ProgressionActionPacket orkAbility(
            com.example.examplemod.progression.ork.PlayerOrkAbility ability) {
        return new ProgressionActionPacket(Action.ORK_ABILITY, ability.name());
    }
}
