package com.example.examplemod.ai.combat;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Getting behind something when the shooting starts.
 *
 * <h2>Sixteen rays, not a pathfinding search</h2>
 *
 * The expensive way to find cover is to ask the pathfinder about every reachable position and score
 * each one. This does the cheap version and stops there: sample a fixed ring of candidate spots
 * around the unit, and for each one cast a single ray to the threat. A spot the ray cannot reach is
 * cover. Sixteen ray casts is roughly what one vanilla mob's line-of-sight check costs over a
 * second, and it happens only when a unit is actually suppressed.
 *
 * <p>The brief asks for exactly this restraint — "not absurdly expensive pathfinding, search a
 * limited radius and only when necessary" — so the two gates are: {@link FCSuppression#wantsCover}
 * must be true, and the search has a cooldown so a unit that failed to find cover does not re-run
 * the ring every tick while it stays pinned.
 *
 * <h2>Why it sits above the attack goal</h2>
 *
 * A pinned soldier taking cover instead of shooting is the point. The goal only runs while
 * suppression is over the threshold, so it hands movement back to the attack goal as soon as the
 * fire lets up — which is what makes a firefight ebb and flow instead of freezing.
 */
public class FCCoverGoal extends Goal {

    /** How far out the ring of candidate positions sits. */
    private static final int SEARCH_RADIUS = 7;

    /** Candidate spots on the ring. Sixteen is a spot every 22.5 degrees. */
    private static final int SAMPLES = 16;

    /** Ticks before a unit that found nothing tries again. */
    private static final int RETRY_COOLDOWN = 40;

    /** Close enough to count as arrived. */
    private static final double ARRIVAL_DISTANCE = 1.6D;

    private final PathfinderMob mob;

    @Nullable
    private Vec3 coverSpot;

    private int cooldown;

    public FCCoverGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        if (!FCSuppression.wantsCover(this.mob)) {
            return false;
        }

        LivingEntity threat = this.mob.getTarget();

        if (threat == null || !threat.isAlive()) {
            // Being shot at by something you cannot see is exactly when cover matters most, but with
            // no threat position there is nothing to hide from — any spot is as good as any other,
            // and moving at random reads as panic rather than as taking cover.
            return false;
        }

        // Already behind something: nothing to do, and no search to pay for.
        if (!hasLineOfSight(this.mob.position(), threat)) {
            return false;
        }

        this.coverSpot = findCover(threat);

        if (this.coverSpot == null) {
            this.cooldown = RETRY_COOLDOWN;
            return false;
        }

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.coverSpot != null
                && FCSuppression.wantsCover(this.mob)
                && this.mob.distanceToSqr(this.coverSpot) > ARRIVAL_DISTANCE * ARRIVAL_DISTANCE
                && !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (this.coverSpot != null) {
            this.mob.getNavigation().moveTo(this.coverSpot.x, this.coverSpot.y, this.coverSpot.z, 1.15D);
        }
    }

    @Override
    public void stop() {
        this.coverSpot = null;
        this.mob.getNavigation().stop();

        // A short rest either way. Arriving in cover and immediately searching for better cover is
        // how a unit ends up jittering between two blocks for the whole battle.
        this.cooldown = RETRY_COOLDOWN / 2;
    }

    /**
     * The nearest sampled spot with no line of sight to the threat.
     *
     * <p>Nearest rather than best: a soldier takes the cover in front of them, and scoring every
     * candidate for quality would mean walking past usable cover to reach a marginally better one.
     */
    @Nullable
    private Vec3 findCover(LivingEntity threat) {
        Vec3 origin = this.mob.position();

        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < SAMPLES; i++) {
            double angle = i * (Math.PI * 2.0D / SAMPLES);

            int x = (int) Math.round(origin.x + Math.cos(angle) * SEARCH_RADIUS);
            int z = (int) Math.round(origin.z + Math.sin(angle) * SEARCH_RADIUS);

            BlockPos ground = groundAt(x, (int) origin.y, z);

            if (ground == null) {
                continue;
            }

            Vec3 candidate = new Vec3(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D);

            if (hasLineOfSight(candidate, threat)) {
                continue;
            }

            double distance = candidate.distanceToSqr(origin);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }

        return best;
    }

    /**
     * A standable spot at this column, searched a few blocks up and down from the unit's own level.
     *
     * <p>Deliberately narrow. A wider search would find cover at the bottom of a ravine and send a
     * suppressed soldier over the edge to reach it.
     */
    @Nullable
    private BlockPos groundAt(int x, int y, int z) {
        Level level = this.mob.level();

        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);

            // Never force a chunk to load to answer this. An unloaded column simply is not cover.
            if (!level.isLoaded(pos)) {
                return null;
            }

            if (level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                    && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()) {
                return pos;
            }
        }

        return null;
    }

    /** One ray from a position to the threat's chest. Blocked means cover. */
    private boolean hasLineOfSight(Vec3 from, LivingEntity threat) {
        Vec3 eyes = new Vec3(from.x, from.y + this.mob.getEyeHeight(), from.z);
        Vec3 chest = threat.position().add(0.0D, threat.getBbHeight() * 0.5D, 0.0D);

        BlockHitResult hit = this.mob.level().clip(new ClipContext(
                eyes, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));

        return hit.getType() == HitResult.Type.MISS;
    }
}
