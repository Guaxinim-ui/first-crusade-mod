package com.example.examplemod.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a weapon actually sits on a unit, and — the part that matters for gameplay — where its
 * muzzle is in world space.
 *
 * <p>The bug this exists to kill: {@code new LasgunShotEntity(level, shooter)} inherits
 * {@code AbstractArrow(EntityType, LivingEntity, Level)}, which spawns the projectile at the
 * shooter's EYE position. Shots therefore left the middle of the trooper's face rather than the end
 * of the barrel — visible as bolts clipping through the model and, worse, as shots that hit cover
 * the barrel was clear of.</p>
 *
 * <p>A mount is declared in MODEL SPACE, matching the bone layout in the {@code .geo.json}: X right,
 * Y up, Z forward, in the same 1/16-block pixel units Blockbench uses. {@link #muzzleWorldPosition}
 * then rotates that offset by the unit's body yaw and head pitch and adds it to the unit's position,
 * producing the world point the projectile should be born at.</p>
 *
 * <p>Because the offsets are pixel-space and mirror the geo file, the muzzle stays glued to the
 * barrel when the model changes: move the {@code muzzle} bone in Blockbench, copy the same numbers
 * here, done.</p>
 *
 * @param muzzleX      sideways offset of the muzzle from the unit's centre, in pixels (positive = right)
 * @param muzzleY      height of the muzzle above the unit's feet, in pixels
 * @param muzzleZ      forward offset of the muzzle, in pixels (positive = forward)
 * @param ejectX       sideways offset of the casing ejection port, in pixels
 * @param ejectY       height of the ejection port above the feet, in pixels
 * @param ejectZ       forward offset of the ejection port, in pixels
 * @param aimFromMuzzle whether the shot's DIRECTION is also computed from the muzzle (true for
 *                      rifles) or from the eyes with the muzzle only as an origin (false for
 *                      shoulder-fired heavy weapons where the sight line differs from the barrel)
 */
