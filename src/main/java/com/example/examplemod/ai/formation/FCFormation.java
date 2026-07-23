package com.example.examplemod.ai.formation;

import com.example.examplemod.unit.profile.UnitRole;

import net.minecraft.world.phys.Vec3;

/**
 * The shapes a squad can hold, and the maths that turns a slot index into a world position.
 *
 * <p>A formation is defined relative to its leader: the leader stands at the origin facing along
 * its own body yaw, and every follower gets an offset in "leader space" (right/forward), which is
 * then rotated into the world. That means the whole squad turns as a body when the sergeant turns,
 * rather than each trooper independently deciding where to stand.</p>
 *
 * <h2>Slot assignment</h2>
 * <p>Slots are indexed from 0 and filled in order, but <em>which</em> slot a unit gets depends on
 * its {@link UnitRole}: a {@link UnitRole.FormationSlot#FRONT} role is placed in the leading rank,
 * {@code SUPPORT} in the second, {@code REAR} behind. So a Heavy Gunner naturally ends up behind the
 * riflemen without any per-unit special-casing — it is a consequence of its role, not of code that
 * knows what a Heavy Gunner is.</p>
 *
 * <h2>Sign conventions</h2>
 * <p>Forward is the leader's facing: {@code F = (sin yaw, -cos yaw)} — matching
 * {@link com.example.examplemod.weapon.FCWeaponMount}, because at yaw 0 an entity faces <em>−Z</em>.
 * Right is {@code F} turned 90° clockwise: {@code R = (cos yaw, sin yaw)}. Positive
 * {@code forward} in a slot offset means <em>ahead of</em> the leader; positive {@code right} means
 * to the leader's right.</p>
 */
public enum FCFormation {

    /**
     * A firing line: everyone abreast, so every weapon bears on the enemy at once. The default for
     * a squad that is engaging.
     *
     * <p>Spacing is 2 blocks — wide enough that troopers do not shoot each other's backs (the
     * friendly-fire check does the rest), tight enough that the line reads as one unit.</p>
     */
    LINE,

    /**
     * Single file behind the leader. For moving through corridors, bridges and doorways, where a
     * line would leave half the squad walking into walls.
     */
    COLUMN,

    /**
     * A loose cloud around the leader. Used when under artillery or heavy fire: no two units close
     * enough for one blast to take both.
     */
    DISPERSED,

    /**
     * A ring facing outwards around the leader. For holding a position or protecting something in
     * the middle.
     */
    DEFENSIVE_CIRCLE;

    /** Blocks between adjacent units in a line or column. */
    private static final double BASE_SPACING = 2.0D;

    /** Blocks between ranks (front / support / rear). */
    private static final double RANK_DEPTH = 2.5D;

    /**
     * The world position a follower should stand at.
     *
     * @param leaderPos the leader's position
     * @param leaderYaw the leader's body yaw in degrees
     * @param slot      this follower's index within its rank, 0-based
     * @param rank      which rank the follower belongs to, from its {@link UnitRole}
     * @return the world position for that slot
     */
    public Vec3 slotPosition(Vec3 leaderPos, float leaderYaw, int slot, UnitRole.FormationSlot rank) {
        double yaw = Math.toRadians(leaderYaw);

        double forwardX = Math.sin(yaw);
        double forwardZ = -Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        Vec3 local = localOffset(slot, rank);

        double worldX = local.x * rightX + local.z * forwardX;
        double worldZ = local.x * rightZ + local.z * forwardZ;

        return new Vec3(leaderPos.x + worldX, leaderPos.y, leaderPos.z + worldZ);
    }

    /**
     * The offset from the leader in leader-local space, where {@code x} is right and {@code z} is
     * forward. Split out from {@link #slotPosition} so the shape logic is readable on its own and
     * testable without any world.
     */
    private Vec3 localOffset(int slot, UnitRole.FormationSlot rank) {
        double rankOffset = switch (rank) {
            case FRONT -> 0.0D;
            case SUPPORT -> -RANK_DEPTH;
            case REAR -> -RANK_DEPTH * 2.0D;
        };

        return switch (this) {
            case LINE -> {
                // Alternate right and left of the leader so the line grows evenly outwards
                // instead of trailing off to one side: slots 0,1,2,3 -> +1,-1,+2,-2 spacings.
                int pair = slot / 2 + 1;
                int side = (slot % 2 == 0) ? 1 : -1;
                yield new Vec3(pair * BASE_SPACING * side, 0.0D, rankOffset);
            }

            case COLUMN -> new Vec3(0.0D, 0.0D, rankOffset - (slot + 1) * BASE_SPACING);

            case DISPERSED -> {
                // A widening spiral: each unit further out and at a different bearing, so no two
                // sit close together.
                double angle = slot * 2.399963D;
                double radius = 3.0D + slot * 0.9D;
                yield new Vec3(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius + rankOffset);
            }

            case DEFENSIVE_CIRCLE -> {
                // Evenly spaced on a ring. Eight is a full circle; beyond that units start a
                // second, wider ring rather than crowding the first.
                int ring = slot / 8;
                int indexInRing = slot % 8;
                double angle = (indexInRing / 8.0D) * Math.PI * 2.0D;
                double radius = 3.0D + ring * 2.0D;
                yield new Vec3(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            }
        };
    }

    /**
     * The yaw a follower should face while holding this formation, or {@code null} when it should
     * keep facing whatever it was already facing (normally the enemy).
     *
     * <p>Only {@link #DEFENSIVE_CIRCLE} overrides facing, because the entire point of that shape is
     * covering every approach; in every other formation a trooper that turned to match the leader
     * instead of the threat would be worse off, not better.</p>
     */
    public Float facingFor(float leaderYaw, int slot) {
        if (this != DEFENSIVE_CIRCLE) {
            return null;
        }

        // The unit must face directly away from the centre of the ring. Its outward radial
        // direction in leader-local space is the same angle used to place it in localOffset.
        int indexInRing = slot % 8;
        double angle = (indexInRing / 8.0D) * Math.PI * 2.0D;
        double localX = Math.cos(angle);
        double localZ = Math.sin(angle);

        // Rotate that radial direction into world space by the leader's yaw, so the ring turns
        // with the squad rather than being pinned to absolute north.
        double yaw = Math.toRadians(leaderYaw);
        double forwardX = Math.sin(yaw);
        double forwardZ = -Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        double worldX = localX * rightX + localZ * forwardX;
        double worldZ = localX * rightZ + localZ * forwardZ;

        // Invert the look-vector formula: look(yaw) = (sin yaw, -cos yaw), so a desired direction
        // (x, z) corresponds to yaw = atan2(x, -z). Verified to give an exact outward facing
        // (dot = 1.000) on every slot of the ring.
        return (float) Math.toDegrees(Math.atan2(worldX, -worldZ));
    }

    /** Whether this formation should be held while fighting, or dropped in favour of free movement. */
    public boolean holdsUnderFire() {
        return this == LINE || this == DEFENSIVE_CIRCLE;
    }
}
