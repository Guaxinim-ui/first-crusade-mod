package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The strategic layer, reduced to bookkeeping.
 *
 * <h2>What it stopped deciding</h2>
 *
 * This class used to run a city-building AI: every 100 ticks it chose what each Imperial settlement
 * should build next, queued the project, advanced the settlement's Age out of a resource bank, and
 * rolled for whether to march an attack squad at the nearest Ork camp. Every one of those is gone.
 * A base does not decide anything now — the player does, from the Core, and the Age is a plain
 * function of the Core's level written by {@link ImperialCommandCoreBlockEntity} at upgrade time.
 *
 * <h2>What it still does, and why</h2>
 *
 * The war map still has to agree with the world, the Ork side still runs itself, and a settlement
 * still draws its passive income. That is the whole of {@link #lightStrategicTick}, and it does no
 * per-base entity scan: the one place that needs entities (a city being overrun) is gated on the
 * cheap map question "is there even a camp near this city" before any box is queried.
 */
public final class StrategicWarAIManager {
    private static final int CITY_CAPTURE_RADIUS = 34;
    private static final int CITY_CAPTURE_VERTICAL_DOWN = 32;
    private static final int CITY_CAPTURE_VERTICAL_UP = 48;

    /** No camp within this many blocks of a city means nobody can be overrunning it. */
    private static final int CAPTURE_CHECK_CAMP_RANGE = 160;

    private static final int ORK_EXPANSION_BASE_CHANCE = 4;

    private StrategicWarAIManager() {
    }

    /**
     * The half-minute pass: sync the map, pay the settlements, tick the Ork side, resolve any city
     * actually under an Ork tide.
     */
    public static void lightStrategicTick(ServerLevel level) {
        StrategicWarAIData data = StrategicWarAIData.get(level);
        WorldWarMapData warMap = WorldWarMapData.get(level);

        data.syncWithWorldMap(level, warMap);

        tickImperialSettlements(level, data);
        tickOrkCamps(level, data, warMap);
        tryResolveCityCaptures(level, data, warMap);

        data.setDirty();
    }

    /**
     * Passive income for every loaded base, and nothing else.
     *
     * <p>No plan is chosen, no project is queued and no offensive is rolled for. The Age is not
     * touched here either: it is written by the Core when the player upgrades it, so a base that has
     * never been upgraded stays at OUTPOST no matter how long the world runs.
     */
    private static void tickImperialSettlements(ServerLevel level, StrategicWarAIData data) {
        List<StrategicSettlementRecord> settlements = new ArrayList<>();

        for (StrategicSettlementRecord settlement : data.getImperialSettlements()) {
            settlements.add(settlement);
        }

        for (StrategicSettlementRecord settlement : settlements) {
            BlockEntity blockEntity = level.getBlockEntity(settlement.getPos());

            if (!(blockEntity instanceof ImperialCommandCoreBlockEntity core)) {
                continue;
            }

            settlement.generateImperialIncome(level, core);
        }
    }

    private static void tickOrkCamps(
            ServerLevel level,
            StrategicWarAIData data,
            WorldWarMapData warMap
    ) {
        List<OrkStrategicRecord> camps = new ArrayList<>();

        for (OrkStrategicRecord camp : data.getOrkCamps()) {
            camps.add(camp);
        }

        for (OrkStrategicRecord record : camps) {
            BlockPos campPos = record.getPos();
            BlockEntity blockEntity = level.getBlockEntity(campPos);

            if (!(blockEntity instanceof OrkCampBlockEntity camp)) {
                continue;
            }

            tickOrkCamp(level, data, warMap, record, camp, campPos);
        }
    }

    private static void tickOrkCamp(
            ServerLevel level,
            StrategicWarAIData data,
            WorldWarMapData warMap,
            OrkStrategicRecord record,
            OrkCampBlockEntity camp,
            BlockPos campPos
    ) {
        record.generateOrkIncome(level);

        BlockPos nearestCity = findNearestCity(warMap, campPos);

        if (nearestCity == null) {
            return;
        }

        int tier = WaaaghOverlordManager.getTier(level);

        // Waves are disabled for now (owner request): the Ork city builds itself instead of attacking.
        if (ExampleMod.ORK_WAVES_ENABLED && record.getResources().get(StrategicResourceType.WAAAGH) >= 90) {
            launchOrkStrategicAttack(level, record, camp, campPos, nearestCity, tier);
        }

        maybeSpreadOrkCamp(level, warMap, record, campPos, nearestCity, tier);
    }

    private static void maybeSpreadOrkCamp(
            ServerLevel level,
            WorldWarMapData warMap,
            OrkStrategicRecord record,
            BlockPos campPos,
            BlockPos nearestCity,
            int tier
    ) {
        int maxExtraCampsNearCity = 2 + tier * 2;

        if (countCampsNear(warMap, nearestCity, 220) >= maxExtraCampsNearCity) {
            return;
        }

        if (record.getResources().get(StrategicResourceType.ORK_SCRAP) < 120) {
            return;
        }

        if (record.getResources().get(StrategicResourceType.TEEF) < 80) {
            return;
        }

        int chance = Math.max(1, ORK_EXPANSION_BASE_CHANCE - tier);

        if (level.random.nextInt(chance) != 0) {
            return;
        }

        record.getResources().remove(StrategicResourceType.ORK_SCRAP, 120);
        record.getResources().remove(StrategicResourceType.TEEF, 80);

        BlockPos child = OrkCampManager.seedSpreadCamp(level, campPos, nearestCity);

        if (child == null) {
            return;
        }

        WorldWarMapData.get(level).recordCamp(child);
        WarDominionManager.shift(level, -6);

        StrategicCoreMessageBus.sendToNearestOpenCore(
        level,
        child,
        Component.literal(
                "A WAAAGH! se espalhou. Um novo campo Ork nasceu em "
                        + formatPos(child)
                        + "."
        )
);
    }

    private static void launchOrkStrategicAttack(
            ServerLevel level,
            OrkStrategicRecord record,
            OrkCampBlockEntity camp,
            BlockPos campPos,
            BlockPos targetCity,
            int tier
    ) {
        record.getResources().remove(StrategicResourceType.WAAAGH, 90);

        int aggressionBonus = record.getGovernor().getAggression() / 25;

        int boyz = 5 + tier * 3 + aggressionBonus + camp.getClan().getBonusBoyz();
        int nobz = Math.max(1, tier + camp.getClan().getNobz());
        int gretchin = Math.max(0, 2 + tier + camp.getClan().getBonusGretchin());
        int meganobz = tier >= 2 ? 1 + tier / 2 : 0;
        int killaKans = tier >= 3 ? 1 : 0;

        for (int i = 0; i < boyz; i++) {
            spawnOrkMarcher(level, camp, campPos, targetCity, FCRegistry.ORK_BOY.get().create(level), 1.1D);
        }

        for (int i = 0; i < nobz; i++) {
            spawnOrkMarcher(level, camp, campPos, targetCity, FCRegistry.ORK_NOB.get().create(level), 1.05D);
        }

        for (int i = 0; i < gretchin; i++) {
            spawnOrkMarcher(level, camp, campPos, targetCity, FCRegistry.GRETCHIN.get().create(level), 1.15D);
        }

        for (int i = 0; i < meganobz; i++) {
            spawnOrkMarcher(level, camp, campPos, targetCity, FCRegistry.MEGANOB.get().create(level), 0.95D);
        }

        for (int i = 0; i < killaKans; i++) {
            spawnOrkMarcher(level, camp, campPos, targetCity, FCRegistry.KILLA_KAN.get().create(level), 0.85D);
        }

        WarDominionManager.shift(level, -5 - tier * 2);

        StrategicCoreMessageBus.sendToNearestOpenCore(
        level,
        targetCity,
        Component.literal(
                "Uma ofensiva Ork está vindo contra esta cidade imperial."
        )
);
    }

    private static void spawnOrkMarcher(
            ServerLevel level,
            OrkCampBlockEntity camp,
            BlockPos campPos,
            BlockPos targetCity,
            Mob mob,
            double speed
    ) {
        if (mob == null) {
            return;
        }

        BlockPos spawn = StrategicConstructionPlanner.ground(
                level,
                campPos.offset(
                        level.random.nextInt(9) - 4,
                        0,
                        level.random.nextInt(9) - 4
                )
        );

        mob.moveTo(
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F,
                0.0F
        );

        camp.getClan().applyTo(mob);
        mob.setPersistenceRequired();

        mob.getNavigation().moveTo(
                targetCity.getX() + 0.5D,
                targetCity.getY(),
                targetCity.getZ() + 0.5D,
                speed
        );

        level.addFreshEntity(mob);
    }

    /**
     * Hands a city to the Orks when a green tide is actually standing on it.
     *
     * <p>The entity scan is the expensive part, so it is gated twice: the city's chunk must be
     * loaded, and the war map must already say a camp is close enough for anyone to have walked
     * over. A base with no camp within {@value #CAPTURE_CHECK_CAMP_RANGE} blocks costs a handful of
     * long comparisons and no box query at all — which is the difference between this running for
     * every base every pass and running for the one that is under attack.
     */
    private static void tryResolveCityCaptures(
            ServerLevel level,
            StrategicWarAIData data,
            WorldWarMapData warMap
    ) {
        List<Long> capturedCities = new ArrayList<>();

        for (Object packedObject : new HashSet<>(warMap.getCities())) {
            if (!(packedObject instanceof Long packed)) {
                continue;
            }

            BlockPos cityPos = BlockPos.of(packed);

            if (countCampsNear(warMap, cityPos, CAPTURE_CHECK_CAMP_RANGE) <= 0) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(cityPos);

            if (!(blockEntity instanceof ImperialCommandCoreBlockEntity core)) {
                continue;
            }

            int orkStrength = countFactionStrengthNear(
                    level,
                    cityPos,
                    FirstCrusadeFaction.ORKS
            );

            int imperialStrength = countFactionStrengthNear(
                    level,
                    cityPos,
                    FirstCrusadeFaction.IMPERIUM
            );

            int defenseRequirement = 4 + core.getCityLevel() * 3;

            if (orkStrength >= imperialStrength + defenseRequirement) {
                capturedCities.add(packed);
            }
        }

        for (long packed : capturedCities) {
            captureCityForOrks(level, data, warMap, BlockPos.of(packed));
        }
    }

    private static void captureCityForOrks(
            ServerLevel level,
            StrategicWarAIData data,
            WorldWarMapData warMap,
            BlockPos cityPos
    ) {
        level.setBlock(cityPos, Blocks.AIR.defaultBlockState(), 3);

        warMap.removeCity(cityPos);
        data.removeImperial(cityPos);

        BlockPos campSite = StrategicConstructionPlanner.ground(
                level,
                cityPos.offset(
                        level.random.nextInt(25) - 12,
                        0,
                        level.random.nextInt(25) - 12
                )
        );

        BlockPos newCamp = OrkCampManager.seedWorldCamp(level, campSite, cityPos);

        if (newCamp != null) {
            warMap.recordCamp(newCamp);
        }

        // The ground has changed hands. Flag the fallen city's whole territory so the Imperial
        // grass, memorial blooms and thistle give way to trampled grass, fungus and gob moss.
        // Only the flora tag is ever removed — buildings and player blocks are untouched — and only
        // loaded chunks are queued now; the rest transform when they next load.
        com.example.examplemod.flora.runtime.FloraTransitionManager.onTerritoryCaptured(level, cityPos, 160);

        WarDominionManager.shift(level, -20);

        StrategicCoreMessageBus.sendToNearestOpenCore(
        level,
        cityPos,
        Component.literal(
                "A cidade imperial caiu para a WAAAGH!. A guerra está sendo perdida."
        )
);
    }

    private static int countFactionStrengthNear(
            ServerLevel level,
            BlockPos center,
            FirstCrusadeFaction faction
    ) {
        AABB box = boxAround(
                center,
                CITY_CAPTURE_RADIUS,
                CITY_CAPTURE_VERTICAL_DOWN,
                CITY_CAPTURE_VERTICAL_UP
        );

        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isAlive()
                        && FirstCrusadeFactionManager.getFaction(entity) == faction
        );

        int strength = 0;

        for (LivingEntity entity : entities) {
            if (entity instanceof ImperialCitizenEntity) {
                continue;
            }

            float maxHealth = entity.getMaxHealth();

            if (maxHealth >= 120.0F) {
                strength += 5;
            } else if (maxHealth >= 80.0F) {
                strength += 3;
            } else if (maxHealth >= 40.0F) {
                strength += 2;
            } else {
                strength += 1;
            }
        }

        return strength;
    }

    private static int calculateThreatAgainstCity(
            ServerLevel level,
            WorldWarMapData warMap,
            BlockPos cityPos
    ) {
        int threat = 0;

        for (Object packedObject : warMap.getCamps()) {
            if (!(packedObject instanceof Long packed)) {
                continue;
            }

            BlockPos campPos = BlockPos.of(packed);
            double distance = Math.sqrt(campPos.distSqr(cityPos));

            if (distance < 120) {
                threat += 12;
            } else if (distance < 240) {
                threat += 6;
            } else if (distance < 420) {
                threat += 3;
            }
        }

        AABB box = boxAround(cityPos, 96, 32, 64);

        List<LivingEntity> enemies = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isAlive()
                        && FirstCrusadeFactionManager.getFaction(entity) == FirstCrusadeFaction.ORKS
        );

        threat += enemies.size();

        return threat;
    }

    private static BlockPos findNearestCamp(WorldWarMapData warMap, BlockPos from) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Object packedObject : warMap.getCamps()) {
            if (!(packedObject instanceof Long packed)) {
                continue;
            }

            BlockPos pos = BlockPos.of(packed);
            double distance = pos.distSqr(from);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = pos;
            }
        }

        return nearest;
    }

    public static BlockPos findNearestCity(WorldWarMapData warMap, BlockPos from) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Object packedObject : warMap.getCities()) {
            if (!(packedObject instanceof Long packed)) {
                continue;
            }

            BlockPos pos = BlockPos.of(packed);
            double distance = pos.distSqr(from);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = pos;
            }
        }

        return nearest;
    }

    private static int countCampsNear(WorldWarMapData warMap, BlockPos center, int radius) {
        int count = 0;
        int radiusSqr = radius * radius;

        for (Object packedObject : warMap.getCamps()) {
            if (!(packedObject instanceof Long packed)) {
                continue;
            }

            BlockPos pos = BlockPos.of(packed);

            if (pos.distSqr(center) <= radiusSqr) {
                count++;
            }
        }

        return count;
    }

    public static List<Component> createStatusLines(ServerLevel level) {
        StrategicWarAIData data = StrategicWarAIData.get(level);
        WorldWarMapData warMap = WorldWarMapData.get(level);

        data.syncWithWorldMap(level, warMap);

        List<Component> lines = new ArrayList<>();

        lines.add(Component.literal("=== First Crusade - Guerra Estratégica ==="));
        lines.add(Component.literal("Domínio da guerra: " + WarDominionData.get(level).getDominion()));
        lines.add(Component.literal("Crusade Tier: " + ImperiumOverlordManager.getTier(level)));
        lines.add(Component.literal("WAAAGH! Tier: " + WaaaghOverlordManager.getTier(level)));
        lines.add(Component.literal("Cidades imperiais: " + data.getImperialSettlementCount()));
        lines.add(Component.literal("Campos Orks: " + data.getOrkCampCount()));
        lines.add(Component.literal("Obras ativas: " + data.getProjects().size()));

        for (StrategicSettlementRecord settlement : data.getImperialSettlements()) {
            lines.add(Component.literal(
                    "IMP "
                            + formatPos(settlement.getPos())
                            + " | " + settlement.getAge().getDisplayName()
                            + " | Governador: " + settlement.getGovernor().getName()
                            + " | Plano: " + settlement.getPlan().getDisplayName()
                            + " | " + settlement.getResources().shortText()
            ));

            CitySquad attackSquad = settlement.getAttackSquad();

            String squadText = attackSquad == null
                    ? "nenhum"
                    : attackSquad.getMembers().size()
                            + " tropas ("
                            + attackSquad.getOrder().getDisplayName()
                            + ")";

            lines.add(Component.literal(
                    "    Comandante: "
                            + (settlement.getCommanderUUID() != null ? "ativo" : "ausente")
                            + " | Esquadrão de ataque: "
                            + squadText
            ));
        }

        for (OrkStrategicRecord camp : data.getOrkCamps()) {
            lines.add(Component.literal(
                    "ORK "
                            + formatPos(camp.getPos())
                            + " | Chefe: " + camp.getGovernor().getName()
                            + " | " + camp.getResources().shortText()
            ));
        }

        return lines;
    }

    public static List<Component> createProjectLines(ServerLevel level) {
        StrategicWarAIData data = StrategicWarAIData.get(level);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("=== Obras Estratégicas ==="));

        if (data.getProjects().isEmpty()) {
            lines.add(Component.literal("Nenhuma obra ativa."));
            return lines;
        }

        for (StrategicConstructionProject project : data.getProjects()) {
            lines.add(Component.literal(
                    project.getType().getDisplayName()
                            + " | cidade "
                            + formatPos(project.getCorePos())
                            + " | obra "
                            + formatPos(project.getSitePos())
                            + " | progresso "
                            + project.getProgress()
                            + "/"
                            + project.getTotalBlocks()
            ));
        }

        return lines;
    }

    public static void reset(ServerLevel level) {
        StrategicWarAIData data = StrategicWarAIData.get(level);
        data.reset();
        data.syncWithWorldMap(level, WorldWarMapData.get(level));
        data.setDirty();
    }

    private static AABB boxAround(BlockPos center, int horizontal, int down, int up) {
        return new AABB(
                center.getX() - horizontal,
                center.getY() - down,
                center.getZ() - horizontal,
                center.getX() + horizontal,
                center.getY() + up,
                center.getZ() + horizontal
        );
    }

    private static String formatPos(BlockPos pos) {
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }
}