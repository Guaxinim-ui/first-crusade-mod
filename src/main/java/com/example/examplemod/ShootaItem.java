package com.example.examplemod;

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
 */
public class ShootaItem extends Item {
    private static final int FIRE_COOLDOWN_TICKS = 8;
    private static final double SHOT_DAMAGE = 4.0D;
    private static final float INACCURACY = 6.0F;

    public ShootaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack shoota = player.getItemInHand(hand);
        boolean isCreative = player.getAbilities().instabuild;

        player.getCooldowns().addCooldown(this, FIRE_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS,
                0.7F,
                0.8F
        );

        if (!level.isClientSide) {
            LasgunShotEntity projectile = new LasgunShotEntity(level, player);
            projectile.setBaseDamage(SHOT_DAMAGE);
            projectile.setKnockback(0);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.4F, INACCURACY);

            level.addFreshEntity(projectile);

            if (!isCreative) {
                shoota.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(shoota, level.isClientSide);
    }
}
