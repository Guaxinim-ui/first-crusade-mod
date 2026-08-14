package com.example.examplemod.ai.combat;

import java.util.EnumSet;

import com.example.examplemod.FriendlyFireGuard;
import com.example.examplemod.LasgunCombatPose;
import com.example.examplemod.LasgunAimingEntity;
import com.example.examplemod.performance.ai.FirstCrusadeAiLod;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;
import com.example.examplemod.unit.profile.FCCombatProfile;
import com.example.examplemod.unit.profile.FCUnit;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;

/**
 * The single ranged-combat goal used by every shooting unit in the mod.
 *
 * <p>It replaces the hard-coded {@code ImperialLasgunAttackGoal} constants with values pulled from
 * the unit's {@link FCCombatProfile}, which is what lets one class produce genuinely different
 * behaviour: a Guardsman holds a firing line and squeezes off controlled three-round bursts, while
 * an Ork Shoota Boy walks forward spraying because its profile says
 * {@code fireOnTheMove} and gives it a long burst with poor accuracy.</p>
 *
 * <h2>The firing cycle</h2>
 * <ol>
 *   <li><b>Approach</b> — outside {@code range} or with no line of sight, close the distance and
 *       keep the weapon down.</li>
 *   <li><b>Draw</b> — in range, raise the weapon over {@code drawTicks}.</li>
 *   <li><b>Aim</b> — settle for {@code aimTicks}.</li>
 *   <li><b>Fire</b> — emit {@code burstSize} shots spaced {@code burstShotInterval} apart, each
 *       followed by {@code recoilTicks} of recoil pose.</li>
 *   <li><b>Cooldown</b> — pause {@code burstCooldown}, then either reload or start another burst.</li>
 * </ol>
 *
 * <h2>Standoff behaviour</h2>
 * <p>The goal actively manages distance instead of just running at the target: it advances when
 * beyond {@code idealRange}, holds still inside it, and — if {@code backsAwayFromMelee} is set —
 * retreats when an enemy closes inside {@code minimumRange}. That is what produces the "recuar
 * alguns blocos quando um inimigo corpo a corpo chegar muito perto" behaviour without a separate
 * goal fighting this one for control.</p>
 *
 * <h2>Cost</h2>
 * <p>Line-of-sight and friendly-fire checks are the expensive parts, so they run only at the moment
 * a shot is about to leave the barrel — never every tick. With a hundred troopers on screen that is
 * the difference between a playable battle and a stutter.</p>
 *
 * @param <T> a pathfinding mob that can shoot, exposes combat pose data, and carries a profile
 */
