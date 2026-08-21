package com.example.examplemod.campaign.supply;

import com.example.examplemod.StrategicResourceType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * One lane of the Crusade's logistics: what goes from which world to which, and whether it arrives.
 *
 * <h2>Strategic, not physical</h2>
 *
 * Nothing travels. There is no convoy entity crossing five thousand blocks and no cargo to intercept,
 * because a truck driving for twenty real minutes through unloaded chunks is a tick cost with no
 * gameplay attached to it. A route is a claim about the war — "Verdanis feeds Armageddon" — and the
 * gameplay is in whether that claim survives contact with the enemy.
 *
 * <p>What makes it matter is {@link SupplyNetwork}'s blocking rule: hold the Spaceport on one end and
 * the lane stops. That is the whole reason a sector is worth taking beyond its own percentage — the
 * brief's "Orks control Armageddon's Spaceport → less food, less promethium, slower reinforcements".
 *
 * <h2>Amount vs delivered</h2>
 *
 * {@link #amount} is what the lane is <i>rated</i> for, from the producing world's sectors.
 * {@link #delivered} is what actually got through after the state's throughput and the origin's real
 * production. Keeping both is what lets the War Table say "48 of 120" rather than only showing a
 * number that silently shrank.
 *
 * @param id          {@code agri_world>armageddon:FOOD} — unique per origin/destination/resource
 * @param origin      the front that produces
 * @param destination the front that consumes
 * @param resource    what moves
 */
public class SupplyRoute {

    private final String id;
    private final ResourceLocation origin;
    private final ResourceLocation destination;
    private final StrategicResourceType resource;

    private int amount;
    private int delivered;
    private SupplyState state = SupplyState.ACTIVE;

    /** Why the route is in the state it is, in plain text, for the War Table and the commands. */
    private String reason = "";
    private String reasonArg = "";

    public SupplyRoute(ResourceLocation origin, ResourceLocation destination,
                       StrategicResourceType resource) {
        this.origin = origin;
        this.destination = destination;
        this.resource = resource;
        this.id = idOf(origin, destination, resource);
    }

    public static String idOf(ResourceLocation origin, ResourceLocation destination,
                              StrategicResourceType resource) {
        return origin.getPath() + ">" + destination.getPath() + ":" + resource.name();
    }

    // ====================================================================================
    // Reading
    // ====================================================================================

    public String id() {
        return this.id;
    }

    public ResourceLocation origin() {
        return this.origin;
    }

    public ResourceLocation destination() {
        return this.destination;
    }

    public StrategicResourceType resource() {
        return this.resource;
    }

    /** What the lane is rated to carry per strategic pass. */
    public int amount() {
        return this.amount;
    }

    /** What actually arrived on the last pass. */
    public int delivered() {
        return this.delivered;
    }

    public SupplyState state() {
        return this.state;
    }

    /**
     * Why the lane is not carrying, as a translation key — empty when nothing is wrong.
     *
     * <p>A key and not a sentence. These used to be Portuguese strings built on the server and drawn
     * verbatim, which put "spaceport de agri_world em mãos inimigas" underneath an English route
     * line on an English client. The server does not know the reader's language and must not write
     * prose.
     */
    public String reason() {
        return this.reason;
    }

    /**
     * The one substitution {@link #reason} takes, itself a translation key.
     *
     * <p>Always a key, never prose, and always fed through {@code Component.translatable} by the
     * reader — including for the resource case, where the value is a plain name like {@code Food}
     * that no language file defines. A missing key renders as itself, so one code path covers both
     * "translate this planet's name" and "print this resource as-is" without the drawing side having
     * to know which it got.
     */
    public String reasonArg() {
        return this.reasonArg;
    }

    /** 0-100, for a bar on the War Table. */
    public int efficiency() {
        return this.amount <= 0 ? 0 : Math.min(100, Math.round(100.0F * this.delivered / this.amount));
    }

    // ====================================================================================
    // Writing
    // ====================================================================================

    public void setAmount(int value) {
        this.amount = Math.max(0, value);
    }

    public void setDelivered(int value) {
        this.delivered = Math.max(0, value);
    }

    /**
     * Sets the route's state.
     *
     * @return the previous state if this changed it, otherwise null — so the caller can log a change
     *         and only a change
     */
    public SupplyState setState(SupplyState next, String why, String whyArg) {
        this.reason = why == null ? "" : why;
        this.reasonArg = whyArg == null ? "" : whyArg;

        if (next == null || next == this.state) {
            return null;
        }

        SupplyState previous = this.state;
        this.state = next;
        return previous;
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Origin", this.origin.toString());
        tag.putString("Destination", this.destination.toString());
        tag.putString("Resource", this.resource.name());
        tag.putInt("Amount", this.amount);
        tag.putInt("Delivered", this.delivered);
        tag.putString("State", this.state.name());
        tag.putString("Reason", this.reason);
        tag.putString("ReasonArg", this.reasonArg);
        return tag;
    }

    /** @return null for a tag whose fronts or resource this installation no longer has */
    public static SupplyRoute load(CompoundTag tag) {
        ResourceLocation origin = ResourceLocation.tryParse(tag.getString("Origin"));
        ResourceLocation destination = ResourceLocation.tryParse(tag.getString("Destination"));

        if (origin == null || destination == null) {
            return null;
        }

        StrategicResourceType resource = readResource(tag.getString("Resource"));

        if (resource == null) {
            return null;
        }

        SupplyRoute route = new SupplyRoute(origin, destination, resource);

        route.amount = tag.getInt("Amount");
        route.delivered = tag.getInt("Delivered");
        route.state = SupplyState.fromName(tag.getString("State"));
        // A save written before the reasons became keys holds a Portuguese sentence here. It is left
        // alone rather than migrated: an undefined key renders as itself, so it reads exactly as it
        // used to, and the first strategic pass after loading overwrites it with a real key anyway.
        route.reason = tag.getString("Reason");
        route.reasonArg = tag.getString("ReasonArg");

        return route;
    }

    private static StrategicResourceType readResource(String name) {
        for (StrategicResourceType type : StrategicResourceType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }

        return null;
    }

    /**
     * {@code agri_world -> armageddon  FOOD  48/120  DISRUPTED  (reason)} — the command's line.
     *
     * <p>A Component and not a String because the reason is a translation key now, and the command
     * has to resolve it the same way the War Table does or the one surface built to tell the truth
     * would print {@code supply.firstcrusade.reason.spaceport_lost} at a person.
     */
    public Component shortText() {
        MutableComponent text = Component.literal(this.origin.getPath() + " -> "
                + this.destination.getPath()
                + "  " + this.resource.name()
                + "  " + this.delivered + "/" + this.amount
                + "  " + this.state.name());

        if (!this.reason.isEmpty()) {
            text.append("  (").append(describeReason()).append(")");
        }

        return text;
    }

    /** The reason as displayable text, with its argument substituted. Empty when nothing is wrong. */
    public Component describeReason() {
        if (this.reason.isEmpty()) {
            return Component.empty();
        }

        return Component.translatable(this.reason, Component.translatable(this.reasonArg));
    }
}