public record FCWeaponMount(
        double muzzleX, double muzzleY, double muzzleZ,
        double ejectX, double ejectY, double ejectZ,
        boolean aimFromMuzzle
) {

    /** Blockbench works in 1/16-block pixels; the world works in blocks. */
    private static final double PIXELS_PER_BLOCK = 16.0D;

    /**
     * The Guardsman's lasgun, taken directly from the {@code muzzle} bone of
     * {@code guardsman_rifleman.geo.json} in the aim pose.
     *
     * <p><b>Sign convention:</b> Blockbench geometry treats <em>−Z</em> as forward (entities face
     * −Z), but this record treats <em>+Z</em> as forward because that is what the basis math in
     * {@link #localToWorld} produces. So a muzzle bone at geo {@code z = -12} becomes
     * {@code muzzleZ = 12} here. Getting this backwards is exactly how shots end up leaving the
     * back of a trooper's head, so it is spelled out rather than left implicit.</p>
     *
     * <p>Values: geo muzzle pivot {@code [0, 20.5, -12]}, geo casing_eject pivot
     * {@code [1, 21, -4]}.</p>
     */
    public static final FCWeaponMount GUARDSMAN_LASGUN = new FCWeaponMount(
            0.0D, 20.5D, 12.0D,
            1.0D, 21.0D, 4.0D,
            true
    );

    /**
     * A standard two-handed rifle held at the right shoulder: muzzle forward-right and roughly at
     * chest height. Generic fallback for rifle units that do not yet have their own geo-derived
     * mount.
     */
    public static final FCWeaponMount RIFLE_TWO_HANDED = new FCWeaponMount(
            3.0D, 22.0D, 12.0D,
            2.0D, 22.5D, 4.0D,
            true
    );

    /** A one-handed pistol: closer in, slightly lower, shorter barrel. Generic fallback. */
    public static final FCWeaponMount PISTOL_ONE_HANDED = new FCWeaponMount(
            4.0D, 21.0D, 7.0D,
            3.5D, 21.5D, 4.0D,
            true
    );

    /**
     * The Sergeant's laspistol.
     *
     * <p>Taken from the {@code pistol_muzzle} bone <b>as posed in the aim animation</b>, not from
     * its rest position in the geometry. Those differ a lot here: at rest the pistol hangs by the
     * hip at {@code [-5.5, 13.25, -5]}, but the aim pose extends the arm and brings the weapon
     * toward the centreline, putting the muzzle at {@code [-1.72, 21.61, -11.89]}. Shots are only
     * ever fired from the aim pose, so that is the position the projectile must originate from —
     * using the rest coordinates would spawn bolts down by the trooper's waist.</p>
     *
     * <p>Sign convention as for {@link #GUARDSMAN_LASGUN}: geo {@code -Z} forward becomes {@code +Z}
     * here, so geo {@code z = -11.89} becomes {@code muzzleZ = 11.89}.</p>
     */
    public static final FCWeaponMount SERGEANT_LASPISTOL = new FCWeaponMount(
            -1.72D, 21.61D, 11.89D,
            -1.0D, 21.9D, 6.0D,
            true
    );

    /** A hip- or shoulder-braced heavy weapon: wider, lower, much longer barrel. */
    public static final FCWeaponMount HEAVY_WEAPON = new FCWeaponMount(
            4.5D, 19.0D, 18.0D,
            3.0D, 19.5D, 6.0D,
            false
    );

    /** An Ork shoota, held wide and sloppily, muzzle high and off to the side. */
    public static final FCWeaponMount ORK_SHOOTA = new FCWeaponMount(
            5.0D, 24.0D, 13.0D,
            4.0D, 24.5D, 5.0D,
            true
    );

    /**
     * The world position the projectile should spawn at.
     *
     * <p>The mount offset is expressed relative to the unit facing +Z. It is rotated by the unit's
     * {@code yBodyRot} so the barrel follows the torso, and the forward component is additionally
     * pitched by {@code xRot} so that an aimed-up rifle puts its muzzle up rather than forward.</p>
     */
    public Vec3 muzzleWorldPosition(LivingEntity shooter) {
        return localToWorld(shooter, this.muzzleX, this.muzzleY, this.muzzleZ);
    }

    /** The world position spent casings should be thrown from. */
    public Vec3 ejectWorldPosition(LivingEntity shooter) {
        return localToWorld(shooter, this.ejectX, this.ejectY, this.ejectZ);
    }

    /**
     * The unit vector the shot should travel along.
     *
     * <p>When {@link #aimFromMuzzle} is true this is the muzzle→target line, so the bolt visually
     * leaves the barrel pointing at what it will hit. When false the unit's look vector is used
     * instead, which suits weapons braced away from the sight line.</p>
     */
    public Vec3 firingDirection(LivingEntity shooter, Vec3 targetPoint) {
        if (!this.aimFromMuzzle) {
            return shooter.getLookAngle().normalize();
        }

        Vec3 muzzle = muzzleWorldPosition(shooter);
        Vec3 delta = targetPoint.subtract(muzzle);

        if (delta.lengthSqr() < 1.0E-6D) {
            return shooter.getLookAngle().normalize();
        }

        return delta.normalize();
    }

    /**
     * Rotates a model-space pixel offset into world space around the shooter.
     *
     * <p>Yaw comes from the BODY rotation, not the head: the weapon is mounted to the torso, so it
     * must not swing when the unit merely glances sideways. Pitch comes from {@code xRot} and is
     * applied to the forward component only, which is what keeps the muzzle on the barrel as the
     * rifle elevates.</p>
     *
     * <p>The rotation is done by building the unit's own basis rather than by a raw trig rotation,
     * because Minecraft's yaw convention is easy to get backwards: at yaw 0 an entity faces
     * <em>−Z</em>, not +Z. Writing it as basis vectors makes the convention explicit and testable.</p>
     *
     * <p>Forward is {@code F = (sin(yaw), -cos(yaw))}, which is exactly the horizontal part of
     * {@code getLookAngle()}. Right is {@code F} turned 90° clockwise about Y, i.e.
     * {@code R = (-F.z, F.x) = (cos(yaw), sin(yaw))}. A local offset {@code (x, y, z)} then maps to
     * {@code x·R + y·UP + z·F}. Both axes are verified against {@code getLookAngle()} across the
     * full yaw range.</p>
     */
    private Vec3 localToWorld(LivingEntity shooter, double px, double py, double pz) {
        double x = px / PIXELS_PER_BLOCK;
        double y = py / PIXELS_PER_BLOCK;
        double z = pz / PIXELS_PER_BLOCK;

        // Pitch tilts the forward component up or down. Positive xRot looks DOWN in Minecraft,
        // hence the sign flip so that aiming upward raises the muzzle.
        double pitch = -Math.toRadians(shooter.getXRot());
        double pitchedY = y + z * Math.sin(pitch);
        double pitchedZ = z * Math.cos(pitch);

        double yaw = Math.toRadians(shooter.yBodyRot);
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);

        // Forward (matches getLookAngle horizontally) and right-hand side vectors.
        double forwardX = sinYaw;
        double forwardZ = -cosYaw;
        double rightX = cosYaw;
        double rightZ = sinYaw;

        double worldX = x * rightX + pitchedZ * forwardX;
        double worldZ = x * rightZ + pitchedZ * forwardZ;

        return new Vec3(
                shooter.getX() + worldX,
                shooter.getY() + pitchedY,
                shooter.getZ() + worldZ
        );
    }

    /**
     * Scales this mount for a unit of unusual size. A Nob is roughly 1.3x a Boy, so its muzzle sits
     * proportionally further out and higher, without needing a separately declared mount.
     */
    public FCWeaponMount scaled(double factor) {
        return new FCWeaponMount(
                this.muzzleX * factor, this.muzzleY * factor, this.muzzleZ * factor,
                this.ejectX * factor, this.ejectY * factor, this.ejectZ * factor,
                this.aimFromMuzzle
        );
    }
}
