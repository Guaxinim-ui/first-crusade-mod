package com.example.examplemod.ai.formation;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.unit.profile.FCUnit;
import com.example.examplemod.unit.profile.UnitRole;

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

    private FCFormation formation = FCFormation.LINE;

    public FCSquad(Mob leader) {
        this.leader = leader;
    }

    public Mob getLeader() {
        return this.leader;
    }

    public FCFormation getFormation() {
        return this.formation;
    }

    public void setFormation(FCFormation formation) {
        this.formation = formation == null ? FCFormation.LINE : formation;
    }

    public List<Mob> getMembers() {
        return this.members;
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
     * Adds a unit if it is eligible and there is room.
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
        return true;
    }

    public void remove(Mob member) {
        this.members.remove(member);
    }

    /**
     * Drops members that are dead, removed, or too far to still count as part of the squad.
     *
     * <p>Runs on the leader's tick rather than each follower's, so the cost is once per squad
     * instead of once per trooper.</p>
     *
     * @param maxDistance how far a member may stray before it is considered lost
     */
    public void prune(double maxDistance) {
        double maxSqr = maxDistance * maxDistance;

        this.members.removeIf(member ->
                member == null
                        || !member.isAlive()
                        || member.isRemoved()
                        || member.level() != this.leader.level()
                        || member.distanceToSqr(this.leader) > maxSqr);
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
