package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Keeps the city's workforce organized: releases workers whose work site no longer exists
// and automatically assigns idle citizens to vacant work sites. This is what makes the
// settlement feel alive and self-managing.
public class ImperialWorkforceManager {
    private static final int CITIZEN_SEARCH_RADIUS = 96;
    private static final int WORK_SITE_SCAN_RADIUS = 48;

    private ImperialWorkforceManager() {
    }

    public static void autoManageWorkforce(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                citizenSearchBox(corePos),
                citizen -> citizen.isAlive() && citizen.isAssignedToCommandCore(corePos)
        );

        // 1. Release workers whose work site no longer exists (self-healing workforce).
        for (ImperialCitizenEntity citizen : citizens) {
            if (isWorkSiteJob(citizen.getJob())
                    && !isWorkSiteValid(serverLevel, corePos, citizen.getJob(), citizen.getWorkSitePos())) {
                citizen.clearJob();
            }
        }

        // 2. Gather idle citizens and already-claimed work sites.
        List<ImperialCitizenEntity> idleCitizens = new ArrayList<>();
        Set<BlockPos> claimedSites = new HashSet<>();

        for (ImperialCitizenEntity citizen : citizens) {
            if (citizen.isUnemployed()) {
                idleCitizens.add(citizen);
            } else if (isWorkSiteJob(citizen.getJob()) && citizen.getWorkSitePos() != null) {
                claimedSites.add(citizen.getWorkSitePos().immutable());
            }
        }

        if (idleCitizens.isEmpty()) {
            return;
        }

        // 3. Fill vacant work sites with idle citizens.
        for (BlockPos pos : BlockPos.betweenClosed(
                corePos.offset(-WORK_SITE_SCAN_RADIUS, -16, -WORK_SITE_SCAN_RADIUS),
                corePos.offset(WORK_SITE_SCAN_RADIUS, 40, WORK_SITE_SCAN_RADIUS)
        )) {
            if (idleCitizens.isEmpty()) {
                break;
            }

            if (claimedSites.contains(pos)) {
                continue;
            }

            ImperialCitizenJob job = jobForWorkSite(serverLevel, pos, corePos);

            if (job == null) {
                continue;
            }

            BlockPos sitePos = pos.immutable();
            ImperialCitizenEntity worker = takeNearestCitizen(idleCitizens, sitePos);

            worker.assignToCommandCore(corePos);
            worker.assignJob(job, sitePos);

            claimedSites.add(sitePos);
        }
    }

    private static ImperialCitizenEntity takeNearestCitizen(List<ImperialCitizenEntity> citizens, BlockPos pos) {
        ImperialCitizenEntity nearest = null;
        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < citizens.size(); i++) {
            ImperialCitizenEntity citizen = citizens.get(i);
            double distance = citizen.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = citizen;
                nearestIndex = i;
            }
        }

        citizens.remove(nearestIndex);
        return nearest;
    }

    private static ImperialCitizenJob jobForWorkSite(ServerLevel serverLevel, BlockPos pos, BlockPos corePos) {
        BlockState state = serverLevel.getBlockState(pos);

        if (state.is(ExampleMod.IMPERIAL_MINE.get())) {
            return serverLevel.getBlockEntity(pos) instanceof ImperialMineBlockEntity mine
                    && mine.isAssignedToCommandCore(corePos) ? ImperialCitizenJob.MINER : null;
        }

        if (state.is(ExampleMod.IMPERIAL_GOLD_MINE.get())) {
            return serverLevel.getBlockEntity(pos) instanceof ImperialGoldMineBlockEntity goldMine
                    && goldMine.isAssignedToCommandCore(corePos) ? ImperialCitizenJob.GOLD_MINER : null;
        }

        if (state.is(ExampleMod.IMPERIAL_SCRAP_YARD.get())) {
            return serverLevel.getBlockEntity(pos) instanceof ImperialScrapYardBlockEntity scrapYard
                    && scrapYard.isAssignedToCommandCore(corePos) ? ImperialCitizenJob.SCRAPPER : null;
        }

        if (state.is(ExampleMod.IMPERIAL_FORGE.get())) {
            return serverLevel.getBlockEntity(pos) instanceof ImperialForgeBlockEntity forge
                    && forge.isAssignedToCommandCore(corePos) ? ImperialCitizenJob.SMITH : null;
        }

        if (state.is(ExampleMod.IMPERIAL_PROMETHIUM_REFINERY.get())) {
            return serverLevel.getBlockEntity(pos) instanceof ImperialPromethiumRefineryBlockEntity refinery
                    && refinery.isAssignedToCommandCore(corePos) ? ImperialCitizenJob.STOKER : null;
        }

        if (state.is(ExampleMod.IMPERIAL_FARM.get())) {
            return serverLevel.getBlockEntity(pos) instanceof ImperialFarmBlockEntity farm
                    && farm.isAssignedToCommandCore(corePos) ? ImperialCitizenJob.FARMER : null;
        }

        if (state.is(ExampleMod.IMPERIAL_EMERALD_TRADE_DEPOT.get())) {
            return serverLevel.getBlockEntity(pos) instanceof ImperialEmeraldTradeDepotBlockEntity depot
                    && depot.isAssignedToCommandCore(corePos) ? ImperialCitizenJob.TRADER : null;
        }

        return null;
    }

    private static boolean isWorkSiteValid(ServerLevel serverLevel, BlockPos corePos, ImperialCitizenJob job, BlockPos sitePos) {
        if (sitePos == null) {
            return false;
        }

        return jobForWorkSite(serverLevel, sitePos, corePos) == job;
    }

    private static boolean isWorkSiteJob(ImperialCitizenJob job) {
        return job == ImperialCitizenJob.MINER
                || job == ImperialCitizenJob.GOLD_MINER
                || job == ImperialCitizenJob.SCRAPPER
                || job == ImperialCitizenJob.SMITH
                || job == ImperialCitizenJob.STOKER
                || job == ImperialCitizenJob.FARMER
                || job == ImperialCitizenJob.TRADER;
    }

    private static AABB citizenSearchBox(BlockPos corePos) {
        return new AABB(
                corePos.getX() - CITIZEN_SEARCH_RADIUS,
                corePos.getY() - 32,
                corePos.getZ() - CITIZEN_SEARCH_RADIUS,
                corePos.getX() + CITIZEN_SEARCH_RADIUS,
                corePos.getY() + 64,
                corePos.getZ() + CITIZEN_SEARCH_RADIUS
        );
    }
}
