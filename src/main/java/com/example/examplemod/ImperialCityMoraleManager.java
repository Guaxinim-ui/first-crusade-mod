package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Computes and applies the city's civilian morale (contentment), 0-100.
 *
 * Morale is derived each slow tick from housing, security and crowding, and the Core's
 * stored value eases toward that target so the city feels alive rather than snapping.
 * High morale boosts production and allows population growth; low morale chokes both.
 *
 * Food (Farm) is a planned input and is intentionally left as a future factor.
 */
public final class ImperialCityMoraleManager {
    public static final int MIN_MORALE = 0;
    public static final int MAX_MORALE = 100;
    public static final int DEFAULT_MORALE = 50;

    // Population growth stalls below this morale.
    private static final int GROWTH_MORALE_THRESHOLD = 35;

    // Each Habitation block comfortably houses this many citizens.
    private static final int CITIZENS_PER_HABITATION = 3;
    private static final int HABITATION_SCAN_RADIUS = 96;

    // How many morale points the stored value can move per slow tick toward the target.
    private static final int EASE_STEP = 2;

    private ImperialCityMoraleManager() {
    }

    public static void tickMorale(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        int target = computeTargetMorale(serverLevel, core);
        int current = core.getCityMorale();
        int next = easeToward(current, target);

        if (next != current) {
            core.setCityMorale(next);
        }
    }

    public static int computeTargetMorale(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        int population = ImperialPopulationManager.countAssignedCitizens(serverLevel, core);

        int morale = DEFAULT_MORALE;
        morale += housingFactor(serverLevel, core, population);
        morale += securityFactor(core);
        morale += crowdingFactor(core, population);

        // The mere presence of the Primarch inspires the whole settlement.
        if (ImperialPrimarchManager.isPrimarchPresent(serverLevel, core)) {
            morale += 15;
        }

        return clamp(morale);
    }

    // Housing: well-housed citizens are content; homeless citizens drag morale down hard.
    private static int housingFactor(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core, int population) {
        int housingCapacity = countHabitations(serverLevel, core) * CITIZENS_PER_HABITATION;

        if (population <= 0) {
            return 0;
        }

        if (housingCapacity >= population) {
            return 20;
        }

        int homeless = population - housingCapacity;
        double homelessRatio = (double) homeless / (double) population;

        return -(int) Math.round(homelessRatio * 30.0);
    }

    // Security: Core integrity and the absence of active raids reassure the populace.
    private static int securityFactor(ImperialCommandCoreBlockEntity core) {
        int factor = (core.getCityIntegrityValue() - 50) / 5; // -10 .. +10

        if (core.hasActiveOrkRaid()) {
            factor -= 15;
        }

        factor += Math.min(core.getOrkRaidVictoriesValue(), 5);

        return factor;
    }

    // Crowding: a settlement bursting at its population cap breeds discontent.
    private static int crowdingFactor(ImperialCommandCoreBlockEntity core, int population) {
        int capacity = ImperialPopulationManager.getCitizenCapacity(core);

        if (capacity <= 0) {
            return 0;
        }

        if (population >= capacity) {
            return -10;
        }

        if (population <= capacity / 2) {
            return 5;
        }

        return 0;
    }

    public static int countHabitations(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        BlockPos corePos = core.getBlockPos();
        int count = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                corePos.offset(-HABITATION_SCAN_RADIUS, -32, -HABITATION_SCAN_RADIUS),
                corePos.offset(HABITATION_SCAN_RADIUS, 64, HABITATION_SCAN_RADIUS)
        )) {
            if (serverLevel.getBlockState(pos).is(ExampleMod.IMPERIAL_HABITATION.get())) {
                count++;
            }
        }

        return count;
    }

    // Production multiplier: 0.5x at morale 0, 1.0x at 50, up to 1.25x at 100.
    public static double getProductionMultiplier(int morale) {
        int clamped = clamp(morale);

        if (clamped <= DEFAULT_MORALE) {
            return 0.5D + (clamped / (double) DEFAULT_MORALE) * 0.5D;
        }

        return 1.0D + ((clamped - DEFAULT_MORALE) / (double) DEFAULT_MORALE) * 0.25D;
    }

    public static boolean allowsGrowth(int morale) {
        return morale >= GROWTH_MORALE_THRESHOLD;
    }

    public static String getMoraleLabel(int morale) {
        if (morale >= 80) {
            return "Jubilant";
        }

        if (morale >= 60) {
            return "Content";
        }

        if (morale >= 40) {
            return "Uneasy";
        }

        if (morale >= 20) {
            return "Discontent";
        }

        return "Rebellious";
    }

    private static int easeToward(int current, int target) {
        if (current < target) {
            return Math.min(target, current + EASE_STEP);
        }

        if (current > target) {
            return Math.max(target, current - EASE_STEP);
        }

        return current;
    }

    public static int clamp(int morale) {
        return Math.max(MIN_MORALE, Math.min(MAX_MORALE, morale));
    }
}
