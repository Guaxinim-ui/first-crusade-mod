package com.example.examplemod;

import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Bolter — the iconic Adeptus Astartes ranged weapon. Heavier and far stronger than the Lasgun:
 * it needs no power cell (a Space Marine weapon is self-sufficient), but fires slower, kicks back
 * the target and wears its own durability per shot. Reuses {@link LasgunShotEntity} for the round.
 */
public class BolterItem extends Item {
    private static final int FIRE_COOLDOWN_TICKS = 24;
    private static final double BOLT_DAMAGE = 12.0D;

    public BolterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(TwoHandedGunClientExtensions.INSTANCE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bolter = player.getItemInHand(hand);
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
                0.7F
        );

        if (!level.isClientSide) {
            LasgunShotEntity projectile = new LasgunShotEntity(level, player);
            projectile.setBaseDamage(BOLT_DAMAGE);
            projectile.setKnockback(1);
            projectile.setMicroExplosive(true);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 4.4F, 0.6F);

            level.addFreshEntity(projectile);

            if (!isCreative) {
                bolter.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
        }

        return InteractionResultHolder.sidedSuccess(bolter, level.isClientSide);
    }
}