public class FCRangedAttackGoal<T extends PathfinderMob & RangedAttackMob & LasgunAimingEntity & FCUnit>
        extends Goal {

    /** Phases of the firing cycle. Mapped onto {@link LasgunCombatPose} for the renderer. */
    private enum Phase {
        APPROACH,
        DRAW,
        AIM,
        FIRE,
        COOLDOWN,
        RELOAD
    }

    private final T shooter;
    private final FCCombatProfile profile;

    private Phase phase = Phase.APPROACH;
    private int phaseTicks;

    /** Shots already fired in the current burst. */
    private int shotsInBurst;
    /** Shots fired since the last reload. */
    private int shotsSinceReload;
    /** Ticks until the next shot inside a burst may be taken. */
    private int shotDelay;
    /** Ticks of recoil pose still to show. */
    private int recoilTicks;
    /** Throttle for path recalculation — repathing every tick is the classic mob-AI performance sink. */
    private int repathCooldown;
    /** Ticks the goal has been blocked by an ally in the fire lane. */
    private int blockedTicks;

    public FCRangedAttackGoal(T shooter) {
        this.shooter = shooter;
        this.profile = shooter.getCombatProfile();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.profile.isRanged()) {
            return false;
        }

        LivingEntity target = this.shooter.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.shooter.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        // Hand over to the melee goal once the enemy is genuinely on top of us and we are not the
        // kind of unit that backs off.
        if (this.profile.minimumRange() > 0.0F
                && !this.profile.backsAwayFromMelee()
                && this.shooter.distanceTo(target) < this.profile.minimumRange()) {
            return false;
        }

        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.phase = Phase.APPROACH;
        this.phaseTicks = 0;
        this.shotsInBurst = 0;
        this.shotDelay = 0;
        this.recoilTicks = 0;
        this.repathCooldown = 0;
        this.blockedTicks = 0;
        this.shooter.setAggressive(true);
    }

    @Override
    public void stop() {
        this.shooter.getNavigation().stop();
        this.shooter.setAggressive(false);
        this.shooter.clearLasgunCombatPose();
        this.phase = Phase.APPROACH;
        this.phaseTicks = 0;
        this.shotsInBurst = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.shooter.getTarget();

        if (target == null || !target.isAlive()) {
            this.shooter.setAggressive(false);
            this.shooter.clearLasgunCombatPose();
            return;
        }

        this.shooter.setAggressive(true);
        this.shooter.getLookControl().setLookAt(target, 60.0F, 60.0F);

        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }

        double distance = this.shooter.distanceTo(target);
        boolean canSee = this.shooter.getSensing().hasLineOfSight(target);

        // Recoil is purely a display state; let it run down regardless of phase.
        if (this.recoilTicks > 0) {
            this.recoilTicks--;
        }

        switch (this.phase) {
            case APPROACH -> tickApproach(target, distance, canSee);
            case DRAW -> tickDraw(distance, canSee);
            case AIM -> tickAim(target, distance, canSee);
            case FIRE -> tickFire(target, distance, canSee);
            case COOLDOWN -> tickCooldown(target, distance, canSee);
            case RELOAD -> tickReload(target);
        }

        this.phaseTicks++;
    }

    // ------------------------------------------------------------------
    // Phases
    // ------------------------------------------------------------------

    private void tickApproach(LivingEntity target, double distance, boolean canSee) {
        this.shooter.setLasgunCombatPose(LasgunCombatPose.IDLE, this.phaseTicks);

        if (distance <= this.profile.range() && canSee) {
            enterPhase(Phase.DRAW);
            return;
        }

        moveToward(target, this.profile.chaseSpeed());
    }

    private void tickDraw(double distance, boolean canSee) {
        this.shooter.setLasgunCombatPose(LasgunCombatPose.DRAWING, this.phaseTicks);

        // Lost the shot mid-draw: drop back to approach rather than finishing a pointless animation.
        if (distance > this.profile.range() || !canSee) {
            enterPhase(Phase.APPROACH);
            return;
        }

        holdPosition();

        if (this.phaseTicks >= this.profile.drawTicks()) {
            enterPhase(Phase.AIM);
        }
    }

    private void tickAim(LivingEntity target, double distance, boolean canSee) {
        this.shooter.setLasgunCombatPose(LasgunCombatPose.AIMING, this.phaseTicks);

        if (distance > this.profile.range() || !canSee) {
            enterPhase(Phase.APPROACH);
            return;
        }

        manageStandoff(target, distance);

        if (this.phaseTicks >= this.profile.aimTicks()) {
            this.shotsInBurst = 0;
            this.shotDelay = 0;
            enterPhase(Phase.FIRE);
        }
    }

    private void tickFire(LivingEntity target, double distance, boolean canSee) {
        this.shooter.setLasgunCombatPose(
                this.recoilTicks > 0 ? LasgunCombatPose.SHOOTING : LasgunCombatPose.AIMING,
                this.recoilTicks > 0 ? this.profile.recoilTicks() - this.recoilTicks : 0
        );

        if (distance > this.profile.range() || !canSee) {
            enterPhase(Phase.APPROACH);
            return;
        }

        manageStandoff(target, distance);

        if (this.shotDelay > 0) {
            this.shotDelay--;
            return;
        }

        // Burst finished — pause, or reload if the magazine ran dry.
        if (this.shotsInBurst >= this.profile.burstSize()) {
            if (needsReload()) {
                enterPhase(Phase.RELOAD);
            } else {
                enterPhase(Phase.COOLDOWN);
            }
            return;
        }

        // The expensive checks, done once per shot rather than once per tick.
        if (!isAimedAt(target)) {
            return;
        }

        if (!FriendlyFireGuard.hasClearShot(this.shooter, target)) {
            this.blockedTicks++;

            // Give the shooter a moment to acquire a clean lane before making it move; troops that
            // sidestep on the very first blocked tick look twitchy.
            if (this.blockedTicks > 6) {
                FriendlyFireGuard.strafeForClearShot(this.shooter, target);
                this.blockedTicks = 0;
            }

            return;
        }

        this.blockedTicks = 0;
        fireOneShot(target);
    }

    private void tickCooldown(LivingEntity target, double distance, boolean canSee) {
        this.shooter.setLasgunCombatPose(LasgunCombatPose.COOLDOWN, this.phaseTicks);

        if (distance > this.profile.range() || !canSee) {
            enterPhase(Phase.APPROACH);
            return;
        }

        manageStandoff(target, distance);

        if (this.phaseTicks >= this.profile.burstCooldown()) {
            this.shotsInBurst = 0;
            enterPhase(Phase.FIRE);
        }
    }

    private void tickReload(LivingEntity target) {
        this.shooter.setLasgunCombatPose(LasgunCombatPose.COOLDOWN, this.phaseTicks);

        // Reloading is a bad time to be standing in the open: back off while doing it.
        if (this.profile.backsAwayFromMelee()) {
            retreatFrom(target, this.profile.retreatSpeed() * 0.8D);
        } else {
            holdPosition();
        }

        if (this.phaseTicks >= this.profile.reloadTicks()) {
            this.shotsSinceReload = 0;
            this.shotsInBurst = 0;
            enterPhase(Phase.AIM);
        }
    }

    // ------------------------------------------------------------------
    // Firing
    // ------------------------------------------------------------------

    private void fireOneShot(LivingEntity target) {
        // A vanilla swing gives the client a second, always-synced recoil signal alongside the
        // custom pose data — useful on dedicated servers where pose updates can lag a tick.
        this.shooter.swing(InteractionHand.MAIN_HAND);
        this.shooter.performRangedAttack(target, 1.0F);

        this.shotsInBurst++;
        this.shotsSinceReload++;
        this.shotDelay = this.profile.burstShotInterval();
        this.recoilTicks = this.profile.recoilTicks();

        this.shooter.setLasgunCombatPose(LasgunCombatPose.SHOOTING, 0);
    }

    private boolean needsReload() {
        return this.profile.shotsPerMagazine() > 0
                && this.profile.reloadTicks() > 0
                && this.shotsSinceReload >= this.profile.shotsPerMagazine();
    }

    /**
     * Whether the weapon is pointed close enough at the target to justify a shot. Without this a
     * unit that has just been given a new target fires instantly in whatever direction it happened
     * to be facing, which reads as troops shooting sideways.
     */
    private boolean isAimedAt(LivingEntity target) {
        Vec3 toTarget = target.position()
                .add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(this.shooter.getEyePosition());

        if (toTarget.lengthSqr() < 1.0E-4D) {
            return true;
        }

        // Body-facing as a horizontal unit vector. Written out rather than taken from a helper so
        // it provably matches the convention used by FCWeaponMount (at yaw 0 an entity faces -Z).
        double yaw = Math.toRadians(this.shooter.yBodyRot);
        Vec3 facing = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));

        Vec3 flat = new Vec3(toTarget.x, 0.0D, toTarget.z);

        if (flat.lengthSqr() < 1.0E-4D) {
            return true;
        }

        // cos(35°) ≈ 0.82 — generous enough that troops do not freeze while turning, tight enough
        // that they do not fire over their own shoulder.
        return facing.dot(flat.normalize()) > 0.82D;
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    /**
     * Keeps the unit at its preferred fighting distance: advance if too far, back off if an enemy
     * has closed inside the minimum, otherwise stand and shoot.
     */
    private void manageStandoff(LivingEntity target, double distance) {
        if (this.profile.backsAwayFromMelee()
                && this.profile.minimumRange() > 0.0F
                && distance < this.profile.minimumRange()) {
            retreatFrom(target, this.profile.retreatSpeed());
            return;
        }

        if (distance > this.profile.idealRange()) {
            // Units that cannot shoot while moving must choose: close the gap OR fire. Firing wins
            // as long as the target is inside effective range, which is what makes heavy weapons
            // feel like emplacements rather than skirmishers.
            if (this.profile.canFireOnTheMove()) {
                moveToward(target, this.profile.firingMoveSpeed());
            } else if (distance > this.profile.range() * 0.9D) {
                moveToward(target, this.profile.chaseSpeed());
            } else {
                holdPosition();
            }

            return;
        }

        holdPosition();
    }

    private void moveToward(LivingEntity target, double speed) {
        if (this.repathCooldown > 0) {
            return;
        }

        this.shooter.getNavigation().moveTo(target, speed);

        // Throttle repathing. The original comment here called this "the single biggest AI cost in a
        // large battle", which overstates it: PathNavigation#createPath already returns the current
        // path unchanged while the target stays on the same block, so only a target that has moved
        // costs a real A*. This still halves pathfinds during a chase, and half a second of
        // staleness is imperceptible, but it is not where the big money was.
        // The number moved to the performance config so this goal and ImperialLasgunAttackGoal
        // share one dial instead of two literals.
        this.repathCooldown = FirstCrusadeAiLod.scale(
                this.shooter, FirstCrusadePerformanceConfig.repathInterval());
    }

    private void retreatFrom(LivingEntity target, double speed) {
        if (this.repathCooldown > 0) {
            return;
        }

        Vec3 away = this.shooter.position().subtract(target.position());
        Vec3 flat = new Vec3(away.x, 0.0D, away.z);

        if (flat.lengthSqr() < 0.01D) {
            return;
        }

        Vec3 destination = this.shooter.position().add(flat.normalize().scale(4.0D));

        this.shooter.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
        // Deliberately shorter than a chase repath: falling back is an evasive hop and needs to
        // react faster. Still scaled by LOD.
        this.repathCooldown = FirstCrusadeAiLod.scale(this.shooter, 8);
    }

    private void holdPosition() {
        if (!this.shooter.getNavigation().isDone()) {
            this.shooter.getNavigation().stop();
        }
    }

    private void enterPhase(Phase next) {
        this.phase = next;
        this.phaseTicks = 0;
    }
}
