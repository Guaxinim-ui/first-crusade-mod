package com.example.examplemod.weapon;

import com.example.examplemod.LasgunShotEntity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns weapon projectiles from the barrel rather than from the shooter's face.
 *
 * <p>Vanilla's {@code AbstractArrow(EntityType, LivingEntity, Level)} constructor places the
 * projectile at the shooter's eye position and there is no setter that reliably re-seats it before
 * the first tick — so the fix is to construct the projectile, then explicitly move it to the muzzle
 * point supplied by {@link FCWeaponMount} and shoot it along the muzzle→target line.</p>
 *
 * <p>Everything here runs server-side; the muzzle flash is emitted through
 * {@link ServerLevel#sendParticles} so it reaches every tracking client without a custom packet.</p>
 */
public final class FCProjectiles {

    /** Base spread in degrees applied at accuracy 1.0. Scaled up as accuracy falls. */
    private static final float BASE_SPREAD_DEGREES = 0.6F;

    /** Spread added at accuracy 0.0, i.e. the worst case for a wildly firing Ork. */
    private static final float MAX_EXTRA_SPREAD_DEGREES = 11.0F;

    private FCProjectiles() {
    }

    /**
     * Fires a las-bolt from the shooter's muzzle at the given target.
     *
     * @param shooter   the unit pulling the trigger
     * @param target    what it is shooting at
     * @param mount     where its barrel is
     * @param velocity  bolt speed in blocks per tick (lasguns sit around 2.4)
     * @param accuracy  0..1 from {@link com.example.examplemod.unit.profile.FCCombatProfile#accuracy}
     * @param damage    damage the bolt deals on a clean hit
     * @return the spawned bolt, or {@code null} on the client
     */
    public static LasgunShotEntity fireLasBolt(LivingEntity shooter,
                                               LivingEntity target,
                                               FCWeaponMount mount,
                                               float velocity,
                                               float accuracy,
                                               double damage) {
        if (shooter.level().isClientSide) {
            return null;
        }

        Vec3 muzzle = mount.muzzleWorldPosition(shooter);

        // Aim at the target's centre of mass, not its feet: shooting at position() makes troops
        // consistently shoot the ground in front of a target standing on a slope.
        Vec3 aimPoint = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 direction = mount.firingDirection(shooter, aimPoint);

        LasgunShotEntity bolt = new LasgunShotEntity(shooter.level(), shooter);
        bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
        bolt.setBaseDamage(damage);

        bolt.shoot(direction.x, direction.y, direction.z, velocity, spreadFor(accuracy));

        shooter.level().addFreshEntity(bolt);
        spawnMuzzleFlash(shooter, mount, ParticleTypes.END_ROD, 3);

        return bolt;
    }

    /**
     * Fires an already-constructed projectile from the muzzle. Use this for weapons that do not
     * shoot las-bolts (bolters, shootas) so they get the same barrel-origin treatment.
     */
    public static void fireFromMuzzle(LivingEntity shooter,
                                      LivingEntity target,
                                      AbstractArrow projectile,
                                      FCWeaponMount mount,
                                      float velocity,
                                      float accuracy) {
        if (shooter.level().isClientSide) {
            return;
        }

        Vec3 muzzle = mount.muzzleWorldPosition(shooter);
        Vec3 aimPoint = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 direction = mount.firingDirection(shooter, aimPoint);

        projectile.setPos(muzzle.x, muzzle.y, muzzle.z);
        projectile.shoot(direction.x, direction.y, direction.z, velocity, spreadFor(accuracy));

        shooter.level().addFreshEntity(projectile);
    }

    /**
     * Converts an accuracy rating into the divergence value {@code AbstractArrow#shoot} expects.
     *
     * <p>A Guardsman at 0.92 gets roughly 1.5° of spread — tight but not robotic. An Ork at 0.55
     * gets about 5.6°, which at fifteen blocks is a metre and a half of scatter: visibly sloppy
     * without being useless.</p>
     */
    public static float spreadFor(float accuracy) {
        float clamped = Mth.clamp(accuracy, 0.0F, 1.0F);
        return BASE_SPREAD_DEGREES + (1.0F - clamped) * MAX_EXTRA_SPREAD_DEGREES;
    }

    /**
     * Emits a short flash at the muzzle. Deliberately a particle rather than a spawned entity, and
     * deliberately a small count — this fires several times a second per shooting trooper, and with
     * a large battle on screen the particle budget is the first thing to break.
     */
    public static void spawnMuzzleFlash(LivingEntity shooter, FCWeaponMount mount,
                                        ParticleOptions particle, int count) {
        if (!(shooter.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 muzzle = mount.muzzleWorldPosition(shooter);

        serverLevel.sendParticles(
                particle,
                muzzle.x, muzzle.y, muzzle.z,
                count,
                0.02D, 0.02D, 0.02D,
                0.0D
        );
    }

    /** Ejects a spent casing particle from the weapon's ejection port. */
    public static void spawnCasingEject(LivingEntity shooter, FCWeaponMount mount) {
        if (!(shooter.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 eject = mount.ejectWorldPosition(shooter);

        serverLevel.sendParticles(
                ParticleTypes.CRIT,
                eject.x, eject.y, eject.z,
                1,
                0.05D, 0.05D, 0.05D,
                0.02D
        );
    }

    /** Plays a weapon report at the muzzle so the sound is positioned on the barrel. */
    public static void playWeaponSound(LivingEntity shooter, FCWeaponMount mount,
                                       SoundEvent sound, float volume, float pitch) {
        if (shooter.level().isClientSide || sound == null) {
            return;
        }

        Vec3 muzzle = mount.muzzleWorldPosition(shooter);

        shooter.level().playSound(
                null,
                muzzle.x, muzzle.y, muzzle.z,
                sound,
                SoundSource.HOSTILE,
                volume,
                pitch
        );
    }
}
