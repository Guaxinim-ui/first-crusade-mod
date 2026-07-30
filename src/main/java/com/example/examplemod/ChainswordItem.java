package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * Chainsword with a real combat motor state.
 *
 * <p>The chain starts when its wielder enters combat, remains active while combat continues and
 * shuts down exactly 60 ticks (3 seconds) after the last combat refresh. The active timestamp is
 * stored on the ItemStack, so players and ordinary held-item renderers can use the same state for
 * the moving chain model.</p>
 */
public class ChainswordItem extends SwordItem {
    public static final int COMBAT_GRACE_TICKS = 60;

    private static final int LOOP_SOUND_INTERVAL_TICKS = 14;
    private static final int SHRED_FIRE_SECONDS = 3;

    private static final String TAG_ACTIVE_UNTIL = "FirstCrusadeChainswordActiveUntil";
    private static final String TAG_MOTOR_RUNNING = "FirstCrusadeChainswordMotorRunning";
    private static final String TAG_NEXT_LOOP = "FirstCrusadeChainswordNextLoop";

    public ChainswordItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        activate(stack, entity);
        return false;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        activate(stack, attacker);
        target.setSecondsOnFire(SHRED_FIRE_SECONDS);

        if (!attacker.level().isClientSide) {
            attacker.level().playSound(null, target.blockPosition(), FCWeaponSounds.CHAIN_SWORD_HIT.get(),
                    soundSource(attacker), 1.05F, 0.88F + attacker.getRandom().nextFloat() * 0.10F);
        }

        return super.hurtEnemy(stack, target, attacker);
    }

    /** Starts the motor or refreshes the 3-second post-combat shutdown timer. */
    public static void activate(ItemStack stack, LivingEntity wielder) {
        if (!(stack.getItem() instanceof ChainswordItem)) {
            return;
        }

        Level level = wielder.level();
        long now = level.getGameTime();
        CompoundTag tag = stack.getOrCreateTag();
        boolean wasRunning = tag.getBoolean(TAG_MOTOR_RUNNING) && tag.getLong(TAG_ACTIVE_UNTIL) > now;

        tag.putLong(TAG_ACTIVE_UNTIL, now + COMBAT_GRACE_TICKS);
        tag.putBoolean(TAG_MOTOR_RUNNING, true);

        if (!level.isClientSide && !wasRunning) {
            tag.putLong(TAG_NEXT_LOOP, now + 5L);
            level.playSound(null, wielder.blockPosition(), FCWeaponSounds.CHAIN_SWORD_START.get(),
                    soundSource(wielder), 0.95F, 0.94F + wielder.getRandom().nextFloat() * 0.08F);
        }
    }

    /** Keeps the saw alive without replaying its start sound every tick. */
    public static void keepRunning(ItemStack stack, LivingEntity wielder) {
        if (!(stack.getItem() instanceof ChainswordItem) || wielder.level().isClientSide) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        long now = wielder.level().getGameTime();
        if (tag.getLong(TAG_ACTIVE_UNTIL) - now <= 20L) {
            activate(stack, wielder);
        }
    }

    /** Plays loop/stop sounds and finalises state transitions. Called once per living-entity tick. */
    public static void serverTick(ItemStack stack, LivingEntity wielder) {
        if (!(stack.getItem() instanceof ChainswordItem) || wielder.level().isClientSide) {
            return;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }

        Level level = wielder.level();
        long now = level.getGameTime();
        boolean running = tag.getBoolean(TAG_MOTOR_RUNNING);
        boolean active = running && tag.getLong(TAG_ACTIVE_UNTIL) > now;

        if (active) {
            long nextLoop = tag.getLong(TAG_NEXT_LOOP);
            if (now >= nextLoop) {
                level.playSound(null, wielder.blockPosition(), FCWeaponSounds.CHAIN_SWORD_LOOP.get(),
                        soundSource(wielder), 0.62F, 0.96F + wielder.getRandom().nextFloat() * 0.08F);
                tag.putLong(TAG_NEXT_LOOP, now + LOOP_SOUND_INTERVAL_TICKS);
            }
            return;
        }

        if (running) {
            tag.putBoolean(TAG_MOTOR_RUNNING, false);
            tag.remove(TAG_NEXT_LOOP);
            level.playSound(null, wielder.blockPosition(), FCWeaponSounds.CHAIN_SWORD_STOP.get(),
                    soundSource(wielder), 0.85F, 0.92F + wielder.getRandom().nextFloat() * 0.06F);
        }
    }

    public static boolean isRunning(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        return tag != null
                && tag.getBoolean(TAG_MOTOR_RUNNING)
                && tag.getLong(TAG_ACTIVE_UNTIL) > level.getGameTime();
    }

    private static SoundSource soundSource(LivingEntity entity) {
        return entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
    }
}
