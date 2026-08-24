package com.example.examplemod.campaign.convoy;

import com.example.examplemod.StrategicResourceType;
import com.example.examplemod.campaign.supply.SupplyRoute;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * A relief shipment forced down a lane the war has cut.
 *
 * <h2>Why this exists when {@link SupplyRoute} says nothing travels</h2>
 *
 * It still does not. {@link SupplyRoute}'s note rules out a cargo entity crossing five thousand
 * blocks of unloaded chunk, and that ruling stands: a convoy is a record with a clock, exactly like
 * {@link com.example.examplemod.campaign.force.StrategicDeployment}, which has modelled a strength
 * with an origin, a destination and a travel time since the force layer was written. Nothing is
 * spawned, nothing is pathfound, and no chunk is touched.
 *
 * <p>What a convoy adds to the lane underneath it is a thing that can be <i>lost</i>. The ordinary
 * lane is arithmetic: a number arrives every pass and the only way to change it is to take a
 * spaceport. That is the right model for routine logistics and a dead end for gameplay, because
 * there is no moment in it. A convoy is the moment — dispatched precisely when the arithmetic has
 * already failed, carrying several passes' worth in one run, and able to not arrive.
 *
 * <h2>Integrity, not strength</h2>
 *
 * A convoy does not fight. {@link #integrity} is how much of it is still on the road, 0 to 100, and
 * it only ever goes down from the war and up from somebody defending it.
 *
 * <p>The cargo that lands is scaled by the integrity that survived, rather than being all-or-nothing
 * on arrival. All-or-nothing would make every point above zero worth exactly the same, which is to
 * say it would make defending a convoy that is going to make it anyway pointless. Scaling puts a
 * price on each point and gives the player something to do for the whole run instead of only at the
 * end.
 *
 * <h2>Escorted at the destination, and dispatched despite the blockade</h2>
 *
 * The run is modelled as ending rather than beginning: the front at risk is {@link #destination},
 * because that is where the cargo is needed, where the player who needs it is standing, and where an
 * order can be paid into a Command Core. And a lane whose <i>origin</i> spaceport is the thing in
 * enemy hands still dispatches — running the blockade is the entire reason a relief convoy is a
 * thing anybody would order.
 */
public class Convoy {

    /** Full integrity. A convoy that is never touched arrives with all of it. */
    public static final int FULL_INTEGRITY = 100;

    private final String id;

    /** The lane this runs, so a finished convoy can still hold that lane's cooldown open. */
    private final String laneId;

    private final ResourceLocation origin;
    private final ResourceLocation destination;
    private final StrategicResourceType resource;

    /** What it set out with. Never changes; what lands is this scaled by surviving integrity. */
    private final int cargo;

    private int integrity = FULL_INTEGRITY;

    private ConvoyState state = ConvoyState.IN_TRANSIT;

    private final long departedAt;
    private final long arrivesAt;

    /** Game time it stopped being in transit, or 0 while it still is. For the retirement sweep. */
    private long finishedAt;

    /**
     * The ESCORT order raised for this run, or empty.
     *
     * <p>Held so arrival resolves <i>that</i> order rather than every ESCORT standing on the front.
     * Two convoys can be in the air at once, and without the link the first to land would complete
     * both orders.
     */
    private String escortOperationId = "";

    /**
     * True once anyone has been on the destination front while this run was in the air.
     *
     * <h2>What it is for: an order nobody took up must not pay</h2>
     *
     * The cargo does not need a witness — logistics happens whether or not the player is looking, and
     * an arrived convoy delivers either way. The <i>order</i> is a different claim: "see the convoy
     * through" is something a person does, and with cut lanes numbering in double figures a convoy
     * lands every couple of minutes forever. Completing the escort for every one of those would have
     * paid research and dominion on a timer, with no action attached to it — an idle income the
     * player could neither influence nor turn off.
     *
     * <p>So an unescorted arrival lapses its order and keeps its cargo. One pass of presence is
     * enough, deliberately: how <i>much</i> escorting happened is already measured, by the integrity
     * the kills bought, and asking the question twice would punish a player for arriving late to a run
     * they then saved.
     */
    private boolean escorted;

    public Convoy(String id, ResourceLocation origin, ResourceLocation destination,
                  StrategicResourceType resource, int cargo, long departedAt, long arrivesAt) {
        this.id = id;
        this.laneId = SupplyRoute.idOf(origin, destination, resource);
        this.origin = origin;
        this.destination = destination;
        this.resource = resource;
        this.cargo = Math.max(1, cargo);
        this.departedAt = departedAt;
        this.arrivesAt = arrivesAt;
    }

    // ====================================================================================
    // Reading
    // ====================================================================================

    public String id() {
        return this.id;
    }

    public String laneId() {
        return this.laneId;
    }

    public ResourceLocation origin() {
        return this.origin;
    }

    /** The front the cargo is for, and the one the convoy is at risk on. */
    public ResourceLocation destination() {
        return this.destination;
    }

    public StrategicResourceType resource() {
        return this.resource;
    }

    public int cargo() {
        return this.cargo;
    }

    public int integrity() {
        return this.integrity;
    }

    public ConvoyState state() {
        return this.state;
    }

    public boolean isInTransit() {
        return this.state == ConvoyState.IN_TRANSIT;
    }

    public long departedAt() {
        return this.departedAt;
    }

    public long arrivesAt() {
        return this.arrivesAt;
    }

    public long finishedAt() {
        return this.finishedAt;
    }

    public String escortOperationId() {
        return this.escortOperationId;
    }

    public boolean hasEscortOrder() {
        return !this.escortOperationId.isEmpty();
    }

    /** True when anyone was on the destination front at any point during the run. */
    public boolean wasEscorted() {
        return this.escorted;
    }

    public long ticksRemaining(long gameTime) {
        return Math.max(0L, this.arrivesAt - gameTime);
    }

    /** How much of the cargo would land right now, scaled by surviving integrity. */
    public int deliverable() {
        return Math.max(0, Math.round(this.cargo * this.integrity / (float) FULL_INTEGRITY));
    }

    // ====================================================================================
    // Writing
    // ====================================================================================

    public void setEscortOperationId(String operationId) {
        this.escortOperationId = operationId == null ? "" : operationId;
    }

    /** Records that somebody was on the destination front for this pass. Never unset. */
    public void markEscorted() {
        this.escorted = true;
    }

    /**
     * The war takes its bite.
     *
     * @return true when this run was the one that finished it off
     */
    public boolean damage(int amount) {
        if (!isInTransit() || amount <= 0) {
            return false;
        }

        this.integrity = Math.max(0, this.integrity - amount);
        return this.integrity <= 0;
    }

    /**
     * Somebody killed something that was shooting at it.
     *
     * <p>Capped at {@link #FULL_INTEGRITY} rather than allowed to bank: a player who clears the front
     * early must not be able to store up protection against attrition that has not happened yet, or
     * the escort becomes a thing you finish before it starts.
     */
    public void defend(int amount) {
        if (!isInTransit() || amount <= 0) {
            return;
        }

        this.integrity = Math.min(FULL_INTEGRITY, this.integrity + amount);
    }

    public void arrive(long gameTime) {
        finishAs(ConvoyState.ARRIVED, gameTime);
    }

    public void lose(long gameTime) {
        this.integrity = 0;
        finishAs(ConvoyState.LOST, gameTime);
    }

    /**
     * The one place a convoy stops being in transit.
     *
     * <p>Stamping {@link #finishedAt} here and not at the two call sites is the same guarantee
     * {@link com.example.examplemod.campaign.operation.Operation} makes for the same reason: a zero
     * left behind reads to the retirement sweep as "finished at the dawn of the world", and the
     * record would be swept away in the pass that ended it — taking the lane's cooldown with it.
     */
    private void finishAs(ConvoyState ending, long gameTime) {
        this.state = ending;
        this.finishedAt = gameTime;
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("Origin", this.origin.toString());
        tag.putString("Destination", this.destination.toString());
        tag.putString("Resource", this.resource.name());
        tag.putInt("Cargo", this.cargo);
        tag.putInt("Integrity", this.integrity);
        tag.putString("State", this.state.name());
        tag.putLong("Departed", this.departedAt);
        tag.putLong("Arrives", this.arrivesAt);
        tag.putLong("Finished", this.finishedAt);
        tag.putString("Escort", this.escortOperationId);
        tag.putBoolean("Escorted", this.escorted);
        return tag;
    }

    /** @return null for a tag naming fronts or a resource this installation no longer has */
    public static Convoy load(CompoundTag tag) {
        ResourceLocation origin = ResourceLocation.tryParse(tag.getString("Origin"));
        ResourceLocation destination = ResourceLocation.tryParse(tag.getString("Destination"));

        if (origin == null || destination == null) {
            return null;
        }

        StrategicResourceType resource = readResource(tag.getString("Resource"));

        if (resource == null) {
            return null;
        }

        Convoy convoy = new Convoy(
                tag.getString("Id"),
                origin,
                destination,
                resource,
                tag.getInt("Cargo"),
                tag.getLong("Departed"),
                tag.getLong("Arrives"));

        convoy.integrity = tag.getInt("Integrity");
        convoy.state = ConvoyState.fromName(tag.getString("State"));
        convoy.finishedAt = tag.getLong("Finished");
        convoy.escortOperationId = tag.getString("Escort");
        convoy.escorted = tag.getBoolean("Escorted");

        return convoy;
    }

    private static StrategicResourceType readResource(String name) {
        for (StrategicResourceType type : StrategicResourceType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }

        return null;
    }

    // ====================================================================================
    // Display
    // ====================================================================================

    /** {@code agri_world -> armageddon  FOOD  120  int 74%  IN_TRANSIT  (38s)} — the command's line. */
    public Component shortText(long gameTime) {
        MutableComponent text = Component.literal(this.origin.getPath() + " -> "
                + this.destination.getPath()
                + "  " + this.resource.name()
                + "  " + this.cargo
                + "  int " + this.integrity + "%"
                + "  " + this.state.name());

        if (isInTransit()) {
            text.append("  (" + (ticksRemaining(gameTime) / 20L) + "s)");
        }

        return text;
    }
}
