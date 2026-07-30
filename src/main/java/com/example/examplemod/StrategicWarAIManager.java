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

public final class StrategicWarAIManager {
    private static final int MAX_PROJECTS_PER_CITY = 1;

    private static final int CITY_CAPTURE_RADIUS = 34;
    private static final int CITY_CAPTURE_VERTICAL_DOWN = 32;
    private static final int CITY_CAPTURE_VERTICAL_UP = 48;

    private static final int IMPERIAL_ATTACK_BASE_CHANCE = 3;
    private static final int ORK_EXPANSION_BASE_CHANCE = 4;

    private StrategicWarAIManager() {
    }

    public static void forceStrategicTick(ServerLevel level) {
        StrategicWarAIData data = StrategicWarAIData.get(level);
        WorldWarMapData warMap = WorldWarMapData.get(level);

        data.syncWithWorldMap(level, warMap);

        tickImperialCities(level, data, warMap);
        tickOrkCamps(level, data, warMap);
        tryResolveCityCaptures(level, data, warMap);

        data.setDirty();
    }

    private static void tickImperialCities(
            ServerLevel level,
            StrategicWarAIData data,
            WorldWarMapData warMap
    ) {
        List<StrategicSettlementRecord> cities = new ArrayList<>();

        for (StrategicSettlementRecord settlement : data.getImperialSettlements()) {
            cities.add(settlement);
        }

        for (StrategicSettlementRecord settlement : cities) {
            BlockPos corePos = settlement.getPos();
            BlockEntity blockEntity = level.getBlockEntity(corePos);

            if (!(blockEntity instanceof ImperialCommandCoreBlockEntity core)) {
                continue;
            }

            tickImperialCity(level, data, warMap, settlement, core);
        }
    }

    private static void tickImperialCity(
            ServerLevel level,
            StrategicWarAIData data,
            WorldWarMapData warMap,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core
    ) {
        settlement.generateImperialIncome(level, core);

        int threat = calculateThreatAgainstCity(level, warMap, core.getBlockPos());
        int dominion = WarDominionData.get(level).getDominion();

        settlement.choosePlan(threat, dominion);

        if (tryAdvanceAge(level, settlement, core)) {
            return;
        }

        if (data.countActiveProjectsForCity(core.getBlockPos()) < MAX_PROJECTS_PER_CITY) {
            StrategicConstructionType choice = chooseImperialConstruction(settlement, core, threat);

            if (choice != null) {
                boolean queued = StrategicConstructionBuilder.queueProject(
                        level,
                        data,
                        settlement,
                        core,
                        choice
                );

                if (queued) {
                    return;
                }
            }
        }

        maybeLaunchImperialAttack(level, warMap, settlement, core, threat);
    }

    private static boolean tryAdvanceAge(
            ServerLevel level,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core
    ) {
        if (!settlement.canAdvanceAge()) {
            return false;
        }

        boolean wantsTech =
                settlement.getPlan() == StrategicPlan.TECH_UP
                        || settlement.getAge().ordinal() <= StrategicAge.FORTIFIED_SETTLEMENT.ordinal()
                        || level.random.nextInt(3) == 0;

        if (!wantsTech) {
            return false;
        }

        StrategicAge oldAge = settlement.getAge();

        if (!settlement.advanceAge()) {
            return false;
        }

        ImperiumOverlordManager.contributeFromCity(level, core);
        WarDominionManager.shift(level, 5 + settlement.getAge().ordinal() * 2);

        StrategicCoreMessageBus.sendToOpenCore(
        level,
        core.getBlockPos(),
        Component.literal(
                "A cidade imperial evoluiu de "
                        + oldAge.getDisplayName()
                        + " para "
                        + settlement.getAge().getDisplayName()
                        + "."
        )
);

        return true;
    }

