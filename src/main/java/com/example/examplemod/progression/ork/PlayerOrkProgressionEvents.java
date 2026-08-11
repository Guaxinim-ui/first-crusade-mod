package com.example.examplemod.progression.ork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.PlayerFaction;
import com.example.examplemod.PlayerFactionData;
import com.example.examplemod.progression.PlayerProgressionEvents;
import com.example.examplemod.progression.PlayerProgressionManager;
import com.example.examplemod.progression.PlayerProgressionNetwork;
import com.example.examplemod.progression.PlayerProgressionProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * How an Ork earns: by krumping things, and by being in the middle of it.
 *
 * <h2>No tick anywhere</h2>
 *
 * Krumpagem moves on a death. Fury moves on a blow landing in either direction. Fury's decay is
 * arithmetic on a timestamp, worked out only when somebody asks. So a world with no Ork player in a
 * fight runs none of this, and a world with no Ork players at all runs two {@code instanceof} checks
 * per death — which the game was doing anyway.
 *
 * <h2>The anti-farm is borrowed, not rebuilt</h2>
 *
 * {@link PlayerProgressionEvents#countsAsRealKill} already refuses summons, distant kills and the
 * fifth zombie of the same type inside one window. Its state lives on the outer profile, which every
 * player has, so an Ork and an Astartes are throttled by the same counter. Writing a second one here
 * would have been a second one to keep honest.
 *
 * <h2>What Fury is allowed to come from</h2>
 *
 * Both directions are filtered by {@link PlayerOrkProgressionCombat}. Landing a blow only pays for
 * something that would have been worth Krumpagem to kill — so not another Ork, not a cow, not an
 * armour stand. Taking one only pays when there is somebody on the other end of it, so a cactus, a
 * lava pool and a long fall are worth nothing. Without those two rules the bar could be held at a
 * hundred by standing in a fire next to a pig, and the shout it pays for would cost only patience.
 *
 * <h2>Fury does not send a profile</h2>
 *
 * A Fury gain used to end in {@link PlayerProgressionNetwork#sync}, which sends the entire profile
 * and broadcasts a body — per swing. It now sends {@link SyncOrkFuryPacket}: two fields, to one
 * player, and no more often than {@link PlayerOrkProgressionBalance#FURY_SYNC_INTERVAL_TICKS}. A kill
 * still sends the full profile, because a kill really does change Krumpagem, the tallies and possibly
 * the Teef balance — and kills are not a thing that happens twice a second.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOrkProgressionEvents {
    private PlayerOrkProgressionEvents() {
    }

    /**
     * When each player was last sent a Fury packet.
     *
     * <p>Transient, and cleared on logout. Losing it costs one extra packet on the next blow, which
     * is the cheapest possible failure mode for a throttle.
     */
    private static final Map<UUID, Long> LAST_FURY_SYNC = new HashMap<>();

    // ==================================================================== krumping

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player) || !isOrk(player)) {
            return;
        }

        LivingEntity victim = event.getEntity();

        int krump = PlayerOrkProgressionCombat.krumpFor(victim);
        if (krump <= 0) {
            return;
        }

        PlayerProgressionProfile outer = PlayerProgressionManager.profile(player);

        // Same window, same rules as the Imperial side.
        if (!PlayerProgressionEvents.countsAsRealKill(outer, player, victim)) {
            return;
        }

        PlayerOrkProgressionProfile ork = outer.ork();
        long now = player.level().getGameTime();

        boolean elite = PlayerOrkProgressionCombat.isElite(victim);

        ork.addKrump(krump);
        ork.countKill(elite, PlayerOrkProgressionCombat.isImperial(victim));
        ork.addFury(PlayerOrkCombatModifiers.scaleFury(ork,
                PlayerOrkProgressionCombat.furyFor(victim)), now);

        // BIG TEEF — big gitz have big teeth, and they go straight in the bag. Elites only, so it
        // is a reward for picking a hard fight rather than a trickle from every zombie.
        int bigTeef = PlayerOrkRewardModifiers.bigTeefBonus(ork, elite);
        if (bigTeef > 0) {
            ork.addTeef(PlayerOrkRewardModifiers.scaleTeef(ork, bigTeef));
        }

        // The full profile, because a kill really did change several fields of it. Krumpagem, the
        // tallies and the Teef balance are all on the screen the moment the player opens the tree.
        commitProfile(player);

        // Already up to date, so the throttled Fury packet has nothing to add for a while.
        LAST_FURY_SYNC.put(player.getUUID(), now);
    }

    // ==================================================================== fury

    /**
     * Fury from blows, both given and taken.
     *
     * <p>{@code LOWEST} priority so a hit another mod cancels never counts — an Ork should not get
     * excited about damage that never landed.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }

        // ---- taking one ---------------------------------------------------------
        if (event.getEntity() instanceof ServerPlayer victim && isOrk(victim)
                && PlayerOrkProgressionCombat.isValidFurySource(event.getSource().getEntity())) {

            gainFury(victim, PlayerOrkProgressionBalance.FURY_ON_DAMAGE_TAKEN, false);
        }

        // ---- landing one --------------------------------------------------------
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && isOrk(attacker)
                && PlayerOrkProgressionCombat.isValidFuryTarget(event.getEntity())) {

            gainFury(attacker, PlayerOrkProgressionBalance.FURY_ON_DAMAGE_DEALT, true);
        }
    }

    /**
     * @param throttled true for dealt damage, which needs a floor between awards — a fast weapon
     *                  would otherwise be a Fury faucet. Damage taken is not throttled: being hit
     *                  hard and often is exactly when an Ork should be getting angry fastest.
     */
    private static void gainFury(ServerPlayer player, int amount, boolean throttled) {
        PlayerOrkProgressionProfile ork = PlayerProgressionManager.profile(player).ork();
        long now = player.level().getGameTime();

        if (throttled) {
            if (!ork.mayAwardDamageFury(now)) {
                return;
            }
            ork.markDamageFury(now);
        }

        ork.addFury(PlayerOrkCombatModifiers.scaleFury(ork, amount), now);

        if (player.level() instanceof ServerLevel level) {
            PlayerProgressionManager.data(level).markChanged();
        }

        sendFury(player, ork, now);
    }

    /**
     * Pushes the bar, at most so often.
     *
     * <p>The save is already marked dirty by the caller, so a packet skipped here loses nothing
     * permanent: the value is on disk, the client is at most half a second behind, and the next blow
     * or the next full sync catches it up. What it does save is the profile packet and the body
     * broadcast that used to go out on every single hit.
     */
    private static void sendFury(ServerPlayer player, PlayerOrkProgressionProfile ork, long now) {
        Long last = LAST_FURY_SYNC.get(player.getUUID());

        if (last != null
                && now - last < PlayerOrkProgressionBalance.FURY_SYNC_INTERVAL_TICKS) {
            return;
        }

        LAST_FURY_SYNC.put(player.getUUID(), now);

        FirstCrusadeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncOrkFuryPacket(ork.fury(now), now));
    }

    // ==================================================================== housekeeping

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_FURY_SYNC.remove(event.getEntity().getUUID());
    }

    private static boolean isOrk(ServerPlayer player) {
        return PlayerFactionData.get(player.serverLevel()).getFaction(player.getUUID())
                == PlayerFaction.ORKS;
    }

    /**
     * Marks the save dirty and pushes the whole profile to its owner.
     *
     * <p>Only from a kill, where several fields really did move. The Fury path deliberately does not
     * come through here.
     */
    private static void commitProfile(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            PlayerProgressionManager.data(level).markChanged();
        }

        PlayerProgressionNetwork.sync(player);
    }
}
