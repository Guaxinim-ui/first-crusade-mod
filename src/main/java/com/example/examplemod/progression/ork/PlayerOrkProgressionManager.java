package com.example.examplemod.progression.ork;

import com.example.examplemod.OrkClan;
import com.example.examplemod.progression.PlayerProgressionManager;
import com.example.examplemod.progression.PlayerProgressionNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The three things an Ork can ask the server to do: buy a rank, grow, and pick a klan.
 *
 * <h2>Every answer is computed here</h2>
 *
 * The client sends an id. This looks the node up in {@link PlayerOrkProgressionTree}, asks
 * {@link PlayerOrkProgressionRequirements} whether it is allowed, takes the Teef out of the profile
 * and writes the result. Nothing about cost or eligibility arrives from outside.
 *
 * <h2>Growing is not surgery</h2>
 *
 * An Ork does not get opened up; he just gets bigger. So an evolution is: check the gates, set the
 * stage, make room if he grew into a ceiling, roar, and tell everyone. No timer, no consumable, no
 * table of side effects.
 */
public final class PlayerOrkProgressionManager {
    private PlayerOrkProgressionManager() {
    }

    public static PlayerOrkProgressionProfile profile(ServerPlayer player) {
        return PlayerProgressionManager.profile(player).ork();
    }

    // ==================================================================== buying

    /** @return the message to show the player; empty when it worked */
    public static Component buyRank(ServerPlayer player, String nodeId) {
        PlayerOrkProgressionTree.Node node = PlayerOrkProgressionTree.node(nodeId);
        if (node == null) {
            // An id nothing matches is a client sending something the server never offered.
            return Component.empty();
        }

        PlayerOrkProgressionProfile ork = profile(player);

        PlayerOrkProgressionRequirements.Result check =
                PlayerOrkProgressionRequirements.checkBuy(player, ork, node);
        if (!check.ok()) {
            return check.reason();
        }

        int nextRank = ork.rank(node.id()) + 1;
        int cost = node.costFor(nextRank);

        // spendTeef is the only thing that may move the balance, and it refuses rather than going
        // negative — so a race between two packets costs a refusal, never a free node.
        if (!ork.spendTeef(cost)) {
            return Component.translatable("msg.firstcrusade.ork.no_teef", cost, ork.teef());
        }

        ork.setRank(node.id(), nextRank);

        // Recalculate, not just save: half these nodes are attributes, and a player who spent Teef
        // and felt nothing until his next relog would rightly call the purchase broken.
        commitAndRecalculate(player);

        player.playNotifySound(SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        return Component.empty();
    }

    // ==================================================================== growing

    public static Component evolve(ServerPlayer player, String nodeId) {
        PlayerOrkProgressionTree.Node node = PlayerOrkProgressionTree.node(nodeId);
        if (node == null || !node.isEvolution()) {
            return Component.empty();
        }

        PlayerOrkProgressionProfile ork = profile(player);

        PlayerOrkProgressionRequirements.Result check =
                PlayerOrkProgressionRequirements.checkEvolve(player, ork, node.stage());
        if (!check.ok()) {
            return check.reason();
        }

        ork.setStage(node.stage());
        // Held at rank 1 so the next evolution can name it as a parent, and so the screen can draw
        // the road the player actually walked.
        ork.setRank(node.id(), 1);

        // Reshapes the body and pushes it to every client that can see him. It also lifts him out
        // of a ceiling he just grew into — an Ork who becomes a Warboss in a tunnel should end up
        // standing in the open, not inside the rock.
        commitAndRecalculate(player);

        roar(player, node.stage());
        return Component.empty();
    }

    /**
     * The moment everyone notices.
     *
     * <p>One sound, one burst of particles, one message. Deliberately not an aura or a status —
     * growing is an event, and anything that lingers would be a thing to tick.
     */
    private static void roar(ServerPlayer player, PlayerOrkEvolutionStage stage) {
        ServerLevel level = player.serverLevel();

        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 1.4F, 0.6F);

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0D, player.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.1D);

        player.sendSystemMessage(Component.translatable("msg.firstcrusade.ork.grew." + stage.id())
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

        // A new Warboss is news. The rest is between him and the Boyz who saw it.
        if (stage == PlayerOrkEvolutionStage.WARBOSS) {
            Component announcement = Component.translatable("msg.firstcrusade.ork.new_warboss",
                    player.getDisplayName()).withStyle(ChatFormatting.GREEN);

            for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
                other.sendSystemMessage(announcement);
            }
        }
    }

    // ==================================================================== klan

    public static Component selectClan(ServerPlayer player, String clanName) {
        PlayerOrkProgressionProfile ork = profile(player);

        PlayerOrkProgressionRequirements.Result check =
                PlayerOrkProgressionRequirements.checkClan(player, ork);
        if (!check.ok()) {
            return check.reason();
        }

        // Strict: an unrecognised name picks nothing. fromName would have answered GOFFS, which
        // for a permanent choice arriving over the wire means a malformed packet silently decides
        // the player's klan for him.
        OrkClan clan = OrkClan.fromNameStrict(clanName == null
                ? "" : clanName.toUpperCase(java.util.Locale.ROOT));

        if (clan == null) {
            return Component.translatable("msg.firstcrusade.ork.bad_klan");
        }

        ork.setClan(clan);

        // The clan's own multipliers are built for mobs and are far too large for a player; the
        // player-side bonuses are small and live in the attribute pass, never in OrkClan.applyTo.
        commitAndRecalculate(player);

        player.sendSystemMessage(Component.translatable("msg.firstcrusade.ork.clan_chosen",
                Component.literal(clan.name())).withStyle(ChatFormatting.GREEN));

        return Component.empty();
    }

    // ==================================================================== plumbing

    private static void commit(ServerPlayer player) {
        PlayerProgressionManager.data(player.serverLevel()).markChanged();
        PlayerProgressionNetwork.sync(player);
    }

    /**
     * Saves, reapplies and syncs — once.
     *
     * <p>The obvious spelling is {@code commit()} then {@code recalculate()}, and it sends the whole
     * profile twice: {@code recalculate} already ends in a sync. Marking dirty and letting
     * recalculate do the sending is one packet instead of two, on a path that runs on every single
     * purchase.
     */
    private static void commitAndRecalculate(ServerPlayer player) {
        PlayerProgressionManager.data(player.serverLevel()).markChanged();
        PlayerProgressionManager.recalculate(player);
    }
}
