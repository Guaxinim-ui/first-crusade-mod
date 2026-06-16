package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public class GuardsmanKnifeAttackGoal extends MeleeAttackGoal {
    private final GuardsmanEntity guardsman;
    private final double meleeRangeSqr;

    public GuardsmanKnifeAttackGoal(GuardsmanEntity guardsman, double speedModifier, double meleeRange) {
        super(guardsman, speedModifier, true);
        this.guardsman = guardsman;
        this.meleeRangeSqr = meleeRange * meleeRange;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.guardsman.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        if (this.guardsman.distanceToSqr(target) > this.meleeRangeSqr) {
            return false;
        }

        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.guardsman.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        if (this.guardsman.distanceToSqr(target) > this.meleeRangeSqr * 1.5D) {
            return false;
        }

        return super.canContinueToUse();
    }

    @Override
    public void start() {
        this.guardsman.setUsingKnife();
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        this.guardsman.setUsingLasgun();
    }
}