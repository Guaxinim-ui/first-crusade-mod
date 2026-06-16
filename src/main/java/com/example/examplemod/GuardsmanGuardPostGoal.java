package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class GuardsmanGuardPostGoal extends Goal {
    private final GuardsmanEntity guardsman;
    private final double speedModifier;
    private final double maxDistanceFromPost;

    public GuardsmanGuardPostGoal(GuardsmanEntity guardsman, double speedModifier, double maxDistanceFromPost) {
        this.guardsman = guardsman;
        this.speedModifier = speedModifier;
        this.maxDistanceFromPost = maxDistanceFromPost;

        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.guardsman.getTarget() != null) {
            return false;
        }

        BlockPos guardPost = this.guardsman.getGuardPostPos();

        if (guardPost == null) {
            return false;
        }

        double maxDistanceSquared = this.maxDistanceFromPost * this.maxDistanceFromPost;

        return this.guardsman.distanceToSqr(
                guardPost.getX() + 0.5D,
                guardPost.getY(),
                guardPost.getZ() + 0.5D
        ) > maxDistanceSquared;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.guardsman.getTarget() != null) {
            return false;
        }

        BlockPos guardPost = this.guardsman.getGuardPostPos();

        if (guardPost == null) {
            return false;
        }

        return !this.guardsman.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos guardPost = this.guardsman.getGuardPostPos();

        if (guardPost == null) {
            return;
        }

        boolean foundPath = this.guardsman.getNavigation().moveTo(
                guardPost.getX() + 0.5D,
                guardPost.getY(),
                guardPost.getZ() + 0.5D,
                this.speedModifier
        );

        if (!foundPath) {
            this.guardsman.teleportTo(
                    guardPost.getX() + 0.5D,
                    guardPost.getY(),
                    guardPost.getZ() + 0.5D
            );
        }
    }

    @Override
    public void stop() {
        this.guardsman.getNavigation().stop();
    }
}