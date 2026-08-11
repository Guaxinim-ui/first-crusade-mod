package com.example.examplemod.progression.ork;

import com.example.examplemod.OrkClan;
import com.example.examplemod.progression.PlayerProgressionClientView;
import com.example.examplemod.progression.PlayerProgressionProfile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The one place a WAAAGH rank becomes a number a weapon or a blow can use.
 *
 * <h2>Why this exists at all</h2>
 *
 * {@code ShootaItem} shipped with three constants in it — a cooldown, a damage and a spread — and no
 * way for the Dakka branch to reach them. The fix is not to put tree lookups inside the item: an item
 * is a description of a thing, not a place for progression logic, and the next Ork gun would have
 * copied the same code. The item asks a question here and gets a number back, and every Dakka node in
 * the tree is spelled out exactly once, in this file.
 *
 * <h2>It answers on both sides</h2>
 *
 * A gun's cooldown is applied on the client too — that is what draws the sweep over the hotbar icon,
 * and what stops the client sending a shot the server would refuse. So {@link #profileOf} resolves a
 * {@link ServerPlayer} against the real profile and anybody else against
 * {@link PlayerProgressionClientView}, which holds the local player's copy of exactly the same data
 * the server sent. Both sides compute the same cooldown from the same ranks.
 *
 * <p>Nothing here is <i>trusted</i> from the client: the projectile is created server-side, so the
 * damage, the spread and the extra bolts are all decided on the server whatever the client believed.
 * The client's copy only ever affects what the client draws.
 *
 * <h2>No state, no tick</h2>
 *
 * Every method is a pure function of a profile that already exists. Nothing is cached, nothing is
 * scheduled, and nothing runs unless a shot is fired or a blow lands.
 */
public final class PlayerOrkCombatModifiers {
    private PlayerOrkCombatModifiers() {
    }

    // ==================================================================== side-aware lookup

    /**
     * The Ork profile for this player, whichever side is asking, or null when there is none.
     *
     * <p>On the client only the local player's profile is ever known, which is the only one whose
     * cooldown this client draws — so a null here on a remote player is correct, not a gap.
     */
    private static PlayerOrkProgressionProfile profileOf(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return PlayerOrkProgressionManager.profile(serverPlayer);
        }

        PlayerProgressionProfile self = PlayerProgressionClientView.self();
        return self == null ? null : self.ork();
    }

    /** Whether this player fights for the WAAAGH, asked of whichever side has the answer. */
    public static boolean isOrk(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return PlayerOrkProgressionRequirements.isOrk(serverPlayer);
        }

        return PlayerProgressionClientView.isOrk();
    }

    /** The profile to read, or null when this player has no business having Ork bonuses. */
    private static PlayerOrkProgressionProfile orkOf(Player player) {
        return isOrk(player) ? profileOf(player) : null;
    }

    // ==================================================================== dakka

    /**
     * How long the Shoota rests between squeezes.
     *
     * <p>Clamped to {@link PlayerOrkProgressionBalance#SHOOTA_MIN_COOLDOWN_TICKS} rather than allowed
     * to reach zero: a weapon with no cooldown is a weapon whose client-side and server-side rate of
     * fire disagree on any real connection.
     */
    public static int shootaCooldownTicks(Player player) {
        PlayerOrkProgressionProfile ork = orkOf(player);
        int cooldown = PlayerOrkProgressionBalance.SHOOTA_BASE_COOLDOWN_TICKS;

        if (ork != null) {
            cooldown -= ork.rank("more_dakka")
                    * PlayerOrkProgressionBalance.MORE_DAKKA_COOLDOWN_PER_RANK;

            if (ork.rank("neva_enuff_dakka") > 0) {
                cooldown -= PlayerOrkProgressionBalance.NEVA_ENUFF_DAKKA_COOLDOWN;
            }
        }

        return Math.max(PlayerOrkProgressionBalance.SHOOTA_MIN_COOLDOWN_TICKS, cooldown);
    }

    /** What one bolt is worth. Bad Moons buy better shootas, so theirs bite harder. */
    public static double shootaDamage(Player player) {
        PlayerOrkProgressionProfile ork = orkOf(player);
        double damage = PlayerOrkProgressionBalance.SHOOTA_BASE_DAMAGE;

        if (ork == null) {
            return damage;
        }

        damage += ork.rank("basic_dakka")
                * PlayerOrkProgressionBalance.BASIC_DAKKA_DAMAGE_PER_RANK;

        if (ork.rank("neva_enuff_dakka") > 0) {
            damage += PlayerOrkProgressionBalance.NEVA_ENUFF_DAKKA_DAMAGE;
        }

        if (ork.clan() == OrkClan.BAD_MOONS) {
            damage *= 1.0D + PlayerOrkProgressionBalance.BAD_MOONS_RANGED;
        }

        return damage;
    }

    /**
     * The spread on a bolt.
     *
     * <p>Sprinting adds spread — an Ork running full tilt is not aiming — and DAKKA ON DA MOVE is the
     * node that buys it back, which is precisely what its name promises.
     */
    public static float shootaInaccuracy(Player player) {
        PlayerOrkProgressionProfile ork = orkOf(player);
        float spread = PlayerOrkProgressionBalance.SHOOTA_BASE_INACCURACY;

        boolean moving = player.isSprinting();
        int onTheMove = ork == null ? 0 : ork.rank("dakka_on_the_move");

        if (moving) {
            // The penalty is cancelled a third at a time, so the node is felt at every rank rather
            // than only at the last one.
            float penalty = PlayerOrkProgressionBalance.SHOOTA_SPRINT_SPREAD
                    * (1.0F - Math.min(1.0F, onTheMove / 3.0F));
            spread += penalty;
        }

        spread -= onTheMove * PlayerOrkProgressionBalance.DAKKA_ON_THE_MOVE_SPREAD_PER_RANK;

        return Math.max(PlayerOrkProgressionBalance.SHOOTA_MIN_INACCURACY, spread);
    }

    /**
     * How many <i>extra</i> bolts this squeeze produced.
     *
     * <p>Rolled once per shot on the server, and capped, so the burst is a burst and never a stream.
     */
    public static int shootaExtraShots(Player player, RandomSource random) {
        PlayerOrkProgressionProfile ork = orkOf(player);
        if (ork == null) {
            return 0;
        }

        double chance = ork.rank("dakka_dakka_dakka")
                * PlayerOrkProgressionBalance.DAKKA_DAKKA_EXTRA_SHOT_PER_RANK;

        if (ork.rank("neva_enuff_dakka") > 0) {
            chance += PlayerOrkProgressionBalance.NEVA_ENUFF_DAKKA_EXTRA_SHOT;
        }

        int extra = 0;
        // Each roll is independent and the whole thing stops at the cap, so a maxed Warboss averages
        // well under two extra bolts instead of ever firing a magazine in one tick.
        while (extra < PlayerOrkProgressionBalance.SHOOTA_MAX_EXTRA_SHOTS
                && random.nextDouble() < chance) {
            extra++;
            chance -= 0.5D;
        }

        return extra;
    }

    // ==================================================================== melee

    /**
     * The multiplier on a swing, with everything conditional already decided.
     *
     * @param victim   what is being hit — needed for the elite and the rear-arc checks
     * @param sprinting whether the blow was thrown at a run
     */
    public static double meleeMultiplier(Player player, PlayerOrkProgressionProfile ork,
                                         LivingEntity victim, boolean sprinting) {
        double multiplier = 1.0D;

        multiplier += ork.rank("harder_hitting")
                * PlayerOrkProgressionBalance.HARDER_HITTING_PER_RANK;

        if (PlayerOrkCombatEvents.isOrkMelee(player.getMainHandItem())) {
            multiplier += ork.rank("big_choppa") * PlayerOrkProgressionBalance.BIG_CHOPPA_PER_RANK;
        }

        if (PlayerOrkProgressionCombat.isElite(victim)) {
            multiplier += ork.rank("krump_everything")
                    * PlayerOrkProgressionBalance.KRUMP_EVERYTHING_PER_RANK;
        }

        // SNEAKY GIT — the victim is not looking at him.
        int sneaky = ork.rank("sneaky_git");
        if (sneaky > 0 && isBehind(player, victim)) {
            multiplier += sneaky * PlayerOrkProgressionBalance.SNEAKY_GIT_PER_RANK;
        }

        // KRUMP FIRST — the opening blow, on something nobody has touched yet.
        if (ork.rank("krump_first") > 0 && victim.getHealth() >= victim.getMaxHealth()) {
            multiplier += PlayerOrkProgressionBalance.KRUMP_FIRST_OPENER;
        }

        // RUN AN 'IT — only if he actually ran into it.
        if (ork.rank("run_and_hit") > 0 && sprinting) {
            multiplier += PlayerOrkProgressionBalance.RUN_AND_HIT_SPRINT_DAMAGE;
        }

        if (ork.rank("brutal_but_kunnin") > 0) {
            multiplier += PlayerOrkProgressionBalance.BRUTAL_BUT_KUNNIN_MELEE;
        }

        if (ork.clan() == OrkClan.GOFFS) {
            multiplier += PlayerOrkProgressionBalance.GOFFS_MELEE;
        }

        return multiplier;
    }

    /**
     * Whether the attacker is in the victim's rear arc.
     *
     * <p>Compared against where the victim is <i>looking</i> rather than where it is walking: a mob
     * backing away while facing the player has not turned its back on him.
     */
    private static boolean isBehind(Player player, Entity victim) {
        Vec3 facing = victim.getLookAngle();
        Vec3 toAttacker = player.position().subtract(victim.position());

        if (toAttacker.lengthSqr() < 1.0E-4D) {
            return false;
        }

        // Flattened: standing on a mob's head is not standing behind it.
        Vec3 flatFacing = new Vec3(facing.x, 0.0D, facing.z);
        Vec3 flatToAttacker = new Vec3(toAttacker.x, 0.0D, toAttacker.z);

        if (flatFacing.lengthSqr() < 1.0E-4D || flatToAttacker.lengthSqr() < 1.0E-4D) {
            return false;
        }

        return flatFacing.normalize().dot(flatToAttacker.normalize())
                < -PlayerOrkProgressionBalance.SNEAKY_GIT_REAR_ARC;
    }

    /**
     * The fraction of an incoming blow this Ork shrugs off.
     *
     * <p>Each source is capped and the total is capped again. Two ceilings rather than one because a
     * future node added to the sum must not be able to push the total past the design's limit by
     * itself, and a rebalance of an existing node must not either.
     */
    public static double damageReduction(PlayerOrkProgressionProfile ork) {
        double reduction = Math.min(PlayerOrkProgressionBalance.DOESNT_HURT_MUCH_CAP,
                ork.rank("doesnt_hurt_much") * PlayerOrkProgressionBalance.DOESNT_HURT_MUCH_PER_RANK);

        if (ork.rank("kunnin_but_brutal") > 0) {
            reduction += PlayerOrkProgressionBalance.KUNNIN_BUT_BRUTAL_REDUCTION;
        }

        return Math.min(PlayerOrkProgressionBalance.DAMAGE_REDUCTION_CEILING, reduction);
    }

    // ==================================================================== fury

    /**
     * Scales a Fury award by the nodes that make an Ork angrier.
     *
     * <p>Applied at the award rather than to the stored value, so the cap and the decay arithmetic in
     * {@link PlayerOrkProgressionProfile} stay exactly as they were.
     */
    public static int scaleFury(PlayerOrkProgressionProfile ork, int amount) {
        if (amount <= 0) {
            return 0;
        }

        double multiplier = 1.0D
                + ork.rank("louder_waaagh") * PlayerOrkProgressionBalance.LOUDER_WAAAGH_PER_RANK;

        if (ork.rank("da_greenest") > 0) {
            multiplier += PlayerOrkProgressionBalance.DA_GREENEST_FURY;
        }

        return (int) Math.max(1L, Math.round(amount * multiplier));
    }

    // ==================================================================== weapon helper

    /** Whether this stack is the Orks' own gun. One method so the next Ork gun is one edit. */
    public static boolean isShoota(ItemStack stack) {
        return stack.getItem() instanceof com.example.examplemod.ShootaItem;
    }
}
