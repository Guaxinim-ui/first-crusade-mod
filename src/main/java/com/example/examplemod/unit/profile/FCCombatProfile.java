package com.example.examplemod.unit.profile;

/**
 * Every tunable number that shapes how a unit fights, in one immutable record.
 *
 * <p>The point is that {@link com.example.examplemod.ai.combat.FCRangedAttackGoal} and its melee
 * counterpart are written ONCE and behave completely differently depending on the profile handed to
 * them. A disciplined Guardsman and a berserk Ork Shoota Boy run the same goal class; what makes one
 * hold a firing line and the other charge while spraying is entirely in these fields.</p>
 *
 * <p>Build them with {@link #builder()}. Every field has a sane default, so a profile only states
 * what makes that unit unusual:</p>
 *
 * <pre>
 *   FCCombatProfile.builder()
 *       .range(18.0F).idealRange(12.0F).minimumRange(4.0F)
 *       .burst(3, 4, 35)
 *       .accuracy(0.92F)
 *       .courage(0.75F)
 *       .usesCover(true)
 *       .build();
 * </pre>
 *
 * <p>All distances are in blocks, all times in ticks (20/sec).</p>
 */
public record FCCombatProfile(
        // ---- Ranged engagement envelope ----
        /** Maximum distance at which the unit will open fire. Beyond this it advances instead. */
        float range,
        /** The distance the unit TRIES to hold. Closer and it backs off, further and it advances. */
        float idealRange,
        /** Inside this distance the unit stops shooting and switches to melee (0 = never switch). */
        float minimumRange,

        // ---- Rate of fire ----
        /** Shots fired back-to-back before the unit pauses. 1 = single shots. */
        int burstSize,
        /** Ticks between individual shots INSIDE a burst. */
        int burstShotInterval,
        /** Ticks of pause between bursts — this is the real rate-of-fire knob. */
        int burstCooldown,
        /** Ticks spent raising the weapon before the first shot of an engagement. */
        int drawTicks,
        /** Ticks spent settling the aim after the weapon is up, before firing. */
        int aimTicks,
        /** Ticks the recoil pose is held after each shot. */
        int recoilTicks,
        /** Ticks a reload takes, played after {@link #shotsPerMagazine} shots. 0 = never reload. */
        int reloadTicks,
        /** Shots before a reload is required. 0 = unlimited. */
        int shotsPerMagazine,

        // ---- Quality of fire ----
        /**
         * 0..1. Fed to the projectile's inaccuracy term — 1.0 is a laser, 0.5 sprays wildly.
         * A Guardsman sits near 0.9; an Ork near 0.55.
         */
        float accuracy,
        /**
         * Whether the unit may fire while walking. Heavy weapons set this false and must halt;
         * Orks set it true and advance while spraying.
         */
        boolean canFireOnTheMove,
        /** Movement speed multiplier applied while firing on the move (ignored if the above is false). */
        double firingMoveSpeed,

        // ---- Movement ----
        /** Speed multiplier when closing on a target. */
        double chaseSpeed,
        /** Speed multiplier when backing away from a too-close enemy. */
        double retreatSpeed,

        // ---- Nerve ----
        /**
         * 0..1. Below roughly 0.3 a unit breaks and runs when hurt or outnumbered; above 0.8 it
         * essentially never retreats. Orks sit high, Grots sit very low.
         */
        float courage,
        /**
         * Health fraction (0..1) below which the unit considers retreating at all. Combined with
         * {@link #courage}: a brave unit with a low threshold fights nearly to the death.
         */
        float retreatHealthThreshold,
        /** Whether the unit actively seeks cover blocks when under fire. */
        boolean usesCover,
        /** Whether the unit backs off when an enemy closes inside {@link #minimumRange}. */
        boolean backsAwayFromMelee,

        // ---- Melee ----
        /** Reach in blocks for melee swings. */
        float meleeReach,
        /** Ticks between melee swings. */
        int meleeCooldown,
        /** Ticks between heavy melee swings; 0 disables heavy attacks entirely. */
        int heavyAttackCooldown,
        /** Chance (0..1) that any given swing becomes a heavy attack. */
        float heavyAttackChance,

        // ---- Perception ----
        /** How often (ticks) the unit re-runs target selection. Higher = cheaper, less responsive. */
        int targetScanInterval,
        /** Radius in blocks searched for targets. Kept small on purpose; this is the main AI cost. */
        double targetScanRadius,
        /** Ticks a unit keeps chasing a target it can no longer see before giving up. */
        int targetMemoryTicks,

        // ---- Leadership ----
        /** Max units that may follow this one in formation. 0 = cannot lead. */
        int maxFollowers,
        /** How far a follower may drift from its formation slot before correcting. */
        double formationTolerance
) {

    /** Sensible mid-line-infantry defaults; the builder starts from these. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience: how many ticks a whole burst cycle occupies, useful for animation length checks
     * and for the goal's internal bookkeeping.
     */
    public int fullBurstDurationTicks() {
        return Math.max(1, this.burstSize) * Math.max(1, this.burstShotInterval) + this.burstCooldown;
    }

    /** True when this profile describes a unit that can shoot at all. */
    public boolean isRanged() {
        return this.range > 0.0F && this.burstSize > 0;
    }

    /** True when the unit is capable of, and willing to, fight hand to hand. */
    public boolean isMelee() {
        return this.meleeReach > 0.0F;
    }

    /**
     * Whether the unit should consider retreating right now.
     *
     * @param healthFraction current health / max health, 0..1
     * @param nearbyEnemies  enemies within a short radius
     * @param nearbyAllies   allies within the same radius
     */
    public boolean shouldRetreat(float healthFraction, int nearbyEnemies, int nearbyAllies) {
        if (this.courage >= 0.95F) {
            return false;
        }

        if (healthFraction > this.retreatHealthThreshold) {
            return false;
        }

        // Being outnumbered erodes nerve; being supported restores it.
        float pressure = nearbyEnemies - (nearbyAllies * 0.5F);

        if (pressure <= 0.0F) {
            return false;
        }

        // Courage 0 breaks under any pressure; courage 0.9 needs to be badly swamped.
        return pressure > this.courage * 6.0F;
    }

    public static final class Builder {
        private float range = 16.0F;
        private float idealRange = 10.0F;
        private float minimumRange = 3.0F;

        private int burstSize = 1;
        private int burstShotInterval = 4;
        private int burstCooldown = 30;
        private int drawTicks = 20;
        private int aimTicks = 10;
        private int recoilTicks = 5;
        private int reloadTicks = 0;
        private int shotsPerMagazine = 0;

        private float accuracy = 0.85F;
        private boolean canFireOnTheMove = false;
        private double firingMoveSpeed = 0.6D;

        private double chaseSpeed = 1.0D;
        private double retreatSpeed = 1.15D;

        private float courage = 0.6F;
        private float retreatHealthThreshold = 0.35F;
        private boolean usesCover = false;
        private boolean backsAwayFromMelee = true;

        private float meleeReach = 2.5F;
        private int meleeCooldown = 20;
        private int heavyAttackCooldown = 0;
        private float heavyAttackChance = 0.0F;

        private int targetScanInterval = 20;
        private double targetScanRadius = 24.0D;
        private int targetMemoryTicks = 100;

        private int maxFollowers = 0;
        private double formationTolerance = 2.0D;

        private Builder() {
        }

        /** Ranged envelope: max engagement range, preferred standoff, melee-switch threshold. */
        public Builder range(float range, float idealRange, float minimumRange) {
            this.range = range;
            this.idealRange = idealRange;
            this.minimumRange = minimumRange;
            return this;
        }

        public Builder range(float range) {
            this.range = range;
            return this;
        }

        public Builder idealRange(float idealRange) {
            this.idealRange = idealRange;
            return this;
        }

        public Builder minimumRange(float minimumRange) {
            this.minimumRange = minimumRange;
            return this;
        }

        /** Rate of fire: shots per burst, ticks between them, ticks of pause after. */
        public Builder burst(int burstSize, int burstShotInterval, int burstCooldown) {
            this.burstSize = burstSize;
            this.burstShotInterval = burstShotInterval;
            this.burstCooldown = burstCooldown;
            return this;
        }

        /** Animation timing for the shoot cycle. */
        public Builder shootTiming(int drawTicks, int aimTicks, int recoilTicks) {
            this.drawTicks = drawTicks;
            this.aimTicks = aimTicks;
            this.recoilTicks = recoilTicks;
            return this;
        }

        public Builder reload(int shotsPerMagazine, int reloadTicks) {
            this.shotsPerMagazine = shotsPerMagazine;
            this.reloadTicks = reloadTicks;
            return this;
        }

        public Builder accuracy(float accuracy) {
            this.accuracy = accuracy;
            return this;
        }

        /** Lets the unit shoot while walking, at the given speed multiplier. */
        public Builder fireOnTheMove(double firingMoveSpeed) {
            this.canFireOnTheMove = true;
            this.firingMoveSpeed = firingMoveSpeed;
            return this;
        }

        /** Forces the unit to halt before firing (heavy weapons). */
        public Builder mustHaltToFire() {
            this.canFireOnTheMove = false;
            return this;
        }

        public Builder speeds(double chaseSpeed, double retreatSpeed) {
            this.chaseSpeed = chaseSpeed;
            this.retreatSpeed = retreatSpeed;
            return this;
        }

        public Builder courage(float courage) {
            this.courage = courage;
            return this;
        }

        public Builder retreatHealthThreshold(float retreatHealthThreshold) {
            this.retreatHealthThreshold = retreatHealthThreshold;
            return this;
        }

        public Builder usesCover(boolean usesCover) {
            this.usesCover = usesCover;
            return this;
        }

        public Builder backsAwayFromMelee(boolean backsAwayFromMelee) {
            this.backsAwayFromMelee = backsAwayFromMelee;
            return this;
        }

        /** Melee capability: reach, swing cooldown. */
        public Builder melee(float meleeReach, int meleeCooldown) {
            this.meleeReach = meleeReach;
            this.meleeCooldown = meleeCooldown;
            return this;
        }

        /** Adds heavy attacks on top of normal swings. */
        public Builder heavyAttack(int heavyAttackCooldown, float heavyAttackChance) {
            this.heavyAttackCooldown = heavyAttackCooldown;
            this.heavyAttackChance = heavyAttackChance;
            return this;
        }

        /** Disables melee entirely (pure shooters, non-combatants). */
        public Builder noMelee() {
            this.meleeReach = 0.0F;
            return this;
        }

        /** Perception cost/responsiveness tradeoff. */
        public Builder perception(int targetScanInterval, double targetScanRadius, int targetMemoryTicks) {
            this.targetScanInterval = targetScanInterval;
            this.targetScanRadius = targetScanRadius;
            this.targetMemoryTicks = targetMemoryTicks;
            return this;
        }

        /** Makes this unit able to anchor a formation. */
        public Builder leads(int maxFollowers, double formationTolerance) {
            this.maxFollowers = maxFollowers;
            this.formationTolerance = formationTolerance;
            return this;
        }

        public FCCombatProfile build() {
            return new FCCombatProfile(
                    this.range, this.idealRange, this.minimumRange,
                    this.burstSize, this.burstShotInterval, this.burstCooldown,
                    this.drawTicks, this.aimTicks, this.recoilTicks,
                    this.reloadTicks, this.shotsPerMagazine,
                    this.accuracy, this.canFireOnTheMove, this.firingMoveSpeed,
                    this.chaseSpeed, this.retreatSpeed,
                    this.courage, this.retreatHealthThreshold, this.usesCover, this.backsAwayFromMelee,
                    this.meleeReach, this.meleeCooldown, this.heavyAttackCooldown, this.heavyAttackChance,
                    this.targetScanInterval, this.targetScanRadius, this.targetMemoryTicks,
                    this.maxFollowers, this.formationTolerance
            );
        }
    }
}
