package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Measures how dangerous the enemies around a settlement are, by quantity AND quality. Every
 * hostile unit contributes a weight based on what it is; the sum is the Threat Score, which maps
 * to a 0-4 Threat Level. This is the strategic core: leaders only march out, and overlords only
 * dispatch reinforcements, once the threat crosses a level (see the master design doc).
 */
public final class ThreatAssessmentManager {
    public static final int DEFAULT_RADIUS = 96;

    // Threat Levels.
    public static final int LEVEL_CALM = 0;
    public static final int LEVEL_VIGILANT = 1;
    public static final int LEVEL_ALERT = 2;
    public static final int LEVEL_SIEGE = 3;
    public static final int LEVEL_CRITICAL = 4;

    private ThreatAssessmentManager() {
    }

    public static int assessThreat(ServerLevel serverLevel, BlockPos centerPos, int radius) {
        AABB box = new AABB(
                centerPos.getX() - radius,
                centerPos.getY() - 64,
                centerPos.getZ() - radius,
                centerPos.getX() + radius,
                centerPos.getY() + 96,
                centerPos.getZ() + radius
        );

        List<Mob> enemies = serverLevel.getEntitiesOfClass(
                Mob.class,
                box,
                mob -> mob.isAlive() && isEnemyOfImperium(mob)
        );

        int score = 0;

        for (Mob enemy : enemies) {
            score += weightOf(enemy);
        }

        return score;
    }

    public static int threatLevel(int score) {
        if (score <= 0) {
            return LEVEL_CALM;
        }

        if (score < 10) {
            return LEVEL_VIGILANT;
        }

        if (score < 25) {
            return LEVEL_ALERT;
        }

        if (score < 50) {
            return LEVEL_SIEGE;
        }

        return LEVEL_CRITICAL;
    }

    public static String threatLevelName(int level) {
        return switch (level) {
            case LEVEL_VIGILANT -> "Vigilant";
            case LEVEL_ALERT -> "Alert";
            case LEVEL_SIEGE -> "Siege";
            case LEVEL_CRITICAL -> "Critical";
            default -> "Calm";
        };
    }

    private static boolean isEnemyOfImperium(Mob mob) {
        FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(mob);
        return faction == FirstCrusadeFaction.ORKS || faction == FirstCrusadeFaction.HOSTILE;
    }

    // Quality weight per unit. Expands as new units (Killa Kan, etc.) are added.
    private static int weightOf(Mob mob) {
        if (mob.getType() == ExampleMod.WARBOSS.get()) {
            return 30;
        }

        if (mob.getType() == ExampleMod.MEGANOB.get()) {
            return 12;
        }

        if (mob.getType() == ExampleMod.ORK_NOB.get()) {
            return 6;
        }

        if (mob.getType() == ExampleMod.ORK_BOY.get()) {
            return 3;
        }

        if (mob.getType() == ExampleMod.GRETCHIN.get()) {
            return 1;
        }

        // Generic hostile (zombies, etc.) count as minor pressure.
        return 2;
    }
}
