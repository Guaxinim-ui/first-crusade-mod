package com.example.examplemod.progression;

import com.example.examplemod.ExampleMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the progression meets the game.
 *
 * <h2>Everything that is not an attribute lives here</h2>
 *
 * Flat stats are {@link PlayerProgressionAttributes}' job. Everything conditional — a multiplier that
 * only applies to Orks, a regeneration that only runs out of combat, a night vision that only exists
 * in the dark — has to be decided at the moment it matters, and this is that moment. Keeping the two
 * kinds apart is what stops the mod from computing the same bonus in two ways.
 *
 * <h2>Once a second, not once a tick</h2>
 *
 * The player tick fires twenty times a second for every player online. The heavy work — surgery,
 * regeneration, the organs' passive effects — is gated behind a one-second stride; only the ability
 * channel, which has to be responsive, runs every tick.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class PlayerProgressionEvents {
    private PlayerProgressionEvents() {
    }

    private static final int STRIDE = 20;

    // ==================================================================== size

    /** The player's body. Fires on both sides, for every player, which is what multiplayer needs. */
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        PlayerProgressionSizeManager.onSize(event);
    }

    // ==================================================================== lifecycle

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerProgressionManager.recalculate(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerProgressionAbilityManager.forget(player.getUUID());
            com.example.examplemod.progression.ork.PlayerOrkAbilityManager.forget(player.getUUID());
            PlayerProgressionManager.recalculate(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerProgressionManager.recalculate(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerProgressionAbilityManager.forget(event.getEntity().getUUID());
        com.example.examplemod.progression.ork.PlayerOrkAbilityManager
                .forget(event.getEntity().getUUID());
        PlayerProgressionSizeManager.forget(event.getEntity().getUUID());
    }

    /**
     * A client that just started seeing a player needs that player's stage, or it will draw and
     * collide with a Space Marine as though he were a Guardsman.
     *
     * <p>{@link PlayerProgressionNetwork#sync} only reaches the clients tracking the player when it
     * runs; this covers everyone who arrives afterwards.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher
                && event.getTarget() instanceof ServerPlayer subject) {
            PlayerProgressionNetwork.syncStageTo(watcher, subject);
        }
    }

    // ==================================================================== the tick

    /**
     * The pose keeper, on both sides and before vanilla decides.
     *
     * <p>{@code START} is not a preference: Forge fires it from the first line of
     * {@code Player.tick()}, and {@code updatePlayerPose()} reads the forced pose later in that same
     * tick. At {@code END} the decision would already have been made and the player already ejected.
     *
     * <p>Both sides, because each runs its own {@code updatePlayerPose}. Server-only would leave the
     * client standing the player up locally and then arguing with the server about where he is.
     */
    @SubscribeEvent
    public static void onPlayerPose(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            PlayerProgressionPose.tick(event.player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide) {
            return;
        }

        // Responsive half: the prayer channel and the stasis clock.
        PlayerProgressionAbilityManager.tick(player);

        // And the Ork half — the rallied Boyz. It returns on its first line unless somebody has
        // just shouted, so a world with no Ork order running pays one map lookup for it.
        com.example.examplemod.progression.ork.PlayerOrkAbilityManager.tick(player);

        if (player.tickCount % STRIDE != 0) {
            return;
        }

        if (!PlayerProgressionManager.isImperial(player)) {
            return;
        }

        PlayerProgressionProfile profile = PlayerProgressionManager.profile(player);

        PlayerProgressionManager.tickSurgery(player);
        tickOrgans(player, profile);
        tickOutOfCombatRegen(player, profile);
    }

    /**
     * The organs that are not numbers: sight in the dark, air under water, a body that does not
     * need to sleep.
     *
     * <p>Night vision is only applied in real darkness and always at a duration well past the point
     * vanilla starts flashing it, so it fades in rather than strobing at the edge of a torch.
     */
    private static void tickOrgans(ServerPlayer player, PlayerProgressionProfile profile) {
        boolean catalepsean = profile.hasImplant("catalepsean_node");
        boolean occulobe = profile.hasImplant("occulobe_lyman");
        boolean multiLung = profile.hasImplant("multi_lung");

        if (catalepsean || occulobe) {
            BlockPos pos = player.blockPosition();
            int light = player.level().getMaxLocalRawBrightness(pos);

            if (light <= 6) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false));
            }
        }

        if (catalepsean) {
            // A Space Marine does not owe the night anything, so the phantoms have no claim.
            player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        }

        if (occulobe) {
            player.removeEffect(MobEffects.CONFUSION);
            player.removeEffect(MobEffects.BLINDNESS);
        }

        if (multiLung) {
            player.setAirSupply(player.getMaxAirSupply());
            player.removeEffect(MobEffects.POISON);
        }

        if (profile.hasImplant("susan_melanochrome_oolitic") && player.isOnFire()) {
            player.clearFire();
        }
    }

    /**
     * Slow healing once the shooting has stopped.
     *
     * <p>Gated on three things at once, because any one of them alone makes it a nuisance: a combat
     * tag so it never heals mid-fight, a food floor so it is not free, and a rank so a player who
     * did not buy it gets nothing.
     */
    private static void tickOutOfCombatRegen(ServerPlayer player, PlayerProgressionProfile profile) {
        double rate = PlayerProgressionManager.aggregate(profile).outOfCombatRegen();
        if (rate <= 0.0D) {
            return;
        }

        long now = player.level().getGameTime();
        if (now - profile.lastDamagedAt < PlayerProgressionBalance.COMBAT_TAG_TICKS) {
            return;
        }

        if (player.getFoodData().getFoodLevel() < PlayerProgressionBalance.MIN_FOOD_FOR_REGEN) {
            return;
        }

        if (player.getHealth() < player.getMaxHealth()) {
            player.heal((float) rate);
        }
    }

    // ==================================================================== damage

    /**
     * Both halves of a hit: what the player deals, and what they take.
     *
     * <p>{@code LOW} priority so other mods' cancellations are respected first — a bonus applied to
     * a hit that never lands is a bonus computed for nothing.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        // ------------------------------------------------------------ dealing
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            float multiplier = PlayerProgressionCombat.outgoingMultiplier(
                    attacker, event.getEntity(), event.getSource().getDirectEntity());

            if (multiplier != 1.0F) {
                event.setAmount(event.getAmount() * multiplier);
            }
        }

        // ------------------------------------------------------------ taking
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }

        PlayerProgressionProfile profile = PlayerProgressionManager.profile(victim);
        profile.lastDamagedAt = victim.level().getGameTime();

        PlayerProgressionAbilityManager.interruptPrayer(victim);

        PlayerProgressionManager.Totals totals = PlayerProgressionManager.aggregate(profile);
        double reduction = totals.damageReduction();

        // War Skin only answers the world, not a blade.
        if (isEnvironmental(event)) {
            reduction = Math.max(reduction, totals.environmentalReduction());
        }

        if (reduction > 0.0D) {
            event.setAmount((float) (event.getAmount() * (1.0D - reduction)));
        }

        larramanClot(victim, profile, event.getAmount());
    }

    private static boolean isEnvironmental(LivingHurtEvent event) {
        return event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_DROWNING);
    }

    /** Larraman's organ: one emergency clot when the wound would otherwise be the last one. */
    private static void larramanClot(ServerPlayer player, PlayerProgressionProfile profile,
                                     float incoming) {
        if (!profile.hasImplant("larraman_organ")) {
            return;
        }

        long now = player.level().getGameTime();
        if (profile.larramanReadyAt() > now) {
            return;
        }

        float after = player.getHealth() - incoming;
        if (after > player.getMaxHealth() * PlayerProgressionBalance.LARRAMAN_THRESHOLD || after <= 0.0F) {
            return;
        }

        player.heal(PlayerProgressionBalance.LARRAMAN_HEAL);
        profile.setLarramanReadyAt(now + PlayerProgressionBalance.LARRAMAN_COOLDOWN_TICKS);
        PlayerProgressionManager.data(player.serverLevel()).markChanged();

        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.progression.larraman")
                        .withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        double bonus = PlayerProgressionManager.totals(player).healingReceived();
        if (bonus > 0.0D) {
            event.setAmount((float) (event.getAmount() * (1.0D + bonus)));
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        double cut = PlayerProgressionManager.totals(player).fallDamageCut();

        // A landing inside the roll's window is forgiven outright.
        if (PlayerProgressionAbilityManager.isRollingGrace(player, player.level().getGameTime())) {
            cut = Math.max(cut, 0.75D);
        }

        if (cut > 0.0D) {
            event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1.0D - cut)));
        }
    }

    /** The Emperor's Vigil: bad things last less long. Good things are left alone. */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().isBeneficial()) {
            return;
        }

        double cut = PlayerProgressionManager.totals(player).debuffDurationCut();
        if (cut <= 0.0D) {
            return;
        }

        // Rebuilt rather than mutated: the duration field is not ours to write behind the game's
        // back, and a shortened copy applied over the original is what the effect system expects.
        int shortened = Math.max(1, (int) (instance.getDuration() * (1.0D - cut)));
        player.forceAddEffect(new MobEffectInstance(instance.getEffect(), shortened,
                instance.getAmplifier(), instance.isAmbient(), instance.isVisible()), null);
    }

    // ==================================================================== kills and the trial

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }

        LivingEntity victim = event.getEntity();
        int xp = PlayerProgressionCombat.xpFor(victim);
        if (xp <= 0) {
            return;
        }

        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        PlayerProgressionProfile profile = data.profile(player.getUUID());

        if (!countsAsRealKill(profile, player, victim)) {
            return;
        }

        // Battle memory: a streak pays a little more, and only while the streak is warm.
        int memoryRank = profile.rank("battle_memory");
        long now = player.level().getGameTime();

        if (memoryRank > 0) {
            if (now > profile.memoryExpiresAt) {
                profile.memoryStacks = 0;
            }
            profile.memoryStacks = Math.min(PlayerProgressionBalance.MEMORY_MAX_STACKS,
                    profile.memoryStacks + 1);
            profile.memoryExpiresAt = now + PlayerProgressionBalance.MEMORY_WINDOW_TICKS;

            xp += (int) Math.round(xp * memoryRank * 0.05D * profile.memoryStacks);
        }

        PlayerProgressionManager.awardXp(player, xp);

        // The Blood Trial counts nothing that happened before the Black Carapace.
        if (profile.stage() == PlayerEvolutionStage.NEOPHYTE && !profile.trialComplete()) {
            profile.countTrialKill(PlayerProgressionCombat.isEliteOrk(victim),
                    PlayerProgressionCombat.isWarboss(victim));

            if (profile.trialRequirementsMet()) {
                profile.setTrialComplete(true);
                player.sendSystemMessage(
                        Component.translatable("msg.firstcrusade.progression.trial_complete")
                                .withStyle(ChatFormatting.GOLD));
            }

            data.markChanged();
        }
    }

    /**
     * The anti-farm guard.
     *
     * <p>Three separate ways to earn nothing: the player summoned it, the same kind of body has
     * piled up in the same window, or the kill happened too far away to have been a fight. None of
     * them alone is enough — a legitimate long session kills plenty of Boyz — so the limit is
     * generous and the window is short.
     */
    /**
     * Shared with the Ork progression.
     *
     * <p>The anti-farm window lives on the outer {@link PlayerProgressionProfile}, which every
     * player has whichever side they picked, so both progressions are throttled by the same counter
     * rather than by two that could be gamed against each other. Made public rather than copied:
     * a second implementation of this would be a second implementation to get wrong.
     */
    public static boolean countsAsRealKill(PlayerProgressionProfile profile, ServerPlayer player,
                                            LivingEntity victim) {
        if (profile.summonedEntityIds.contains(victim.getId())) {
            return false;
        }

        if (player.distanceToSqr(victim) > PlayerProgressionBalance.FARM_RADIUS
                * PlayerProgressionBalance.FARM_RADIUS * 16.0D) {
            return false;
        }

        long now = player.level().getGameTime();
        if (now - profile.recentKillsWindowStart > PlayerProgressionBalance.FARM_WINDOW_TICKS) {
            profile.recentKillsWindowStart = now;
            profile.recentKills.clear();
        }

        String key = victim.getType().toString();
        int seen = profile.recentKills.merge(key, 1, Integer::sum);

        return seen <= PlayerProgressionBalance.FARM_SAME_TYPE_LIMIT;
    }

    // ==================================================================== stasis and equipment

    /** Sus-an is stasis, not a stance: no attacking your way out of it. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (PlayerProgressionAbilityManager.isInStasis(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();

        if (PlayerProgressionAbilityManager.isInStasis(player)) {
            event.setCanceled(true);
            return;
        }

        if (!(player instanceof ServerPlayer server)) {
            return;
        }

        if (!gateEquipment(server, event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether this player may use this piece of wargear, and the message when they may not.
     *
     * <p>Refusal cancels the use and nothing else: the item stays in the hand it was in. A gate that
     * deleted or moved the Bolter would be a gate that eats a player's gear over a missing implant.
     * The message is on the action bar and only fires on an actual attempt, so it cannot spam.
     */
    private static boolean gateEquipment(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        PlayerEvolutionStage required = PlayerProgressionEquipment.requiredStage(stack.getItem());
        if (required == null) {
            return true;
        }

        PlayerEvolutionStage stage = PlayerProgressionManager.profile(player).stage();
        if (stage.isAtLeast(required)) {
            return true;
        }

        player.displayClientMessage(Component.translatable(
                "msg.firstcrusade.progression.equipment_locked",
                stack.getHoverName(), required.displayName()).withStyle(ChatFormatting.RED), true);

        return false;
    }
}
