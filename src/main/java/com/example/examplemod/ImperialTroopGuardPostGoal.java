package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Guard-post goal for the standalone themed city troops (mirrors {@link GuardsmanGuardPostGoal} but
 * typed to {@link AbstractImperialTroopEntity}). When the troop has an assigned post and no target,
 * it walks back to the post if it has strayed too far — which is what lets the patrol manager
 * circulate themed troops around the city perimeter, just like Guardsmen.
 */
public class ImperialTroopGuardPostGoal extends Goal {
    private final AbstractImperialTroopEntity troop;
    private final double speedModifier;
    private final double maxDistanceFromPost;

    public ImperialTroopGuardPostGoal(AbstractImperialTroopEntity troop, double speedModifier, double maxDistanceFromPost) {
        this.troop = troop;
        this.speedModifier = speedModifier;
        this.maxDistanceFromPost = maxDistanceFromPost;

        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.troop.getTarget() != null) {
            return false;
        }

        BlockPos guardPost = this.troop.getGuardPostPos();

        if (guardPost == null) {
            return false;
        }

        double maxDistanceSquared = this.maxDistanceFromPost * this.maxDistanceFromPost;

        return this.troop.distanceToSqr(
                guardPost.getX() + 0.5D,
                guardPost.getY(),
                guardPost.getZ() + 0.5D
        ) > maxDistanceSquared;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.troop.getTarget() != null) {
            return false;
        }

        if (this.troop.getGuardPostPos() == null) {
            return false;
        }

        return !this.troop.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos guardPost = this.troop.getGuardPostPos();

        if (guardPost == null) {
            return;
        }

        boolean foundPath = this.troop.getNavigation().moveTo(
                guardPost.getX() + 0.5D,
                guardPost.getY(),
                guardPost.getZ() + 0.5D,
                this.speedModifier
        );

        if (!foundPath) {
            this.troop.teleportTo(
                    guardPost.getX() + 0.5D,
                    guardPost.getY(),
                    guardPost.getZ() + 0.5D
            );
        }
    }

    @Override
    public void stop() {
        this.troop.getNavigation().stop();
    }
}
