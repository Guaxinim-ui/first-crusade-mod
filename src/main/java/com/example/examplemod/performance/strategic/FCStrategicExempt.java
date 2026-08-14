package com.example.examplemod.performance.strategic;

/**
 * Marks a unit that must never be folded into a strategic force.
 *
 * <h2>Why a marker rather than a list of instanceof checks</h2>
 *
 * The alternative is a chain of {@code instanceof} in the absorption gate, which puts the knowledge
 * "this unit is irreplaceable" in a file that has nothing to do with the unit. Every hero added
 * later then needs somebody to remember to edit that chain, and the failure mode when they forget is
 * silent and permanent: the unit is absorbed and comes back as a default instance of its type.
 * Declaring it on the class itself means a new hero is protected by the same line of code that makes
 * it a hero.
 *
 * <h2>What being exempt costs</h2>
 *
 * An exempt unit stays a live entity when the battle around it is abstracted. That is the point —
 * {@link FCStrategicForce} keeps type, count, health and display name and discards everything else,
 * so anything carrying state that cannot be rebuilt from its constructor has to stay physical. The
 * price is that a hero standing in a far-away battle keeps ticking, which is exactly the trade the
 * brief asks for: a handful of entities is cheap, and losing a Primarch is not.
 *
 * <h2>What should implement this</h2>
 *
 * Unique and irreplaceable units: Primarchs, named characters, army and city commanders, mission
 * NPCs, and anything a player will eventually be able to equip or assign by hand. <b>Not</b> elite
 * line troops — Kasrkin, Custodes and ordinary Space Marines are numerous, interchangeable and
 * rebuilt correctly by their own constructors, so exempting them would only make large battles
 * expensive for no gain.
 */
public interface FCStrategicExempt {

    /**
     * Why this unit stays physical. Shown by the strategic debug command so that a player asking
     * "why did that battle not abstract" gets an answer instead of a silence.
     */
    default String strategicExemptReason() {
        return "unidade unica";
    }
}
