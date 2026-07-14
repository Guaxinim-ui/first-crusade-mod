package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The military brain of every Imperial settlement (AoE-style commander logic). Runs periodically
 * (see {@link StrategicWarAIEvents}) and, per city:
 *
 * - Raises a {@link CityCommanderEntity} once the settlement reaches the Fortified Settlement age
 *   (and a replacement after a respawn cooldown if he falls).
 * - Assesses the situation: under attack -> everyone holds the Core; attack squad in the field ->
 *   keep it marching in formation behind the commander; otherwise troops stay free so the
 *   {@link ImperialPatrolManager} circulates them around the walls.
 * - Forms ATTACK squads from the EXISTING garrison (the city empties out to attack and must
 *   retrain, mirroring the Ork war parties) when the strategic AI decides to strike
 *   ({@link StrategicWarAIManager} pays the resources and calls {@link #tryLaunchAttack}).
 * - Applies cooldowns: minimum spacing between offensives, and a defensive posture window after
 *   a squad is routed.
 *
 * Movement orders reuse the existing guard-post goals (the post is continuously re-aimed at the
 * commander while marching, and at the target during the assault), so no new per-entity AI goals
 * are needed and combat goals always take precedence.
 */
public final class CityMilitaryManager {
    // How often the military AI thinks, in ticks (driven by StrategicWarAIEvents).
    public static final int MILITARY_AI_INTERVAL = 60;

    private static final int GARRISON_RADIUS = 96;

    // Threat score at which the whole garrison is recalled to the Core (ThreatAssessment "Alert").
    private static final int THREAT_ALL_HANDS = 25;

    private static final int MIN_ATTACK_TROOPS = 4;
    private static final int HOME_GUARD_MINIMUM = 3;

    private static final long ATTACK_COOLDOWN_TICKS = 6000L;    // 5 min between offensives
    private static final long DEFEAT_DEFENSIVE_TICKS = 9600L;   // 8 min licking wounds after a rout
    public static final long COMMANDER_RESPAWN_TICKS = 3600L;   // 3 min until a new commander rises

    // Formation distances (squared, blocks).
    private static final double REGROUP_DIST_SQR = 28.0D * 28.0D;
    private static final double ENGAGE_DIST_SQR = 24.0D * 24.0D;

    private static final int HOLD_RING_RADIUS = 6;

    // Units currently under squad orders, rebuilt every military tick. The patrol manager skips
    // these so peacetime patrols don't fight the squad's movement orders.
    private static final Set<UUID> squaddedUnits = new HashSet<>();

    private CityMilitaryManager() {
    }

    public static boolean isSquadded(LivingEntity entity) {
        return squaddedUnits.contains(entity.getUUID());
    }

    public static void tickAll(ServerLevel level) {
        StrategicWarAIData data = StrategicWarAIData.get(level);

        squaddedUnits.clear();

        long gameTime = level.getGameTime();

        List<StrategicSettlementRecord> settlements = new ArrayList<>();

        for (StrategicSettlementRecord settlement : data.getImperialSettlements()) {
            settlements.add(settlement);
        }

        for (StrategicSettlementRecord settlement : settlements) {
            BlockPos corePos = settlement.getPos();

            if (!level.isLoaded(corePos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(corePos);

            if (!(blockEntity instanceof ImperialCommandCoreBlockEntity core)) {
                continue;
            }

            tickCity(level, data, settlement, core, gameTime);
        }

        data.setDirty();
    }

    private static void tickCity(
            ServerLevel level,
            StrategicWarAIData data,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core,
            long gameTime
    ) {
        ensureCommander(level, settlement, core, gameTime);

        int threat = core.getLiveThreatScore();
        boolean underAttack = core.hasActiveOrkRaid() || threat >= THREAT_ALL_HANDS;

        if (underAttack) {
            holdTheLine(level, settlement, core);
            return;
        }

        if (settlement.getAttackSquad() != null) {
            tickAttackSquad(level, settlement, core, gameTime);
        }
    }

    // ------------------------------------------------------------------
    // Commander
    // ------------------------------------------------------------------

    private static void ensureCommander(
            ServerLevel level,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core,
            long gameTime
    ) {
        if (settlement.getAge().ordinal() < StrategicAge.FORTIFIED_SETTLEMENT.ordinal()) {
            return;
        }

        if (settlement.getCommanderUUID() != null) {
            return;
        }

        if (gameTime < settlement.getCommanderRespawnAtGameTime()) {
            return;
        }

        CityCommanderEntity commander = FCRegistry.CITY_COMMANDER.get().create(level);

        if (commander == null) {
            return;
        }

        BlockPos spawn = StrategicConstructionPlanner.ground(
                level,
                core.getBlockPos().offset(
                        level.random.nextInt(7) - 3,
                        0,
                        level.random.nextInt(7) - 3
                )
        );

        commander.moveTo(
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F,
                0.0F
        );

        commander.assignToCommandCore(core.getBlockPos());
        level.addFreshEntity(commander);

        settlement.setCommanderUUID(commander.getUUID());

        StrategicCoreMessageBus.sendToOpenCore(
                level,
                core.getBlockPos(),
                Component.literal("Um Lorde Comandante assumiu o comando da guarnição da cidade.")
        );
    }

    @Nullable
    private static CityCommanderEntity resolveCommander(
            ServerLevel level,
            StrategicSettlementRecord settlement
    ) {
        UUID commanderUUID = settlement.getCommanderUUID();

        if (commanderUUID == null) {
            return null;
        }

        Entity entity = level.getEntity(commanderUUID);

        if (entity instanceof CityCommanderEntity commander && commander.isAlive()) {
            return commander;
        }

        return null;
    }

    // ------------------------------------------------------------------
    // Attack squads
    // ------------------------------------------------------------------

    /**
     * Mobilizes an attack squad out of the EXISTING garrison and marches it (behind the city's
     * commander) against {@code target}. Returns false when the city can't attack right now —
     * no commander, garrison too small, cooldown running, defensive posture or a squad already
     * in the field. The caller (strategic AI) only pays the attack's resource cost when this
     * returns true.
     */
    public static boolean tryLaunchAttack(
            ServerLevel level,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core,
            BlockPos target,
            int maxTroops
    ) {
        long gameTime = level.getGameTime();

        if (settlement.getAttackSquad() != null) {
            return false;
        }

        if (gameTime < settlement.getAttackCooldownUntilGameTime()) {
            return false;
        }

        if (gameTime < settlement.getDefensiveUntilGameTime()) {
            return false;
        }

        if (core.hasActiveOrkRaid()) {
            return false;
        }

        CityCommanderEntity commander = resolveCommander(level, settlement);

        if (commander == null) {
            return false;
        }

        List<PathfinderMob> garrison = collectGarrison(level, core.getBlockPos());

        int available = garrison.size() - HOME_GUARD_MINIMUM;

        if (available < MIN_ATTACK_TROOPS) {
            return false;
        }

        int squadSize = Math.min(maxTroops, available);

        garrison.sort((first, second) -> Double.compare(
                first.distanceToSqr(commander),
                second.distanceToSqr(commander)
        ));

        CitySquad squad = new CitySquad(CitySquadType.ATTACK, CitySquadOrder.FOLLOW_LEADER, target);

        for (int i = 0; i < squadSize; i++) {
            PathfinderMob member = garrison.get(i);

            squad.addMember(member.getUUID());
            squaddedUnits.add(member.getUUID());

            assignPost(member, commander.blockPosition());
        }

        squad.sealInitialSize();

        settlement.setAttackSquad(squad);
        settlement.setAttackCooldownUntilGameTime(gameTime + ATTACK_COOLDOWN_TICKS);

        squaddedUnits.add(commander.getUUID());
        assignPost(commander, target);

        StrategicCoreMessageBus.sendToOpenCore(
                level,
                core.getBlockPos(),
                Component.literal(
                        "O Lorde Comandante reuniu um esquadrão de "
                                + squadSize
                                + " tropas e marcha contra o alvo em "
                                + formatPos(target)
                                + "."
                )
        );

        return true;
    }

    private static void tickAttackSquad(
            ServerLevel level,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core,
            long gameTime
    ) {
        CitySquad squad = settlement.getAttackSquad();

        if (squad == null) {
            return;
        }

        List<PathfinderMob> members = resolveMembers(level, squad);

        for (PathfinderMob member : members) {
            squaddedUnits.add(member.getUUID());
        }

        CityCommanderEntity commander = resolveCommander(level, settlement);

        if (commander != null) {
            squaddedUnits.add(commander.getUUID());
        }

        // Rout: commander fell or the squad was mauled -> fall back, defensive posture window.
        if (commander == null || members.isEmpty() || squad.isMauled()) {
            squad.setOrder(CitySquadOrder.RETURN_TO_BASE);
            orderPostsTo(members, core.getBlockPos());

            settlement.setDefensiveUntilGameTime(gameTime + DEFEAT_DEFENSIVE_TICKS);
            settlement.setAttackSquad(null);

            WarDominionManager.shift(level, -2);

            StrategicCoreMessageBus.sendToOpenCore(
                    level,
                    core.getBlockPos(),
                    Component.literal(
                            "O esquadrão de ataque foi derrotado e recuou. A cidade entra em postura defensiva.")
            );

            return;
        }

        BlockPos target = squad.getTargetPos();

        // Victory: the target camp no longer exists (razed by the overrun check) -> march home.
        boolean targetGone = target == null
                || (level.isLoaded(target)
                        && !(level.getBlockEntity(target) instanceof OrkCampBlockEntity));

        if (targetGone) {
            squad.setOrder(CitySquadOrder.RETURN_TO_BASE);
            orderPostsTo(members, core.getBlockPos());
            assignPost(commander, core.getBlockPos());

            settlement.setAttackSquad(null);

            StrategicCoreMessageBus.sendToOpenCore(
                    level,
                    core.getBlockPos(),
                    Component.literal("O esquadrão de ataque cumpriu a missão e retorna à cidade.")
            );

            return;
        }

        double commanderToTargetSqr = commander.distanceToSqr(
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D
        );

        if (commanderToTargetSqr <= ENGAGE_DIST_SQR) {
            // Assault: everyone converges on the camp; combat goals take over on contact.
            squad.setOrder(CitySquadOrder.ATTACK_POSITION);

            assignPost(commander, target);
            orderPostsTo(members, target);

            return;
        }

        // March: members keep formation on the commander; if too dispersed the commander waits.
        boolean dispersed = false;

        for (PathfinderMob member : members) {
            if (member.distanceToSqr(commander) > REGROUP_DIST_SQR) {
                dispersed = true;
                break;
            }
        }

        squad.setOrder(dispersed ? CitySquadOrder.REGROUP : CitySquadOrder.FOLLOW_LEADER);

        assignPost(commander, dispersed ? commander.blockPosition() : target);
        orderPostsTo(members, commander.blockPosition());
    }

    // ------------------------------------------------------------------
    // Defense
    // ------------------------------------------------------------------

    /**
     * All-hands defense: the garrison (and any attack squad still in the field) is recalled to a
     * tight ring around the Command Core. Troops already fighting keep fighting — the guard post
     * only matters when they have no target.
     */
    private static void holdTheLine(
            ServerLevel level,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core
    ) {
        CitySquad attackSquad = settlement.getAttackSquad();

        if (attackSquad != null) {
            attackSquad.setOrder(CitySquadOrder.RETURN_TO_BASE);

            List<PathfinderMob> squadMembers = resolveMembers(level, attackSquad);
            orderPostsTo(squadMembers, core.getBlockPos());

            CityCommanderEntity commander = resolveCommander(level, settlement);

            if (commander != null) {
                assignPost(commander, core.getBlockPos());
            }

            settlement.setAttackSquad(null);
        }

        BlockPos corePos = core.getBlockPos();
        List<PathfinderMob> garrison = collectGarrison(level, corePos);

        int index = 0;

        for (PathfinderMob troop : garrison) {
            squaddedUnits.add(troop.getUUID());

            if (troop.getTarget() != null) {
                index++;
                continue;
            }

            double angle = (Math.PI * 2.0D / 8.0D) * (index % 8);

            BlockPos post = StrategicConstructionPlanner.ground(
                    level,
                    corePos.offset(
                            (int) Math.round(Math.cos(angle) * HOLD_RING_RADIUS),
                            0,
                            (int) Math.round(Math.sin(angle) * HOLD_RING_RADIUS)
                    )
            );

            assignPost(troop, post);
            index++;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static List<PathfinderMob> collectGarrison(ServerLevel level, BlockPos corePos) {
        AABB box = boxAround(corePos, GARRISON_RADIUS);

        List<PathfinderMob> garrison = new ArrayList<>();

        garrison.addAll(level.getEntitiesOfClass(
                GuardsmanEntity.class,
                box,
                guardsman -> guardsman.isAlive() && guardsman.isAssignedToCommandCore(corePos)
        ));

        garrison.addAll(level.getEntitiesOfClass(
                AbstractImperialTroopEntity.class,
                box,
                troop -> troop.isAlive()
                        && troop.isAssignedToCommandCore(corePos)
                        && !(troop instanceof CityCommanderEntity)
        ));

        return garrison;
    }

    private static List<PathfinderMob> resolveMembers(ServerLevel level, CitySquad squad) {
        List<PathfinderMob> members = new ArrayList<>();

        Iterator<UUID> iterator = squad.getMembers().iterator();

        while (iterator.hasNext()) {
            UUID memberId = iterator.next();
            Entity entity = level.getEntity(memberId);

            if (entity instanceof PathfinderMob mob && mob.isAlive()) {
                members.add(mob);
            } else {
                iterator.remove();
            }
        }

        return members;
    }

    private static void orderPostsTo(List<PathfinderMob> troops, BlockPos post) {
        for (PathfinderMob troop : troops) {
            if (troop.getTarget() != null) {
                continue;
            }

            assignPost(troop, post);
        }
    }

    private static void assignPost(PathfinderMob troop, BlockPos post) {
        if (troop instanceof GuardsmanEntity guardsman) {
            guardsman.assignGuardPost(post);
        } else if (troop instanceof AbstractImperialTroopEntity themed) {
            themed.assignGuardPost(post);
        }
    }

    private static AABB boxAround(BlockPos center, int radius) {
        return new AABB(
                center.getX() - radius,
                center.getY() - 48,
                center.getZ() - radius,
                center.getX() + radius,
                center.getY() + 80,
                center.getZ() + radius
        );
    }

    private static String formatPos(BlockPos pos) {
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }
}
