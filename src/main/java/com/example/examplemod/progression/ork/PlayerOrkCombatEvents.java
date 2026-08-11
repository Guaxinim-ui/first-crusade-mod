package com.example.examplemod.progression.ork;

import com.example.examplemod.ChoppaItem;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.PowerKlawItem;
import com.example.examplemod.progression.PlayerProgressionManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The nodes that change a blow, applied where blows are decided.
 *
 * <h2>Why these are not attributes</h2>
 *
 * {@link PlayerOrkProgressionAttributes} handles anything flat and unconditional — health, armour,
 * speed. These cannot be: "+4% melee damage" is a percentage of the hit, not a number added to an
 * attribute, and "+5% against elites", "+10% from behind" and "+15% at a run" are conditions that
 * only exist at the moment of the swing.
 *
 * <h2>Melee only</h2>
 *
 * The multiplier applies when the damage's direct source <i>is</i> the player — a swing. A bolt from
 * a Shoota has the projectile as its direct source and is deliberately untouched here; ranged is the
 * Dakka branch's business, and it is applied when the bolt is created.
 *
 * <h2>The arithmetic is not here either</h2>
 *
 * What each rank is worth lives in {@link PlayerOrkCombatModifiers}. This class decides <i>when</i>
 * to ask — which event, which side, whose blow — and applies the answer. The split is what lets the
 * screen show a player the same number the fight will use, without the screen having to reach into
 * an event handler.
 *
 * <h2>Cost</h2>
 *
 * Two early returns for anything that is not an Ork player hitting something or being hit. No tick,
 * no scan, nothing held between events.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOrkCombatEvents {
    private PlayerOrkCombatEvents() {
    }

    /**
     * {@code LOW} so other mods' cancellations land first, and so this multiplies a number that has
     * already been through the ordinary damage pipeline.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        dealing(event);
        taking(event);
    }

    // ==================================================================== what he deals

    private static void dealing(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !PlayerOrkProgressionRequirements.isOrk(player)) {
            return;
        }

        // A swing, not a shot: the direct source of a projectile is the projectile.
        if (event.getSource().getDirectEntity() != player) {
            return;
        }

        PlayerOrkProgressionProfile ork = PlayerOrkProgressionManager.profile(player);

        double multiplier = PlayerOrkCombatModifiers.meleeMultiplier(
                player, ork, event.getEntity(), player.isSprinting());

        if (multiplier != 1.0D) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    // ==================================================================== what he takes

    private static void taking(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !PlayerOrkProgressionRequirements.isOrk(player)) {
            return;
        }

        PlayerOrkProgressionProfile ork = PlayerOrkProgressionManager.profile(player);

        // NUM DOI TANTO and KUNNIN BUT BRUTAL. Capped inside the modifiers, per source and again in
        // total, so no rebalance of either can quietly turn into damage immunity.
        double reduction = PlayerOrkCombatModifiers.damageReduction(ork);

        if (reduction > 0.0D) {
            event.setAmount((float) (event.getAmount() * (1.0D - reduction)));
        }
    }

    // ==================================================================== NOT DEAD YET

    /**
     * The blow that should have finished him does not.
     *
     * <h3>Why this is a different event from everything else in this file</h3>
     *
     * {@code LivingHurtEvent} fires <b>before</b> armour, enchantments and absorption have had their
     * say, so its amount is not the damage the player is about to lose — comparing it against his
     * health would fire this on blows a Warboss in plate would have shrugged off, burning a five
     * minute cooldown on a scratch. {@code LivingDamageEvent} is the last stop before
     * {@code setHealth}: its amount is exactly what is about to come off, so "is this lethal" is an
     * honest question here and only here.
     *
     * <h3>Why not the death event</h3>
     *
     * By the time {@code LivingDeathEvent} fires the player is dead as far as half the game is
     * concerned, and reviving him from there means fighting the death screen, the inventory drop and
     * every other listener. Zeroing the damage before it lands has no side effects at all.
     *
     * <p>The cooldown lives in the profile rather than a static map, because a passive that resets
     * itself on every relog is a passive with no cooldown.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !PlayerOrkProgressionRequirements.isOrk(player)) {
            return;
        }

        PlayerOrkProgressionProfile ork = PlayerOrkProgressionManager.profile(player);

        if (ork.rank("not_dead_yet") <= 0) {
            return;
        }

        // Only a blow that would actually finish him.
        if (event.getAmount() < player.getHealth()) {
            return;
        }

        long now = player.level().getGameTime();
        if (ork.lastStandReadyAt() > now) {
            return;
        }

        // Set rather than reduced: the point is a known, readable outcome — two hearts and still
        // standing — not a random survivor's sliver that depends on how hard the blow was.
        event.setAmount(0.0F);
        player.setHealth(PlayerOrkProgressionBalance.NOT_DEAD_YET_HEALTH);
        player.setAbsorptionAmount(0.0F);

        ork.setLastStandReadyAt(now + PlayerOrkProgressionBalance.NOT_DEAD_YET_COOLDOWN_TICKS);
        PlayerProgressionManager.data(player.serverLevel()).markChanged();

        player.serverLevel().sendParticles(ParticleTypes.ANGRY_VILLAGER,
                player.getX(), player.getY() + 1.2D, player.getZ(), 16, 0.4D, 0.6D, 0.4D, 0.02D);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 1.0F, 0.7F);

        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.ork.not_dead_yet")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
    }

    /**
     * Whether this is an Ork blade.
     *
     * <p>Two {@code instanceof} for now because there are exactly two. The moment a third lands this
     * should become an item tag ({@code firstcrusade:ork_melee_weapons}) — the check is here, in one
     * method, precisely so that change is one edit.
     */
    public static boolean isOrkMelee(ItemStack stack) {
        return stack.getItem() instanceof ChoppaItem || stack.getItem() instanceof PowerKlawItem;
    }

    /** Kept for readers outside this package that only care whether a victim is worth extra. */
    public static boolean isElite(LivingEntity victim) {
        return PlayerOrkProgressionCombat.isElite(victim);
    }
}
