package com.example.examplemod;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class GuardsmanLasgunAttackGoal extends Goal {
    private final GuardsmanEntity guardsman;
    private final double speedModifier;
    private final int attackInterval;
    private final float attackRadius;
    private final float minimumRange;

    private int attackTime = 20;

    public GuardsmanLasgunAttackGoal(GuardsmanEntity guardsman, double speedModifier, int attackInterval, float attackRadius, float minimumRange) {
        this.guardsman = guardsman;
        this.speedModifier = speedModifier;
        this.attackInterval = attackInterval;
        this.attackRadius = attackRadius;
        this.minimumRange = minimumRange;

        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.guardsman.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        double distance = this.guardsman.distanceTo(target);

        return distance > this.minimumRange;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.guardsman.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        double distance = this.guardsman.distanceTo(target);

        return distance > this.minimumRange;
    }

    @Override
    public void start() {
        this.guardsman.setUsingLasgun();
        this.attackTime = 10;
    }

    @Override
    public void stop() {
        this.guardsman.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.guardsman.getTarget();

        if (target == null) {
            return;
        }

        this.guardsman.setUsingLasgun();

        double distanceSqr = this.guardsman.distanceToSqr(target);
        double attackRadiusSqr = this.attackRadius * this.attackRadius;
        boolean canSeeTarget = this.guardsman.getSensing().hasLineOfSight(target);

        this.guardsman.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (distanceSqr > attackRadiusSqr || !canSeeTarget) {
            this.guardsman.getNavigation().moveTo(target, this.speedModifier);
        } else {
            this.guardsman.getNavigation().stop();
        }

        this.attackTime--;

        if (this.attackTime <= 0) {
            if (canSeeTarget && distanceSqr <= attackRadiusSqr) {
                // Hold fire when a comrade stands in the lane: sidestep to open the line and try
                // again shortly (the las-bolt itself also ignores allies, as a second safety net).
                if (!FriendlyFireGuard.hasClearShot(this.guardsman, target)) {
                    FriendlyFireGuard.strafeForClearShot(this.guardsman, target);
                    this.attackTime = 8;
                    return;
                }

                this.guardsman.performLasgunAttack(target);
                this.attackTime = this.attackInterval;
            } else {
                this.attackTime = 10;
            }
        }
    }
}