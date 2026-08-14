package com.example.examplemod.ai.formation;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Implemented by units that can be commanded by a squad leader.
 *
 * <p>This is intentionally tiny — a single nullable reference to whoever is leading. Everything
 * else about formation behaviour lives in {@link FCFormationGoal} and {@link FCSquad}, so adding
 * "this unit can be led" to a mob costs one field and three trivial methods.</p>
 *
 * <p>The leader reference is <em>not</em> saved to NBT, matching {@link FCSquad}: squads are
 * transient and re-form on their own after a reload.</p>
 */
public interface FCSquadMember {

    /** The mob currently leading this unit, or {@code null} if it is unattached. */
    @Nullable
    Mob getSquadLeader();

    /** Sets or clears this unit's leader. */
    void setSquadLeader(@Nullable Mob leader);

    /** Whether this unit is currently in someone's squad and that leader is still alive. */
    default boolean hasSquad() {
        Mob leader = getSquadLeader();
        return leader != null && leader.isAlive() && !leader.isRemoved();
    }

    /**
     * The enemy this unit's squad has settled on, or null if it has no squad or the squad has no
     * target.
     *
     * <p>A member that gets a target from here does not run a scan of its own at all, which is the
     * whole saving. Null means "decide for yourself", which is what an unattached soldier and a
     * squad with nothing in sight both need.
     */
    @Nullable
    default LivingEntity getSquadTarget() {
        if (!hasSquad()) {
            return null;
        }

        return getSquadLeader() instanceof FCSquadLeader leader
                ? leader.getSquad().getSharedTarget()
                : null;
    }
}
