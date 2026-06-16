package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

public class FirstCrusadeHurtByTargetGoal extends HurtByTargetGoal {
    public FirstCrusadeHurtByTargetGoal(PathfinderMob mob) {
        super(mob);
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = this.mob.getLastHurtByMob();

        if (!FirstCrusadeFactionManager.canAttack(this.mob, attacker)) {
            return false;
        }

        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();

        if (!FirstCrusadeFactionManager.canAttack(this.mob, target)) {
            return false;
        }

        return super.canContinueToUse();
    }
}