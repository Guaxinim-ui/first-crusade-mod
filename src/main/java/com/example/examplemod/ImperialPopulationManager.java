package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ImperialPopulationManager {
    private ImperialPopulationManager() {
    }

    public static boolean trainNearestCitizenAsGuardsman(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Player player) {
        ImperialCitizenEntity citizen = findNearestTrainableCitizen(serverLevel, commandCore, player);

        if (citizen == null) {
            player.displayClientMessage(Component.literal("No trainable Imperial Citizen found near the Command Core."), true);
            return false;
        }

        GuardsmanEntity guardsman = ExampleMod.GUARDSMAN.get().create(serverLevel);

        if (guardsman == null) {
            player.displayClientMessage(Component.literal("Failed to create Guardsman."), true);
            return false;
        }

        guardsman.moveTo(
                citizen.getX(),
                citizen.getY(),
                citizen.getZ(),
                citizen.getYRot(),
                citizen.getXRot()
        );

        guardsman.assignToCommandCore(commandCore.getBlockPos());
        guardsman.setCustomName(Component.literal("Imperial Guardsman"));

        citizen.discard();
        serverLevel.addFreshEntity(guardsman);

        player.displayClientMessage(Component.literal("Imperial Citizen trained into Guardsman."), false);
        return true;
    }

    public static int countAssignedCitizens(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive() && citizen.isAssignedToCommandCore(corePos)
        );

        return citizens.size();
    }

    public static ImperialCitizenEntity findNearestTrainableCitizen(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Player player) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.isReadyForTraining()
        );

        ImperialCitizenEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ImperialCitizenEntity citizen : citizens) {
            double distance = citizen.distanceToSqr(player);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = citizen;
            }
        }

        return nearest;
    }

    private static AABB createSearchBox(BlockPos center, int radius) {
        return new AABB(
                center.getX() - radius,
                center.getY() - 32,
                center.getZ() - radius,
                center.getX() + radius,
                center.getY() + 64,
                center.getZ() + radius
        );
    }
}