package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

public class FirstCrusadeNearestEnemyTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    public FirstCrusadeNearestEnemyTargetGoal(Mob mob) {
        super(
                mob,
                LivingEntity.class,
                10,
                true,
                false,
                target -> FirstCrusadeFactionManager.canAttack(mob, target)
        );
    }
}