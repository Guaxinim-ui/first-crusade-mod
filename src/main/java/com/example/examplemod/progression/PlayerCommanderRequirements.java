package com.example.examplemod.progression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The single authority on "may this player take that command node".
 *
 * <p>Same contract as {@link PlayerProgressionRequirements}: every verdict carries the sentence that
 * explains it, so the greyed-out node and the refused packet can never give different reasons. The
 * client calls {@link #checkClientVisible} to draw; the server calls {@link #checkUnlock} again when
 * the packet lands, because the packet was written by somebody else's computer.
 */
public final class PlayerCommanderRequirements {
    private PlayerCommanderRequirements() {
    }

    /** Whether this profile counts as owning a node. The root is free and never stored as bought. */
    public static boolean owns(PlayerCommanderProfile profile, PlayerCommanderNodeDefinition node) {
        return node.isRoot() || profile.has(node.id());
    }

    public static boolean owns(PlayerCommanderProfile profile, String nodeId) {
        PlayerCommanderNodeDefinition node = PlayerCommanderTree.node(nodeId);
        return node != null && owns(profile, node);
    }

    /**
     * The client-side half: everything that can be answered from the profile alone.
     *
     * <p>Faction is not checked here — the client does not hold other players' factions and would
     * only be guessing at its own. The server checks it.
     */
    public static PlayerProgressionRequirements.Result checkClientVisible(
            PlayerCommanderProfile profile, PlayerCommanderNodeDefinition node) {

        if (node.isRoot()) {
            return PlayerProgressionRequirements.Result.no(
                    "msg.firstcrusade.command.root_is_free");
        }

        if (owns(profile, node)) {
            return PlayerProgressionRequirements.Result.no("msg.firstcrusade.command.already_taken");
        }

        for (String id : node.prerequisites()) {
            PlayerCommanderNodeDefinition required = PlayerCommanderTree.node(id);
            if (required != null && !owns(profile, required)) {
                return PlayerProgressionRequirements.Result.no(
                        "msg.firstcrusade.command.needs_node", required.displayName());
            }
        }

        if (profile.successfulRaids() < node.requiredWins()) {
            return PlayerProgressionRequirements.Result.no(
                    "msg.firstcrusade.command.needs_wins",
                    node.requiredWins(), profile.successfulRaids());
        }

        if (profile.points() < node.cost()) {
            return PlayerProgressionRequirements.Result.no(
                    "msg.firstcrusade.command.no_points", node.cost(), profile.points());
        }

        return PlayerProgressionRequirements.Result.OK;
    }

    /** The server's version: the faction gate, then everything the client already checked. */
    public static PlayerProgressionRequirements.Result checkUnlock(
            ServerPlayer player, PlayerCommanderProfile profile,
            PlayerCommanderNodeDefinition node) {

        PlayerProgressionRequirements.Result faction =
                PlayerProgressionRequirements.checkFaction(player);
        if (!faction.ok()) {
            return faction;
        }

        return checkClientVisible(profile, node);
    }

    /** Whether a player may start a raid at all — the Imperial gate the Ork panel button obeys. */
    public static PlayerProgressionRequirements.Result checkMayRaid(ServerPlayer player) {
        return PlayerProgressionRequirements.checkFaction(player);
    }

    /** Reason text for a node that cannot be taken, or an empty component when it can. */
    public static Component reasonFor(PlayerCommanderProfile profile,
                                      PlayerCommanderNodeDefinition node) {
        return checkClientVisible(profile, node).reason();
    }
}
