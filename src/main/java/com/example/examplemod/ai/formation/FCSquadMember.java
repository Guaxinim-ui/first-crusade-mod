package com.example.examplemod.ai.formation;

import javax.annotation.Nullable;

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
}
