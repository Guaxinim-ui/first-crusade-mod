package com.example.examplemod.performance.strategic;

import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;

import net.minecraft.world.entity.Mob;

/**
 * The single rule for whether a unit may be turned into a number.
 *
 * <p>One class, one question, so that every caller — absorption, the debug command, anything added
 * later — gets the same answer, and so that changing the rule is one edit rather than a hunt through
 * the codebase for the {@code instanceof} chain that drifted.
 *
 * <h2>What absorption actually destroys</h2>
 *
 * {@link FCStrategicForce} preserves entity type, unit count, average health and display name. It
 * does not preserve inventory, equipment, {@code getPersistentData()}, squad membership, guard-post
 * assignment or anything else the entity was carrying. A unit that comes back is the same
 * <i>kind</i> of unit, not the same individual. That is an acceptable trade for a line trooper whose
 * constructor rebuilds it exactly, and an unacceptable one for anything else — which is the whole
 * job of this class.
 *
 * <h2>The custom-name trap</h2>
 *
 * The obvious rule is "never absorb a named entity", and on this mod it is wrong. Fifteen or so
 * classes — AgriMilitia, Custodes, Enforcer, Feudal Knight, Jungle Fighter, Kasrkin, Penal
 * Legionnaire, Mine Guard and others — call {@code setCustomName} <i>in their constructor</i>, with
 * {@code setCustomNameVisible(true)}, using the name as a type label rather than as individuality.
 * An earlier version of the absorption gate excluded anything named on that reasonable-sounding
 * theory and silently excluded most of the army, so absorption did nothing at all.
 *
 * <p>And the two cases cannot be told apart after the fact: a constructor label and a player's name
 * tag leave an entity in an identical state. There is no flag that says which one happened. So
 * rather than guess, this exposes the choice as {@code strategic.absorbNamedUnits} and states the
 * consequence in the config comment. Units that are genuinely irreplaceable declare themselves
 * through {@link FCStrategicExempt} instead, which is a fact about the class and therefore knowable.
 */
public final class FCStrategicEligibility {

    private FCStrategicEligibility() {
    }

    /**
     * Whether one entity may be folded into a strategic force.
     *
     * <p>Deliberately <b>not</b> gated on {@code isPersistenceRequired()}. It reads like the right
     * check and is exactly wrong here: AbstractImperialTroopEntity, GuardsmanEntity, CustodesEntity
     * and the Ork camp all call {@code setPersistenceRequired()} in their constructors, so in this
     * mod the flag means "vanilla must not despawn me", which is every single unit this system
     * exists for. Gating on it made absorption silently do nothing. Absorption is not a despawn: the
     * unit is kept as data and comes back.
     */
    public static boolean canAbsorb(Mob mob) {
        return reason(mob) == null;
    }

    /**
     * Why this unit cannot be absorbed, or null when it can.
     *
     * <p>The reason is the useful half. A sweep that abstracts nothing has several possible causes,
     * and a debug command that answers "0" without saying which one costs more time than it saves.
     *
     * @return a short Portuguese phrase for the debug command, or null if the unit is eligible
     */
    public static String reason(Mob mob) {
        if (!mob.isAlive()) {
            return "morto";
        }

        // A rider and its mount have to travel together or neither makes sense on the way back.
        if (mob.isPassenger() || mob.isVehicle()) {
            return "montado ou carregando passageiro";
        }

        if (mob.isLeashed()) {
            return "preso por corda";
        }

        // Declared by the class, so a hero added later is protected without anybody remembering to
        // come back here.
        if (mob instanceof FCStrategicExempt exempt) {
            return exempt.strategicExemptReason();
        }

        if (mob.hasCustomName() && !FirstCrusadePerformanceConfig.absorbNamedUnits()) {
            return "nomeado (strategic.absorbNamedUnits=false)";
        }

        FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(mob);
        if (faction == FirstCrusadeFaction.NEUTRAL || faction == FirstCrusadeFaction.PLAYER) {
            return "faccao " + faction.name();
        }

        return null;
    }
}
