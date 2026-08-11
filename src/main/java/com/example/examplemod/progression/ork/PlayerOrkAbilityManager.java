package com.example.examplemod.progression.ork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.examplemod.PlayerFaction;
import com.example.examplemod.PlayerFactionData;
import com.example.examplemod.progression.PlayerProgressionCombat;
import com.example.examplemod.progression.PlayerProgressionManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The four buttons an Ork has, and every rule about whether they may be pressed.
 *
 * <h2>Nothing lingers</h2>
 *
 * An Ork ability is an event. It happens, it lands, it is over — no channel, no aura, no per-tick
 * effect that has to be maintained. The shout does <b>one</b> scan of who can hear it and applies
 * ordinary mob effects with a duration, rather than re-checking a radius twenty times a second; the
 * order does <b>one</b> scan when the key is pressed and never another.
 *
 * <h2>The one exception, and why it is bounded</h2>
 *
 * BOYZ, OVER 'ERE has Boyz walking somewhere for half a minute, so something has to keep them
 * walking. {@link #tick} is that something, and it is built so it costs nothing when nobody has
 * shouted: the map is empty, the first line returns, and no Ork code runs at all. When it is not
 * empty it re-paths at most every {@code RALLY_REPATH_TICKS}, and only the Boyz that were in earshot
 * at the moment of the shout — held by id, so there is never a second area scan.
 *
 * <h2>Neither ability walks through a wall</h2>
 *
 * The headbutt clips against blocks before it looks for an entity, so a git behind a door is safe.
 * The charge tests the box it would land in and refuses rather than clipping — adding velocity and
 * hoping the collision system catches up is how a dash becomes a wall-phase on a busy server. Neither
 * adds any upward velocity, so neither is a way to fly.
 */
public final class PlayerOrkAbilityManager {
    private PlayerOrkAbilityManager() {
    }

    /**
     * Who has Boyz on their way, which Boyz, and when to nudge them next.
     *
     * <p>Transient on purpose. A rally interrupted by a server restart is a rally that did not
     * happen, which is the correct outcome and needs no save format.
     */
    private static final Map<UUID, Rally> RALLIES = new HashMap<>();

    private record Rally(List<UUID> boyz, long until, long nextPathAt) {
    }

    // ==================================================================== entry point

    /** @return the message to show the player, or empty when the ability fired */
    public static Component use(ServerPlayer player, PlayerOrkAbility ability) {
        if (!PlayerOrkProgressionRequirements.isOrk(player)) {
            return Component.translatable("msg.firstcrusade.ork.not_ork");
        }

        PlayerOrkProgressionProfile ork = PlayerOrkProgressionManager.profile(player);

        if (ork.rank(ability.nodeId()) <= 0) {
            return Component.translatable("msg.firstcrusade.ork.ability_locked",
                    ability.displayName());
        }

        long now = player.level().getGameTime();

        return switch (ability) {
            case HEADBUTT -> headbutt(player, ork, now);
            case WAAAGH_ROAR -> waaagh(player, ork, now);
            case BOSS_ORDER -> order(player, ork, now);
            case CHARGE -> charge(player, ork, now);
        };
    }

    private static Component cooldownMessage(long readyAt, long now) {
        int seconds = (int) Math.ceil((readyAt - now) / 20.0D);
        return Component.translatable("msg.firstcrusade.ork.on_cooldown", seconds);
    }

    // ==================================================================== 'EADBUTT

    /**
     * A short, hard blow with his forehead.
     *
     * <p>Blocks are clipped first and the ray is cut short at whatever it hit, so the search for a
     * target only ever runs over the stretch of air the player can actually reach. A git on the other
     * side of a wall is not in front of him, whatever the look vector says.
     */
    private static Component headbutt(ServerPlayer player, PlayerOrkProgressionProfile ork,
                                      long now) {
        if (ork.headbuttReadyAt() > now) {
            return cooldownMessage(ork.headbuttReadyAt(), now);
        }

        LivingEntity target = lookingAt(player, PlayerOrkProgressionBalance.HEADBUTT_RANGE);
        if (target == null) {
            return Component.translatable("msg.firstcrusade.ork.no_target");
        }

        float damage = PlayerOrkProgressionBalance.HEADBUTT_DAMAGE
                + ork.stage().ordinal() * PlayerOrkProgressionBalance.HEADBUTT_DAMAGE_PER_STAGE;

        target.hurt(player.damageSources().playerAttack(player), damage);

        // Stunned rather than held: the git can still walk away, badly and dizzily, which is a beat
        // in a fight rather than a stun-lock.
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                PlayerOrkProgressionBalance.HEADBUTT_STUN_TICKS, 2, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                PlayerOrkProgressionBalance.HEADBUTT_STUN_TICKS, 0, false, true));

        target.knockback(PlayerOrkProgressionBalance.HEADBUTT_KNOCKBACK,
                player.getX() - target.getX(), player.getZ() - target.getZ());

        ork.setHeadbuttReadyAt(now + PlayerOrkProgressionBalance.HEADBUTT_COOLDOWN_TICKS);
        commit(player);

        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getEyeY(), target.getZ(), 14, 0.3D, 0.3D, 0.3D, 0.2D);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.5F, 1.6F);

        return Component.empty();
    }

    // ==================================================================== KRUMP FIRST

    /**
     * A run at whatever is in front.
     *
     * <p>The destination is tested before any velocity is handed out, and the movement is strictly
     * horizontal — an ability that adds upward velocity is an ability players use to climb.
     */
    private static Component charge(ServerPlayer player, PlayerOrkProgressionProfile ork, long now) {
        if (ork.chargeReadyAt() > now) {
            return cooldownMessage(ork.chargeReadyAt(), now);
        }

        if (player.isPassenger() || player.getVehicle() != null) {
            return Component.translatable("msg.firstcrusade.ork.no_charge_now");
        }

        Vec3 look = player.getLookAngle();
        Vec3 direction = new Vec3(look.x, 0.0D, look.z);
        if (direction.lengthSqr() < 1.0E-4D) {
            return Component.translatable("msg.firstcrusade.ork.no_charge_now");
        }

        direction = direction.normalize();

        AABB destination = player.getBoundingBox().move(
                direction.x * PlayerOrkProgressionBalance.CHARGE_TEST_DISTANCE, 0.0D,
                direction.z * PlayerOrkProgressionBalance.CHARGE_TEST_DISTANCE);

        if (!player.level().noCollision(player, destination)) {
            return Component.translatable("msg.firstcrusade.ork.charge_blocked");
        }

        // Y is kept, never added to: he keeps falling if he was falling, and he does not rise.
        player.setDeltaMovement(direction.x * PlayerOrkProgressionBalance.CHARGE_POWER,
                Math.min(0.0D, player.getDeltaMovement().y),
                direction.z * PlayerOrkProgressionBalance.CHARGE_POWER);
        player.hurtMarked = true;   // makes the server push the new velocity to the client

        // One look for something to hit, at the moment of the press. Nothing is watched on the way.
        LivingEntity target = lookingAt(player, PlayerOrkProgressionBalance.CHARGE_HIT_RANGE);
        if (target != null) {
            target.hurt(player.damageSources().playerAttack(player),
                    PlayerOrkProgressionBalance.CHARGE_DAMAGE);
            target.knockback(PlayerOrkProgressionBalance.HEADBUTT_KNOCKBACK,
                    player.getX() - target.getX(), player.getZ() - target.getZ());
        }

        ork.setChargeReadyAt(now + PlayerOrkProgressionBalance.CHARGE_COOLDOWN_TICKS);
        commit(player);

        player.serverLevel().sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.2D, player.getZ(), 10, 0.3D, 0.05D, 0.3D, 0.02D);
        player.playNotifySound(SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 0.9F, 0.8F);

        return Component.empty();
    }

    // ==================================================================== WAAAAAAAAAGH!

    /**
     * The shout. The whole Fury bar, spent at once, on everything green that can hear it.
     *
     * <p>Requires a full bar rather than scaling with what is in it: a shout at 40 Fury that gives a
     * fortieth of a buff is a button players press constantly and never notice. One scan, ordinary
     * mob effects with a duration on them, and nothing left running afterwards.
     */
    private static Component waaagh(ServerPlayer player, PlayerOrkProgressionProfile ork, long now) {
        if (ork.waaaghReadyAt() > now) {
            return cooldownMessage(ork.waaaghReadyAt(), now);
        }

        int fury = ork.fury(now);
        if (fury < PlayerOrkProgressionBalance.FURY_MAX) {
            return Component.translatable("msg.firstcrusade.ork.not_angry_enough",
                    PlayerOrkProgressionBalance.FURY_MAX, fury);
        }

        int listen = ork.rank("boyz_listen");

        double radius = PlayerOrkProgressionBalance.WAAAGH_RADIUS
                + listen * PlayerOrkProgressionBalance.WAAAGH_RADIUS_PER_LISTEN;
        int duration = PlayerOrkProgressionBalance.WAAAGH_DURATION_TICKS
                + listen * PlayerOrkProgressionBalance.WAAAGH_DURATION_PER_LISTEN;

        // DA GREENEST shouts a whole level louder.
        int amplifier = ork.rank("da_greenest") > 0 ? 1 : 0;

        ServerLevel level = player.serverLevel();
        int touched = 0;

        // The one scan. Everything below is applied from this list; nothing re-reads the world.
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), LivingEntity::isAlive)) {

            if (touched >= PlayerOrkProgressionBalance.WAAAGH_MAX_TARGETS) {
                break;
            }

            if (entity != player && !isGreenskin(level, entity)) {
                continue;
            }

            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration,
                    amplifier, false, true));
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration,
                    amplifier, false, true));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration,
                    amplifier, false, true));

            touched++;
        }

        // Spent, whatever it reached: the shout happened.
        ork.setFury(0, now);
        ork.setWaaaghReadyAt(now + PlayerOrkProgressionBalance.WAAAGH_COOLDOWN_TICKS);
        commit(player);

        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 2.0F, 0.7F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                player.getX(), player.getY() + 1.4D, player.getZ(), 30, 1.0D, 0.8D, 1.0D, 0.05D);

        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.ork.waaagh_shouted", touched)
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);

        return Component.empty();
    }

    // ==================================================================== I'Z DA BOSS

    /**
     * Point at a git, or call the Boyz over.
     *
     * <p>Two orders behind one button, decided by whether the player is looking at something worth
     * krumping. With a target it is one scan and one {@code setTarget} each; without one it falls
     * through to the rally, which is a different node and refuses politely if he has not bought it.
     */
    private static Component order(ServerPlayer player, PlayerOrkProgressionProfile ork, long now) {
        if (ork.orderReadyAt() > now) {
            return cooldownMessage(ork.orderReadyAt(), now);
        }

        ServerLevel level = player.serverLevel();
        int listen = ork.rank("boyz_listen");

        double radius = PlayerOrkProgressionBalance.ORDER_RADIUS
                + listen * PlayerOrkProgressionBalance.ORDER_RADIUS_PER_LISTEN;
        int limit = PlayerOrkProgressionBalance.ORDER_MAX_BOYZ
                + listen * PlayerOrkProgressionBalance.ORDER_BOYZ_PER_LISTEN;

        LivingEntity target = lookingAt(player, PlayerOrkProgressionBalance.ORDER_TARGET_RANGE);

        // An Ork is never a valid order: pointing the Boyz at their own is how a WAAAGH ends.
        if (target != null && isGreenskin(level, target)) {
            target = null;
        }

        List<Mob> boyz = nearbyBoyz(level, player, radius, limit);

        if (boyz.isEmpty()) {
            return Component.translatable("msg.firstcrusade.ork.no_boyz");
        }

        if (target != null) {
            for (Mob boy : boyz) {
                boy.setTarget(target);
            }

            ork.setOrderReadyAt(now + PlayerOrkProgressionBalance.ORDER_COOLDOWN_TICKS);
            commit(player);

            shout(player, level);
            player.displayClientMessage(
                    Component.translatable("msg.firstcrusade.ork.boyz_attack",
                            boyz.size(), target.getDisplayName())
                            .withStyle(ChatFormatting.GREEN), true);

            return Component.empty();
        }

        return rally(player, ork, level, boyz, now);
    }

    /**
     * BOYZ, OVER 'ERE — the fallback order, and its own node.
     *
     * <p>The Boyz are captured by id here and never scanned for again. {@link #tick} looks each one
     * up by id when it re-paths, so a rally is a fixed list of Boyz walking rather than a repeating
     * search of the area around a player.
     */
    private static Component rally(ServerPlayer player, PlayerOrkProgressionProfile ork,
                                   ServerLevel level, List<Mob> boyz, long now) {
        if (ork.rank("boyz_come_here") <= 0) {
            return Component.translatable("msg.firstcrusade.ork.no_target");
        }

        List<UUID> ids = new ArrayList<>(boyz.size());
        for (Mob boy : boyz) {
            ids.add(boy.getUUID());
            boy.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(),
                    PlayerOrkProgressionBalance.RALLY_SPEED);
        }

        RALLIES.put(player.getUUID(), new Rally(ids,
                now + PlayerOrkProgressionBalance.RALLY_DURATION_TICKS,
                now + PlayerOrkProgressionBalance.RALLY_REPATH_TICKS));

        ork.setOrderReadyAt(now + PlayerOrkProgressionBalance.ORDER_COOLDOWN_TICKS);
        commit(player);

        shout(player, level);
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.ork.boyz_coming", ids.size())
                        .withStyle(ChatFormatting.GREEN), true);

        return Component.empty();
    }

    /**
     * Keeps a rally walking, and costs nothing when there is no rally.
     *
     * <p>Called once per player per tick from the progression's tick handler. The first line is the
     * whole performance story: with an empty map — which is the state of every world where nobody has
     * just shouted — this returns before it has read anything at all.
     */
    public static void tick(ServerPlayer player) {
        if (RALLIES.isEmpty()) {
            return;
        }

        Rally rally = RALLIES.get(player.getUUID());
        if (rally == null) {
            return;
        }

        long now = player.level().getGameTime();

        if (now >= rally.until()) {
            RALLIES.remove(player.getUUID());
            return;
        }

        if (now < rally.nextPathAt()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        List<UUID> remaining = new ArrayList<>(rally.boyz().size());

        for (UUID id : rally.boyz()) {
            // By id, not by another area scan: the Boyz that were told are the Boyz that come.
            if (!(level.getEntity(id) instanceof Mob boy) || !boy.isAlive()) {
                continue;
            }

            // Arrived. Dropping him from the list is what makes a rally get cheaper as it works.
            if (boy.distanceToSqr(player) <= PlayerOrkProgressionBalance.RALLY_ARRIVED_DISTANCE
                    * PlayerOrkProgressionBalance.RALLY_ARRIVED_DISTANCE) {
                continue;
            }

            boy.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(),
                    PlayerOrkProgressionBalance.RALLY_SPEED);
            remaining.add(id);
        }

        if (remaining.isEmpty()) {
            RALLIES.remove(player.getUUID());
            return;
        }

        RALLIES.put(player.getUUID(), new Rally(remaining, rally.until(),
                now + PlayerOrkProgressionBalance.RALLY_REPATH_TICKS));
    }

    /** Drops every transient trace of a player. Called on logout, death and reset. */
    public static void forget(UUID playerId) {
        RALLIES.remove(playerId);
    }

    // ==================================================================== shared plumbing

    /**
     * The first living thing along the player's line of sight, or null.
     *
     * <p>Blocks first: the ray is cut at whatever solid it meets, and only then searched for an
     * entity. Doing it the other way round finds the git behind the wall and then has to explain why
     * it does not count.
     */
    private static LivingEntity lookingAt(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));

        BlockHitResult blocked = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (blocked.getType() != HitResult.Type.MISS) {
            end = blocked.getLocation();
        }

        AABB search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range))
                .inflate(1.0D);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end,
                search, candidate -> candidate != player && candidate.isAlive()
                        && candidate instanceof LivingEntity living && !living.isInvulnerable());

        return hit == null || !(hit.getEntity() instanceof LivingEntity living) ? null : living;
    }

    /** Ork mobs near enough to hear an order, capped so one press can never touch a whole army. */
    private static List<Mob> nearbyBoyz(ServerLevel level, ServerPlayer player, double radius,
                                        int limit) {
        List<Mob> boyz = new ArrayList<>();

        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && PlayerProgressionCombat.isOrk(candidate))) {

            if (boyz.size() >= limit) {
                break;
            }
            boyz.add(mob);
        }

        return boyz;
    }

    /**
     * Whether this is on the WAAAGH's side — an Ork mob, or a player who chose the Orks.
     *
     * <p>The mob half reuses {@code PlayerProgressionCombat.isOrk}, which is the same list the
     * Imperial anti-Ork bonuses read. Two lists of what counts as an Ork is two lists that disagree
     * the day somebody adds a Stormboy.
     */
    private static boolean isGreenskin(ServerLevel level, Entity entity) {
        if (PlayerProgressionCombat.isOrk(entity)) {
            return true;
        }

        if (entity instanceof ServerPlayer other) {
            return PlayerFactionData.get(level).getFaction(other.getUUID()) == PlayerFaction.ORKS;
        }

        return false;
    }

    private static void shout(ServerPlayer player, ServerLevel level) {
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 1.1F, 1.1F);
    }

    /** Saves the cooldown that just moved and pushes the profile to its owner. Once, at the end. */
    private static void commit(ServerPlayer player) {
        PlayerProgressionManager.data(player.serverLevel()).markChanged();
        com.example.examplemod.progression.PlayerProgressionNetwork.sync(player);
    }
}
