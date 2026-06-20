package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
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

    // Throttle for the "decisive counter-charge" announcement so it fires once per dispatch, not
    // every tick the city stays at critical threat.
    private static final String LAST_DISPATCH_TAG = "FirstCrusadeLastDispatch";
    private static final long DISPATCH_ANNOUNCE_COOLDOWN = 1200L; // ~1 minute

    // Personal army (retinue): the nearest Guardsmen who march and fight alongside the Primarch.
    private static final String RETINUE_TAG = "FirstCrusadeRetinueUntil";
    private static final int RETINUE_SIZE = 6;
    private static final double RETINUE_GATHER_RADIUS = 32.0D;
    private static final double RETINUE_FOLLOW_DISTANCE_SQR = 8.0D * 8.0D;
    private static final long RETINUE_DURATION_TICKS = 300L;

    private ImperialPrimarchManager() {
    }

    // Used by the patrol manager to leave the Primarch's retinue out of normal patrols.
    public static boolean isInRetinue(Entity entity, long gameTime) {
        return entity.getPersistentData().getLong(RETINUE_TAG) > gameTime;
    }

    // Each tick, the Primarch gathers his nearest Guardsmen as a personal army: freed from their
    // patrol posts, they follow him on the march and charge whatever he fights.
    private static void updatePersonalArmy(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core, PrimarchEntity primarch) {
        long expiry = serverLevel.getGameTime() + RETINUE_DURATION_TICKS;
        BlockPos corePos = core.getBlockPos();

        List<GuardsmanEntity> nearby = serverLevel.getEntitiesOfClass(
                GuardsmanEntity.class,
                primarch.getBoundingBox().inflate(RETINUE_GATHER_RADIUS),
                guardsman -> guardsman.isAlive() && guardsman.isAssignedToCommandCore(corePos)
        );

        nearby.sort(Comparator.comparingDouble(guardsman -> guardsman.distanceToSqr(primarch)));

        int count = Math.min(RETINUE_SIZE, nearby.size());
        LivingEntity primarchTarget = primarch.getTarget();

        for (int i = 0; i < count; i++) {
            GuardsmanEntity soldier = nearby.get(i);

            soldier.getPersistentData().putLong(RETINUE_TAG, expiry);
            soldier.clearGuardPost(); // released from patrol so it can follow the Primarch

            if (primarchTarget != null
                    && soldier.getTarget() == null
                    && FirstCrusadeFactionManager.canAttack(soldier, primarchTarget)) {
                soldier.setTarget(primarchTarget);
            } else if (primarchTarget == null
                    && soldier.getTarget() == null
                    && soldier.distanceToSqr(primarch) > RETINUE_FOLLOW_DISTANCE_SQR) {
                soldier.getNavigation().moveTo(primarch.getX(), primarch.getY(), primarch.getZ(), 1.1D);
            }
        }
    }

    public static void tickPrimarch(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        PrimarchEntity primarch = getPrimarch(serverLevel, core);

        if (primarch != null) {
            // A living Primarch personally steadies the city's defences...
            core.engineerRepair(1);
            // ...gathers his personal army...
            updatePersonalArmy(serverLevel, core, primarch);

            // ...and acts on the threat: at a critical assault he is dispatched to counter-charge the
            // enemy spearhead; while the city is safe he instead marches out to break the Ork camp.
            if (core.getLiveThreatLevel() >= ThreatAssessmentManager.LEVEL_CRITICAL) {
                leadCriticalCounterCharge(serverLevel, core, primarch);
            } else {
                leadSortieAgainstCamp(serverLevel, core, primarch);
            }
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

    // At a critical assault the Primarch is dispatched to take the fight to the enemy: he charges the
    // single most dangerous foe near the city (with his retinue, which follows his target), rather
    // than waiting for the horde to reach the Core. Announced once per dispatch.
    private static void leadCriticalCounterCharge(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core, PrimarchEntity primarch) {
        if (primarch.getTarget() != null) {
            return; // already locked onto a foe
        }

        Mob spearhead = ThreatAssessmentManager.findStrongestEnemy(
                serverLevel, core.getBlockPos(), core.getTerritoryRadius());

        if (spearhead == null || !FirstCrusadeFactionManager.canAttack(primarch, spearhead)) {
            return;
        }

        primarch.setTarget(spearhead);

        long now = serverLevel.getGameTime();

        if (primarch.getPersistentData().getLong(LAST_DISPATCH_TAG) + DISPATCH_ANNOUNCE_COOLDOWN < now) {
            primarch.getPersistentData().putLong(LAST_DISPATCH_TAG, now);
            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    core.getBlockPos(),
                    "The Primarch leads a decisive counter-charge against the foe!"
            );
        }
    }

    // While the city is safe, the Primarch marches on the Ork camp to break it at the source.
    // If a real threat is gathering at home (Alert level or higher), he stays to defend instead.
    private static void leadSortieAgainstCamp(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core, PrimarchEntity primarch) {
        if (primarch.getTarget() != null) {
            return;
        }

        if (core.hasActiveOrkRaid() || core.getLiveThreatLevel() >= ThreatAssessmentManager.LEVEL_ALERT) {
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
