package com.example.examplemod.progression;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The command career's front door: what the raid system asks, and the only place a command node's
 * numbers become gameplay.
 *
 * <h2>Questions about the whole profile, not about one node</h2>
 *
 * "How many soldiers may this player call" is not a property of any single node — it is the best
 * ceiling among every node they own. So the readers here walk the profile and answer, exactly as
 * {@link PlayerProgressionManager#aggregate} does for the Astartes tree. The assault manager asks
 * these four questions and nothing else, which is why it needs to know no command ids at all.
 *
 * <h2>Pure where the client needs it</h2>
 *
 * Everything that only reads takes a {@link PlayerCommanderProfile}, so the screen can call the same
 * method the server does and the header can never disagree with what actually happens. Only the
 * methods that change something take a {@link ServerPlayer}.
 */
public final class PlayerCommanderManager {
    private PlayerCommanderManager() {
    }

    // ==================================================================== access

    public static PlayerCommanderProfile profile(ServerPlayer player) {
        return PlayerProgressionManager.profile(player).commander();
    }

    // ==================================================================== the four questions

    /**
     * How many soldiers this commander may call: the highest ceiling they own, or zero.
     *
     * <p>Zero is a real answer, not a failure — a player with no reinforcement node fights the raid
     * alone, and is told so.
     */
    public static int reinforcementLimit(PlayerCommanderProfile profile) {
        int best = 0;

        for (PlayerCommanderNodeDefinition node : PlayerCommanderTree.all()) {
            if (node.effect() == PlayerCommanderEffect.REINFORCEMENT_LIMIT
                    && PlayerCommanderRequirements.owns(profile, node)) {
                best = Math.max(best, node.value());
            }
        }

        return Math.min(best, PlayerCommanderBalance.MAX_REINFORCEMENTS);
    }

    /** Where the called squad is set down, in blocks from the camp. */
    public static int approachDistance(PlayerCommanderProfile profile) {
        for (PlayerCommanderNodeDefinition node : PlayerCommanderTree.all()) {
            if (node.effect() == PlayerCommanderEffect.APPROACH_CUT
                    && PlayerCommanderRequirements.owns(profile, node)) {
                return node.value();
            }
        }

        return PlayerCommanderBalance.APPROACH_DISTANCE;
    }

    /** The wait between raids, with Priority Vox already taken off. */
    public static int cooldownTicks(PlayerCommanderProfile profile) {
        int base = PlayerCommanderBalance.RAID_COOLDOWN_TICKS;

        if (owns(profile, PlayerCommanderTree.PRIORITY_VOX)) {
            base -= (int) Math.round(base * PlayerCommanderBalance.PRIORITY_VOX_COOLDOWN_CUT);
        }

        return Math.max(1, base);
    }

    public static boolean prefersSergeant(PlayerCommanderProfile profile) {
        return owns(profile, PlayerCommanderTree.FIELD_SERGEANT);
    }

    public static boolean hasCoordinatedAssault(PlayerCommanderProfile profile) {
        return owns(profile, PlayerCommanderTree.COORDINATED_ASSAULT);
    }

    private static boolean owns(PlayerCommanderProfile profile, String nodeId) {
        return PlayerCommanderRequirements.owns(profile, nodeId);
    }

    // ==================================================================== buying

    /**
     * Takes one command node, or explains why not.
     *
     * @return the message to show the player; empty when it worked
     */
    public static Component unlock(ServerPlayer player, String nodeId) {
        PlayerCommanderNodeDefinition node = PlayerCommanderTree.node(nodeId);
        if (node == null) {
            // An id nothing matches is a client sending something the server never offered.
            return Component.empty();
        }

        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        PlayerCommanderProfile profile = data.profile(player.getUUID()).commander();

        PlayerProgressionRequirements.Result check =
                PlayerCommanderRequirements.checkUnlock(player, profile, node);
        if (!check.ok()) {
            return check.reason();
        }

        if (!profile.spendPoints(node.cost())) {
            return Component.translatable("msg.firstcrusade.command.no_points",
                    node.cost(), profile.points());
        }

        profile.take(node.id());
        data.markChanged();

        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.0F);
        player.displayClientMessage(Component.translatable(
                "msg.firstcrusade.command.node_unlocked", node.displayName())
                .withStyle(ChatFormatting.GOLD), true);

        PlayerProgressionNetwork.sync(player);
        return Component.empty();
    }

    // ==================================================================== experience

    /** Awards commander experience, reports any level gained, and syncs. */
    public static void awardXp(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }

        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        PlayerCommanderProfile profile = data.profile(player.getUUID()).commander();

        int levels = profile.addXp(amount);
        data.markChanged();

        player.displayClientMessage(Component.translatable(
                "msg.firstcrusade.command.xp_gained", amount).withStyle(ChatFormatting.GRAY), true);

        if (levels > 0) {
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 0.9F);
            player.sendSystemMessage(Component.translatable(
                    "msg.firstcrusade.command.level_up", profile.level())
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable(
                    "msg.firstcrusade.command.point_gained", levels)
                    .withStyle(ChatFormatting.GOLD));
        }

        PlayerProgressionNetwork.sync(player);
    }

    public static void awardPoints(ServerPlayer player, int amount) {
        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        data.profile(player.getUUID()).commander().grantPoints(amount);
        data.markChanged();
        PlayerProgressionNetwork.sync(player);
    }

    /**
     * The one-off Command Point a player is given on reaching Astra Veteran.
     *
     * <p>Guarded by a flag on the profile rather than by "did they just level up", because the
     * caller is a login/recalculate hook that fires many times for the same player. Idempotent by
     * construction: the second call does nothing.
     */
    public static void grantVeteranPointIfDue(ServerPlayer player) {
        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        PlayerProgressionProfile full = data.profile(player.getUUID());
        PlayerCommanderProfile profile = full.commander();

        if (profile.veteranPointGranted()) {
            return;
        }

        if (!full.stage().isAtLeast(PlayerEvolutionStage.ASTRA_VETERAN)) {
            return;
        }

        profile.markVeteranPointGranted();
        profile.grantPoints(PlayerCommanderBalance.VETERAN_GRANT_POINTS);
        data.markChanged();

        player.sendSystemMessage(Component.translatable(
                "msg.firstcrusade.command.veteran_grant",
                PlayerCommanderBalance.VETERAN_GRANT_POINTS).withStyle(ChatFormatting.GOLD));

        PlayerProgressionNetwork.sync(player);
    }

    // ==================================================================== raid bookkeeping

    public static void onRaidStarted(ServerPlayer player, int troopsCalled) {
        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        PlayerCommanderProfile profile = data.profile(player.getUUID()).commander();

        boolean first = profile.startedRaids() == 0;

        profile.countRaidStarted();
        profile.countTroopsCalled(troopsCalled);
        profile.setCooldownReadyAt(
                player.level().getGameTime() + cooldownTicks(profile));

        data.markChanged();

        if (first) {
            awardXp(player, PlayerCommanderBalance.XP_START_FIRST_RAID);
        } else {
            PlayerProgressionNetwork.sync(player);
        }
    }

    public static void onRaidWon(ServerPlayer player, boolean flawless) {
        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        data.profile(player.getUUID()).commander().countRaidWon();
        data.markChanged();

        int xp = PlayerCommanderBalance.XP_RAID_WON + PlayerCommanderBalance.XP_CAMP_DESTROYED;
        if (flawless) {
            xp += PlayerCommanderBalance.XP_FLAWLESS_BONUS;
        }

        awardXp(player, xp);
    }

    public static void onRaidFailed(ServerPlayer player) {
        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        PlayerCommanderProfile profile = data.profile(player.getUUID()).commander();

        profile.countRaidFailed();
        // A failure is not a punishment: the wait before trying again is the short one.
        profile.setCooldownReadyAt(
                player.level().getGameTime() + PlayerCommanderBalance.ABORT_COOLDOWN_TICKS);

        data.markChanged();
        PlayerProgressionNetwork.sync(player);
    }

    /** Remaining cooldown in ticks, or zero. */
    public static long cooldownRemaining(PlayerCommanderProfile profile, long gameTime) {
        return Math.max(0L, profile.cooldownReadyAt() - gameTime);
    }
}
