package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Governs the unique Primarch: his ascension, his passive governance of the city while he
 * lives, and the mourning that follows his death.
 *
 * Ascension is the costliest feat in the settlement — only a peak city that has bled for many
 * victories and hoarded gene-seed and Crusadium can call its demigod son into being. While he
 * lives he steadies the Core himself (slow repair); his battlefield leadership lives on the
 * entity. If he dies the city mourns (see {@link ImperialCommandCoreBlockEntity#onPrimarchDeath()}).
 */
public final class ImperialPrimarchManager {
    private static final int REQUIRED_CITY_LEVEL = 5;
    private static final int REQUIRED_RAID_VICTORIES = 5;
    private static final int GENE_SEED_COST = 15;
    private static final int CRUSADIUM_COST = 32;
    private static final int SEARCH_RADIUS = 192;

    private ImperialPrimarchManager() {
    }

    public static void tickPrimarch(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        PrimarchEntity primarch = getPrimarch(serverLevel, core);

        if (primarch != null) {
            // A living Primarch personally steadies the city's defences...
            core.engineerRepair(1);
            // ...and, when the city is safe, marches out to break the nearby Ork camp.
            leadSortieAgainstCamp(serverLevel, core, primarch);
            return;
        }

        if (core.getPrimarchMourningCooldownTicks() > 0) {
            return;
        }

        if (core.getCityLevel() < REQUIRED_CITY_LEVEL) {
            return;
        }

        if (core.getOrkRaidVictoriesValue() < REQUIRED_RAID_VICTORIES) {
            return;
        }

        if (core.getEmperorGeneSeed() < GENE_SEED_COST || core.getCrusadium() < CRUSADIUM_COST) {
            return;
        }

        if (!core.consumeEmperorGeneSeed(GENE_SEED_COST)) {
            return;
        }

        if (!core.consumeCrusadium(CRUSADIUM_COST)) {
            return;
        }

        spawnPrimarch(serverLevel, core);
    }

    public static boolean isPrimarchPresent(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        return getPrimarch(serverLevel, core) != null;
    }

    public static PrimarchEntity getPrimarch(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        BlockPos corePos = core.getBlockPos();

        List<PrimarchEntity> primarchs = serverLevel.getEntitiesOfClass(
                PrimarchEntity.class,
                searchBox(corePos),
                primarch -> primarch.isAlive() && primarch.isAssignedToCommandCore(corePos)
        );

        return primarchs.isEmpty() ? null : primarchs.get(0);
    }

    // While the city is safe, the Primarch marches on the Ork camp to break it at the source.
    private static void leadSortieAgainstCamp(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core, PrimarchEntity primarch) {
        if (core.hasActiveOrkRaid() || primarch.getTarget() != null) {
            return;
        }

        BlockPos campPos = core.getOrkCampPos();

        if (!OrkCampManager.isCampStillThere(serverLevel, campPos)) {
            return;
        }

        primarch.getNavigation().moveTo(
                campPos.getX() + 0.5D,
                campPos.getY(),
                campPos.getZ() + 0.5D,
                1.0D
        );
    }

    public static int countPrimarchs(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        BlockPos corePos = core.getBlockPos();

        List<PrimarchEntity> primarchs = serverLevel.getEntitiesOfClass(
                PrimarchEntity.class,
                searchBox(corePos),
                primarch -> primarch.isAlive() && primarch.isAssignedToCommandCore(corePos)
        );

        return primarchs.size();
    }

    private static void spawnPrimarch(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        PrimarchEntity primarch = ExampleMod.PRIMARCH.get().create(serverLevel);

        if (primarch == null) {
            return;
        }

        BlockPos corePos = core.getBlockPos();
        BlockPos spawnPos = corePos.offset(0, 1, 3);

        primarch.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        primarch.assignToCommandCore(corePos);
        primarch.setHealth(primarch.getMaxHealth());

        serverLevel.addFreshEntity(primarch);

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                corePos,
                "The Primarch has risen! The Emperor's son strides forth to lead the city."
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
