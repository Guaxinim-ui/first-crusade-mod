package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

public final class StrategicConstructionBuilder {
    private static final int MAX_BUILDERS_PER_PROJECT = 3;
    private static final int BUILD_RADIUS = 8;
    private static final int BLOCKS_PER_BUILDER_PER_TICK = 2;

    private StrategicConstructionBuilder() {
    }

    public static void tickConstruction(ServerLevel level, StrategicWarAIData data) {
        List<StrategicConstructionProject> finishedProjects = new ArrayList<>();

        for (StrategicConstructionProject project : data.getProjects()) {
            BlockPos corePos = project.getCorePos();
            BlockPos sitePos = project.getSitePos();

            BlockEntity blockEntity = level.getBlockEntity(corePos);

            if (!(blockEntity instanceof ImperialCommandCoreBlockEntity core)) {
                finishedProjects.add(project);
                continue;
            }

            assignBuilders(level, core, project);

            int nearbyBuilders = countNearbyBuilders(level, corePos, sitePos);

            if (nearbyBuilders <= 0) {
                continue;
            }

            List<StrategicConstructionPlanner.ConstructionPlacement> placements =
                    StrategicConstructionPlanner.createPlacements(project.getType(), sitePos);

            project.setTotalBlocks(placements.size());

            int blocksThisCycle = Math.min(
                    8,
                    nearbyBuilders * BLOCKS_PER_BUILDER_PER_TICK
            );

            for (int i = 0; i < blocksThisCycle; i++) {
                if (project.isFinished()) {
                    break;
                }

                int index = project.getProgress();

                if (index < 0 || index >= placements.size()) {
                    break;
                }

                StrategicConstructionPlanner.ConstructionPlacement placement = placements.get(index);

                level.setBlock(placement.pos(), placement.state(), 3);
                project.addProgress(1);
            }

            if (project.isFinished()) {
                finishProject(level, data, core, project);
                finishedProjects.add(project);
            }
        }

        if (!finishedProjects.isEmpty()) {
            for (StrategicConstructionProject project : finishedProjects) {
                data.removeProject(project);
            }

            data.setDirty();
        }
    }

    public static boolean queueProject(
            ServerLevel level,
            StrategicWarAIData data,
            StrategicSettlementRecord settlement,
            ImperialCommandCoreBlockEntity core,
            StrategicConstructionType type
    ) {
        if (!type.isUnlocked(settlement.getAge())) {
            return false;
        }

        if (!settlement.getResources().canAfford(type)) {
            return false;
        }

        BlockPos site = StrategicConstructionPlanner.findConstructionSite(level, core, type, data);

        if (site == null) {
            return false;
        }

        int totalBlocks = StrategicConstructionPlanner.countBlocks(type);

        StrategicConstructionProject project = new StrategicConstructionProject(
                core.getBlockPos(),
                site,
                type,
                totalBlocks
        );

        settlement.getResources().spend(type);
        data.addProject(project);

        StrategicCoreMessageBus.sendToOpenCore(
        level,
        core.getBlockPos(),
        Component.literal(
                "Construtores imperiais iniciaram: "
                        + type.getDisplayName()
                        + " em "
                        + formatPos(site)
                        + "."
        )
);
    private static void finishProject(
            ServerLevel level,
            StrategicWarAIData data,
            ImperialCommandCoreBlockEntity core,
            StrategicConstructionProject project
    ) {
        assignCompletedBlockEntity(level, core.getBlockPos(), project.getSitePos(), project.getType());
        clearBuildersFromProject(level, core.getBlockPos(), project.getSitePos());

        StrategicSettlementRecord record = data.getOrCreateImperial(level, core.getBlockPos());
        record.addBuilt(project.getType());

        if (project.getType().isMilitary()) {
            WarDominionManager.shift(level, 4);
        }

        if (project.getType() == StrategicConstructionType.WALL_BASTION) {
            WarDominionManager.shift(level, 3);
        }

        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal(
                        "Obra concluída: "
                                + project.getType().getDisplayName()
                                + " em "
                                + formatPos(project.getSitePos())
                                + "."
                ),
                false
        );

        data.setDirty();
    }

