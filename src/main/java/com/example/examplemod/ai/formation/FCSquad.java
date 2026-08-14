package com.example.examplemod.ai.formation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.examplemod.performance.ai.FirstCrusadeAiLod;
import com.example.examplemod.unit.profile.FCUnit;
import com.example.examplemod.unit.profile.UnitRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The live membership of one squad: a leader and the units currently following it.
 *
 * <p>This is deliberately runtime-only state held on the leader, not saved to NBT. A squad is a
 * transient arrangement — troops die, get separated, wander out of range — and persisting it would
 * mean reconciling stale entity references on every world load. Instead the sergeant re-recruits
 * from whoever is nearby, which reforms the same squad within a second or two of a reload and
 * degrades gracefully when half of it is missing.</p>
 *
 * <h2>The two-sided membership invariant</h2>
 *
 * <p>Membership is stored twice: this roster, and a {@link FCSquadMember#getSquadLeader()} reference
 * on each follower. Both have to agree, and keeping them in agreement is the whole reason every
 * mutation lives in this class and {@link #getMembers()} hands out a read-only view.</p>
 *
 * <p>They used to disagree. Four different places dropped units from the roster — {@link #prune},
 * {@link #remove}, the leader goal's {@code stop()} and the sergeant's {@code die()} — and not one
 * of them told the soldier it had been dropped. The result was a <i>ghost</i>: a trooper whose
 * leader reference still pointed at a live sergeant, so {@link FCSquadMember#hasSquad()} answered
 * yes while the roster said no. A ghost is worse than a straggler, because every consequence
 * compounds:</p>
 *
 * <ul>
 *   <li>it keeps inheriting the squad's shared target, since that lookup goes through the leader
 *       and never consulted the roster;</li>
 *   <li>it keeps marching to a formation slot, because {@link FCFormationGoal} only asks
 *       {@code hasSquad()};</li>
 *   <li>{@link #slotOf} cannot find it, so it returns the <i>next free</i> slot index — the exact
 *       slot the next recruit will be given, putting two soldiers on one spot;</li>
 *   <li>it is invisible to {@link #size()} and {@link #isFull}, so the squad size limit stops
 *       meaning anything;</li>
 *   <li>and no other sergeant will ever take it, because recruitment refuses to poach a unit that
 *       claims to have a leader — while its own ex-leader has already pruned it for being too far
 *       away to re-recruit. A permanent dead zone.</li>
 * </ul>
 *
 * <p>The performance cost is the quiet one: a ghost falls back to scanning for its own target, so
 * the one-scan-per-squad saving leaks away as ghosts accumulate over a long battle.</p>
 *
 * <h2>Slot stability</h2>
 * <p>Followers keep their slot index for as long as they stay in the squad. If slots were
 * recalculated from a distance sort every tick, two troopers at similar distances would swap places
 * repeatedly and spend the whole battle walking past each other. Slots are only reassigned when a
 * member actually leaves.</p>
 */
public class FCSquad {

    /** The unit everyone is following. Must satisfy {@link FCUnit#canLead()}. */
    private final Mob leader;

    /** Followers in slot order: index in this list <em>is</em> the slot index within its rank. */
    private final List<Mob> members = new ArrayList<>();

    /**
     * Handed out by {@link #getMembers()}.
     *
     * <p>Read-only on purpose, and cached rather than wrapped per call so reading the roster costs
     * no allocation. Three of the four ways a soldier used to be orphaned were external code
     * reaching through the old mutable getter — {@code squad.getMembers().clear()} in two places.
     * With the view immutable those calls cannot be written, and the invariant holds by
     * construction instead of by everybody remembering.</p>
     */
    private final List<Mob> membersView = Collections.unmodifiableList(this.members);

    /**
     * A stable identity for this squad, independent of the leader's entity reference.
     *
     * <p>Object identity is enough while everything happens in one tick of one server, and stops
     * being enough the moment anything needs to name a squad: a debug command, an order issued from
     * elsewhere, staggered scheduling that has to spread squads deterministically, or a future
     * strategic layer that assigns objectives. Generated rather than derived from the leader's
     * entity id because entity ids are recycled, and a recycled id would silently transfer one
     * squad's orders to an unrelated one.
     */
    private final UUID id = UUID.randomUUID();

    private FCFormation formation = FCFormation.LINE;

    /** What the squad is doing. Drives how often the expensive decisions are re-made. */
    private FCSquadState state = FCSquadState.IDLE;

    /** What the squad has been told to do. FOLLOW is the pre-existing behaviour. */
    private FCSquadOrder order = FCSquadOrder.FOLLOW;

    /** Where the current order points, if it points anywhere. */
    private BlockPos destination;

    /**
     * The enemy the whole squad is shooting at, chosen once by the leader.
     *
     * <p>This is the point of item 18 of the performance brief. Before it, twenty riflemen each ran
     * their own role-weighted scan — twenty sweeps of the enemy list, twenty scorings, twenty
     * line-of-sight checks — to arrive at roughly the same answer. Now the sergeant scans and the
     * squad inherits, so a squad of twenty costs one scan instead of twenty.
     *
     * <p>It costs nothing extra to produce: the sergeant was already choosing a target for itself,
     * and publishing it is one assignment. And it improves the fighting as a side effect, because a
     * squad that shares a target concentrates its fire instead of spreading one wound across nine
     * different Orks.
     *
     * <p>Runtime only, like the roster. A target reference in NBT would be a stale entity id on the
     * next world load.
     */
    private LivingEntity sharedTarget;

    public FCSquad(Mob leader) {
        this.leader = leader;
    }

    /**
     * The squad's current target, or null when there is none worth inheriting.
     *
     * <p>Validated on read rather than on write: the target can die, be removed or change dimension
     * at any moment between the leader publishing it and a member asking for it.
     */
    public LivingEntity getSharedTarget() {
        if (this.sharedTarget == null
                || !this.sharedTarget.isAlive()
                || this.sharedTarget.isRemoved()
                || this.sharedTarget.level() != this.leader.level()) {
            this.sharedTarget = null;
        }

        return this.sharedTarget;
    }

    public void setSharedTarget(LivingEntity sharedTarget) {
        this.sharedTarget = sharedTarget;
    }

    public Mob getLeader() {
        return this.leader;
    }

    /** This squad's stable identity, independent of the leader's entity reference. */
    public UUID getId() {
        return this.id;
    }

    public FCSquadState getState() {
        return this.state;
    }

    public FCSquadOrder getOrder() {
        return this.order;
    }

    /** Issues an order. A null order means FOLLOW, which is "stay with the leader". */
    public void setOrder(FCSquadOrder order, @Nullable BlockPos destination) {
        this.order = order == null ? FCSquadOrder.FOLLOW : order;
        this.destination = destination;
    }

    /** Where the current order points, or null when it does not point anywhere. */
    @Nullable
    public BlockPos getDestination() {
        return this.destination;
    }

    /**
     * Re-reads what the squad is doing.
     *
     * <p>Kept here rather than in the goal for the same reason {@link #chooseFormation} is: it is a
     * decision about the squad, and a future order system needs one place to override it.
     *
     * @param inCombat  the leader has a live target
     * @param scattered at least one follower is a long way from where it should be
     */
    public void updateState(boolean inCombat, boolean scattered) {
        // An explicit order outranks the situation: a squad told to fall back is RETREATING even
        // while it is still being shot at, or it would never disengage.
        if (this.order == FCSquadOrder.RETREAT) {
            this.state = FCSquadState.RETREATING;
            return;
        }

        if (inCombat) {
            this.state = this.order == FCSquadOrder.DEFEND
                    ? FCSquadState.DEFENDING
                    : FCSquadState.ENGAGING;
            return;
        }

        if (scattered) {
            this.state = FCSquadState.REGROUPING;
            return;
        }

        // Movement is read from the leader rather than asked of it: a squad following a sergeant
        // who is walking somewhere is MOVING whether or not anybody gave an order.
        boolean leaderMoving = !this.leader.getNavigation().isDone()
                || this.leader.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3D;

        this.state = leaderMoving || this.order.needsDestination()
                ? FCSquadState.MOVING
                : FCSquadState.IDLE;
    }

    /**
     * Stretches a base interval by whichever throttle currently applies more strongly.
     *
     * <p><b>Maximum, not product.</b> Distance and idleness are two reasons to be cheap and they
     * overlap almost completely — a squad far from every player is usually also doing nothing.
     * Multiplying them compounds into intervals like 80x, where a squad stops reacting at all;
     * taking the larger keeps the strongest reason and stays bounded by it. Same rule, and same
     * reasoning, as the master-versus-channel density in the particle budget.
     */
    public int intervalFor(int baseTicks) {
        int byDistance = FirstCrusadeAiLod.forEntity(this.leader).intervalMultiplier();
        int byState = this.state.intervalScale();

        return Math.max(1, baseTicks * Math.max(byDistance, byState));
    }

    public FCFormation getFormation() {
        return this.formation;
    }

    public void setFormation(FCFormation formation) {
        this.formation = formation == null ? FCFormation.LINE : formation;
    }

    /** The roster, read-only. Join and leave through {@link #add}, {@link #remove}, {@link #prune}. */
    public List<Mob> getMembers() {
        return this.membersView;
    }

    public int size() {
        return this.members.size();
    }

    public boolean isFull(int maxFollowers) {
        return this.members.size() >= maxFollowers;
    }

    public boolean contains(Mob candidate) {
        return this.members.contains(candidate);
    }

    /**
     * Adds a unit if it is eligible and there is room, and points it back at this squad's leader.
     *
     * <p>Attaching here rather than at the call site is half of the invariant: a caller that adds
     * without attaching produces a member the squad thinks it has and the soldier does not.
     *
     * @return true if the unit joined
     */
    public boolean add(Mob candidate, int maxFollowers) {
        if (candidate == this.leader || contains(candidate) || isFull(maxFollowers)) {
            return false;
        }

        if (!(candidate instanceof FCUnit unit) || !unit.acceptsOrders()) {
            return false;
        }

        this.members.add(candidate);

        if (candidate instanceof FCSquadMember member) {
            member.setSquadLeader(this.leader);
        }

        return true;
    }

    /** Drops one unit and releases it, so it is free to be recruited by somebody else. */
    public void remove(Mob member) {
        if (this.members.remove(member)) {
            detach(member);
        }
    }

    /**
     * Drops members that are dead, removed, or too far to still count as part of the squad.
     *
     * <p>Runs on the leader's tick rather than each follower's, so the cost is once per squad
     * instead of once per trooper.</p>
     *
     * <p>An explicit iterator rather than {@code removeIf}: each departure has to release the
     * soldier as well as delete the entry, and a predicate that quietly mutates the thing it is
     * filtering is how the two halves drifted apart in the first place.</p>
     *
     * @param maxDistance how far a member may stray before it is considered lost
     */
    public void prune(double maxDistance) {
        double maxSqr = maxDistance * maxDistance;

        Iterator<Mob> iterator = this.members.iterator();
        while (iterator.hasNext()) {
            Mob member = iterator.next();

            boolean lost = member == null
                    || !member.isAlive()
                    || member.isRemoved()
                    || member.level() != this.leader.level()
                    || member.distanceToSqr(this.leader) > maxSqr;

            if (lost) {
                iterator.remove();
                detach(member);
            }
        }
    }

    /**
     * Dissolves the squad, releasing every follower.
     *
     * <p>This is what the leader's goal and the leader's death both need, and what
     * {@code getMembers().clear()} pretended to be at three separate call sites. Clearing the list
     * alone leaves every last follower a ghost.
     */
    public void disband() {
        for (Mob member : this.members) {
            detach(member);
        }

        this.members.clear();
        this.sharedTarget = null;
    }

    /**
     * Clears a departing unit's leader reference.
     *
     * <p>Only when it still points at <i>this</i> squad's leader. A unit that has already been
     * picked up by another sergeant must not have that new squad broken by the old one tidying up
     * after itself — an ordering that is easy to hit, because a soldier can be recruited elsewhere
     * on the same tick this squad decides it strayed too far.
     */
    private void detach(Mob member) {
        if (member instanceof FCSquadMember squadMember && squadMember.getSquadLeader() == this.leader) {
            squadMember.setSquadLeader(null);
        }
    }

    /**
     * The slot index of a member <em>within its own rank</em>.
     *
     * <p>Ranks are counted separately so that the third rifleman is front-rank slot 2 even if two
     * heavy gunners joined before it. Without this, a squad's front rank would develop gaps
     * wherever a support unit happened to be recruited early.</p>
     */
    public int slotOf(Mob member) {
        UnitRole.FormationSlot rank = rankOf(member);
        int slot = 0;

        for (Mob other : this.members) {
            if (other == member) {
                return slot;
            }

            if (rankOf(other) == rank) {
                slot++;
            }
        }

        return slot;
    }

    /** Which rank a unit belongs to, defaulting to the front for anything without a role. */
    public static UnitRole.FormationSlot rankOf(LivingEntity entity) {
        if (entity instanceof FCUnit unit) {
            return unit.getUnitRole().preferredFormationSlot();
        }

        return UnitRole.FormationSlot.FRONT;
    }

    /**
     * Picks the formation that suits the situation: a firing line when engaged, a column when
     * moving through tight space, dispersed when badly outnumbered.
     *
     * <p>Kept here rather than in the goal so a future order system (a player telling a sergeant
     * "form column") can override it in one place.</p>
     */
    public FCFormation chooseFormation(boolean inCombat, boolean confined, int enemyCount) {
        if (confined) {
            return FCFormation.COLUMN;
        }

        if (inCombat) {
            return enemyCount > this.members.size() + 2 ? FCFormation.DISPERSED : FCFormation.LINE;
        }

        return FCFormation.COLUMN;
    }
}
