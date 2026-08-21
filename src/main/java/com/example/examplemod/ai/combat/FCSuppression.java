package com.example.examplemod.ai.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.example.examplemod.ai.formation.FCSquadMember;
import com.example.examplemod.unit.profile.FCUnit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Being shot at, as a number.
 *
 * <h2>No unit ticks for this</h2>
 *
 * The obvious design gives every soldier a suppression field that counts down each tick. Three
 * hundred soldiers in a battle is three hundred decrements a tick to move a number that only matters
 * when somebody reads it.
 *
 * <p>So suppression <b>decays lazily</b>. An entry stores the value and the game time it was set;
 * the current level is computed from the elapsed time at the moment it is asked for. Nothing ticks,
 * nothing is scheduled, and a unit nobody is asking about costs exactly nothing. The only periodic
 * work is {@link #sweep}, which drops entries that have decayed to zero, and it runs once every
 * thirty seconds over a map that is empty outside a firefight.
 *
 * <h2>Keyed by entity id, and deliberately not persisted</h2>
 *
 * Suppression is a state of mind that lasts seconds. A save that restored it would be restoring the
 * memory of gunfire that stopped before the server did. Entity id rather than UUID because the map
 * is per-run anyway and the id is what every event already has in hand.
 *
 * <h2>What it does</h2>
 *
 * <ul>
 *   <li><b>Accuracy.</b> {@link #accuracyPenalty} widens a shot's spread. Read by
 *       {@link FCRangedAttackGoal}.</li>
 *   <li><b>Advance.</b> Above {@link #PINNED_LEVEL} a unit stops closing on its target — it is
 *       pinned, which is what "moves less" means for a soldier under fire.</li>
 *   <li><b>Cover.</b> Above {@link #COVER_LEVEL} it looks for something to get behind. See
 *       {@link FCCoverGoal}.</li>
 *   <li><b>Nerve.</b> {@link #breaksNerve} pushes an already-hurt unit over its retreat threshold
 *       sooner.</li>
 * </ul>
 *
 * <h2>Leadership</h2>
 *
 * A Sergeant or a Nob with the unit takes the edge off — {@link #apply} scales incoming suppression
 * down when the unit's squad leader is alive and close. That is the whole of the brief's "Sergeant
 * can reduce this effect", and it costs one already-cached reference and one distance check, on an
 * event that only fires when somebody is actually being shot.
 */
public final class FCSuppression {

    private FCSuppression() {
    }

    /** Ceiling. A unit at 100 is as pinned as it gets. */
    public static final int MAX = 100;

    /** At or above this, a unit stops advancing on its target. */
    public static final int PINNED_LEVEL = 45;

    /** At or above this, a unit breaks off to look for cover. */
    public static final int COVER_LEVEL = 60;

    /** At or above this, a unit that is also hurt will fall back. */
    public static final int BREAK_LEVEL = 80;

    /** Points shed per second with nothing landing nearby. */
    private static final double DECAY_PER_SECOND = 8.0D;

    /** Suppression a direct hit inflicts on the target. */
    public static final int HIT_SUPPRESSION = 30;

    /** Suppression a hit inflicts on the target's neighbours — the "shots going past" half. */
    public static final int NEAR_MISS_SUPPRESSION = 12;

    /** How far the shock of a hit spreads to nearby friends. */
    public static final double SPREAD_RADIUS = 7.0D;

    /** How much a nearby squad leader cuts incoming suppression. */
    private static final double LEADER_MITIGATION = 0.45D;

    /** How close the leader has to be to steady anyone. */
    private static final double LEADER_RANGE = 14.0D;

    private record Entry(int level, long setAt) {
    }

    private static final Map<Integer, Entry> LEVELS = new HashMap<>();

    // ====================================================================================
    // Reading
    // ====================================================================================

    /** This unit's suppression right now, 0 to {@link #MAX}, decayed from when it was last set. */
    public static int level(LivingEntity entity) {
        Entry entry = LEVELS.get(entity.getId());

        if (entry == null) {
            return 0;
        }

        return decayed(entry, entity.level().getGameTime());
    }

    private static int decayed(Entry entry, long now) {
        long elapsed = Math.max(0L, now - entry.setAt());
        int lost = (int) (elapsed * DECAY_PER_SECOND / 20.0D);

        return Math.max(0, entry.level() - lost);
    }

    /** True when this unit should stop closing on its target. */
    public static boolean isPinned(LivingEntity entity) {
        return level(entity) >= PINNED_LEVEL;
    }

    /** True when this unit should be looking for something to get behind. */
    public static boolean wantsCover(LivingEntity entity) {
        return level(entity) >= COVER_LEVEL;
    }

    /**
     * Whether suppression is enough to break an already-shaken unit.
     *
     * <p>Takes the health fraction rather than deciding alone: suppression makes a hurt soldier run,
     * it does not make a fresh one run. A unit that fell back purely because it was shot at would
     * make a firing line impossible to hold with anybody.
     */
    public static boolean breaksNerve(LivingEntity entity, float healthFraction) {
        return level(entity) >= BREAK_LEVEL && healthFraction < 0.6F;
    }

    /**
     * Extra spread on a shot, in the same units the ranged goal's inaccuracy uses.
     *
     * <p>Linear in the suppression level, up to roughly tripling a steady unit's spread at full
     * suppression. Enough to matter, not enough to make a suppressed line stop being dangerous —
     * a firefight neither side can win by shooting is a firefight nobody will stand in.
     */
    public static float accuracyPenalty(LivingEntity entity) {
        return level(entity) * 0.06F;
    }

    // ====================================================================================
    // Writing
    // ====================================================================================

    /**
     * Adds suppression to a unit, softened by its leader.
     *
     * <p>Only combat units are tracked. A citizen, an animal or another mod's cow being shot is not
     * a soldier being pinned, and letting them into the map would fill it with entries nothing ever
     * reads.
     */
    public static void apply(LivingEntity entity, int amount) {
        if (amount <= 0 || !(entity instanceof FCUnit unit) || unit.getUnitRole().isNonCombatant()) {
            return;
        }

        int applied = steadiedByLeader(entity, amount);

        if (applied <= 0) {
            return;
        }

        long now = entity.level().getGameTime();
        int current = level(entity);

        LEVELS.put(entity.getId(), new Entry(Math.min(MAX, current + applied), now));
    }

    /**
     * A squad leader within {@link #LEADER_RANGE} cuts incoming suppression.
     *
     * <p>Reads the member's own cached leader reference rather than scanning for one: the squad
     * system already keeps it, so steadying a soldier costs a null check and a distance comparison.
     */
    private static int steadiedByLeader(LivingEntity entity, int amount) {
        if (!(entity instanceof FCSquadMember member) || !member.hasSquad()) {
            return amount;
        }

        Mob leader = member.getSquadLeader();

        if (leader == null || leader.distanceToSqr(entity) > LEADER_RANGE * LEADER_RANGE) {
            return amount;
        }

        return (int) Math.round(amount * (1.0D - LEADER_MITIGATION));
    }

    /**
     * Spreads the shock of a hit: the unit that was hit, and its neighbours who watched it happen.
     *
     * <p>One box query, only when a shot actually connects — never on a tick. This is the whole
     * input side of the system, and it is why suppression is free when nobody is fighting.
     */
    public static void applyHit(LivingEntity hit) {
        if (!(hit instanceof FCUnit)) {
            return;
        }

        apply(hit, HIT_SUPPRESSION);

        com.example.examplemod.FirstCrusadeFaction faction =
                com.example.examplemod.FirstCrusadeFactionManager.getFaction(hit);

        for (LivingEntity nearby : hit.level().getEntitiesOfClass(LivingEntity.class,
                hit.getBoundingBox().inflate(SPREAD_RADIUS),
                other -> other != hit && other instanceof FCUnit && other.isAlive())) {

            // Only the victim's own side flinches. A hit that suppressed the shooters standing
            // around their own casualty would mean a squad suppresses itself by winning.
            if (com.example.examplemod.FirstCrusadeFactionManager.getFaction(nearby) == faction) {
                apply(nearby, NEAR_MISS_SUPPRESSION);
            }
        }
    }

    /** Clears a unit's suppression outright — it died, or it was absorbed into a strategic force. */
    public static void clear(LivingEntity entity) {
        LEVELS.remove(entity.getId());
    }

    /**
     * Drops entries that have decayed to nothing.
     *
     * <p>Needed because entries are written on an event and never read again once the unit is out of
     * contact — without a sweep the map would grow for the lifetime of the server. Cheap: it walks a
     * map that only has entries for units that were shot at in the last dozen seconds.
     */
    public static void sweep(long gameTime) {
        Iterator<Map.Entry<Integer, Entry>> iterator = LEVELS.entrySet().iterator();

        while (iterator.hasNext()) {
            if (decayed(iterator.next().getValue(), gameTime) <= 0) {
                iterator.remove();
            }
        }
    }

    /** Forgets everything. Called when a server stops, so a second world starts clean. */
    public static void reset() {
        LEVELS.clear();
    }

    /** How many units are currently suppressed. For the performance debug command. */
    public static int trackedCount() {
        return LEVELS.size();
    }
}
