package com.example.examplemod.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.example.examplemod.FCRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The navigation terminal's menu: a slotless container carrying one snapshot of the destination
 * list to the client.
 *
 * <h2>Why a menu and not a bare screen</h2>
 *
 * There is no inventory here, so a plain {@code Screen} would work — the mod has one of those
 * already. A menu is used anyway for one property that would otherwise have to be written by hand:
 * {@link #stillValid} is polled by the server every tick, so a player who walks away from the
 * terminal has the screen closed <i>by the server</i>, which is exactly the "close the menu if the
 * player moves too far" rule and cannot be bypassed by a modified client.
 *
 * <h2>The whole list travels once</h2>
 *
 * Definitions are sent in full rather than looked up client-side from {@link PlanetRegistry}. It
 * costs a few kilobytes on open and buys two things: a client sees exactly what the server has (so
 * a datapack-defined planet needs no client-side copy), and there is no second source of truth to
 * drift. Nothing here is sent per tick.
 */
public class PlanetNavigationMenu extends AbstractContainerMenu {

    /**
     * One row of the list, as the client receives it.
     *
     * @param definition the planet
     * @param state      what this player may do with it
     * @param missing    requirements not yet met — the tooltip's "Requirements:" list
     */
    public record Entry(PlanetDefinition definition, PlanetTravelState state,
                        List<PlanetUnlockRequirement> missing) {
    }

    private final BlockPos terminalPos;
    private final List<Entry> entries;
    private final PlanetTravelManager.Blocker terminalBlocker;

    @Nullable
    private final PlanetDefinition returnDestination;

    private final boolean hasReturnPoint;
    private final boolean onPlanet;

    /** Client constructor: everything is read back out of the buffer the server wrote. */
    public PlanetNavigationMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extra) {
        super(FCRegistry.PLANET_NAVIGATION_MENU.get(), containerId);

        this.terminalPos = extra.readBlockPos();
        this.terminalBlocker = extra.readEnum(PlanetTravelManager.Blocker.class);

        int count = extra.readVarInt();
        List<Entry> read = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            PlanetDefinition definition = PlanetDefinition.read(extra);
            PlanetTravelState state = extra.readEnum(PlanetTravelState.class);

            int missingCount = extra.readVarInt();
            List<PlanetUnlockRequirement> missing = new ArrayList<>(missingCount);
            for (int m = 0; m < missingCount; m++) {
                missing.add(PlanetUnlockRequirement.read(extra));
            }

            read.add(new Entry(definition, state, List.copyOf(missing)));
        }

        this.entries = List.copyOf(read);

        // Decoded step by step rather than in one conditional expression. The reads have to happen
        // in exactly the order the server wrote them, and a `&&` that short-circuits past a read is
        // a decoder that silently desynchronises from the next field onwards.
        this.hasReturnPoint = extra.readBoolean();
        PlanetDefinition destination = null;

        if (this.hasReturnPoint) {
            boolean returnsToPlanet = extra.readBoolean();
            if (returnsToPlanet) {
                destination = PlanetRegistry.get(extra.readResourceLocation()).orElse(null);
            }
        }

        this.returnDestination = destination;
        this.onPlanet = extra.readBoolean();
    }

    /** Server constructor. */
    public PlanetNavigationMenu(int containerId, Inventory playerInventory, BlockPos terminalPos,
                                List<Entry> entries, PlanetTravelManager.Blocker blocker,
                                boolean hasReturnPoint, @Nullable PlanetDefinition returnDestination,
                                boolean onPlanet) {
        super(FCRegistry.PLANET_NAVIGATION_MENU.get(), containerId);
        this.terminalPos = terminalPos;
        this.entries = List.copyOf(entries);
        this.terminalBlocker = blocker;
        this.hasReturnPoint = hasReturnPoint;
        this.returnDestination = returnDestination;
        this.onPlanet = onPlanet;
    }

    // ====================================================================================
    // Snapshot
    // ====================================================================================

    /** Builds the list this player should see, and writes it for the client. */
    public static void writeSnapshot(ServerPlayer player, BlockPos terminalPos, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeEnum(PlanetTravelManager.checkTerminal(player, terminalPos));

        PlanetUnlockData data = PlanetUnlockData.get(player.serverLevel());
        List<PlanetDefinition> planets = PlanetRegistry.all();

        buffer.writeVarInt(planets.size());
        for (PlanetDefinition planet : planets) {
            planet.write(buffer);
            buffer.writeEnum(PlanetTravelManager.stateOf(player, planet));

            List<PlanetUnlockRequirement> missing = data.missingFor(player.getUUID(), planet);
            buffer.writeVarInt(missing.size());
            for (PlanetUnlockRequirement requirement : missing) {
                requirement.write(buffer);
            }
        }

        PlanetUnlockData.ReturnPoint point = data.returnPoint(player.getUUID());
        buffer.writeBoolean(point != null);

        if (point != null) {
            buffer.writeBoolean(point.planetId() != null);
            if (point.planetId() != null) {
                buffer.writeResourceLocation(point.planetId());
            }
        }

        buffer.writeBoolean(FCPlanets.isCrusadeWorld(player.level().dimension()));
    }

    public static List<Entry> buildEntries(ServerPlayer player) {
        PlanetUnlockData data = PlanetUnlockData.get(player.serverLevel());
        List<Entry> entries = new ArrayList<>();

        for (PlanetDefinition planet : PlanetRegistry.all()) {
            entries.add(new Entry(planet, PlanetTravelManager.stateOf(player, planet),
                    data.missingFor(player.getUUID(), planet)));
        }

        return entries;
    }

    // ====================================================================================
    // Accessors
    // ====================================================================================

    public BlockPos getTerminalPos() {
        return this.terminalPos;
    }

    public List<Entry> getEntries() {
        return this.entries;
    }

    public PlanetTravelManager.Blocker getTerminalBlocker() {
        return this.terminalBlocker;
    }

    public boolean hasReturnPoint() {
        return this.hasReturnPoint;
    }

    /**
     * Whether the player is standing on one of the mod's planets.
     *
     * <p>This is what decides if "return" is offered at all. A record of where somebody came from is
     * not the same question: a traveller on Armageddon whose record was lost still needs a way off
     * it, and the server falls back to the overworld for exactly that case.
     */
    public boolean isOnPlanet() {
        return this.onPlanet;
    }

    /** True when the return leg is something the player can actually ask for. */
    public boolean canReturn() {
        return this.onPlanet || this.hasReturnPoint;
    }

    /** The planet the return leg leads to, or empty when it leads to the ordinary overworld. */
    public Optional<PlanetDefinition> getReturnDestination() {
        return Optional.ofNullable(this.returnDestination);
    }

    public Component returnDestinationName() {
        return getReturnDestination()
                .map(PlanetDefinition::displayName)
                .orElseGet(() -> Component.translatable("msg.firstcrusade.spaceport.home"));
    }

    // ====================================================================================
    // Container contract
    // ====================================================================================

    /**
     * Polled server-side every tick. Walking away from the terminal closes the screen, and the
     * closing is the server's decision rather than the client's courtesy.
     */
    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(this.terminalPos)
                .is(FCRegistry.PLANETARY_NAVIGATION_TERMINAL.get())
                && player.blockPosition().distSqr(this.terminalPos)
                        <= PlanetTravelManager.TERMINAL_RANGE * PlanetTravelManager.TERMINAL_RANGE;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
