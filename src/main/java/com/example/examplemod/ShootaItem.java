package com.example.examplemod;

import com.example.examplemod.progression.ork.PlayerOrkCombatModifiers;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Shoota — the Orks' crude dakka gun. It needs no ammo (Orks just spray), fires fast and weak, and
 * is wildly inaccurate (a Boy can't aim) — quantity of fire over quality. Poorly built, so it wears
 * out quickly. Reuses {@link LasgunShotEntity} for the round.
 *
 * <h2>The numbers are not in here</h2>
 *
 * The cooldown, the damage and the spread used to be three constants in this file, which meant the
 * Dakka branch of the WAAAGH tree could not touch the gun it is entirely about. They now come from
 * {@link PlayerOrkCombatModifiers}, which reads the player's ranks. This class asks; it does not
 * compute, and it never looks a node up itself — an item that knows about a progression tree is an
 * item every future Ork gun copies the tree lookup from.
 *
 * <p>The cooldown is asked for on both sides on purpose: the client draws the sweep over the icon and
 * refuses a shot of its own accord, so if it used the base number while the server used the improved
 * one, MOAR DAKKA would be a node the player paid for and could not feel. Everything that matters —
 * the projectile, its damage, its spread, whether a second bolt happened — is decided in the
 * server-side branch below.
 */
public class ShootaItem extends Item {
    public ShootaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack shoota = player.getItemInHand(hand);
        boolean isCreative = player.getAbilities().instabuild;

        player.getCooldowns().addCooldown(this, PlayerOrkCombatModifiers.shootaCooldownTicks(player));
        player.awardStat(Stats.ITEM_USED.get(this));

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                com.example.examplemod.FCWeaponSounds.SHOOTA_FIRE.get(),
                SoundSource.PLAYERS,
                0.7F,
                0.8F
        );

        if (!level.isClientSide) {
            double damage = PlayerOrkCombatModifiers.shootaDamage(player);
            float spread = PlayerOrkCombatModifiers.shootaInaccuracy(player);

            // One bolt always, plus whatever DAKKA DAKKA DAKKA rolled. The roll happens once and is
            // capped inside the modifiers, so a burst is a burst and never a stream.
            int bolts = 1 + PlayerOrkCombatModifiers.shootaExtraShots(player, player.getRandom());

            for (int shot = 0; shot < bolts; shot++) {
                LasgunShotEntity projectile = new LasgunShotEntity(level, player);
                projectile.setBaseDamage(damage);
                projectile.setKnockback(0);
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                        3.4F, spread);

                level.addFreshEntity(projectile);
            }

            if (!isCreative) {
                // One squeeze, one point of wear, however many bolts came out of it — a burst that
                // ate three durability would make the best Dakka node the fastest way to break the
                // gun it improves.
                shoota.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(shoota, level.isClientSide);
    }
}
