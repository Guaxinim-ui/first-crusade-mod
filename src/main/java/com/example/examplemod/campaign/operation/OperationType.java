package com.example.examplemod.campaign.operation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The kinds of order the Crusade issues.
 *
 * <h2>Generated from the war, not from a list</h2>
 *
 * None of these is picked at random. {@link OperationManager} reads the front — who holds what, what
 * is disputed, how hard it is being fought — and issues the order that situation calls for. A planet
 * the Imperium is losing offers DEFEND; one with an intact enemy seat offers ASSASSINATION; one
 * already held offers RECON on the ground nobody has walked. That is the difference between missions
 * that describe the war and missions that are wallpaper over it.
 *
 * <p>ESCORT is the one exception to "generated from the war": it is raised by the convoy layer for a
 * particular convoy rather than chosen from the front's shape. See its own note.
 *
 * @see OperationTrigger for why two of these are defined but not yet issued
 */
public enum OperationType {

    /** Hold a sector until the clock runs out. */
    DEFEND("defend", OperationTrigger.SECTOR_HELD, 1, 4800, 1.0D, ChatFormatting.GOLD),

    /** Break the enemy on this front: kill enough of them. */
    ASSAULT("assault", OperationTrigger.KILL_ENEMY, 12, 12_000, 1.1D, ChatFormatting.RED),

    /** Take a named sector. */
    CAPTURE("capture", OperationTrigger.SECTOR_TAKEN, 1, 24_000, 1.4D, ChatFormatting.AQUA),

    /** Raze enemy holdings on this front. */
    DESTROY("destroy", OperationTrigger.RAZE_CAMP, 2, 24_000, 1.3D, ChatFormatting.LIGHT_PURPLE),

    /** Walk ground nobody has walked. Cheap, and the only order a quiet front can offer. */
    RECON("recon", OperationTrigger.VISIT_SECTOR, 1, 12_000, 0.5D, ChatFormatting.GREEN),

    /** Stay alive on a front that is trying to kill you. */
    SURVIVE("survive", OperationTrigger.TIME_ON_FRONT, 6, 9600, 0.8D, ChatFormatting.YELLOW),

    /** Kill the thing running the enemy war effort here. */
    ASSASSINATION("assassination", OperationTrigger.KILL_LEADER, 1, 24_000, 1.8D, ChatFormatting.DARK_RED),

    /**
     * See a relief convoy through to this front.
     *
     * <p>The one order that is not chosen from the shape of the war: it is raised by
     * {@link com.example.examplemod.campaign.convoy.ConvoyManager} when a cut lane forces a shipment
     * through, and it names that one convoy. Required is always 1 — a convoy either lands or it does
     * not — and the play is in the integrity it lands with, not in a counter.
     */
    ESCORT("escort", OperationTrigger.CONVOY_ARRIVED, 1, 12_000, 1.2D, ChatFormatting.WHITE),

    // ------------------------------------------------------------------ defined, not yet issued

    /** Reach a cut-off force. Waiting on rescuable squads existing as a thing the world can hold. */
    RESCUE("rescue", OperationTrigger.MANUAL, 1, 12_000, 1.2D, ChatFormatting.WHITE),

    /** Bring something back. Waiting on the artefacts it would be about. */
    RECOVER("recover", OperationTrigger.MANUAL, 1, 24_000, 1.5D, ChatFormatting.WHITE);

    private final String key;
    private final OperationTrigger trigger;
    private final int baseRequired;
    private final int baseDurationTicks;
    private final double rewardScale;
    private final ChatFormatting colour;

    OperationType(String key, OperationTrigger trigger, int baseRequired, int baseDurationTicks,
                  double rewardScale, ChatFormatting colour) {
        this.key = key;
        this.trigger = trigger;
        this.baseRequired = baseRequired;
        this.baseDurationTicks = baseDurationTicks;
        this.rewardScale = rewardScale;
        this.colour = colour;
    }

    public String key() {
        return this.key;
    }

    public OperationTrigger trigger() {
        return this.trigger;
    }

    /** How much of the thing has to happen. Scaled up by the front's intensity when issued. */
    public int baseRequired() {
        return this.baseRequired;
    }

    /** How long the order stands before it expires. */
    public int baseDurationTicks() {
        return this.baseDurationTicks;
    }

    /** Multiplier on the reward, so a harder order is worth more. */
    public double rewardScale() {
        return this.rewardScale;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    public Component displayName() {
        return Component.translatable("operation.firstcrusade." + this.key).withStyle(this.colour);
    }

    /** True for an operation that names a particular sector rather than the front as a whole. */
    public boolean needsTargetSector() {
        return this.trigger == OperationTrigger.SECTOR_HELD
                || this.trigger == OperationTrigger.SECTOR_TAKEN
                || this.trigger == OperationTrigger.VISIT_SECTOR;
    }

    /** True for an operation that hands its target sector over on success. */
    public boolean capturesOnSuccess() {
        return this == CAPTURE;
    }

    public static OperationType fromName(String name) {
        if (name != null) {
            for (OperationType type : values()) {
                if (type.name().equalsIgnoreCase(name) || type.key.equalsIgnoreCase(name)) {
                    return type;
                }
            }
        }

        return RECON;
    }
}
