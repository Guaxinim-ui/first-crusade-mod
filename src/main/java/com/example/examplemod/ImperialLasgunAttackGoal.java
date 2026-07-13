package com.example.examplemod;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;

public class ImperialLasgunAttackGoal<T extends PathfinderMob & RangedAttackMob & LasgunAimingEntity> extends Goal {
    private static final int DRAW_TICKS = 14;
    private static final int AIM_TICKS = 10;
    private static final int SHOOT_TICKS = 5;

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

        if (target == null || !target.isAlive()) {
            return false;
        }

        return this.shooter.distanceTo(target) > this.minimumRange;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.shooter.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        return this.shooter.distanceTo(target) > this.minimumRange;
    }

    @Override
    public void start() {
        this.warmupTicks = 0;
        this.cooldownTicks = 0;
        this.shootingTicks = 0;
        this.shooter.setLasgunCombatPose(LasgunCombatPose.DRAWING, 0);
    }

    @Override
    public void stop() {
        this.shooter.getNavigation().stop();
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
            this.shooter.clearLasgunCombatPose();
            return;
        }

        double distanceSqr = this.shooter.distanceToSqr(target);
        double attackRadiusSqr = this.attackRadius * this.attackRadius;
        boolean canSeeTarget = this.shooter.getSensing().hasLineOfSight(target);

        this.shooter.getLookControl().setLookAt(target, 40.0F, 40.0F);

        if (distanceSqr > attackRadiusSqr || !canSeeTarget) {
            this.shooter.getNavigation().moveTo(target, this.speedModifier);
            this.warmupTicks = 0;
            this.shootingTicks = 0;
            this.shooter.setLasgunCombatPose(LasgunCombatPose.DRAWING, 0);
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
            this.shooter.setLasgunCombatPose(LasgunCombatPose.COOLDOWN, Math.min(this.cooldownTicks, this.attackInterval));
            this.cooldownTicks--;
            return;
        }

        this.shooter.setLasgunCombatPose(LasgunCombatPose.AIMING, AIM_TICKS);

        if (!FriendlyFireGuard.hasClearShot(this.shooter, target)) {
            FriendlyFireGuard.strafeForClearShot(this.shooter, target);
            this.cooldownTicks = 8;
            return;
        }

        this.shooter.performRangedAttack(target, 1.0F);
        this.shooter.setLasgunCombatPose(LasgunCombatPose.SHOOTING, 0);
        this.shootingTicks = SHOOT_TICKS;
        this.cooldownTicks = this.attackInterval;
        this.warmupTicks = DRAW_TICKS + AIM_TICKS;
    }
}
