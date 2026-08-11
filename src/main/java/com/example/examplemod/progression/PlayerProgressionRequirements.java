package com.example.examplemod.progression;

import javax.annotation.Nullable;

import com.example.examplemod.ImperialCommandCoreBlockEntity;
import com.example.examplemod.PlayerFaction;
import com.example.examplemod.PlayerFactionData;
import com.example.examplemod.StrategicAge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The single authority on "may this player do that".
 *
 * <h2>Every answer carries its reason</h2>
 *
 * A check that returns {@code false} tells the player nothing, and a screen that greys a node out
 * with no explanation reads as a broken screen — the navigation terminal already taught this project
 * that lesson. So every result is a {@link Result}: a verdict and the sentence to show. The tooltip
 * prints the reason, the packet handler sends it as a message, and the two can never disagree.
 *
 * <h2>Client may ask, server decides</h2>
 *
 * The client calls the cheap half of this ({@link #checkClientVisible}) to grey things out, and the
 * server calls the whole of it again when a packet arrives. The client's copy is a courtesy; the
 * server's is the rule.
 */
public final class PlayerProgressionRequirements {
    private PlayerProgressionRequirements() {
    }

    /** A verdict plus the sentence that explains it. {@link #OK} is the only passing value. */
    public record Result(boolean ok, Component reason) {
        public static final Result OK = new Result(true, Component.empty());

        public static Result no(String key, Object... args) {
            return new Result(false, Component.translatable(key, args));
        }
    }

    // ==================================================================== faction

    /** The whole tree is Imperial. Orks get told so, once, and in their own words. */
    public static Result checkFaction(ServerPlayer player) {
        PlayerFaction faction = PlayerFactionData.get(player.serverLevel()).getFaction(player);

        if (faction == PlayerFaction.ORKS) {
            return Result.no("msg.firstcrusade.progression.ork_branch");
        }

        if (faction != PlayerFaction.IMPERIUM) {
            return Result.no("msg.firstcrusade.progression.no_faction");
        }

        return Result.OK;
    }

    // ==================================================================== buying a rank

    /**
     * Whether this player may buy the next rank of a node right now.
     *
     * <p>Order matters for the message, not the outcome: the reason a player sees should be the
     * first real obstacle, so the tree's own shape is checked before their wallet.
     */
    public static Result checkRankPurchase(ServerPlayer player, PlayerProgressionProfile profile,
                                           PlayerSkillNodeDefinition node) {
        Result faction = checkFaction(player);
        if (!faction.ok()) {
            return faction;
        }

        if (node.isRoot()) {
            return Result.no("msg.firstcrusade.progression.root_is_free");
        }

        if (node.implant() || node.ascension()) {
            return Result.no("msg.firstcrusade.progression.not_bought_with_points");
        }

        int current = profile.rank(node.id());
        if (current >= node.maxRanks()) {
            return Result.no("msg.firstcrusade.progression.max_rank");
        }

        Result gate = checkPrerequisites(profile, node);
        if (!gate.ok()) {
            return gate;
        }

        int cost = node.costOfRank(current + 1);
        if (profile.points() < cost) {
            return Result.no("msg.firstcrusade.progression.no_points", cost, profile.points());
        }

        return Result.OK;
    }

    /**
     * Every prerequisite at rank 1 or better.
     *
     * <p>This is the whole of the "cannot skip an implant" rule. A cycle's skills require the
     * previous organ; the organ requires its own three skills. Nothing outside a cycle points into
     * it, so there is no node anywhere on the tree that lets a player step over a surgery.
     */
    public static Result checkPrerequisites(PlayerProgressionProfile profile,
                                            PlayerSkillNodeDefinition node) {
        for (String id : node.prerequisites()) {
            PlayerSkillNodeDefinition required = PlayerProgressionTree.node(id);
            if (required == null) {
                continue;
            }

            boolean satisfied = required.implant()
                    ? profile.hasImplant(id)
                    : profile.hasRank(id);

            if (!satisfied) {
                return Result.no("msg.firstcrusade.progression.needs_node", required.displayName());
            }
        }

        return Result.OK;
    }

    /** How many of an implant's three training nodes are done — the "2/3" the screen shows. */
    public static int trainingMet(PlayerProgressionProfile profile, PlayerSkillNodeDefinition node) {
        int met = 0;
        for (String id : node.prerequisites()) {
            if (profile.hasRank(id)) {
                met++;
            }
        }
        return met;
    }

    // ==================================================================== surgery

    /**
     * Whether the operating table is ready for this organ.
     *
     * <p>Runs every check the surgery needs <b>before</b> a single unit of gene-seed is touched.
     * That ordering is the point: a surgery that failed after taking the seed would be a resource
     * sink with a bug in it, and the player would have no way to tell which.
     */
    public static Result checkSurgery(ServerPlayer player, PlayerProgressionProfile profile,
                                      PlayerSkillNodeDefinition node) {
        Result faction = checkFaction(player);
        if (!faction.ok()) {
            return faction;
        }

        PlayerEvolutionNodeDefinition surgery = PlayerProgressionTree.surgery(node.id());
        if (surgery == null) {
            return Result.no("msg.firstcrusade.progression.not_an_implant");
        }

        if (profile.hasImplant(node.id())) {
            return Result.no("msg.firstcrusade.progression.already_implanted");
        }

        if (profile.isInSurgery()) {
            return Result.no("msg.firstcrusade.progression.surgery_running");
        }

        Result gate = checkPrerequisites(profile, node);
        if (!gate.ok()) {
            return Result.no("msg.firstcrusade.progression.training_incomplete",
                    trainingMet(profile, node), node.prerequisites().size());
        }

        // The Black Carapace asks for the whole road behind it, not only its own cycle.
        if (surgery.isBlackCarapace() && profile.implantCount() < PlayerProgressionBalance.IMPLANT_COUNT - 1) {
            return Result.no("msg.firstcrusade.progression.needs_all_implants",
                    profile.implantCount(), PlayerProgressionBalance.IMPLANT_COUNT - 1);
        }

        ImperialCommandCoreBlockEntity core = findOwnedCore(player);
        if (core == null) {
            return Result.no("msg.firstcrusade.progression.no_core");
        }

        if (core.getCityLevel() < surgery.minCoreLevel()) {
            return Result.no("msg.firstcrusade.progression.core_level", surgery.minCoreLevel());
        }

        if (!ageReached(core, surgery.minimumAge())) {
            return Result.no("msg.firstcrusade.progression.core_age",
                    Component.literal(surgery.minimumAge().name()));
        }

        if (core.getEmperorGeneSeed() < surgery.geneSeed()) {
            return Result.no("msg.firstcrusade.progression.no_gene_seed", surgery.geneSeed());
        }

        if (!hasHeadroom(player)) {
            return Result.no("msg.firstcrusade.progression.no_headroom");
        }

        return Result.OK;
    }

    /**
     * The nearest Command Core this player owns, or null.
     *
     * <p>One box query, only when somebody asks for surgery — never on a tick.
     */
    @Nullable
    public static ImperialCommandCoreBlockEntity findOwnedCore(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double range = PlayerProgressionBalance.SURGERY_CORE_RANGE;
        AABB box = new AABB(player.blockPosition()).inflate(range);

        ImperialCommandCoreBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof ImperialCommandCoreBlockEntity core)) {
                continue;
            }

            if (!core.isOwner(player)) {
                continue;
            }

            double distance = pos.distSqr(player.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = core;
            }
        }

        return best;
    }

    /**
     * Whether the settlement has reached the age the organ needs.
     *
     * <p>Read off the city level rather than asked of the strategic layer: the Core is the thing the
     * player is standing next to, and a surgery should not fail for a reason the player cannot see
     * from where they are.
     */
    private static boolean ageReached(ImperialCommandCoreBlockEntity core, StrategicAge age) {
        return switch (age) {
            case OUTPOST -> true;
            case FORTIFIED_SETTLEMENT -> core.getCityLevel() >= 2;
            case MANUFACTORUM_AGE -> core.getCityLevel() >= 2;
            case ASTARTES_AGE -> core.getCityLevel() >= 3;
            case PLANETARY_WAR -> core.getCityLevel() >= 4;
        };
    }

    /**
     * Room to grow.
     *
     * <p>A surgery that ends with the patient two blocks taller inside a one-block ceiling is a
     * player suffocating in a wall, so the operation refuses to start in a crawlspace. Checked
     * before the seed is spent, like everything else here.
     */
    public static boolean hasHeadroom(ServerPlayer player) {
        AABB grown = player.getBoundingBox()
                .setMaxY(player.getY() + PlayerProgressionBalance.SURGERY_CLEARANCE);

        return player.level().noCollision(player, grown);
    }

    // ==================================================================== ascension

    /** The last node: the Black Carapace, then the Blood Trial, and nothing else will do. */
    public static Result checkAscension(ServerPlayer player, PlayerProgressionProfile profile) {
        Result faction = checkFaction(player);
        if (!faction.ok()) {
            return faction;
        }

        if (profile.stage() == PlayerEvolutionStage.SPACE_MARINE) {
            return Result.no("msg.firstcrusade.progression.already_astartes");
        }

        if (profile.stage() != PlayerEvolutionStage.NEOPHYTE) {
            return Result.no("msg.firstcrusade.progression.needs_neophyte");
        }

        if (!profile.trialRequirementsMet()) {
            return Result.no("msg.firstcrusade.progression.trial_incomplete");
        }

        if (findOwnedCore(player) == null) {
            return Result.no("msg.firstcrusade.progression.no_core");
        }

        return Result.OK;
    }

    // ==================================================================== equipment

    /** The stage a piece of Astartes wargear asks of whoever picks it up. */
    public static PlayerEvolutionStage requiredStageFor(net.minecraft.world.item.Item item) {
        return PlayerProgressionEquipment.requiredStage(item);
    }
}
