package com.example.examplemod.ai.formation;

/**
 * What a squad is doing right now, and therefore how hard it needs to think.
 *
 * <h2>State is a performance dial, not decoration</h2>
 *
 * A squad standing in a courtyard with no enemy in sight is asking the same expensive questions as
 * one that is being charged — where is the enemy, where should everyone stand, is anyone out of
 * position — and getting the same answer every time. The state is what lets those questions be asked
 * at a rate that matches how fast the answer can change. A firing line re-decides constantly; a
 * garrison does not need to re-decide four times a second that nothing is happening.
 *
 * <h2>How this combines with AI level of detail</h2>
 *
 * {@link com.example.examplemod.performance.ai.FirstCrusadeAiLod} already stretches intervals by
 * distance to the nearest player. Both throttles point the same way, and multiplying them is the
 * obvious mistake: STRATEGIC detail at 20x times {@link #IDLE} at 4x is 80x, which is a squad that
 * effectively stops existing. So the two are combined by <b>maximum, not product</b> — the throttle
 * is whichever reason to be cheap is stronger, never both stacked. That is the same rule the
 * particle budget uses for master and channel density, for the same reason.
 */
public enum FCSquadState {

    /** Nothing in sight and nowhere to be. The cheapest state, and the most common one. */
    IDLE(4),

    /** Marching to a destination. The formation matters, the enemy list does not yet. */
    MOVING(2),

    /** In contact. Everything runs at full rate — this is what the budget is being saved for. */
    ENGAGING(1),

    /** Holding ground against an expected attack: alert, but not yet spending like a firefight. */
    DEFENDING(1),

    /** Breaking off. The destination is shared, so nobody recalculates an attack of their own. */
    RETREATING(1),

    /** Scattered and reforming. Formation work is the priority; target scanning can wait. */
    REGROUPING(2);

    private final int intervalScale;

    FCSquadState(int intervalScale) {
        this.intervalScale = intervalScale;
    }

    /**
     * How much longer this state waits between expensive decisions. 1 is full rate.
     *
     * <p>Combine with the AI level-of-detail multiplier using {@code Math.max}, never by
     * multiplying. See the class comment.
     */
    public int intervalScale() {
        return this.intervalScale;
    }

    /** Whether the squad is currently in or expecting contact, and should not be throttled hard. */
    public boolean isFighting() {
        return this == ENGAGING || this == DEFENDING || this == RETREATING;
    }
}