    private static StrategicConstructionType chooseImperialConstruction(
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core,
            int threat
    ) {
        StrategicAge age = settlement.getAge();
        StrategicPlan plan = settlement.getPlan();

        if (plan == StrategicPlan.SURVIVE || threat >= 18) {
            if (age.ordinal() >= StrategicAge.FORTIFIED_SETTLEMENT.ordinal()
                    && settlement.getBuiltCount(StrategicConstructionType.WALL_BASTION) < 2 + age.ordinal()) {
                return StrategicConstructionType.WALL_BASTION;
            }

            if (settlement.getBuiltCount(StrategicConstructionType.BARRACKS) < 1 + age.ordinal()) {
                return StrategicConstructionType.BARRACKS;
            }

            if (settlement.getBuiltCount(StrategicConstructionType.HABITATION) < 2 + age.ordinal()) {
                return StrategicConstructionType.HABITATION;
            }
        }

        if (plan == StrategicPlan.DEFENSE || threat >= 10) {
            if (age.ordinal() >= StrategicAge.FORTIFIED_SETTLEMENT.ordinal()
                    && settlement.getBuiltCount(StrategicConstructionType.WALL_BASTION) < 1 + age.ordinal()) {
                return StrategicConstructionType.WALL_BASTION;
            }

            if (settlement.getBuiltCount(StrategicConstructionType.BARRACKS) < 1 + age.ordinal()) {
                return StrategicConstructionType.BARRACKS;
            }
        }

        if (plan == StrategicPlan.MILITARY || plan == StrategicPlan.ATTACK) {
            if (settlement.getBuiltCount(StrategicConstructionType.BARRACKS) < 1 + age.ordinal()) {
                return StrategicConstructionType.BARRACKS;
            }

            if (age.ordinal() >= StrategicAge.ASTARTES_AGE.ordinal()
                    && settlement.getBuiltCount(StrategicConstructionType.COMMAND_BASTION) < 1) {
                return StrategicConstructionType.COMMAND_BASTION;
            }
        }

        if (settlement.getBuiltCount(StrategicConstructionType.HABITATION) < 2 + age.ordinal()) {
            return StrategicConstructionType.HABITATION;
        }

        // Economy structures are kept to ONE of each — they level up with the city instead of
        // multiplying (owner request: cleaner to test, the single work-site just produces more).
        if (settlement.getBuiltCount(StrategicConstructionType.FARM) < 1) {
            return StrategicConstructionType.FARM;
        }

        if (settlement.getBuiltCount(StrategicConstructionType.MINE) < 1) {
            return StrategicConstructionType.MINE;
        }

        if (settlement.getBuiltCount(StrategicConstructionType.SCRAP_YARD) < 1) {
            return StrategicConstructionType.SCRAP_YARD;
        }

        if (age.ordinal() >= StrategicAge.FORTIFIED_SETTLEMENT.ordinal()
                && settlement.getBuiltCount(StrategicConstructionType.REFINERY) < 1) {
            return StrategicConstructionType.REFINERY;
        }

        if (age.ordinal() >= StrategicAge.MANUFACTORUM_AGE.ordinal()
                && settlement.getBuiltCount(StrategicConstructionType.FORGE) < 1) {
            return StrategicConstructionType.FORGE;
        }

        if (age.ordinal() >= StrategicAge.MANUFACTORUM_AGE.ordinal()
                && settlement.getBuiltCount(StrategicConstructionType.GOLD_MINE) < 1) {
            return StrategicConstructionType.GOLD_MINE;
        }

        if (age.ordinal() >= StrategicAge.MANUFACTORUM_AGE.ordinal()
                && settlement.getBuiltCount(StrategicConstructionType.TRADE_DEPOT) < 1) {
            return StrategicConstructionType.TRADE_DEPOT;
        }

        if (settlement.getBuiltCount(StrategicConstructionType.BARRACKS) < 1 + age.ordinal()) {
            return StrategicConstructionType.BARRACKS;
        }

        return null;
    }

    private static void maybeLaunchImperialAttack(
            ServerLevel level,
            WorldWarMapData warMap,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core,
            int threat
    ) {
        if (settlement.getPlan() != StrategicPlan.ATTACK && threat > 8) {
            return;
        }

        int attackChance = Math.max(1, IMPERIAL_ATTACK_BASE_CHANCE - settlement.getAge().ordinal() / 2);

        if (level.random.nextInt(attackChance) != 0) {
            return;
        }

        BlockPos targetCamp = findNearestCamp(warMap, core.getBlockPos());

        if (targetCamp == null) {
            return;
        }

        int age = settlement.getAge().ordinal();

        int foodCost = 80 + age * 45;
        int ironCost = 65 + age * 40;
        int scrapCost = 45 + age * 25;
        int coalCost = 25 + age * 15;
        int promethiumCost = age >= 2 ? 25 + age * 10 : 0;
        int crusadiumCost = age >= 3 ? 20 : 0;

        boolean canPay = settlement.getResources().canAfford(
                foodCost,
                ironCost,
                scrapCost,
                coalCost,
                0,
                0,
                promethiumCost,
                0,
                crusadiumCost,
                0
        );

        if (!canPay) {
            return;
        }

        // The offensive is no longer conjured out of thin air: the city's EXISTING garrison is
        // mobilized into an attack squad led by the City Commander (the city empties out to attack
        // and must retrain, like the Ork war parties). Resources are only spent if the squad
        // actually formed — no commander / small garrison / cooldown means no attack this cycle.
        int maxTroops = 4 + age * 3;

        boolean launched = CityMilitaryManager.tryLaunchAttack(
                level,
                settlement,
                core,
                targetCamp,
                maxTroops
        );

        if (!launched) {
            return;
        }

        settlement.getResources().spend(
                foodCost,
                ironCost,
                scrapCost,
                coalCost,
                0,
                0,
                promethiumCost,
                0,
                crusadiumCost,
                0
        );

        WarDominionManager.shift(level, 3 + age);
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