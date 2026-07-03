package com.example.examplemod;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps ranged troops from gunning each other down. Two layers:
 *
 * 1. {@link #hasClearShot} — checked ONCE right before a shot leaves the barrel (never every
 *    tick): walks the shooter→target line and reports whether an ALLY of the shooter's faction
 *    stands in the fire lane. Blocked shooters hold fire and {@link #strafeForClearShot} sidesteps
 *    them a couple of blocks so the line opens up (a cheap "spread out, you're in my lane").
 *
 * 2. The las-bolt itself ignores allied bodies entirely (see LasgunShotEntity#canHitEntity), so
 *    even a shot fired into a scrum passes clean through friends and only bites the enemy.
 */
public final class FriendlyFireGuard {
    // How close (blocks) to the fire line an ally must be to block the shot.
    private static final double FIRE_LANE_RADIUS = 0.9D;

    private FriendlyFireGuard() {
    }

    /** True when no ally of the shooter stands between it and the target. */
    public static boolean hasClearShot(LivingEntity shooter, LivingEntity target) {
        Vec3 from = shooter.getEyePosition();
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);

        AABB lane = new AABB(from, to).inflate(1.0D);

        List<LivingEntity> allies = shooter.level().getEntitiesOfClass(
                LivingEntity.class,
                lane,
                entity -> entity != shooter
                        && entity != target
                        && entity.isAlive()
                        && FirstCrusadeFactionManager.areAllies(shooter, entity)
        );

        if (allies.isEmpty()) {
            return true;
        }

        for (LivingEntity ally : allies) {
            Vec3 allyCenter = ally.position().add(0.0D, ally.getBbHeight() * 0.5D, 0.0D);

            if (distanceToSegment(allyCenter, from, to) <= FIRE_LANE_RADIUS + ally.getBbWidth() * 0.5D) {
                return false;
            }
        }

        return true;
    }

    /**
     * Sidesteps a blocked shooter two blocks perpendicular to the fire line (alternating side by
     * entity id so a clump naturally fans out into a loose firing line instead of a stack).
     */
    public static void strafeForClearShot(PathfinderMob shooter, LivingEntity target) {
        Vec3 toTarget = target.position().subtract(shooter.position());
        Vec3 flat = new Vec3(toTarget.x, 0.0D, toTarget.z);

        if (flat.lengthSqr() < 0.01D) {
            return;
        }

        Vec3 side = flat.normalize().cross(new Vec3(0.0D, 1.0D, 0.0D));
        double direction = (shooter.getId() & 1) == 0 ? 2.0D : -2.0D;

        Vec3 spot = shooter.position().add(side.scale(direction));
        BlockPos destination = BlockPos.containing(spot.x, shooter.getY(), spot.z);

        shooter.getNavigation().moveTo(
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                1.0D
        );
    }

    /** Distance from a point to the from→to segment. */
    private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        double lengthSqr = line.lengthSqr();

        if (lengthSqr < 1.0E-4D) {
            return point.distanceTo(from);
        }

        double t = point.subtract(from).dot(line) / lengthSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));

        return point.distanceTo(from.add(line.scale(t)));
    }
}
