package com.example.examplemod.progression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.examplemod.AbstractImperialTroopEntity;
import com.example.examplemod.ImperialCitizenEntity;
import com.example.examplemod.PlayerFaction;
import com.example.examplemod.PlayerFactionData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The four active abilities: prayer, roll, acid and stasis.
 *
 * <h2>The server owns every one of them</h2>
 *
 * A key press sends a request and nothing else. Whether the player has the node, whether the
 * cooldown has expired, which way a roll may go and whether an acid spit has a legal target are all
 * decided here, on the server, with the client's opinion discarded. The client is told the outcome.
 *
 * <h2>Channelling is state, and state has to be somewhere</h2>
 *
 * The prayer takes two seconds of standing still, so something has to watch those two seconds. That
 * watch is {@link #tick}, called once per player per tick from the event handler, and the state it
 * reads is the map below — deliberately transient. A prayer interrupted by a server restart is a
 * prayer that did not happen, which is the correct outcome and needs no save format.
 */
public final class PlayerProgressionAbilityManager {
    private PlayerProgressionAbilityManager() {
    }

    /** Who is mid-prayer, and since when. */
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    /** Who is in Sus-an stasis, and until when. */
    private static final Map<UUID, Long> STASIS = new HashMap<>();

    /** Who rolled recently — the window in which a landing is forgiven. */
    private static final Map<UUID, Long> ROLL_GRACE = new HashMap<>();

    private record Channel(long startedAt, Vec3 origin) {
    }

    // ==================================================================== entry point

    /**
     * Handles an ability request.
     *
     * @return the message to show the player, or empty when the ability fired
     */
    public static Component use(ServerPlayer player, PlayerProgressionAbility ability) {
        if (!PlayerProgressionManager.isImperial(player)) {
            return Component.translatable("msg.firstcrusade.progression.ork_branch");
        }

        PlayerProgressionProfile profile = PlayerProgressionManager.profile(player);
        int rank = profile.rank(ability.nodeId());

        if (rank <= 0) {
            return Component.translatable("msg.firstcrusade.progression.ability_locked",
                    ability.displayName());
        }

        long now = player.level().getGameTime();

        return switch (ability) {
            case PRAYER -> startPrayer(player, profile, rank, now);
            case ROLL -> roll(player, profile, rank, now);
            case ACID -> acid(player, profile, rank, now);
            case STASIS -> stasis(player, profile, rank, now);
        };
    }

    private static Component cooldownMessage(long readyAt, long now) {
        int seconds = (int) Math.ceil((readyAt - now) / 20.0D);
        return Component.translatable("msg.firstcrusade.progression.on_cooldown", seconds);
    }

    // ==================================================================== prayer

    private static Component startPrayer(ServerPlayer player, PlayerProgressionProfile profile,
                                         int rank, long now) {
        if (profile.prayerReadyAt() > now) {
            return cooldownMessage(profile.prayerReadyAt(), now);
        }

        if (CHANNELS.containsKey(player.getUUID())) {
            return Component.translatable("msg.firstcrusade.progression.already_praying");
        }

        CHANNELS.put(player.getUUID(), new Channel(now, player.position()));

        player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.progression.praying")
                        .withStyle(ChatFormatting.GOLD), true);

        return Component.empty();
    }

    /**
     * Advances a prayer, and finishes or breaks it.
     *
     * <p>Movement is measured against where the prayer began rather than per-tick velocity: a player
     * nudged by a mob has not walked away, and a player who walked in a circle has. The channel also
     * slows them, so standing still is easy to do on purpose and hard to do by accident.
     */
    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = player.level().getGameTime();

        tickStasis(player, id, now);

        Channel channel = CHANNELS.get(id);
        if (channel == null) {
            return;
        }

        if (player.isDeadOrDying()) {
            CHANNELS.remove(id);
            return;
        }

        if (player.position().distanceToSqr(channel.origin())
                > PlayerProgressionBalance.PRAYER_MOVE_LIMIT * 400.0D) {
            breakPrayer(player, "msg.firstcrusade.progression.prayer_moved");
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(), 2, 0.3D, 0.5D, 0.3D, 0.01D);

        if (now - channel.startedAt() >= PlayerProgressionBalance.PRAYER_CHANNEL_TICKS) {
            CHANNELS.remove(id);
            completePrayer(player, now);
        }
    }

    /** Damage during the channel ends it. Called from the hurt event. */
    public static void interruptPrayer(ServerPlayer player) {
        if (CHANNELS.containsKey(player.getUUID())) {
            breakPrayer(player, "msg.firstcrusade.progression.prayer_broken");
        }
    }

    private static void breakPrayer(ServerPlayer player, String key) {
        CHANNELS.remove(player.getUUID());
        player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.2F);
        player.displayClientMessage(
                Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    /**
     * The prayer lands: absorption, and whatever else the faith branch has added to it.
     *
     * <p>Absorption is <i>set</i>, never added, and capped — a player who prays twice does not walk
     * around with forty golden hearts, which is the whole reason the effect is absorption and not
     * max health.
     */
    private static void completePrayer(ServerPlayer player, long now) {
        PlayerProgressionData data = PlayerProgressionManager.data(player.serverLevel());
        PlayerProgressionProfile profile = data.profile(player.getUUID());

        int prayerRank = profile.rank("prayer_to_the_emperor");
        int psalmRank = profile.rank("psalm_of_protection");

        float hearts = Math.min(PlayerProgressionBalance.PRAYER_MAX_ABSORPTION,
                (prayerRank + 1) * 2.0F);

        int duration = PlayerProgressionBalance.PRAYER_BASE_DURATION_TICKS
                + Math.min(PlayerProgressionBalance.PRAYER_MAX_BONUS_DURATION_TICKS,
                        psalmRank * 60);

        int cooldown = Math.max(PlayerProgressionBalance.PRAYER_MIN_COOLDOWN_TICKS,
                PlayerProgressionBalance.PRAYER_BASE_COOLDOWN_TICKS
                        - Math.min(PlayerProgressionBalance.PRAYER_MAX_COOLDOWN_CUT_TICKS,
                                psalmRank * 160));

        player.setAbsorptionAmount(Math.min(PlayerProgressionBalance.PRAYER_MAX_ABSORPTION, hearts));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 0, false, true));

        // Iron Liturgy — resistance in the Emperor's name.
        int liturgy = profile.rank("iron_liturgy");
        if (liturgy > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    liturgy * 60, 0, false, true));
        }

        // Mucranoid — a skin of wax against the flames.
        int mucranoid = profile.rank("mucranoid");
        if (mucranoid > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                    mucranoid * 60, 0, false, true));
        }

        // Psalm of Vigilance — the enemy stops being able to hide.
        int vigilance = profile.rank("psalm_of_vigilance");
        if (vigilance > 0) {
            markEnemies(player, 6.0D + vigilance * 2.0D, vigilance * 40);
        }

        // Litany of the Death Angel — the brothers beside you get a share.
        int litany = profile.rank("death_angel_litany");
        if (litany > 0) {
            shareWithAllies(player, litany);
        }

        profile.setPrayerReadyAt(now + cooldown);
        data.markChanged();

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.2D, player.getZ(),
                24, 0.5D, 0.8D, 0.5D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2D, player.getZ(),
                18, 0.5D, 0.8D, 0.5D, 0.02D);

        player.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.1F);
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.progression.prayer_answered")
                        .withStyle(ChatFormatting.GOLD), true);

        PlayerProgressionNetwork.sync(player);
    }

    /** Glows every valid enemy nearby — and nothing Imperial, ever. */
    private static void markEnemies(ServerPlayer player, double radius, int ticks) {
        AABB box = player.getBoundingBox().inflate(radius);

        for (LivingEntity entity : player.serverLevel()
                .getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {

            if (isImperialFriend(player, entity)) {
                continue;
            }

            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        }
    }

    /** Allies get a smaller ward. Orks and enemy-faction players get nothing. */
    private static void shareWithAllies(ServerPlayer player, int rank) {
        AABB box = player.getBoundingBox().inflate(PlayerProgressionBalance.PRAYER_CHOIR_RADIUS);
        float hearts = Math.min(6.0F, rank * 2.0F);

        for (LivingEntity entity : player.serverLevel()
                .getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {

            if (entity == player || !isImperialFriend(player, entity)) {
                continue;
            }

            // Set, not add: two people praying does not stack into an unkillable squad.
            entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), hearts));
            entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0, false, true));
        }
    }

    /**
     * Whether an entity is on the Emperor's side, from this player's point of view.
     *
     * <p>Imperial troops and citizens always are; a player is only when they chose the Imperium.
     */
    private static boolean isImperialFriend(ServerPlayer player, LivingEntity entity) {
        if (entity instanceof AbstractImperialTroopEntity || entity instanceof ImperialCitizenEntity) {
            return true;
        }

        if (entity instanceof ServerPlayer other) {
            PlayerFaction faction = PlayerFactionData.get(player.serverLevel()).getFaction(other);
            return faction == PlayerFaction.IMPERIUM;
        }

        return false;
    }

    // ==================================================================== tactical roll

    /**
     * A short dash the way the player is already facing.
     *
     * <p>The move is refused rather than clipped when there is a wall in front: adding velocity and
     * hoping the collision system catches it is how a dash becomes a wall-phase on a laggy server.
     * The destination is tested first, so the roll either happens in open air or does not happen.
     */
    private static Component roll(ServerPlayer player, PlayerProgressionProfile profile,
                                  int rank, long now) {
        if (profile.rollReadyAt() > now) {
            return cooldownMessage(profile.rollReadyAt(), now);
        }

        if (player.isPassenger() || player.getVehicle() != null) {
            return Component.translatable("msg.firstcrusade.progression.no_roll_mounted");
        }

        if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && player.isInWaterOrBubble()) {
            return Component.translatable("msg.firstcrusade.progression.no_roll_now");
        }

        Vec3 look = player.getLookAngle();
        Vec3 direction = new Vec3(look.x, 0.0D, look.z);
        if (direction.lengthSqr() < 1.0E-4D) {
            return Component.translatable("msg.firstcrusade.progression.no_roll_now");
        }

        direction = direction.normalize();
        double power = PlayerProgressionBalance.ROLL_BASE_POWER
                + rank * PlayerProgressionBalance.ROLL_POWER_PER_RANK;

        // Test where the roll would put them before giving them any velocity at all.
        AABB destination = player.getBoundingBox()
                .move(direction.x * power * 2.0D, 0.0D, direction.z * power * 2.0D);

        if (!player.level().noCollision(player, destination)) {
            return Component.translatable("msg.firstcrusade.progression.roll_blocked");
        }

        player.setDeltaMovement(direction.x * power, Math.max(0.0D, player.getDeltaMovement().y),
                direction.z * power);
        player.hurtMarked = true;   // makes the server push the new velocity to the client

        int cooldown = Math.max(PlayerProgressionBalance.ROLL_MIN_COOLDOWN_TICKS,
                PlayerProgressionBalance.ROLL_BASE_COOLDOWN_TICKS
                        - rank * PlayerProgressionBalance.ROLL_COOLDOWN_PER_RANK);

        profile.setRollReadyAt(now + cooldown);
        ROLL_GRACE.put(player.getUUID(), now + PlayerProgressionBalance.ROLL_GRACE_TICKS);
        PlayerProgressionManager.data(player.serverLevel()).markChanged();

        player.serverLevel().sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.2D, player.getZ(), 8, 0.2D, 0.05D, 0.2D, 0.01D);
        player.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.6F);

        PlayerProgressionNetwork.sync(player);
        return Component.empty();
    }

    /** True while a landing is still forgiven by a recent roll. */
    public static boolean isRollingGrace(Player player, long now) {
        Long until = ROLL_GRACE.get(player.getUUID());
        return until != null && until > now;
    }

    // ==================================================================== Betcher's gland

    private static Component acid(ServerPlayer player, PlayerProgressionProfile profile,
                                  int rank, long now) {
        if (profile.acidReadyAt() > now) {
            return cooldownMessage(profile.acidReadyAt(), now);
        }

        double range = PlayerProgressionBalance.ACID_BASE_RANGE
                + rank * PlayerProgressionBalance.ACID_RANGE_PER_RANK;
        float damage = PlayerProgressionBalance.ACID_BASE_DAMAGE
                + rank * PlayerProgressionBalance.ACID_DAMAGE_PER_RANK;

        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(range));
        AABB box = new AABB(eye, reach).inflate(1.0D);

        List<LivingEntity> targets = player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, box,
                entity -> entity.isAlive() && entity != player && !isImperialFriend(player, entity));

        if (targets.isEmpty()) {
            return Component.translatable("msg.firstcrusade.progression.no_target");
        }

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            // Corrosion, not fire: it eats the victim, and it never touches a block.
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 40 + rank * 20, 0));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40 + rank * 20, 0));
        }

        player.serverLevel().sendParticles(ParticleTypes.SNEEZE,
                reach.x, reach.y, reach.z, 20, 0.4D, 0.4D, 0.4D, 0.05D);
        player.playNotifySound(SoundEvents.SPIDER_HURT, SoundSource.PLAYERS, 0.7F, 0.6F);

        profile.setAcidReadyAt(now + PlayerProgressionBalance.ACID_BASE_COOLDOWN_TICKS);
        PlayerProgressionManager.data(player.serverLevel()).markChanged();
        PlayerProgressionNetwork.sync(player);

        return Component.empty();
    }

    // ==================================================================== Sus-an stasis

    /**
     * Emergency stasis: only for the nearly dead, and never a way out of a fight you are winning.
     *
     * <p>Deliberately not invulnerability. Resistance is high, the healing is slow, and a big enough
     * hit still ends the player — the organ buys a chance, not a save state.
     */
    private static Component stasis(ServerPlayer player, PlayerProgressionProfile profile,
                                    int rank, long now) {
        if (profile.stasisReadyAt() > now) {
            return cooldownMessage(profile.stasisReadyAt(), now);
        }

        if (player.getHealth() > player.getMaxHealth() * PlayerProgressionBalance.SUSAN_HEALTH_THRESHOLD) {
            return Component.translatable("msg.firstcrusade.progression.stasis_not_wounded");
        }

        int duration = PlayerProgressionBalance.SUSAN_BASE_DURATION_TICKS
                + rank * PlayerProgressionBalance.SUSAN_DURATION_PER_RANK;

        STASIS.put(player.getUUID(), now + duration);

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 3, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 1, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 6, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 4, false, false));

        profile.setStasisReadyAt(now + PlayerProgressionBalance.SUSAN_COOLDOWN_TICKS);
        PlayerProgressionManager.data(player.serverLevel()).markChanged();

        player.playNotifySound(SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 0.6F);
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.progression.stasis_entered")
                        .withStyle(ChatFormatting.AQUA), true);

        PlayerProgressionNetwork.sync(player);
        return Component.empty();
    }

    private static void tickStasis(ServerPlayer player, UUID id, long now) {
        Long until = STASIS.get(id);
        if (until == null) {
            return;
        }

        if (now >= until) {
            STASIS.remove(id);
            player.displayClientMessage(
                    Component.translatable("msg.firstcrusade.progression.stasis_ended")
                            .withStyle(ChatFormatting.AQUA), true);
            return;
        }

        player.serverLevel().sendParticles(ParticleTypes.SOUL,
                player.getX(), player.getY() + 1.0D, player.getZ(), 1, 0.3D, 0.4D, 0.3D, 0.0D);
    }

    /** True while the player is frozen in Sus-an: no attacking, no items. */
    public static boolean isInStasis(Player player) {
        Long until = STASIS.get(player.getUUID());
        return until != null && until > player.level().getGameTime();
    }

    // ==================================================================== housekeeping

    /** Drops every transient trace of a player. Called on logout, death and reset. */
    public static void forget(UUID playerId) {
        CHANNELS.remove(playerId);
        STASIS.remove(playerId);
        ROLL_GRACE.remove(playerId);
    }

    public static boolean isPraying(Player player) {
        return CHANNELS.containsKey(player.getUUID());
    }
}
