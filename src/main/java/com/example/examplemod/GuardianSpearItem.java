package com.example.examplemod;

import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * Guardian Spear — the halberd of the Adeptus Custodes. A true hybrid weapon: it swings as a
 * heavy melee blade (vanilla {@link SwordItem} behaviour, like the Chainsword) and its built-in
 * bolter fires on right-click (same pattern as {@link BolterItem}, reusing {@link LasgunShotEntity}).
 */
public class GuardianSpearItem extends SwordItem {
    private static final int FIRE_COOLDOWN_TICKS = 20;
    private static final double BOLT_DAMAGE = 11.0D;

    public GuardianSpearItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack spear = player.getItemInHand(hand);
        boolean isCreative = player.getAbilities().instabuild;

        player.getCooldowns().addCooldown(this, FIRE_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                FCWeaponSounds.BOLTER_FIRE.get(),
                SoundSource.PLAYERS,
                0.9F,
                0.9F
        );

        if (!level.isClientSide) {
            LasgunShotEntity projectile = new LasgunShotEntity(level, player);
            projectile.setBaseDamage(BOLT_DAMAGE);
            projectile.setKnockback(1);
            projectile.setMicroExplosive(true);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 4.2F, 0.5F);

            level.addFreshEntity(projectile);

            if (!isCreative) {
                spear.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(spear, level.isClientSide);
    }
}
