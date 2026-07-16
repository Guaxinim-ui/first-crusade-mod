package com.example.examplemod;

import java.util.EnumSet;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;

/**
 * Shared lasgun combat goal for Imperial ranged troops.
 *
 * <p>The goal deliberately separates weapon raising, aiming, firing and recoil/cooldown. The
 * current phase is stored in synced entity data through {@link LasgunAimingEntity}, so the client
 * renderer can animate the arms instead of showing a static weapon attached to the arm.</p>
 */
public class ImperialLasgunAttackGoal<T extends PathfinderMob & RangedAttackMob & LasgunAimingEntity> extends Goal {
    /** 1.4 seconds: long enough for the player to clearly see the weapon being raised. */
    public static final int DRAW_TICKS = 28;
    /** Brief aim confirmation before the first shot. */
    public static final int AIM_TICKS = 12;
    /** Visible recoil duration. */
    public static final int SHOOT_TICKS = 6;

    private final T shooter;
    private final double speedModifier;
    private final int attackInterval;
    private final float attackRadius;
    private final float minimumRange;

    private int warmupTicks;
    private int cooldownTicks;
    private int shootingTicks;

    public ImperialLasgunAttackGoal(T shooter, double speedModifier, int attackInterval, float attackRadius) {
        this(shooter, speedModifier, attackInterval, attackRadius, 0.0F);
    }

    public ImperialLasgunAttackGoal(T shooter, double speedModifier, int attackInterval, float attackRadius, float minimumRange) {
        this.shooter = shooter;
        this.speedModifier = speedModifier;
        this.attackInterval = attackInterval;
        this.attackRadius = attackRadius;
        this.minimumRange = minimumRange;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.shooter.getTarget();
        return target != null && target.isAlive() && this.shooter.distanceTo(target) > this.minimumRange;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.shooter.getTarget();
        return target != null && target.isAlive() && this.shooter.distanceTo(target) > this.minimumRange;
    }

    @Override
    public void start() {
        this.warmupTicks = 0;
        this.cooldownTicks = 0;
        this.shootingTicks = 0;
        this.shooter.setAggressive(true);
        this.shooter.setLasgunCombatPose(LasgunCombatPose.DRAWING, 0);
    }

    @Override
    public void stop() {
        this.shooter.getNavigation().stop();
        this.shooter.setAggressive(false);
        this.shooter.clearLasgunCombatPose();
        this.warmupTicks = 0;
        this.cooldownTicks = 0;
        this.shootingTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
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

        double distanceSqr = this.shooter.distanceToSqr(target);
        double attackRadiusSqr = this.attackRadius * this.attackRadius;
        boolean canSeeTarget = this.shooter.getSensing().hasLineOfSight(target);

        this.shooter.getLookControl().setLookAt(target, 60.0F, 60.0F);

        // Do not raise the rifle while running blindly after a target. Once the troop reaches a
        // clear firing position the full draw animation starts from the beginning.
        if (distanceSqr > attackRadiusSqr || !canSeeTarget) {
            this.shooter.getNavigation().moveTo(target, this.speedModifier);
            this.warmupTicks = 0;
            this.cooldownTicks = 0;
            this.shootingTicks = 0;
            this.shooter.clearLasgunCombatPose();
            return;
        }

        this.shooter.getNavigation().stop();

        if (this.shootingTicks > 0) {
            int poseTick = SHOOT_TICKS - this.shootingTicks;
            this.shooter.setLasgunCombatPose(LasgunCombatPose.SHOOTING, poseTick);
            this.shootingTicks--;
            return;
        }

        if (this.warmupTicks < DRAW_TICKS) {
            this.shooter.setLasgunCombatPose(LasgunCombatPose.DRAWING, this.warmupTicks);
            this.warmupTicks++;
            return;
        }

        if (this.warmupTicks < DRAW_TICKS + AIM_TICKS) {
            this.shooter.setLasgunCombatPose(LasgunCombatPose.AIMING, this.warmupTicks - DRAW_TICKS);
            this.warmupTicks++;
            return;
        }

        if (this.cooldownTicks > 0) {
            this.shooter.setLasgunCombatPose(LasgunCombatPose.COOLDOWN, this.attackInterval - this.cooldownTicks);
            this.cooldownTicks--;
            return;
        }

        this.shooter.setLasgunCombatPose(LasgunCombatPose.AIMING, AIM_TICKS);

        if (!FriendlyFireGuard.hasClearShot(this.shooter, target)) {
            FriendlyFireGuard.strafeForClearShot(this.shooter, target);
            this.cooldownTicks = 8;
            return;
        }

        // Swing is a vanilla-synchronised animation event. Besides the custom pose data, this gives
        // the renderer a second reliable recoil signal on both dedicated and integrated servers.
        this.shooter.swing(InteractionHand.MAIN_HAND);
        this.shooter.performRangedAttack(target, 1.0F);
        this.shooter.setLasgunCombatPose(LasgunCombatPose.SHOOTING, 0);
        this.shootingTicks = SHOOT_TICKS;
        this.cooldownTicks = this.attackInterval;
        this.warmupTicks = DRAW_TICKS + AIM_TICKS;
    }
}
