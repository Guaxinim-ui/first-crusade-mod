package com.example.examplemod;

import java.util.EnumSet;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.example.examplemod.assault.ImperialExpeditionTags;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * "You have wandered off; walk back." The whole of a simple base's idle discipline.
 *
 * <h2>What it replaced</h2>
 *
 * {@link GuardsmanGuardPostGoal} pulled a soldier to an exact block, and the patrol manager handed
 * out a new exact block every thirty seconds — so a garrison marched in circles forever, repathing
 * on a timer whether or not anything had changed. This goal does nothing at all while the soldier is
 * near home, which is almost always.
 *
 * <h2>Cheap by construction</h2>
 *
 * {@link #canUse()} is asked several times a second by the goal selector, so the real check is
 * behind a cooldown and behind a squared-distance compare — no pathfinding is attempted until the
 * soldier is genuinely far away. Once walking, {@link #canContinueToUse()} keeps the existing path
 * rather than issuing a new one: a valid path is left alone.
 *
 * <h2>Expeditions outrank home</h2>
 *
 * A soldier called to a raid carries {@link ImperialExpeditionTags#ON_EXPEDITION}, and while it does
 * this goal stands down entirely. Otherwise the base would drag its own reinforcements back the
 * moment they set out.
 */
public class LightweightReturnToBaseGoal extends Goal {
    private final PathfinderMob mob;
    private final Supplier<BlockPos> home;
    private final double speedModifier;
    private final double triggerDistance;

    /** Ticks until the next real check. Keeps the common case to one integer decrement. */
    private int checkCooldown;

    private static final int CHECK_INTERVAL = 40;

    public LightweightReturnToBaseGoal(PathfinderMob mob, Supplier<BlockPos> home,
                                       double speedModifier, double triggerDistance) {
        this.mob = mob;
        this.home = home;
        this.speedModifier = speedModifier;
        this.triggerDistance = triggerDistance;

        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.checkCooldown > 0) {
            this.checkCooldown--;
            return false;
        }

        this.checkCooldown = CHECK_INTERVAL;

        if (this.mob.getTarget() != null || ImperialExpeditionTags.isOnExpedition(this.mob)) {
            return false;
        }

        BlockPos anchor = anchor();
        if (anchor == null) {
            return false;
        }

        return distanceSqrTo(anchor) > this.triggerDistance * this.triggerDistance;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() != null || ImperialExpeditionTags.isOnExpedition(this.mob)) {
            return false;
        }

        BlockPos anchor = anchor();
        if (anchor == null) {
            return false;
        }

        // Walking is finished when the soldier is comfortably inside the ring again, not when it
        // stands exactly on the Core — that last metre is what made the old goal shuffle in place.
        double settled = this.triggerDistance * 0.5D;
        if (distanceSqrTo(anchor) <= settled * settled) {
            return false;
        }

        return !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos anchor = anchor();
        if (anchor == null) {
            return;
        }

        this.mob.getNavigation().moveTo(
                anchor.getX() + 0.5D,
                anchor.getY(),
                anchor.getZ() + 0.5D,
                this.speedModifier
        );
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.checkCooldown = CHECK_INTERVAL;
    }

    @Nullable
    private BlockPos anchor() {
        return this.home.get();
    }

    private double distanceSqrTo(BlockPos pos) {
        return this.mob.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }
}
