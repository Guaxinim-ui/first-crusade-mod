package com.example.examplemod.ai.combat;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.performance.ai.FirstCrusadeAiLod;
import com.example.examplemod.performance.ai.FirstCrusadeCombatantIndex;
import com.example.examplemod.unit.profile.UnitRole;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Chooses which enemy a unit should shoot at.
 *
 * <p>Vanilla's {@code NearestAttackableTargetGoal} picks whatever is closest, which makes squads
 * behave stupidly: a whole firing line will ignore the medic patching everyone up because a
 * rifleman is half a block nearer. This scorer instead weighs the target's {@link UnitRole} against
 * distance, so troops naturally shoot the leader and the medic first while still preferring the
 * enemy in their face over one across the field.</p>
 *
 * <h2>Scoring</h2>
 * <p>Score = {@code rolePriority × 10 − distance}. The 10:1 ratio was chosen so that a role step of
 * one (say medic at 6 versus rifleman at 2) is worth about forty blocks of distance — decisive at
 * normal engagement ranges, but never so extreme that a unit walks past an enemy stabbing it to
 * reach a distant medic. Anything already attacking us gets a flat bonus on top, because ignoring
 * the thing currently shooting you is the one behaviour players always read as broken.</p>
 *
 * <h2>Cost</h2>
 * <p>This is the most expensive thing a unit does, so it is <em>not</em> meant to run every tick.
 * {@link #shouldRescan} gates it behind the profile's {@code targetScanInterval}, offset by entity
 * id so that a hundred troopers spawned on the same tick do not all rescan on the same tick
 * forever.</p>
 */
public final class FCTargetPriority {

    /** Blocks of distance one point of role priority is worth. */
    private static final double ROLE_WEIGHT = 10.0D;

    /** Score bonus for an enemy that is currently targeting us. */
    private static final double RETALIATION_BONUS = 25.0D;

    /** Score bonus for an enemy we can actually see right now. */
    private static final double VISIBILITY_BONUS = 15.0D;

    private FCTargetPriority() {
    }

    /**
     * Whether a unit should re-run target selection this tick.
     *
     * <p>The {@code + entityId} term staggers the scans of units spawned together. Without it a
     * squad of twenty spawned in one tick would spike the server every {@code interval} ticks
     * forever; with it the same twenty spread their scans evenly across the window.</p>
     */
    public static boolean shouldRescan(Mob mob, int interval) {
        // Stretched by how far the unit is from anyone who could notice. A rifleman at the player's
        // elbow rescans on its profile's interval; the same rifleman in a battle a hundred and fifty
        // blocks away rescans a twentieth as often, and shoots exactly as hard.
        return shouldRescanScaled(mob, FirstCrusadeAiLod.scale(mob, interval));
    }

    /**
     * The same schedule, for a caller that has already worked out its own interval.
     *
     * <p>Exists so a squad leader can use {@code FCSquad#intervalFor}, which folds in the squad's
     * state as well as the distance to a player. Passing that result to {@link #shouldRescan} would
     * apply the level-of-detail multiplier a second time, and the compounded number is not a
     * slightly-too-slow scan — at STRATEGIC detail it is four hundred ticks, which is a sergeant who
     * looks for the enemy once every twenty seconds.
     *
     * @param scaledInterval ticks between scans, already stretched by whatever the caller wanted
     */
    public static boolean shouldRescanScaled(Mob mob, int scaledInterval) {
        if (scaledInterval <= 1) {
            return true;
        }

        // Safe here, unlike inside a Goal's canUse: this is called from customServerAiStep, which
        // vanilla runs on every tick, so no parity of tickCount can hide the zero case.
        return (mob.tickCount + mob.getId()) % scaledInterval == 0;
    }

    /**
     * Picks the best enemy for {@code hunter} within its profile's scan radius, or {@code null}.
     *
     * <p>Callers should gate this behind {@link #shouldRescan}.</p>
     */
    public static LivingEntity selectTarget(Mob hunter, double radius) {
        // The candidate list comes from the shared per-level index rather than a box query of this
        // unit's own: the scoring below is what makes this class worth its cost, and it was being
        // paid for twice — once to find who is nearby, once to rank them.
        List<LivingEntity> candidates =
                FirstCrusadeCombatantIndex.collectEnemies(hunter, radius, new ArrayList<>());

        if (candidates.isEmpty()) {
            return null;
        }

        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (LivingEntity candidate : candidates) {
            double score = score(hunter, candidate);

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    /**
     * How much {@code hunter} wants {@code candidate} dead. Higher is better; the units are
     * "blocks of distance", so the numbers stay intuitive to tune.
     */
    public static double score(Mob hunter, LivingEntity candidate) {
        double distance = hunter.distanceTo(candidate);
        double score = -distance;

        UnitRole role = FirstCrusadeFactionManager.getRole(candidate);

        if (role != null) {
            score += role.targetPriority() * ROLE_WEIGHT;
        } else {
            // Players and unmigrated mobs have no role. Treat them as line infantry so they are
            // neither ignored nor obsessively prioritised.
            score += UnitRole.LINE_INFANTRY.targetPriority() * ROLE_WEIGHT;
        }

        // Shoot back at whatever is shooting at us.
        if (candidate instanceof Mob candidateMob && candidateMob.getTarget() == hunter) {
            score += RETALIATION_BONUS;
        }

        if (hunter.getSensing().hasLineOfSight(candidate)) {
            score += VISIBILITY_BONUS;
        }

        // A wounded enemy is worth finishing: cheap way to make squads concentrate fire.
        float healthFraction = candidate.getHealth() / Math.max(1.0F, candidate.getMaxHealth());
        score += (1.0F - healthFraction) * 8.0D;

        return score;
    }

    /**
     * Whether a unit's existing target is still worth keeping. Used to avoid target-flapping, where
     * a unit switches every scan and never actually shoots anything.
     *
     * <p>A target is kept unless it is dead, out of range, or a markedly better option exists — the
     * {@code switchMargin} is what stops two equally-scored enemies from causing a stutter.</p>
     */
    public static boolean shouldKeepTarget(Mob hunter, LivingEntity current, LivingEntity alternative,
                                           double maxRange, double switchMargin) {
        if (current == null || !current.isAlive()) {
            return false;
        }

        if (!FirstCrusadeFactionManager.canAttack(hunter, current)) {
            return false;
        }

        if (hunter.distanceTo(current) > maxRange) {
            return false;
        }

        if (alternative == null || alternative == current) {
            return true;
        }

        return score(hunter, alternative) <= score(hunter, current) + switchMargin;
    }
}
