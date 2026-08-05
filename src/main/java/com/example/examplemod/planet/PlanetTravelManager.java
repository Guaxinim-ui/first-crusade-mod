package com.example.examplemod.planet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.examplemod.FCRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The server's answer to "may this player go there, and what happens when they do".
 *
 * <h2>Everything important happens here, and only here</h2>
 *
 * The screen decides nothing. It draws what {@link #evaluate} said and sends a request; this class
 * re-runs every check when the request arrives, because the two moments are different — a player can
 * open the terminal, watch an Ork walk onto the pad, and press launch. A client that is lying is
 * handled by the same code that handles a world that changed.
 *
 * <h2>The launch is not instant</h2>
 *
 * A confirmed launch enters {@link #PENDING} and takes {@link #COUNTDOWN_TICKS} to complete, with
 * messages along the way. The cost is taken at confirmation, not at arrival, so a player cannot
 * spend the same plate on two trips by disconnecting halfway; the trip is dropped if they leave.
 *
 * <p>The per-tick cost of all this is a walk over a map that is empty whenever nobody is mid-launch,
 * which is almost always.
 */
public final class PlanetTravelManager {
    private PlanetTravelManager() {
    }

    /** How close the player must stay to the terminal, in blocks. */
    public static final double TERMINAL_RANGE = 8.0D;

    /** How far the terminal will look for the Spaceport it belongs to. */
    private static final int SPACEPORT_RANGE = 8;

    /** Enemies inside this radius of the terminal count as holding the pad. */
    private static final double ENEMY_RANGE = 16.0D;

    /** Launch sequence length: four seconds of countdown, one message per second. */
    private static final int COUNTDOWN_TICKS = 80;

    /**
     * Why a destination cannot be used. The message is what the terminal shows; keeping the reason
     * as an enum (rather than only a string) is what lets the screen grey the right button and the
     * commands report the same cause.
     */
    public enum Blocker {
        NONE("none"),
        NO_SPACEPORT("no_spaceport"),
        ENEMY_CONTROL("enemy_control"),
        TOO_FAR("too_far"),
        LOCKED("locked"),
        NOT_DEPLOYABLE("not_deployable"),
        ALREADY_THERE("already_there"),
        MISSING_COST("missing_cost"),
        IN_TRANSIT("in_transit"),
        NO_DIMENSION("no_dimension"),
        DISABLED("disabled");

        private final String key;

        Blocker(String key) {
            this.key = key;
        }

        public boolean ok() {
            return this == NONE;
        }

        public Component message() {
            return Component.translatable("msg.firstcrusade.planet.blocked." + this.key);
        }
    }

    private record PendingTravel(ResourceKey<Level> destination, java.util.Optional<net.minecraft.resources.ResourceLocation> planetId,
                                 int ticksLeft) {
    }

    private static final Map<UUID, PendingTravel> PENDING = new HashMap<>();

    // ====================================================================================
    // Validation
    // ====================================================================================

    /**
     * Whether the terminal itself may be used at all — checked before the screen opens and again on
     * every request. Independent of any particular destination.
     */
    public static Blocker checkTerminal(ServerPlayer player, BlockPos terminal) {
        ServerLevel level = player.serverLevel();

        if (player.blockPosition().distSqr(terminal) > TERMINAL_RANGE * TERMINAL_RANGE) {
            return Blocker.TOO_FAR;
        }

        if (PENDING.containsKey(player.getUUID())) {
            return Blocker.IN_TRANSIT;
        }

        if (!hasSpaceport(level, terminal)) {
            return Blocker.NO_SPACEPORT;
        }

        if (isUnderEnemyControl(level, terminal)) {
            return Blocker.ENEMY_CONTROL;
        }

        return Blocker.NONE;
    }

    /** Whether this player may launch to this planet right now. */
    public static Blocker evaluate(ServerPlayer player, PlanetDefinition planet, BlockPos terminal) {
        Blocker terminalState = checkTerminal(player, terminal);
        if (!terminalState.ok()) {
            return terminalState;
        }

        if (!planet.enabled()) {
            return Blocker.DISABLED;
        }

        PlanetUnlockData data = PlanetUnlockData.get(player.serverLevel());
        if (!data.isUnlocked(player.getUUID(), planet)) {
            return Blocker.LOCKED;
        }

        if (!planet.isDeployable()) {
            return Blocker.NOT_DEPLOYABLE;
        }

        if (player.level().dimension().equals(planet.destinationDimension())) {
            return Blocker.ALREADY_THERE;
        }

        MinecraftServer server = player.getServer();
        if (server == null || server.getLevel(planet.destinationDimension()) == null) {
            return Blocker.NO_DIMENSION;
        }

        if (!canPay(player, planet)) {
            return Blocker.MISSING_COST;
        }

        return Blocker.NONE;
    }

    /** The travel state the screen paints for this planet. */
    public static PlanetTravelState stateOf(ServerPlayer player, PlanetDefinition planet) {
        if (player.level().dimension().equals(planet.destinationDimension())) {
            return PlanetTravelState.CURRENT;
        }

        PlanetUnlockData data = PlanetUnlockData.get(player.serverLevel());
        if (!data.isUnlocked(player.getUUID(), planet)) {
            return PlanetTravelState.LOCKED;
        }

        MinecraftServer server = player.getServer();
        boolean dimensionExists = planet.isDeployable()
                && server != null
                && server.getLevel(planet.destinationDimension()) != null;

        return dimensionExists ? PlanetTravelState.AVAILABLE : PlanetTravelState.DISCOVERED_NOT_DEPLOYABLE;
    }

    private static boolean hasSpaceport(ServerLevel level, BlockPos terminal) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -SPACEPORT_RANGE; dx <= SPACEPORT_RANGE; dx++) {
            for (int dz = -SPACEPORT_RANGE; dz <= SPACEPORT_RANGE; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    cursor.set(terminal.getX() + dx, terminal.getY() + dy, terminal.getZ() + dz);
                    if (level.getBlockState(cursor).is(FCRegistry.SPACEPORT.get())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * True when hostiles are standing on the pad.
     *
     * <p>One AABB query, only when the terminal is used — never on a tick. "Under enemy control" is
     * the mod's own greenskins, which are all {@link Monster}s, so the broad type is also the
     * accurate one.
     */
    private static boolean isUnderEnemyControl(ServerLevel level, BlockPos terminal) {
        AABB box = new AABB(terminal).inflate(ENEMY_RANGE);
        return !level.getEntitiesOfClass(Monster.class, box, monster -> monster.isAlive()).isEmpty();
    }

    private static boolean canPay(ServerPlayer player, PlanetDefinition planet) {
        PlanetDefinition.TravelCost cost = planet.travelCost();
        if (cost.isFree() || player.isCreative()) {
            return true;
        }

        return countIn(player, cost) >= cost.count();
    }

    private static int countIn(ServerPlayer player, PlanetDefinition.TravelCost cost) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(cost.item().get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void charge(ServerPlayer player, PlanetDefinition planet) {
        PlanetDefinition.TravelCost cost = planet.travelCost();
        if (cost.isFree() || player.isCreative()) {
            return;
        }

        int remaining = cost.count();
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                return;
            }
            if (stack.is(cost.item().get())) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
    }

    // ====================================================================================
    // Launch
    // ====================================================================================

    /**
     * Starts a launch after re-validating everything.
     *
     * @return the blocker that stopped it, or {@link Blocker#NONE} when the countdown began
     */
    public static Blocker beginTravel(ServerPlayer player, PlanetDefinition planet, BlockPos terminal) {
        Blocker blocker = evaluate(player, planet, terminal);
        if (!blocker.ok()) {
            player.playNotifySound(PlanetSounds.TRAVEL_ERROR.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            return blocker;
        }

        charge(player, planet);
        rememberOrigin(player);

        PENDING.put(player.getUUID(), new PendingTravel(planet.destinationDimension(),
                java.util.Optional.of(planet.id()), COUNTDOWN_TICKS));

        player.playNotifySound(PlanetSounds.TRAVEL_START.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.planet.calculating"), true);

        return Blocker.NONE;
    }

    /** Sends a traveller back where they came from, or home when that place is gone. */
    public static Blocker beginReturn(ServerPlayer player, BlockPos terminal) {
        Blocker terminalState = checkTerminal(player, terminal);
        if (!terminalState.ok()) {
            return terminalState;
        }

        PlanetUnlockData data = PlanetUnlockData.get(player.serverLevel());
        PlanetUnlockData.ReturnPoint point = data.returnPoint(player.getUUID());

        // No record, or a record pointing at a dimension this installation no longer has: the
        // overworld is the emergency destination, and it always exists.
        ResourceKey<Level> destination = Level.OVERWORLD;
        if (point != null && player.getServer() != null
                && player.getServer().getLevel(point.dimension()) != null
                && !point.dimension().equals(player.level().dimension())) {
            destination = point.dimension();
        }

        rememberOrigin(player);
        PENDING.put(player.getUUID(),
                new PendingTravel(destination, java.util.Optional.empty(), COUNTDOWN_TICKS));

        player.playNotifySound(PlanetSounds.TRAVEL_START.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.planet.calculating"), true);

        return Blocker.NONE;
    }

    private static void rememberOrigin(ServerPlayer player) {
        PlanetUnlockData data = PlanetUnlockData.get(player.serverLevel());
        ResourceKey<Level> from = player.level().dimension();

        data.setReturnPoint(player.getUUID(), new PlanetUnlockData.ReturnPoint(
                from,
                player.blockPosition(),
                PlanetRegistry.byDimension(from).map(PlanetDefinition::id).orElse(null),
                player.level().getGameTime()));
    }

    /**
     * Advances every launch in progress. Called once per server tick; iterates a map that is empty
     * unless somebody is actually mid-launch.
     */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingTravel>> iterator = PENDING.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingTravel> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

            // Logged out mid-launch: drop the trip. They keep the cost, which is the honest
            // outcome of aborting a launch, and they are not teleported on next login into a world
            // they may no longer expect.
            if (player == null) {
                iterator.remove();
                continue;
            }

            PendingTravel travel = entry.getValue();
            int left = travel.ticksLeft() - 1;

            if (left <= 0) {
                iterator.remove();
                complete(server, player, travel);
                continue;
            }

            entry.setValue(new PendingTravel(travel.destination(), travel.planetId(), left));
            announce(player, left);
        }
    }

    private static void announce(ServerPlayer player, int ticksLeft) {
        // One message per second of the countdown, and nothing in between.
        if (ticksLeft % 20 != 0) {
            return;
        }

        int seconds = ticksLeft / 20;
        Component message = switch (seconds) {
            case 3 -> Component.translatable("msg.firstcrusade.planet.locked_on");
            case 2 -> Component.translatable("msg.firstcrusade.planet.preparing");
            case 1 -> Component.translatable("msg.firstcrusade.planet.warp");
            default -> null;
        };

        if (message != null) {
            player.displayClientMessage(message, true);
        }
    }

    private static void complete(MinecraftServer server, ServerPlayer player, PendingTravel travel) {
        ServerLevel destination = server.getLevel(travel.destination());

        if (destination == null) {
            player.displayClientMessage(Blocker.NO_DIMENSION.message(), true);
            player.playNotifySound(PlanetSounds.TRAVEL_ERROR.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            return;
        }

        int x = player.blockPosition().getX();
        int z = player.blockPosition().getZ();
        BlockPos landing = PlanetLanding.findDryLanding(destination, x, z);

        PlanetLanding.buildLandingPad(destination, landing);

        if (FCPlanets.isCrusadeWorld(travel.destination())) {
            com.example.examplemod.WorldSettlementSeeder.seedPlanet(destination, landing);
        }

        player.teleportTo(destination, x + 0.5D, landing.getY(), z + 0.5D,
                player.getYRot(), player.getXRot());

        Component where = travel.planetId()
                .flatMap(PlanetRegistry::get)
                .map(PlanetDefinition::displayName)
                .orElseGet(() -> Component.translatable("msg.firstcrusade.spaceport.home"));

        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.spaceport.arrived_at", where), true);

        // First successful launch is itself an unlock trigger: it is what opens the destinations
        // that only ask the player to have used a Spaceport once.
        grant(player, PlanetUnlockRequirement.TRIGGER_FIRST_LAUNCH);
    }

    /** True while this player's launch sequence is running — used to refuse a second request. */
    public static boolean isTravelling(UUID playerId) {
        return PENDING.containsKey(playerId);
    }

    /**
     * Sends a player to a planet immediately, skipping the pad, the cost and the countdown.
     *
     * <p>For {@code /firstcrusade planet travel}: an operator testing a destination should not have
     * to build a Spaceport first. What it does <b>not</b> skip is the arrival — the return point is
     * still recorded and the landing pad is still built, because a command that drops somebody
     * inside a mountain is not a shortcut, it is a bug with a convenient trigger.
     */
    public static void adminTravel(ServerPlayer player, PlanetDefinition planet) {
        if (!planet.isDeployable()) {
            return;
        }

        rememberOrigin(player);
        complete(player.getServer(), player,
                new PendingTravel(planet.destinationDimension(),
                        java.util.Optional.of(planet.id()), 0));
    }

    // ====================================================================================
    // Unlock helper
    // ====================================================================================

    /**
     * Records a trigger for a player and tells them about anything it opened.
     *
     * <p>Public because the triggers come from all over the mod — a kill, a craft, a command — and
     * every one of them should report the same way.
     */
    public static void grant(ServerPlayer player, String trigger) {
        PlanetUnlockData data = PlanetUnlockData.get(player.serverLevel());
        List<PlanetDefinition> opened = List.copyOf(data.grantTrigger(player.getUUID(), trigger));

        for (PlanetDefinition planet : opened) {
            player.sendSystemMessage(Component.translatable(
                    "msg.firstcrusade.planet.unlocked", planet.displayName()));
            player.playNotifySound(PlanetSounds.SELECTED.get(), SoundSource.PLAYERS, 0.9F, 1.2F);
        }
    }

    /**
     * The state of a destination with no player in the question.
     *
     * <p>For the console, where {@code /firstcrusade planet list} has no player to speak for. The
     * naive fallback was to call everything locked, which reads as "the whole system is broken"
     * rather than as "nobody asked". This answers the question that <i>can</i> be answered without a
     * player: does the destination exist, and does it ask for anything.
     */
    public static PlanetTravelState intrinsicState(MinecraftServer server, PlanetDefinition planet) {
        boolean dimensionExists = planet.isDeployable()
                && server != null
                && server.getLevel(planet.destinationDimension()) != null;

        if (!dimensionExists) {
            return PlanetTravelState.DISCOVERED_NOT_DEPLOYABLE;
        }

        return planet.isUnlockedByDefault()
                ? PlanetTravelState.AVAILABLE
                : PlanetTravelState.LOCKED;
    }

    /** The planet a player is standing on, if that level is one. */
    @Nullable
    public static PlanetDefinition currentPlanet(ServerPlayer player) {
        return PlanetRegistry.byDimension(player.level().dimension()).orElse(null);
    }
}
