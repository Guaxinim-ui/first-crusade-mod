package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Spawns the Adeptus Custodes — never recruited, only earned. A Custodian Guard manifests
 * when a settlement has reached its peak (level 5), already fields several Space Marines,
 * and holds a surplus of the Emperor's gene-seed. Each one costs a large amount of gene-seed,
 * so they remain vanishingly rare. They guard the Core and never leave its inner perimeter.
 */
public final class ImperialCustodesManager {
    private static final int MAX_CUSTODES = 2;
    private static final int REQUIRED_CITY_LEVEL = 5;
    private static final int REQUIRED_SPACE_MARINES = 3;
    private static final int GENE_SEED_COST = 10;
    private static final int SEARCH_RADIUS = 160;

    private ImperialCustodesManager() {
    }

    public static void tickCustodes(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        if (core.getCityLevel() < REQUIRED_CITY_LEVEL) {
            return;
        }

        if (core.getEmperorGeneSeed() < GENE_SEED_COST) {
            return;
        }

        if (countCustodes(serverLevel, core) >= MAX_CUSTODES) {
            return;
        }

        if (SpaceMarineUpgradeManager.countSpaceMarines(serverLevel, core) < REQUIRED_SPACE_MARINES) {
            return;
        }

        if (!core.consumeEmperorGeneSeed(GENE_SEED_COST)) {
            return;
        }

        spawnCustodes(serverLevel, core);
    }

    public static int countCustodes(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        BlockPos corePos = core.getBlockPos();

        List<CustodesEntity> custodes = serverLevel.getEntitiesOfClass(
                CustodesEntity.class,
                searchBox(corePos),
                custodian -> custodian.isAlive() && custodian.isAssignedToCommandCore(corePos)
        );

        return custodes.size();
    }

    private static void spawnCustodes(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        CustodesEntity custodes = FCRegistry.CUSTODES.get().create(serverLevel);

        if (custodes == null) {
            return;
        }

        BlockPos corePos = core.getBlockPos();
        BlockPos spawnPos = corePos.offset(0, 1, 2);

        custodes.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        custodes.assignToCommandCore(corePos);
        custodes.setHealth(custodes.getMaxHealth());

        serverLevel.addFreshEntity(custodes);

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                corePos,
                Component.translatable("msg.firstcrusade.bcast.custodes_risen")
        );
    }

    private static AABB searchBox(BlockPos corePos) {
        return new AABB(
                corePos.getX() - SEARCH_RADIUS,
                corePos.getY() - 64,
                corePos.getZ() - SEARCH_RADIUS,
                corePos.getX() + SEARCH_RADIUS,
                corePos.getY() + 96,
                corePos.getZ() + SEARCH_RADIUS
        );
    }
}
