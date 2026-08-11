package com.example.examplemod.progression.ork;

import com.example.examplemod.AbstractImperialTroopEntity;
import com.example.examplemod.CustodesEntity;
import com.example.examplemod.GuardsmanEntity;
import com.example.examplemod.KasrkinEntity;
import com.example.examplemod.PrimarchEntity;
import com.example.examplemod.SisterOfBattleEntity;
import com.example.examplemod.SkitariiRangerEntity;
import com.example.examplemod.SpaceMarineEntity;
import com.example.examplemod.progression.PlayerProgressionCombat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * What a kill is worth to an Ork, and what does not count as one.
 *
 * <h2>The table is here, not in the event handler</h2>
 *
 * The design's list — Guardsman 3, Kasrkin 8, Space Marine 20 — is balance, and balance that lives
 * inside a {@code LivingDeathEvent} is balance nobody can find. The handler asks; this answers.
 *
 * <h2>What an Ork is not paid for</h2>
 *
 * Krumpagem is the answer to "has this git actually fought anyone", so anything that is not a fight
 * is worth nothing: his own side, his own summons, and — via the shared anti-farm window on the
 * outer profile — the same mob type over and over out of a spawner. That last check is deliberately
 * <b>the Imperial one</b>, not a copy: one window per player, whichever side he picked.
 */
public final class PlayerOrkProgressionCombat {
    private PlayerOrkProgressionCombat() {
    }

    /**
     * Krumpagem for putting this thing down.
     *
     * <p>Zero means "this was not a fight worth the name" and the caller awards nothing at all —
     * no Krump, no Fury, no tally.
     */
    public static int krumpFor(Entity victim) {
        if (victim == null) {
            return 0;
        }

        // Never his own side. A WAAAGH that pays for killing Boyz is a WAAAGH that eats itself.
        if (PlayerProgressionCombat.isOrk(victim)) {
            return 0;
        }

        if (victim instanceof PrimarchEntity || victim instanceof CustodesEntity) {
            return PlayerOrkProgressionBalance.KRUMP_SPACE_MARINE * 2;
        }
        if (victim instanceof SpaceMarineEntity) {
            return PlayerOrkProgressionBalance.KRUMP_SPACE_MARINE;
        }
        if (victim instanceof SisterOfBattleEntity) {
            return PlayerOrkProgressionBalance.KRUMP_SISTER;
        }
        if (victim instanceof KasrkinEntity || victim instanceof SkitariiRangerEntity) {
            return PlayerOrkProgressionBalance.KRUMP_ELITE_TROOP;
        }
        if (victim instanceof AbstractImperialTroopEntity) {
            return PlayerOrkProgressionBalance.KRUMP_SPECIALIST;
        }
        if (victim instanceof GuardsmanEntity) {
            return PlayerOrkProgressionBalance.KRUMP_GUARDSMAN;
        }

        // Anything else that fights back. Animals and villagers are worth nothing on purpose —
        // krumping a cow proves nothing about anybody.
        if (victim instanceof Enemy) {
            return PlayerOrkProgressionBalance.KRUMP_HOSTILE;
        }

        return 0;
    }

    /** The ones worth bragging about. Drives the elite gates on the evolution nodes. */
    public static boolean isElite(Entity victim) {
        return victim instanceof SpaceMarineEntity
                || victim instanceof CustodesEntity
                || victim instanceof PrimarchEntity
                || victim instanceof SisterOfBattleEntity
                || victim instanceof KasrkinEntity
                || victim instanceof SkitariiRangerEntity;
    }

    /** Anything wearing the Emperor's colours, elite or not. */
    public static boolean isImperial(Entity victim) {
        return victim instanceof GuardsmanEntity
                || victim instanceof AbstractImperialTroopEntity
                || victim instanceof SpaceMarineEntity
                || victim instanceof CustodesEntity
                || victim instanceof PrimarchEntity;
    }

    /** Fury for a kill: more for something that was actually dangerous. */
    public static int furyFor(LivingEntity victim) {
        return isElite(victim)
                ? PlayerOrkProgressionBalance.FURY_ON_ELITE_KILL
                : PlayerOrkProgressionBalance.FURY_ON_KILL;
    }

    // ==================================================================== what makes him angry

    /**
     * Whether hitting this is worth any Fury at all.
     *
     * <p>Deliberately the same question as {@link #krumpFor}: if a kill would be worth no Krumpagem
     * then a blow is worth no Fury either. One definition of "that was a fight" rather than two that
     * drift apart — and it closes all three of the holes the first version had at once, because
     * another Ork, a cow and an armour stand are all already worth zero here.
     *
     * <p>Without this an Ork could hold the bar at a hundred by punching a pig, and the shout — the
     * only thing Fury is for — would cost nothing but patience.
     */
    public static boolean isValidFuryTarget(Entity victim) {
        if (victim == null || !(victim instanceof LivingEntity living)) {
            return false;
        }

        if (!living.isAlive() || living.isInvulnerable()) {
            return false;
        }

        return krumpFor(victim) > 0;
    }

    /**
     * Whether taking this blow is worth any Fury.
     *
     * <p>Only damage from something living. An Ork gets angry at <i>someone</i>: a cactus, a lava
     * pool, a long fall and the bottom of the world are not enemies, and treating them as such made
     * standing in fire the cheapest way to fill the bar.
     *
     * <p>A greenskin attacker pays nothing either — two Ork players slapping each other must not be a
     * Fury generator.
     */
    public static boolean isValidFurySource(Entity attacker) {
        if (!(attacker instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }

        return !PlayerProgressionCombat.isOrk(attacker);
    }
}
