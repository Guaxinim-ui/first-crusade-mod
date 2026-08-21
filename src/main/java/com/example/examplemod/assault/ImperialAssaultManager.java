package com.example.examplemod.assault;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.examplemod.AbstractImperialTroopEntity;
import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.GuardsmanEntity;
import com.example.examplemod.GuardsmanRank;
import com.example.examplemod.ImperialCommandCoreBlockEntity;
import com.example.examplemod.OrkCampBlockEntity;
import com.example.examplemod.SimpleImperialBaseManager;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.progression.PlayerCommanderBalance;
import com.example.examplemod.progression.PlayerCommanderManager;
import com.example.examplemod.progression.PlayerCommanderProfile;
import com.example.examplemod.progression.PlayerCommanderRequirements;
import com.example.examplemod.progression.PlayerEvolutionStage;
import com.example.examplemod.progression.PlayerProgressionBalance;
import com.example.examplemod.progression.PlayerProgressionData;
import com.example.examplemod.progression.PlayerProgressionManager;
import com.example.examplemod.progression.PlayerProgressionNetwork;
import com.example.examplemod.progression.PlayerProgressionProfile;
import com.example.examplemod.progression.PlayerProgressionRequirements;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Player-started raids on Ork camps: who may call one, which base answers, how the troops get
 * there, and how they get home.
 *
 * <h2>Nothing runs when nothing is running</h2>
 *
 * {@link #tick} returns on an {@code isEmpty()} before it touches the world. With no raid live this
 * system costs one map lookup a tick, and with one live it costs a handful of entity lookups five
 * times a second. There is no world scan, no per-base sweep and no chunk ticket anywhere in it.
 *
 * <h2>The troops are real</h2>
 *
 * A called squad is the base's own soldiers, moved. Nothing is spawned, {@code recruitedGuardsmen}
 * is not incremented, and a soldier that dies on the raid stays dead and leaves the base one short.
 * That is the whole point: a commander who empties their garrison has emptied their garrison.
 *
 * <h2>Home is captured before anything moves</h2>
 *
 * Every soldier's exact position, facing, Core and home ring go into {@link ExpeditionTroopData} at
 * the moment it is picked — and that record is persisted. A raid that ends, is aborted, or is
 * interrupted by a server restart can always put its soldiers back, because "back" was written down
 * before the first teleport.
 */
public final class ImperialAssaultManager {
    private ImperialAssaultManager() {
    }

    /** Fixed id so the coordinated-assault modifier is removed exactly, never doubled. */
    private static final UUID COORDINATED_KNOCKBACK_ID =
            UUID.fromString("6f2a1d4c-0e58-4f3a-9c21-7b8d5e0a4412");

    // ==================================================================== the raid key

    /**
     * Declares a raid on whatever camp the commander is standing at — the keybind's whole job.
     *
     * <h2>Why the key looks the camp up instead of being told</h2>
     *
     * The Ork panel's button already knows which block was clicked; a key press knows nothing. So
     * the server asks its own war map which camp is within {@link ImperialAssaultBalance#START_RANGE}
     * and hands that to {@link #startRaid}, which then applies every rule unchanged. The client
     * never names a camp, so it can never name the wrong one.
     *
     * <h2>Pressing it during your own raid is a status request, not a mistake</h2>
     *
     * A commander who presses the key again is asking "how is it going", and answering that is more
     * useful than refusing them. It does not call a second wave — reinforcements are called once,
     * when the raid opens.
     */
    public static Component declareNearestRaid(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ImperialAssaultData data = ImperialAssaultData.get(level);

        ImperialAssaultRecord own = data.byInitiator(player.getUUID());
        if (own != null && own.phase().isFighting()) {
            return describeRaid(level, own);
        }

        BlockPos camp = nearestCampWithinRange(level, player.blockPosition());

        if (camp == null) {
            return Component.translatable("msg.firstcrusade.assault.no_camp_nearby",
                    (int) ImperialAssaultBalance.START_RANGE);
        }

        return startRaid(player, camp);
    }

    /** One line of "how is my raid going", for a commander who pressed the key again. */
    private static Component describeRaid(ServerLevel level, ImperialAssaultRecord record) {
        int defenders = level.getBlockEntity(record.campPos()) instanceof OrkCampBlockEntity camp
                ? camp.countLivingDefenders(level)
                : 0;

        int alive = 0;
        for (ExpeditionTroopData troop : record.troops()) {
            Entity entity = level.getEntity(troop.uuid());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                alive++;
            }
        }

        return Component.translatable("msg.firstcrusade.assault.in_progress", defenders, alive);
    }

    /**
     * The nearest camp the player is close enough to declare on.
     *
     * <p>Straight off {@link WorldWarMapData} — the register every camp writes itself into — so this
     * is a walk over a small set of packed longs, not a block or entity scan. The block entity is
     * only asked for once a candidate is close enough to matter.
     */
    @Nullable
    private static BlockPos nearestCampWithinRange(ServerLevel level, BlockPos from) {
        double rangeSqr = ImperialAssaultBalance.START_RANGE * ImperialAssaultBalance.START_RANGE;

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (long packed : WorldWarMapData.get(level).getCamps(level)) {
            BlockPos pos = BlockPos.of(packed);
            double distance = pos.distSqr(from);

            if (distance > rangeSqr || distance >= bestDistance) {
                continue;
            }

            if (level.getBlockEntity(pos) instanceof OrkCampBlockEntity) {
                bestDistance = distance;
                best = pos;
            }
        }

        return best;
    }

    // ==================================================================== starting

    /**
     * Validates and starts a raid. Every check is here and none of them is on the client.
     *
     * @return the reason it did not happen, or an empty component when it did
     */
    public static Component startRaid(ServerPlayer player, BlockPos campPos) {
        ServerLevel level = player.serverLevel();

        PlayerProgressionRequirements.Result faction =
                PlayerCommanderRequirements.checkMayRaid(player);
        if (!faction.ok()) {
            return Component.translatable("msg.firstcrusade.assault.not_imperial");
        }

        double distanceSqr = player.distanceToSqr(
                campPos.getX() + 0.5D, campPos.getY() + 0.5D, campPos.getZ() + 0.5D);

        if (distanceSqr > ImperialAssaultBalance.START_RANGE * ImperialAssaultBalance.START_RANGE) {
            return Component.translatable("msg.firstcrusade.assault.too_far");
        }

        BlockEntity blockEntity = level.getBlockEntity(campPos);
        if (!(blockEntity instanceof OrkCampBlockEntity camp)) {
            return Component.translatable("msg.firstcrusade.assault.no_camp");
        }

        ImperialAssaultData data = ImperialAssaultData.get(level);

        if (data.atCamp(campPos) != null) {
            return Component.translatable("msg.firstcrusade.assault.already_active");
        }

        if (data.byInitiator(player.getUUID()) != null) {
            return Component.translatable("msg.firstcrusade.assault.already_leading");
        }

        PlayerCommanderProfile commander = PlayerCommanderManager.profile(player);
        long now = level.getGameTime();
        long cooldown = PlayerCommanderManager.cooldownRemaining(commander, now);

        if (cooldown > 0) {
            return Component.translatable("msg.firstcrusade.assault.cooldown", cooldown / 20L);
        }

        // ---------------------------------------------------------------- it is happening
        ImperialAssaultRecord record = new ImperialAssaultRecord(
                UUID.randomUUID(), level.dimension(), campPos, player.getUUID(), now);

        record.setInitialDefenders(camp.countLivingDefenders(level));
        record.setApproachDistance(PlayerCommanderManager.approachDistance(commander));

        camp.setUnderAssault(true);
        camp.alertDefenders(level, player);

        data.add(record);

        player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.started",
                record.initialDefenders()).withStyle(ChatFormatting.GOLD));

        int called = callReinforcements(level, player, record, commander);

        record.setPhase(called > 0
                ? ImperialAssaultPhase.REINFORCEMENTS_DEPLOYED
                : ImperialAssaultPhase.ACTIVE);

        data.markChanged();

        PlayerCommanderManager.onRaidStarted(player, called);

        return Component.empty();
    }

    /**
     * Works out how many soldiers this commander may call, finds the base that answers, and sets
     * them down on the approach ring.
     *
     * @return how many actually set out
     */
    private static int callReinforcements(ServerLevel level, ServerPlayer player,
                                          ImperialAssaultRecord record,
                                          PlayerCommanderProfile commander) {
        int limit = PlayerCommanderManager.reinforcementLimit(commander);

        if (limit <= 0) {
            player.sendSystemMessage(
                    Component.translatable("msg.firstcrusade.assault.no_authority")
                            .withStyle(ChatFormatting.GRAY));
            return 0;
        }

        ImperialCommandCoreBlockEntity base = findEligibleBase(level, player, record.campPos());

        if (base == null) {
            player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.no_base")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        record.setOriginCore(base.getBlockPos());

        List<PathfinderMob> available = eligibleSoldiers(level, base.getBlockPos());
        int sendable = available.size() - PlayerCommanderBalance.HOME_GUARD_MINIMUM;

        if (sendable <= 0) {
            player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.no_soldiers")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int sending = Math.min(limit, sendable);

        if (PlayerCommanderManager.prefersSergeant(commander)
                && sending >= PlayerCommanderBalance.REINFORCEMENTS_REINFORCED) {
            // A Sergeant is preferred, never created: if the base has none, the squad simply goes
            // out under an ordinary Guardsman.
            available.sort(Comparator.comparingInt(ImperialAssaultManager::sergeantFirst));
        }

        int distance = (int) Math.round(Math.sqrt(base.getBlockPos().distSqr(record.campPos())));

        for (int i = 0; i < sending; i++) {
            PathfinderMob soldier = available.get(i);

            record.addTroop(ExpeditionTroopData.capture(soldier, base.getBlockPos()));
            deploySoldier(level, soldier, record, base.getBlockPos(), i);
        }

        player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.reinforcements",
                sending, limit).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.base_distance",
                distance).withStyle(ChatFormatting.GRAY));

        if (sending < limit) {
            player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.partial",
                    sending, limit).withStyle(ChatFormatting.YELLOW));
        }

        return sending;
    }

    /** Sorting key: a Sergeant or better sorts to the front, everyone else keeps their place. */
    private static int sergeantFirst(PathfinderMob soldier) {
        if (soldier instanceof GuardsmanEntity guardsman
                && guardsman.getGuardsmanRank().ordinal() >= GuardsmanRank.SERGEANT.ordinal()) {
            return 0;
        }
        return 1;
    }

    // ==================================================================== finding the base

    /**
     * The nearest base that may answer the call.
     *
     * <p>Candidates come from {@link WorldWarMapData} — the register every Core writes itself into —
     * so this never walks the world looking for blocks. They are sorted by distance and the first
     * eligible one wins, with a base the player owns beating an unowned one at the same distance.
     * A base belonging to <i>another</i> player is never eligible: there is no alliance system, and
     * quietly conscripting somebody else's garrison would be the worst possible default.
     */
    @Nullable
    private static ImperialCommandCoreBlockEntity findEligibleBase(ServerLevel level,
                                                                   ServerPlayer player,
                                                                   BlockPos campPos) {
        WorldWarMapData warMap = WorldWarMapData.get(level);
        ImperialAssaultData data = ImperialAssaultData.get(level);

        long searchSqr = (long) ImperialAssaultBalance.BASE_SEARCH_RADIUS
                * ImperialAssaultBalance.BASE_SEARCH_RADIUS;

        List<BlockPos> candidates = new ArrayList<>();

        for (long packed : warMap.getCities(level)) {
            BlockPos pos = BlockPos.of(packed);
            if (pos.distSqr(campPos) <= searchSqr) {
                candidates.add(pos);
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(campPos)));

        ImperialCommandCoreBlockEntity fallback = null;

        for (BlockPos pos : candidates) {
            // The war map is world-wide, so a position may belong to a base on another planet. Asking
            // this level for the block entity is also the check that it is this level's base.
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (!(blockEntity instanceof ImperialCommandCoreBlockEntity core)) {
                continue;
            }

            if (core.hasActiveOrkRaid()) {
                continue;
            }

            if (isAlreadySendingTroops(data, pos)) {
                continue;
            }

            if (eligibleSoldiers(level, pos).size() <= PlayerCommanderBalance.HOME_GUARD_MINIMUM) {
                continue;
            }

            if (core.isOwner(player)) {
                return core;
            }

            if (!core.hasOwner() && fallback == null) {
                fallback = core;
            }
        }

        return fallback;
    }

    private static boolean isAlreadySendingTroops(ImperialAssaultData data, BlockPos corePos) {
        for (ImperialAssaultRecord record : data.raids().values()) {
            if (!record.phase().isOver() && corePos.equals(record.originCore())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The soldiers a base may lend: alive, bound to it, near it, not already away, not riding
     * anything.
     */
    private static List<PathfinderMob> eligibleSoldiers(ServerLevel level, BlockPos corePos) {
        int radius = ImperialAssaultBalance.GARRISON_PICK_RADIUS;

        AABB box = new AABB(
                corePos.getX() - radius, corePos.getY() - 32, corePos.getZ() - radius,
                corePos.getX() + radius, corePos.getY() + 48, corePos.getZ() + radius);

        List<PathfinderMob> soldiers = new ArrayList<>();

        for (GuardsmanEntity guardsman : level.getEntitiesOfClass(GuardsmanEntity.class, box,
                candidate -> candidate.isAlive()
                        && candidate.isAssignedToCommandCore(corePos)
                        && !ImperialExpeditionTags.isOnExpedition(candidate)
                        && !candidate.isPassenger())) {
            soldiers.add(guardsman);
        }

        for (AbstractImperialTroopEntity troop : level.getEntitiesOfClass(
                AbstractImperialTroopEntity.class, box,
                candidate -> candidate.isAlive()
                        && candidate.isAssignedToCommandCore(corePos)
                        && !ImperialExpeditionTags.isOnExpedition(candidate)
                        && !candidate.isPassenger())) {
            soldiers.add(troop);
        }

        return soldiers;
    }

    // ==================================================================== the approach

    /**
     * Sets one soldier down on the approach ring and points it at the camp.
     *
     * <p>The drop is on the side of the ring facing the base it came from, so the squad reads as
     * having marched in from home rather than materialising behind the enemy. Soldiers land in
     * clusters of three rather than all on one block.
     */
    private static void deploySoldier(ServerLevel level, PathfinderMob soldier,
                                      ImperialAssaultRecord record, BlockPos basePos, int index) {
        ImperialExpeditionTags.mark(soldier, record.raidId(), basePos, soldier.blockPosition());

        // The home leash has to come off before the teleport, or the return goal would immediately
        // start walking the soldier back the hundred blocks it was just moved.
        SimpleImperialBaseManager.releaseFromBase(soldier);
        soldier.getNavigation().stop();
        soldier.setTarget(null);

        BlockPos drop = findApproachSpot(level, record.campPos(), basePos,
                record.approachDistance(), index);

        soldier.teleportTo(drop.getX() + 0.5D, drop.getY(), drop.getZ() + 0.5D);
        soldier.getNavigation().moveTo(
                record.campPos().getX() + 0.5D,
                record.campPos().getY(),
                record.campPos().getZ() + 0.5D,
                ImperialAssaultBalance.MARCH_SPEED);
    }

    /**
     * A safe patch of ground on the approach ring.
     *
     * <p>"Safe" is checked, not hoped for: solid floor, two blocks of air, no fluid at the feet or
     * under them, and never nearer the camp than the hard minimum. If the preferred bearing is all
     * water or cliff, the search walks around the ring rather than dropping somebody into it.
     */
    private static BlockPos findApproachSpot(ServerLevel level, BlockPos campPos, BlockPos basePos,
                                             int distance, int index) {
        double dx = basePos.getX() - campPos.getX();
        double dz = basePos.getZ() - campPos.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        double baseAngle = length < 1.0D
                ? level.random.nextDouble() * Math.PI * 2.0D
                : Math.atan2(dz, dx);

        int cluster = index / ImperialAssaultBalance.SQUAD_CLUSTER_SIZE;
        int withinCluster = index % ImperialAssaultBalance.SQUAD_CLUSTER_SIZE;

        // Each cluster sits a little further round the ring; members spread inside it.
        double clusterOffset = Math.toRadians(cluster * 9.0D);

        for (int attempt = 0; attempt < ImperialAssaultBalance.APPROACH_ATTEMPTS; attempt++) {
            // Walk outward from the preferred bearing in both directions, widening as we go.
            double sweep = Math.toRadians((attempt / 2) * 15.0D) * ((attempt % 2 == 0) ? 1 : -1);
            double angle = baseAngle + clusterOffset + sweep;

            int radius = Math.max(ImperialAssaultBalance.MIN_DISTANCE_FROM_CAMP,
                    distance + (attempt % 3 - 1) * PlayerCommanderBalance.APPROACH_TOLERANCE);

            int x = campPos.getX() + (int) Math.round(Math.cos(angle) * radius)
                    + withinCluster * ImperialAssaultBalance.CLUSTER_SPREAD;
            int z = campPos.getZ() + (int) Math.round(Math.sin(angle) * radius)
                    + withinCluster * ImperialAssaultBalance.CLUSTER_SPREAD;

            BlockPos candidate = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, campPos.getY(), z));

            if (isSafeToStandOn(level, candidate)
                    && candidate.distSqr(campPos)
                    >= (long) ImperialAssaultBalance.MIN_DISTANCE_FROM_CAMP
                    * ImperialAssaultBalance.MIN_DISTANCE_FROM_CAMP) {
                return candidate;
            }
        }

        // Nothing on the ring worked. The heightmap directly on the preferred bearing is still a
        // surface, which is a far better answer than dropping the squad inside the camp.
        int x = campPos.getX() + (int) Math.round(Math.cos(baseAngle) * distance);
        int z = campPos.getZ() + (int) Math.round(Math.sin(baseAngle) * distance);

        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, campPos.getY(), z));
    }

    private static boolean isSafeToStandOn(ServerLevel level, BlockPos pos) {
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
            return false;
        }

        BlockPos floor = pos.below();

        if (level.getBlockState(floor).isAir()) {
            return false;
        }

        FluidState floorFluid = level.getFluidState(floor);
        FluidState feetFluid = level.getFluidState(pos);

        return floorFluid.isEmpty() && feetFluid.isEmpty();
    }

    // ==================================================================== the tick

    /** Every live raid, five times a second. Returns immediately when there are none. */
    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ImperialAssaultData data = ImperialAssaultData.get(overworld);

        if (data.isEmpty()) {
            return;
        }

        if (overworld.getGameTime() % ImperialAssaultBalance.UPDATE_INTERVAL_TICKS != 0L) {
            return;
        }

        for (ImperialAssaultRecord record : data.snapshot()) {
            ServerLevel level = server.getLevel(record.dimension());

            if (level == null) {
                // The dimension is gone. Nothing can be done for the troops there, and holding the
                // record open would only keep the camp marked forever.
                data.remove(record.raidId());
                continue;
            }

            tickRaid(level, data, record);
        }

        data.markChanged();
    }

    private static void tickRaid(ServerLevel level, ImperialAssaultData data,
                                 ImperialAssaultRecord record) {
        switch (record.phase()) {
            case STARTING, REINFORCEMENTS_DEPLOYED, ACTIVE -> tickFight(level, data, record);
            case VICTORY, FAILED, RETURNING -> tickReturn(level, data, record);
            case COMPLETE -> data.remove(record.raidId());
        }
    }

    private static void tickFight(ServerLevel level, ImperialAssaultData data,
                                  ImperialAssaultRecord record) {
        BlockEntity blockEntity = level.getBlockEntity(record.campPos());

        if (!(blockEntity instanceof OrkCampBlockEntity camp)) {
            // Somebody broke the camp block, or another force razed it. Either way it fell during
            // this raid, and the raid is won.
            finishVictory(level, data, record, null);
            return;
        }

        if (shouldAbort(level, record)) {
            abort(level, data, record);
            return;
        }

        if (camp.countLivingDefenders(level) <= 0) {
            finishVictory(level, data, record, camp);
            return;
        }

        record.setPhase(ImperialAssaultPhase.ACTIVE);

        maybeApplyArrivalBuff(level, record);
        retargetTroops(level, record);
    }

    /**
     * Whether the raid has lost the player who called it.
     *
     * <p>Absence is measured, not assumed: the first tick the initiator is offline, dead, in another
     * dimension or far away starts a clock, and being back before it runs out clears it. A player
     * who dies and sprints back has their raid; one who logs off does not keep a garrison hostage.
     */
    private static boolean shouldAbort(ServerLevel level, ImperialAssaultRecord record) {
        long now = level.getGameTime();

        if (now - record.startedAtGameTime() > ImperialAssaultBalance.MAX_RAID_TICKS) {
            return true;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(record.initiator());

        boolean present = player != null
                && player.isAlive()
                && player.level().dimension().equals(record.dimension())
                && player.distanceToSqr(
                record.campPos().getX() + 0.5D,
                record.campPos().getY() + 0.5D,
                record.campPos().getZ() + 0.5D)
                <= ImperialAssaultBalance.ABANDON_RADIUS * ImperialAssaultBalance.ABANDON_RADIUS;

        if (present) {
            record.setAbsentSince(0L);
            return false;
        }

        if (record.absentSince() == 0L) {
            record.setAbsentSince(now);
            return false;
        }

        return now - record.absentSince() > ImperialAssaultBalance.ABANDON_GRACE_TICKS;
    }

    /**
     * Points the squad at the fight — at most once every two seconds, and only when it needs it.
     *
     * <p>A soldier with a living target is left entirely alone, and one already walking on a valid
     * path is not given the same order again. Re-issuing {@code moveTo} every tick is what turns a
     * squad of ten into a pathfinding bill.
     */
    private static void retargetTroops(ServerLevel level, ImperialAssaultRecord record) {
        if (level.getGameTime() % ImperialAssaultBalance.RETARGET_INTERVAL_TICKS != 0L) {
            return;
        }

        for (ExpeditionTroopData troop : record.troops()) {
            Entity entity = level.getEntity(troop.uuid());

            if (!(entity instanceof PathfinderMob soldier) || !soldier.isAlive()) {
                continue;
            }

            LivingEntity current = soldier.getTarget();
            if (current != null && current.isAlive()) {
                continue;
            }

            Mob enemy = nearestOrk(level, soldier);

            if (enemy != null) {
                soldier.setTarget(enemy);
                continue;
            }

            // Nothing to shoot yet: keep marching, but only if the current path has run out.
            if (soldier.getNavigation().isDone()) {
                soldier.getNavigation().moveTo(
                        record.campPos().getX() + 0.5D,
                        record.campPos().getY(),
                        record.campPos().getZ() + 0.5D,
                        ImperialAssaultBalance.MARCH_SPEED);
            }
        }
    }

    @Nullable
    private static Mob nearestOrk(ServerLevel level, PathfinderMob soldier) {
        double range = ImperialAssaultBalance.TARGET_SEARCH_RANGE;

        List<Mob> orks = level.getEntitiesOfClass(Mob.class,
                soldier.getBoundingBox().inflate(range),
                candidate -> candidate.isAlive()
                        && FirstCrusadeFactionManager.getFaction(candidate)
                        == FirstCrusadeFaction.ORKS);

        Mob best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Mob ork : orks) {
            double distance = ork.distanceToSqr(soldier);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ork;
            }
        }

        return best;
    }

    /** Speed and a steadier footing when the squad reaches the fight. Once per raid, never stacked. */
    private static void maybeApplyArrivalBuff(ServerLevel level, ImperialAssaultRecord record) {
        if (record.arrivalBuffApplied() || record.troops().isEmpty()) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(record.initiator());
        if (player == null
                || !PlayerCommanderManager.hasCoordinatedAssault(
                PlayerCommanderManager.profile(player))) {
            return;
        }

        boolean anyArrived = false;

        for (ExpeditionTroopData troop : record.troops()) {
            Entity entity = level.getEntity(troop.uuid());

            if (entity instanceof PathfinderMob soldier && soldier.isAlive()
                    && soldier.distanceToSqr(
                    record.campPos().getX() + 0.5D,
                    record.campPos().getY() + 0.5D,
                    record.campPos().getZ() + 0.5D)
                    <= ImperialAssaultBalance.ARRIVAL_RANGE
                    * ImperialAssaultBalance.ARRIVAL_RANGE) {
                anyArrived = true;
                break;
            }
        }

        if (!anyArrived) {
            return;
        }

        for (ExpeditionTroopData troop : record.troops()) {
            Entity entity = level.getEntity(troop.uuid());

            if (entity instanceof PathfinderMob soldier && soldier.isAlive()) {
                applyCoordinatedBuff(soldier);
            }
        }

        record.markArrivalBuffApplied();

        player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.coordinated")
                .withStyle(ChatFormatting.AQUA));
    }

    private static void applyCoordinatedBuff(LivingEntity soldier) {
        soldier.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                PlayerCommanderBalance.COORDINATED_BUFF_TICKS,
                PlayerCommanderBalance.COORDINATED_SPEED_AMPLIFIER, false, false));

        AttributeInstance knockback = soldier.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

        if (knockback != null && knockback.getModifier(COORDINATED_KNOCKBACK_ID) == null) {
            // A transient modifier, removed again when the soldier comes home. An effect would have
            // been simpler, but vanilla has no knockback-resistance effect, and a permanent modifier
            // on a borrowed soldier is a bonus the base keeps forever.
            knockback.addTransientModifier(new AttributeModifier(
                    COORDINATED_KNOCKBACK_ID, "First Crusade coordinated assault", 0.25D,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    // ==================================================================== ending

    /** The camp fell. Pay once, tell everyone, then send the survivors home. */
    private static void finishVictory(ServerLevel level, ImperialAssaultData data,
                                      ImperialAssaultRecord record,
                                      @Nullable OrkCampBlockEntity camp) {
        if (camp != null) {
            camp.setUnderAssault(false);
            camp.razeCamp(level, record.campPos());
        } else {
            // The camp block is already gone (its chunk unloaded, or something else destroyed it),
            // so razeCamp cannot run and the campaign would never hear about the win. Report it here
            // instead, on the same path razeCamp would have used.
            WorldWarMapData.get(level).removeCamp(level, record.campPos());
            com.example.examplemod.campaign.CampaignIntegration.onCampRazed(level, record.campPos());
        }

        if (record.claimReward()) {
            grantVictoryRewards(level, record);
        }

        record.setPhase(ImperialAssaultPhase.VICTORY);
        tickReturn(level, data, record);
    }

    /**
     * Commander experience, the Blood Trial tick, and a message — each exactly once.
     *
     * <p>The once is {@link ImperialAssaultRecord#claimReward()}, which is persisted: a raid that
     * paid out before a restart cannot pay again after one.
     */
    private static void grantVictoryRewards(ServerLevel level, ImperialAssaultRecord record) {
        boolean flawless = record.troops().stream()
                .allMatch(troop -> {
                    Entity entity = level.getEntity(troop.uuid());
                    return entity instanceof LivingEntity living && living.isAlive();
                });

        for (UUID participantId : record.participants()) {
            ServerPlayer participant = level.getServer().getPlayerList().getPlayer(participantId);

            if (participant == null) {
                continue;
            }

            participant.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.victory")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            if (participantId.equals(record.initiator())) {
                PlayerCommanderManager.onRaidWon(participant, flawless);
            }

            countTowardBloodTrial(participant);
        }
    }

    /**
     * A completed raid counts toward the Blood Trial — the alternative route to Astartes for a
     * player who would rather take camps than tally fifty individual kills.
     *
     * <p>Only a Neophyte's raids count, because that is when the Trial begins; the counters are
     * reset at the Black Carapace for exactly that reason.
     */
    private static void countTowardBloodTrial(ServerPlayer player) {
        PlayerProgressionData data = PlayerProgressionData.get(player.serverLevel());
        PlayerProgressionProfile profile = data.profile(player.getUUID());

        if (profile.stage() != PlayerEvolutionStage.NEOPHYTE) {
            PlayerProgressionManager.awardXp(player, PlayerProgressionBalance.XP_RAID_VICTORY);
            return;
        }

        profile.countTrialRaid();
        data.markChanged();

        PlayerProgressionManager.awardXp(player, PlayerProgressionBalance.XP_RAID_VICTORY);
        PlayerProgressionNetwork.sync(player);
    }

    /** Called off. No reward; the troops still come home. */
    public static void abort(ServerLevel level, ImperialAssaultData data,
                             ImperialAssaultRecord record) {
        if (level.getBlockEntity(record.campPos()) instanceof OrkCampBlockEntity camp) {
            camp.setUnderAssault(false);
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(record.initiator());

        if (player != null) {
            player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.failed")
                    .withStyle(ChatFormatting.RED));
            PlayerCommanderManager.onRaidFailed(player);
        }

        record.setPhase(ImperialAssaultPhase.FAILED);
        tickReturn(level, data, record);
    }

    /**
     * Sends every surviving soldier home, in one pass.
     *
     * <p>This is what makes "the player won before the squad arrived" work: the return does not wait
     * for anyone to finish marching. The moment the raid ends, the survivors stop where they are and
     * are put back — walking a hundred blocks home would be a minute of nothing happening.
     */
    private static void tickReturn(ServerLevel level, ImperialAssaultData data,
                                   ImperialAssaultRecord record) {
        record.setPhase(ImperialAssaultPhase.RETURNING);

        int returned = 0;

        for (ExpeditionTroopData troop : record.troops()) {
            Entity entity = level.getEntity(troop.uuid());

            if (entity instanceof PathfinderMob soldier && soldier.isAlive()) {
                restoreSoldier(level, soldier, troop);
                returned++;
            }
            // A soldier that is dead, or in a chunk nobody has loaded, is simply left: the dead stay
            // dead (the base is one short, as it should be) and the unloaded still carry their
            // expedition marks, which is exactly what /fcassault clear_orphans reads.
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(record.initiator());

        if (player != null && returned > 0) {
            player.sendSystemMessage(Component.translatable("msg.firstcrusade.assault.returned",
                    returned).withStyle(ChatFormatting.GRAY));
        }

        record.clearTroops();
        record.setPhase(ImperialAssaultPhase.COMPLETE);

        data.remove(record.raidId());
    }

    /**
     * Puts one soldier back exactly as it was: position, facing, Core, home ring — and no guard post.
     *
     * <p>The Core it left is tried first. If that base has been destroyed since, the saved origin
     * position is still a real place it once stood safely, which is a better fallback than the
     * middle of a battlefield.
     */
    public static void restoreSoldier(ServerLevel level, PathfinderMob soldier,
                                      ExpeditionTroopData troop) {
        BlockPos home = troop.homeCore();
        boolean coreStillThere =
                level.getBlockEntity(home) instanceof ImperialCommandCoreBlockEntity;

        BlockPos destination = pickReturnSpot(level, troop, coreStillThere);

        soldier.getNavigation().stop();
        soldier.setTarget(null);

        soldier.teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
        soldier.setYRot(troop.originYaw());
        soldier.setXRot(troop.originPitch());

        removeCoordinatedBuff(soldier);
        ImperialExpeditionTags.clear(soldier);

        if (coreStillThere) {
            SimpleImperialBaseManager.bindToBase(soldier, home);
        } else if (troop.hadRestriction()) {
            soldier.restrictTo(troop.originPos(), troop.restrictionRadius());
        }
    }

    private static BlockPos pickReturnSpot(ServerLevel level, ExpeditionTroopData troop,
                                           boolean coreStillThere) {
        if (isSafeToStandOn(level, troop.originPos())) {
            return troop.originPos();
        }

        BlockPos anchor = coreStillThere ? troop.homeCore() : troop.originPos();

        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            int radius = ImperialAssaultBalance.RETURN_RING_MIN
                    + level.random.nextInt(ImperialAssaultBalance.RETURN_RING_MAX
                    - ImperialAssaultBalance.RETURN_RING_MIN + 1);

            BlockPos candidate = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    anchor.offset((int) Math.round(Math.cos(angle) * radius), 0,
                            (int) Math.round(Math.sin(angle) * radius)));

            if (isSafeToStandOn(level, candidate)) {
                return candidate;
            }
        }

        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor);
    }

    private static void removeCoordinatedBuff(LivingEntity soldier) {
        AttributeInstance knockback = soldier.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

        if (knockback != null && knockback.getModifier(COORDINATED_KNOCKBACK_ID) != null) {
            knockback.removeModifier(COORDINATED_KNOCKBACK_ID);
        }
    }

    // ==================================================================== operator tools

    /** Forces the raid at a camp to be won. Used by {@code /fcassault victory}. */
    public static boolean forceVictory(ServerLevel level, ImperialAssaultRecord record) {
        BlockEntity blockEntity = level.getBlockEntity(record.campPos());
        ImperialAssaultData data = ImperialAssaultData.get(level);

        finishVictory(level, data, record,
                blockEntity instanceof OrkCampBlockEntity camp ? camp : null);

        return true;
    }

    /** Sends every soldier of every live raid home and ends them all. */
    public static int returnAll(ServerLevel level) {
        ImperialAssaultData data = ImperialAssaultData.get(level);
        int count = 0;

        for (ImperialAssaultRecord record : data.snapshot()) {
            ServerLevel raidLevel = level.getServer().getLevel(record.dimension());

            if (raidLevel == null) {
                data.remove(record.raidId());
                continue;
            }

            if (raidLevel.getBlockEntity(record.campPos()) instanceof OrkCampBlockEntity camp) {
                camp.setUnderAssault(false);
            }

            count += record.troops().size();
            tickReturn(raidLevel, data, record);
        }

        data.markChanged();
        return count;
    }

    /**
     * Recovers soldiers marked as being on an expedition that no longer exists.
     *
     * <p>This is the safety net the persisted marks were put there for: a crash, a dimension that
     * went away, or a raid record that could not be read leaves troopers tagged and leashless. The
     * sweep is a loaded-entity scan, which is why it is a command an operator runs and not something
     * that happens on a timer.
     */
    public static int clearOrphans(ServerLevel level) {
        ImperialAssaultData data = ImperialAssaultData.get(level);
        int recovered = 0;

        List<? extends PathfinderMob> stranded = level.getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(PathfinderMob.class),
                mob -> ImperialExpeditionTags.isOnExpedition(mob));

        for (PathfinderMob soldier : stranded) {
            UUID raidId = ImperialExpeditionTags.raidId(soldier);
            ImperialAssaultRecord record = raidId == null ? null : data.byId(raidId);

            if (record != null && !record.phase().isOver()) {
                continue;
            }

            BlockPos home = ImperialExpeditionTags.homeCore(soldier);
            BlockPos origin = ImperialExpeditionTags.homePos(soldier);

            if (home == null) {
                // No home written down at all: clearing the marks at least frees the soldier from
                // an expedition that will never end.
                ImperialExpeditionTags.clear(soldier);
                removeCoordinatedBuff(soldier);
                recovered++;
                continue;
            }

            BlockPos safeOrigin = origin == null ? home : origin;

            restoreSoldier(level, soldier, new ExpeditionTroopData(
                    soldier.getUUID(),
                    safeOrigin.getX(), safeOrigin.getY(), safeOrigin.getZ(),
                    soldier.getYRot(), soldier.getXRot(),
                    home, true, com.example.examplemod.SimpleImperialBaseBalance.HOME_RADIUS));

            recovered++;
        }

        return recovered;
    }
}
