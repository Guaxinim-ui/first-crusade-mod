package com.example.examplemod.unit.profile;

import com.example.examplemod.FirstCrusadeFaction;

/**
 * Implemented by every mob that takes part in the war. This is the seam that lets the rest of the
 * mod ask "whose side are you on and what do you do?" without a chain of {@code instanceof} checks.
 *
 * <p>Before: {@code FirstCrusadeFactionManager.getFaction} tested twelve concrete classes in
 * sequence, and every new mob meant editing that method. After: a mob answers for itself, and the
 * manager just asks. Adding a Necron warrior touches exactly one file — the Necron warrior.</p>
 *
 * <p>The old {@code instanceof} path is still present in
 * {@link com.example.examplemod.FirstCrusadeFactionManager} as a fallback, so entities that have not been
 * migrated yet keep working unchanged. Once every mob implements this interface that fallback can
 * be deleted.</p>
 */
public interface FCUnit {

    /** The side this unit fights for. Must be stable for the unit's whole life. */
    FirstCrusadeFaction getUnitFaction();

    /** What this unit does on the battlefield. Drives targeting priority and formation placement. */
    UnitRole getUnitRole();

    /** The tunable numbers that shape this unit's combat behaviour. */
    FCCombatProfile getCombatProfile();

    /**
     * A short stable id used for assets and for {@code /summon}-adjacent tooling, e.g.
     * {@code "guardsman_rifleman"}. By convention this matches the GeckoLib asset triplet:
     * {@code geo/<id>.geo.json}, {@code textures/entity/<id>.png},
     * {@code animations/<id>.animation.json}.
     */
    String getUnitId();

    /**
     * Whether this unit may currently be given orders by a leader. Default is "yes if it is not a
     * leader itself and not a civilian" — a Sergeant does not take orders from another Sergeant.
     */
    default boolean acceptsOrders() {
        UnitRole role = getUnitRole();
        return !role.isLeader() && !role.isNonCombatant();
    }

    /** Whether this unit can anchor a formation. */
    default boolean canLead() {
        return getUnitRole().isLeader() && getCombatProfile().maxFollowers() > 0;
    }

    /**
     * Whether this unit should ever be shot at. Civilians and anything with a non-combatant role are
     * excluded from targeting entirely, which is what keeps Imperial guns off Imperial citizens.
     */
    default boolean isValidTarget() {
        return !getUnitRole().isNonCombatant();
    }
}
