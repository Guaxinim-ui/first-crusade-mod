package com.example.examplemod.necron;

import net.minecraft.world.entity.LivingEntity;

/**
 * Who the tomb considers an enemy — which is everyone who is not the tomb.
 *
 * <h2>One predicate, three units</h2>
 *
 * Written once and shared rather than repeated in each entity's {@code registerGoals}. Three copies
 * of a targeting filter is three places for the Necrons to start fighting each other, and the bug it
 * produces (a phalanx that dissolves into a brawl the moment it spawns) reads as an AI failure
 * rather than as the typo it would be.
 *
 * <p>The living check delegates to {@link com.example.examplemod.FirstCrusadeFactionManager}, which
 * already sorts an unrecognised {@code Monster} into {@code HOSTILE} — so the Necrons are hostile to
 * the Imperium, the Orks and the player without a single new faction value, and a unit added to the
 * mod tomorrow becomes a valid target without anyone editing this file.
 */
public final class NecronTargets {

    private NecronTargets() {
    }

    /** True for anything a Necron should shoot at. */
    public static boolean isEnemyOfTheTomb(LivingEntity target) {
        return !isNecron(target);
    }

    /** True for the tomb's own. */
    public static boolean isNecron(LivingEntity entity) {
        return entity instanceof NecronWarriorEntity
                || entity instanceof NecronOverlordEntity
                || entity instanceof NecronScarabEntity;
    }
}