    private static void assignCompletedBlockEntity(
            ServerLevel level,
            BlockPos corePos,
            BlockPos sitePos,
            StrategicConstructionType type
    ) {
        BlockEntity blockEntity = level.getBlockEntity(sitePos);

        if (blockEntity instanceof ImperialMineBlockEntity mine) {
            mine.assignToCommandCore(corePos);
            return;
        }

        if (blockEntity instanceof ImperialGoldMineBlockEntity goldMine) {
            goldMine.assignToCommandCore(corePos);
            return;
        }

        if (blockEntity instanceof ImperialScrapYardBlockEntity scrapYard) {
            scrapYard.assignToCommandCore(corePos);
            return;
        }

        if (blockEntity instanceof ImperialForgeBlockEntity forge) {
            forge.assignToCommandCore(corePos);
            return;
        }

        if (blockEntity instanceof ImperialPromethiumRefineryBlockEntity refinery) {
            refinery.assignToCommandCore(corePos);
            return;
        }

        if (blockEntity instanceof ImperialFarmBlockEntity farm) {
            farm.assignToCommandCore(corePos);
            return;
        }

        if (blockEntity instanceof ImperialEmeraldTradeDepotBlockEntity depot) {
            depot.assignToCommandCore(corePos);
            return;
        }

        if (blockEntity instanceof ImperialBarracksBlockEntity barracks) {
            barracks.assignToCommandCore(corePos);
            return;
        }

        if (blockEntity instanceof ImperialHabitationBlockEntity habitation) {
            habitation.assignToCommandCore(corePos);
        }
    }

    private static void assignBuilders(
            ServerLevel level,
            ImperialCommandCoreBlockEntity core,
            StrategicConstructionProject project
    ) {
        int currentBuilders = countAssignedBuilders(level, core.getBlockPos(), project.getSitePos());

        if (currentBuilders >= MAX_BUILDERS_PER_PROJECT) {
            return;
        }

        AABB searchBox = boxAround(core.getBlockPos(), 96, 32, 64);

        List<ImperialCitizenEntity> citizens = level.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                searchBox,
                citizen -> citizen.isAlive()
                        && !citizen.isChild()
                        && citizen.isAssignedToCommandCore(core.getBlockPos())
                        && (
                        citizen.isUnemployed()
                                || citizen.hasJob(ImperialCitizenJob.BUILDER)
                )
        );

        for (ImperialCitizenEntity citizen : citizens) {
            if (currentBuilders >= MAX_BUILDERS_PER_PROJECT) {
                break;
            }

            if (citizen.hasJob(ImperialCitizenJob.BUILDER)
                    && project.getSitePos().equals(citizen.getWorkSitePos())) {
                continue;
            }

            citizen.assignJob(ImperialCitizenJob.BUILDER, project.getSitePos());

            citizen.getNavigation().moveTo(
                    project.getSitePos().getX() + 0.5D,
                    project.getSitePos().getY(),
                    project.getSitePos().getZ() + 0.5D,
                    0.95D
            );

            currentBuilders++;
        }
    }

    private static int countAssignedBuilders(ServerLevel level, BlockPos corePos, BlockPos sitePos) {
        AABB searchBox = boxAround(corePos, 96, 32, 64);

        return level.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                searchBox,
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.hasJob(ImperialCitizenJob.BUILDER)
                        && sitePos.equals(citizen.getWorkSitePos())
        ).size();
    }

    private static int countNearbyBuilders(ServerLevel level, BlockPos corePos, BlockPos sitePos) {
        AABB searchBox = boxAround(sitePos, BUILD_RADIUS, 8, BUILD_RADIUS);

        return level.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                searchBox,
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.hasJob(ImperialCitizenJob.BUILDER)
                        && sitePos.equals(citizen.getWorkSitePos())
                        && citizen.distanceToSqr(
                        sitePos.getX() + 0.5D,
                        sitePos.getY() + 0.5D,
                        sitePos.getZ() + 0.5D
                ) <= 49.0D
        ).size();
    }

    private static void clearBuildersFromProject(ServerLevel level, BlockPos corePos, BlockPos sitePos) {
        AABB searchBox = boxAround(corePos, 96, 32, 64);

        List<ImperialCitizenEntity> builders = level.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                searchBox,
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.hasJob(ImperialCitizenJob.BUILDER)
                        && sitePos.equals(citizen.getWorkSitePos())
        );

        for (ImperialCitizenEntity builder : builders) {
            builder.clearJob();
        }
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