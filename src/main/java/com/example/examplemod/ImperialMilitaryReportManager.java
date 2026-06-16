package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ImperialMilitaryReportManager {
    private ImperialMilitaryReportManager() {
    }

    public static void showReport(Player player, ImperialCommandCoreBlockEntity commandCore) {
        if (!(commandCore.getLevel() instanceof ServerLevel serverLevel)) {
            showBasicReport(player, commandCore);
            return;
        }

        int guardsmen = countAssignedGuardsmen(serverLevel, commandCore);
        int spaceMarines = countAssignedSpaceMarines(serverLevel, commandCore);
        int totalDefenders = guardsmen + spaceMarines;

        int raidersNearby = OrkRaidManager.countRaidersInsideRadius(serverLevel, commandCore, 160);
        int hostileMobsNearby = countHostileMobs(serverLevel, commandCore, 96);

        player.displayClientMessage(Component.literal("===== Imperial Command Core ====="), false);
        player.displayClientMessage(Component.literal("Name: " + commandCore.getBaseName()), false);
        player.displayClientMessage(Component.literal("Owner: " + commandCore.getOwnerName()), false);
        player.displayClientMessage(Component.literal("Level: " + commandCore.getCityLevel()), false);
        player.displayClientMessage(Component.literal("Core Integrity: " + commandCore.getCityIntegrityValue() + "/100"), false);

        player.displayClientMessage(Component.literal("===== Resources ====="), false);
        player.displayClientMessage(Component.literal(
                "Stored: "
                        + commandCore.getIron() + " Iron, "
                        + commandCore.getScrapMetal() + " Scrap, "
                        + commandCore.getCoal() + " Coal"
        ), false);
        player.displayClientMessage(Component.literal("Storage Capacity: " + commandCore.getStorageCapacity()), false);
        player.displayClientMessage(Component.literal(
                "Daily Production: +"
                        + commandCore.getDailyIronProduction() + " Iron, +"
                        + commandCore.getDailyScrapProduction() + " Scrap, +"
                        + commandCore.getDailyCoalProduction() + " Coal"
        ), false);

        player.displayClientMessage(Component.literal("===== Gene Resources ====="), false);
        player.displayClientMessage(Component.literal(
                "Emperor Gene Seed: "
                        + commandCore.getEmperorGeneSeed()
                        + "/"
                        + commandCore.getEmperorGeneSeedCapacity()
        ), false);
        player.displayClientMessage(Component.literal(
                "Daily Gene Production: +" + commandCore.getDailyEmperorGeneProduction()
        ), false);

        player.displayClientMessage(Component.literal("===== Military ====="), false);
        player.displayClientMessage(Component.literal("Guardsmen: " + guardsmen), false);
        player.displayClientMessage(Component.literal("Space Marines: " + spaceMarines), false);
        player.displayClientMessage(Component.literal("Total Defenders: " + totalDefenders + "/" + commandCore.getMilitaryCapacity()), false);

        if (commandCore.hasPendingSpaceMarineCandidate()) {
            player.displayClientMessage(Component.literal("Space Marine Candidate: moving to Core"), false);
        } else {
            player.displayClientMessage(Component.literal("Space Marine Candidate: none"), false);
        }

        if (commandCore.getSpaceMarinePromotionCooldownSeconds() > 0) {
            player.displayClientMessage(Component.literal(
                    "Space Marine Ascension Cooldown: "
                            + commandCore.getSpaceMarinePromotionCooldownSeconds()
                            + " seconds"
            ), false);
        } else {
            player.displayClientMessage(Component.literal("Space Marine Ascension Cooldown: Ready"), false);
        }

        player.displayClientMessage(Component.literal("===== War Status ====="), false);
        player.displayClientMessage(Component.literal("Threat Level: " + commandCore.getThreatLevelNameForReport()), false);
        player.displayClientMessage(Component.literal("Threat Info: " + commandCore.getThreatLevelDescriptionForReport()), false);
        player.displayClientMessage(Component.literal("Ork Raids Triggered: " + commandCore.getOrkRaidCountValue()), false);
        player.displayClientMessage(Component.literal("Ork Raid Victories: " + commandCore.getOrkRaidVictoriesValue()), false);
        player.displayClientMessage(Component.literal("Imperial War Support: " + commandCore.getImperialWarSupportValue()), false);
        player.displayClientMessage(Component.literal("Raiders Nearby: " + raidersNearby), false);
        player.displayClientMessage(Component.literal("Other Hostile Mobs Nearby: " + hostileMobsNearby), false);

        if (commandCore.hasActiveOrkRaid()) {
            player.displayClientMessage(Component.literal(
                    "Raid Status: ACTIVE for "
                            + commandCore.getActiveOrkRaidSeconds()
                            + " seconds"
            ), false);
        } else {
            player.displayClientMessage(Component.literal("Raid Status: Safe"), false);
        }

        if (commandCore.getReinforcementCooldownSeconds() > 0) {
            player.displayClientMessage(Component.literal(
                    "Reinforcement Cooldown: "
                            + commandCore.getReinforcementCooldownSeconds()
                            + " seconds"
            ), false);
        } else {
            player.displayClientMessage(Component.literal("Reinforcement Cooldown: Ready"), false);
        }

        player.displayClientMessage(Component.literal("===== Commands ====="), false);
        player.displayClientMessage(Component.literal("Shift + Empty Hand: Recruit Guardsman"), false);
        player.displayClientMessage(Component.literal("Shift + Crusadium Plate: Upgrade City"), false);
        player.displayClientMessage(Component.literal("Crusadium Plate: Repair Core"), false);
        player.displayClientMessage(Component.literal("Rotten Flesh: Force Ork Raid Test"), false);
        player.displayClientMessage(Component.literal("Emerald: Call Reinforcements during Raid"), false);
        player.displayClientMessage(Component.literal("Iron Sword: Rally Defenders during Raid"), false);
        player.displayClientMessage(Component.literal("Shield: Fortify Defenders during Raid"), false);
    }

    private static void showBasicReport(Player player, ImperialCommandCoreBlockEntity commandCore) {
        player.displayClientMessage(Component.literal("===== Imperial Command Core ====="), false);
        player.displayClientMessage(Component.literal("Name: " + commandCore.getBaseName()), false);
        player.displayClientMessage(Component.literal("Owner: " + commandCore.getOwnerName()), false);
        player.displayClientMessage(Component.literal("Level: " + commandCore.getCityLevel()), false);
        player.displayClientMessage(Component.literal("Core Integrity: " + commandCore.getCityIntegrityValue() + "/100"), false);
    }

    private static int countAssignedGuardsmen(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();
        AABB searchBox = createSearchBox(corePos, 160);

        List<GuardsmanEntity> guardsmen = serverLevel.getEntitiesOfClass(
                GuardsmanEntity.class,
                searchBox,
                guardsman -> guardsman.isAlive()
                        && guardsman.isAssignedToCommandCore(corePos)
        );

        return guardsmen.size();
    }

    private static int countAssignedSpaceMarines(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();
        AABB searchBox = createSearchBox(corePos, 160);

        List<SpaceMarineEntity> spaceMarines = serverLevel.getEntitiesOfClass(
                SpaceMarineEntity.class,
                searchBox,
                spaceMarine -> spaceMarine.isAlive()
                        && spaceMarine.isAssignedToCommandCore(corePos)
        );

        return spaceMarines.size();
    }

    private static int countHostileMobs(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, int radius) {
        BlockPos corePos = commandCore.getBlockPos();
        AABB searchBox = createSearchBox(corePos, radius);

        List<Monster> monsters = serverLevel.getEntitiesOfClass(
                Monster.class,
                searchBox,
                monster -> monster.isAlive()
                        && !(monster instanceof OrkBoyEntity)
                        && !(monster instanceof OrkNobEntity)
        );

        return monsters.size();
    }

    private static AABB createSearchBox(BlockPos center, int radius) {
        return new AABB(
                center.getX() - radius,
                center.getY() - 64,
                center.getZ() - radius,
                center.getX() + radius,
                center.getY() + 96,
                center.getZ() + radius
        );
    }
}