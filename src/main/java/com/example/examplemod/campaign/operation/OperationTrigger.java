package com.example.examplemod.campaign.operation;

/**
 * What actually advances an operation's progress.
 *
 * <h2>Why the trigger is a separate axis from the type</h2>
 *
 * An operation's <i>type</i> is what it is called and what it is worth. Its trigger is the event the
 * server can observe to know it happened. Those are not the same question, and conflating them is
 * how a mission system ends up with objectives nothing can ever complete.
 *
 * <p>Being explicit about it buys one important guarantee: {@link #MANUAL} types are never generated.
 * The roadmap asks for ten kinds of operation and this build can honestly resolve seven of them;
 * rather than shipping three that appear in the list and can never be finished, the three without a
 * source of progress declare {@code MANUAL} and the generator skips them. They are wired the moment
 * the thing they watch for exists — a convoy entity, a rescuable squad, a recoverable artefact.
 */
public enum OperationTrigger {
    /** An enemy dies on the front. Driven by {@code LivingDeathEvent}. */
    KILL_ENEMY,

    /** A named enemy leader dies on the front. Same event, narrower test. */
    KILL_LEADER,

    /** An Ork camp on the front is razed. Driven by the camp's own destruction path. */
    RAZE_CAMP,

    /** The target sector becomes Imperial. Checked on the strategic pass. */
    SECTOR_TAKEN,

    /** The target sector is still Imperial at the deadline. Checked when the clock runs out. */
    SECTOR_HELD,

    /** A player gets close to the target sector. Checked on the strategic pass. */
    VISIT_SECTOR,

    /** A player is present on the front as the clock runs. Checked on the strategic pass. */
    TIME_ON_FRONT,

    /**
     * Nothing observes this yet. Types declaring it are defined but never generated — see the class
     * note.
     */
    MANUAL;

    /** True for a trigger with a real source of progress, and therefore a generatable operation. */
    public boolean isWired() {
        return this != MANUAL;
    }

    /** True for a trigger the strategic pass has to poll, rather than one an event pushes. */
    public boolean isPolled() {
        return this == SECTOR_TAKEN || this == VISIT_SECTOR || this == TIME_ON_FRONT;
    }

    /** True for a trigger fed by {@code LivingDeathEvent}. */
    public boolean isDeathDriven() {
        return this == KILL_ENEMY || this == KILL_LEADER;
    }
}
