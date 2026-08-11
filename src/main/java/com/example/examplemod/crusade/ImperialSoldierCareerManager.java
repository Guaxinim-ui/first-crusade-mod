package com.example.examplemod.crusade;

import java.util.UUID;

import com.example.examplemod.ImperialTroopGrade;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * The career: who gets promoted, and who is not allowed to be.
 *
 * <h2>Events only</h2>
 *
 * There is no tick here and no experience clock. A soldier's standing moves when something happens
 * to him — he kills something, he comes home from a raid, he dies — and between those moments this
 * class costs nothing at all. That is the brief's rule, and it is also why a base with eight idle
 * Guardsmen adds no load: eight idle Guardsmen generate no events.
 *
 * <h2>One gate, in one place</h2>
 *
 * Merit thresholds live in {@link ImperialSoldierBalance}; what a rank is worth in stats lives in
 * {@code GuardsmanRank}. This class owns the third question, which neither of those can answer: may
 * this particular soldier hold this rank <i>right now</i>. The answer for Sergeant is a quota — one
 * per {@link ImperialSoldierBalance#SOLDIERS_PER_SERGEANT} — because without it every survivor ends
 * up a Sergeant and the rank stops carrying information.
 */
public final class ImperialSoldierCareerManager {
    private ImperialSoldierCareerManager() {
    }

    // ==================================================================== enlistment

    /**
     * Puts a soldier on his base's roster, or finds the record he already has.
     *
     * <p>Called from the one chokepoint where a soldier becomes part of a base, so every spawn path
     * — founding garrison, reinforcement, migration of an old save, a soldier coming home from a
     * raid — enlists exactly once and never twice.
     */
    public static ImperialSoldierRecord enlist(ServerLevel level, BlockPos corePos, LivingEntity soldier) {
        if (corePos == null || soldier == null) {
            return null;
        }

        ImperialCrusadeData data = ImperialCrusadeData.get(level);
        ImperialSoldierRoster roster = data.roster(corePos);

        ImperialSoldierRecord record = roster.enlist(soldier.getUUID(), level.getGameTime());
        data.setDirty();

        return record;
    }

    /** The record for a soldier whose base is known. Null when he is nobody's — never created here. */
    public static ImperialSoldierRecord record(ServerLevel level, BlockPos corePos, UUID id) {
        ImperialSoldierRoster roster = ImperialCrusadeData.get(level).peek(corePos);
        return roster == null ? null : roster.record(id);
    }

    // ==================================================================== the events that matter

    /** A greenskin down. Returns the merit it was worth, for whoever owns the soldier's rank. */
    public static int recordKill(ServerLevel level, BlockPos corePos, LivingEntity soldier,
                                 LivingEntity victim) {
        int merit = meritFor(victim);

        ImperialSoldierRecord record = record(level, corePos, soldier.getUUID());
        if (record == null) {
            return merit;
        }

        if (com.example.examplemod.progression.PlayerProgressionCombat.isWarboss(victim)) {
            record.addWarbossAssist();
            record.addEliteKill();
        } else if (com.example.examplemod.progression.PlayerProgressionCombat.isEliteOrk(victim)) {
            record.addEliteKill();
        } else if (com.example.examplemod.progression.PlayerProgressionCombat.isOrk(victim)) {
            record.addOrkKill();
        }

        ImperialCrusadeData.get(level).setDirty();
        return merit;
    }

    /** A raid resolved. Called once per surviving soldier, never per tick of the raid. */
    public static void recordRaid(ServerLevel level, BlockPos corePos, UUID soldierId, boolean won) {
        ImperialSoldierRecord record = record(level, corePos, soldierId);
        if (record == null) {
            return;
        }

        record.addRaid(won);
        ImperialCrusadeData.get(level).setDirty();
    }

    /**
     * A soldier is dead, and stays dead.
     *
     * @return his record, so the caller can announce him, or {@code null} if he was nobody's
     */
    public static ImperialSoldierRecord recordDeath(ServerLevel level, BlockPos corePos, UUID soldierId,
                                                    String fate) {
        ImperialCrusadeData data = ImperialCrusadeData.get(level);
        ImperialSoldierRoster roster = data.peek(corePos);
        if (roster == null) {
            return null;
        }

        ImperialSoldierRecord record = roster.fall(soldierId, level.getGameTime(), fate);
        if (record != null) {
            data.setDirty();
        }

        return record;
    }

    // ==================================================================== promotion

    /**
     * Whether this soldier may be promoted into the given grade at this base right now.
     *
     * <p>{@link ImperialTroopGrade#VETERAN} is ungated: any soldier who has bled enough has earned
     * it, and a base of veterans is a base that has been through something. {@code SERGEANT} is the
     * quota. A penal regiment refuses both — the Imperium does not build careers out of condemned
     * men, and that is the one place the whole system switches off.
     */
    public static boolean mayPromoteTo(ServerLevel level, BlockPos corePos, ImperialTroopGrade grade) {
        ImperialSoldierRoster roster = ImperialCrusadeData.get(level).peek(corePos);
        if (roster == null) {
            // No roster means no base to hold a quota against. Let the rank table decide alone
            // rather than block a promotion on missing bookkeeping.
            return grade != ImperialTroopGrade.SERGEANT;
        }

        if (!roster.regiment().hasCareer()) {
            return false;
        }

        if (grade != ImperialTroopGrade.SERGEANT) {
            return true;
        }

        int garrison = roster.servingCount();
        if (garrison < ImperialSoldierBalance.MIN_GARRISON_FOR_SERGEANT) {
            return false;
        }

        int allowed = Math.max(1, garrison / ImperialSoldierBalance.SOLDIERS_PER_SERGEANT);
        return roster.countAtGrade(ImperialTroopGrade.SERGEANT) < allowed;
    }

    /** Writes a soldier's new grade onto his record. The entity's own rank is set by its owner. */
    public static void setGrade(ServerLevel level, BlockPos corePos, UUID soldierId,
                                ImperialTroopGrade grade) {
        ImperialSoldierRecord record = record(level, corePos, soldierId);
        if (record == null || record.grade() == grade) {
            return;
        }

        record.setGrade(grade);
        ImperialCrusadeData.get(level).setDirty();
    }

    // ==================================================================== merit table

    /**
     * What a kill is worth.
     *
     * <p>Reuses the progression's own idea of what an Ork is, so "elite" means the same thing to a
     * Guardsman's career as it does to the player's Blood Trial. Two tables would drift.
     */
    public static int meritFor(LivingEntity victim) {
        if (victim == null) {
            return 0;
        }

        if (com.example.examplemod.progression.PlayerProgressionCombat.isWarboss(victim)) {
            return ImperialSoldierBalance.MERIT_WARBOSS;
        }
        if (victim instanceof com.example.examplemod.MeganobEntity) {
            return ImperialSoldierBalance.MERIT_MEGANOB;
        }
        if (victim instanceof com.example.examplemod.KillaKanEntity) {
            return ImperialSoldierBalance.MERIT_KILLA_KAN;
        }
        if (victim instanceof com.example.examplemod.OrkNobEntity) {
            return ImperialSoldierBalance.MERIT_ORK_NOB;
        }
        if (victim instanceof com.example.examplemod.GretchinEntity) {
            return ImperialSoldierBalance.MERIT_GRETCHIN;
        }
        if (com.example.examplemod.progression.PlayerProgressionCombat.isOrk(victim)) {
            return ImperialSoldierBalance.MERIT_ORK_BOY;
        }

        return 0;
    }
}
