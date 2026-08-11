package com.example.examplemod.progression;

import com.example.examplemod.GretchinEntity;
import com.example.examplemod.KillaKanEntity;
import com.example.examplemod.LasgunShotEntity;
import com.example.examplemod.MeganobEntity;
import com.example.examplemod.OrkBoyEntity;
import com.example.examplemod.OrkNobEntity;
import com.example.examplemod.WarbossEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The one place the progression touches a weapon.
 *
 * <h2>Why the items call in here</h2>
 *
 * A Lasgun's fire rate lives inside {@code LasgunItem} as a private constant, and so do the Bolter's
 * and the Plasma Gun's. There is no way to shorten them from outside, and copying the reduction into
 * each item is three places to keep in step. So each weapon asks this class for its cooldown instead
 * of hard-coding it — one line changed per item, one rule in one file, and a weapon added tomorrow
 * gets the behaviour by calling the same method.
 *
 * <h2>What counts as "a personal weapon"</h2>
 *
 * All three guns fire a {@link LasgunShotEntity}, and so do the Guard's own troops. The filter that
 * separates a player's shot from a Guardsman's is therefore not the projectile type but its
 * <b>owner</b>: the bonus applies when the player fired it and never when a soldier, a turret or a
 * vehicle did. That is exactly the distinction the design asks for, and it needs no list of excluded
 * sources to maintain.
 */
public final class PlayerProgressionCombat {
    private PlayerProgressionCombat() {
    }

    // ==================================================================== weapons

    /**
     * A weapon's cooldown after Fire Discipline.
     *
     * <p>Call site: every {@code addCooldown} in a personal weapon. Non-players and unprogressed
     * players get the base value back unchanged, so the call is safe from anywhere.
     */
    public static int cooldownTicks(Player player, int baseTicks) {
        if (!(player instanceof ServerPlayer server)) {
            return baseTicks;
        }

        double cut = PlayerProgressionManager.totals(server).weaponCooldownCut();
        return Math.max(1, (int) Math.round(baseTicks * (1.0D - cut)));
    }

    /**
     * A med-kit's healing after Field Recovery.
     *
     * <p>Same shape as the cooldown: the item asks, this decides.
     */
    public static float healAmount(Player player, float baseHeal) {
        if (!(player instanceof ServerPlayer server)) {
            return baseHeal;
        }

        double bonus = PlayerProgressionManager.totals(server).medkitPower();
        return (float) (baseHeal * (1.0D + bonus));
    }

    /** Muzzle velocity after Marksman's Eye — a faster bolt drops less over its flight. */
    public static float projectileSpeed(Player player, float baseSpeed) {
        if (!(player instanceof ServerPlayer server)) {
            return baseSpeed;
        }

        double accuracy = PlayerProgressionManager.totals(server).weaponAccuracy();
        return (float) (baseSpeed * (1.0D + accuracy));
    }

    /**
     * Shot spread after Marksman's Eye and Moving Fire.
     *
     * <p>The mod's guns pass an inaccuracy value to the projectile; this tightens it rather than
     * inventing a second dispersion system alongside the one that already exists.
     */
    public static float inaccuracy(Player player, float baseInaccuracy) {
        if (!(player instanceof ServerPlayer server)) {
            return baseInaccuracy;
        }

        PlayerProgressionManager.Totals totals = PlayerProgressionManager.totals(server);
        double tighter = totals.weaponAccuracy();

        // Moving Fire only pays while actually moving — standing still it changes nothing.
        if (isMoving(server)) {
            int rank = PlayerProgressionManager.rankOf(server, "moving_fire");
            tighter += rank * 0.1D;
        }

        return (float) Math.max(0.0D, baseInaccuracy * (1.0D - Math.min(0.75D, tighter)));
    }

    private static boolean isMoving(Player player) {
        return player.getDeltaMovement().horizontalDistanceSqr() > 0.001D;
    }

    // ==================================================================== damage multipliers

    /**
     * The multiplier for damage this player is dealing to this victim.
     *
     * <p>Read by the hurt event, which is the only caller. Returns 1.0 for anyone with no relevant
     * ranks, so the event can multiply unconditionally.
     */
    public static float outgoingMultiplier(ServerPlayer attacker, LivingEntity victim,
                                           Entity directSource) {
        PlayerProgressionManager.Totals totals = PlayerProgressionManager.totals(attacker);
        double multiplier = 1.0D;

        // Ranged: only the player's own shot from one of the mod's personal weapons.
        if (directSource instanceof LasgunShotEntity shot && shot.getOwner() == attacker) {
            multiplier += totals.rangedDamage();
        }

        if (isOrk(victim)) {
            multiplier += totals.antiOrkDamage();

            if (isEliteOrk(victim)) {
                multiplier += totals.antiEliteDamage();
            }
        }

        return (float) multiplier;
    }

    public static boolean isOrk(Entity entity) {
        return entity instanceof GretchinEntity
                || entity instanceof OrkBoyEntity
                || isEliteOrk(entity);
    }

    /** The greenskins worth naming: what Xenos Executioner and the Blood Trial both count. */
    public static boolean isEliteOrk(Entity entity) {
        return entity instanceof OrkNobEntity
                || entity instanceof MeganobEntity
                || entity instanceof KillaKanEntity
                || entity instanceof WarbossEntity;
    }

    public static boolean isWarboss(Entity entity) {
        return entity instanceof WarbossEntity;
    }

    /** Progression experience a kill is worth, before the anti-farm guard has its say. */
    public static int xpFor(Entity victim) {
        if (victim instanceof WarbossEntity) {
            return PlayerProgressionBalance.XP_WARBOSS;
        }
        if (victim instanceof MeganobEntity) {
            return PlayerProgressionBalance.XP_MEGANOB;
        }
        if (victim instanceof KillaKanEntity) {
            return PlayerProgressionBalance.XP_KILLA_KAN;
        }
        if (victim instanceof OrkNobEntity) {
            return PlayerProgressionBalance.XP_ORK_NOB;
        }
        if (victim instanceof OrkBoyEntity) {
            return PlayerProgressionBalance.XP_ORK_BOY;
        }
        if (victim instanceof GretchinEntity) {
            return PlayerProgressionBalance.XP_GRETCHIN;
        }

        // Everything else — citizens, troops, animals, other players — is worth nothing, by design.
        return 0;
    }
}
