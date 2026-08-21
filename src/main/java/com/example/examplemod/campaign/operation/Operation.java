package com.example.examplemod.campaign.operation;

import javax.annotation.Nullable;

import com.example.examplemod.campaign.sector.SectorType;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One order the Crusade has issued: what to do, where, by when, and what it pays.
 *
 * <h2>Progress is counted on the server and nowhere else</h2>
 *
 * Every field here is written only by {@link OperationManager}, from events the server observed. No
 * packet advances an operation, and no client is asked whether one is complete. That is not caution
 * for its own sake: an operation pays resources, War Support and sector control, so a client that
 * could report its own progress could award itself a planet.
 */
public class Operation {

    private final String id;
    private final OperationType type;
    private final ResourceLocation frontId;

    /** The sector this order names, or empty for a front-wide one. */
    private final String targetSectorId;

    /** Kept alongside the sector id so the order still reads correctly if the sector is gone. */
    @Nullable
    private final SectorType targetType;

    private final int required;
    private final OperationReward reward;

    /** Game time the order was issued, and the time it stops standing. */
    private final long issuedAt;
    private final long expiresAt;

    private OperationState state = OperationState.ACTIVE;
    private int progress;

    /**
     * Game time the order stopped being active, or 0 while it still is.
     *
     * <p>The retirement grace period is measured from here, not from {@link #issuedAt}. Measured
     * from issue, a long order — CAPTURE stands for twenty minutes — would already be older than the
     * grace by the time it finished, and would be swept off the board in the same pass that
     * completed it. The player would be paid for something they never saw succeed.
     */
    private long finishedAt;

    public Operation(String id, OperationType type, ResourceLocation frontId, String targetSectorId,
                     @Nullable SectorType targetType, int required, OperationReward reward,
                     long issuedAt, long expiresAt) {
        this.id = id;
        this.type = type;
        this.frontId = frontId;
        this.targetSectorId = targetSectorId == null ? "" : targetSectorId;
        this.targetType = targetType;
        this.required = Math.max(1, required);
        this.reward = reward == null ? OperationReward.NONE : reward;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    // ====================================================================================
    // Reading
    // ====================================================================================

    public String id() {
        return this.id;
    }

    public OperationType type() {
        return this.type;
    }

    public OperationTrigger trigger() {
        return this.type.trigger();
    }

    public ResourceLocation frontId() {
        return this.frontId;
    }

    public String targetSectorId() {
        return this.targetSectorId;
    }

    public boolean hasTarget() {
        return !this.targetSectorId.isEmpty();
    }

    @Nullable
    public SectorType targetType() {
        return this.targetType;
    }

    public int required() {
        return this.required;
    }

    public int progress() {
        return this.progress;
    }

    public OperationReward reward() {
        return this.reward;
    }

    public OperationState state() {
        return this.state;
    }

    public long issuedAt() {
        return this.issuedAt;
    }

    public long expiresAt() {
        return this.expiresAt;
    }

    public boolean isActive() {
        return this.state == OperationState.ACTIVE;
    }

    public boolean isExpired(long gameTime) {
        return this.expiresAt > 0 && gameTime >= this.expiresAt;
    }

    /** Ticks left before the order lapses, or -1 for one with no clock. */
    public long ticksRemaining(long gameTime) {
        return this.expiresAt <= 0 ? -1 : Math.max(0, this.expiresAt - gameTime);
    }

    // ====================================================================================
    // Writing
    // ====================================================================================

    /**
     * Advances progress.
     *
     * @return true when this call completed the operation
     */
    public boolean advance(int amount, long gameTime) {
        if (!isActive() || amount <= 0) {
            return false;
        }

        this.progress = Math.min(this.required, this.progress + amount);

        if (this.progress >= this.required) {
            finishAs(OperationState.COMPLETED, gameTime);
            return true;
        }

        return false;
    }

    /** Marks the order complete regardless of the counter. For orders resolved by a state check. */
    public boolean complete(long gameTime) {
        if (!isActive()) {
            return false;
        }

        this.progress = this.required;
        finishAs(OperationState.COMPLETED, gameTime);
        return true;
    }

    public boolean fail(long gameTime) {
        if (!isActive()) {
            return false;
        }

        finishAs(OperationState.FAILED, gameTime);
        return true;
    }

    public boolean expire(long gameTime) {
        if (!isActive()) {
            return false;
        }

        // An order whose clock ran out having done nothing is failed, not merely lapsed. The
        // distinction matters because DEFEND is completed BY the clock running out — the manager
        // resolves that case before this is ever reached.
        finishAs(this.progress > 0 ? OperationState.FAILED : OperationState.EXPIRED, gameTime);
        return true;
    }

    /**
     * The single place an order stops being active. Stamping the time here rather than at each of
     * the four call sites is what guarantees no ending can forget to do it — and forgetting would
     * leave {@link #finishedAt} at zero, which the retirement sweep reads as "finished at the dawn
     * of the world" and deletes on sight.
     */
    private void finishAs(OperationState ending, long gameTime) {
        this.state = ending;
        this.finishedAt = gameTime;
    }

    /** Game time the order ended, or 0 while it is still standing. */
    public long finishedAt() {
        return this.finishedAt;
    }

    // ====================================================================================
    // Display
    // ====================================================================================

    /** "Capture the Manufactorum" / "Destroy 2 Ork holdings" — the line a player reads. */
    public Component describe() {
        Component target = this.targetType == null
                ? Component.translatable("operation.firstcrusade.target.front")
                : this.targetType.displayName();

        return Component.translatable("operation.firstcrusade." + this.type.key() + ".description",
                target, this.required);
    }

    public String shortText() {
        return this.type.name() + "  " + this.progress + "/" + this.required
                + "  " + this.state.name()
                + (hasTarget() ? "  -> " + this.targetSectorId : "");
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("Type", this.type.name());
        tag.putString("Front", this.frontId.toString());
        tag.putString("Target", this.targetSectorId);
        tag.putString("TargetType", this.targetType == null ? "" : this.targetType.name());
        tag.putInt("Required", this.required);
        tag.putInt("Progress", this.progress);
        tag.putString("State", this.state.name());
        tag.putLong("Issued", this.issuedAt);
        tag.putLong("Expires", this.expiresAt);
        tag.putLong("Finished", this.finishedAt);
        tag.put("Reward", this.reward.save());
        return tag;
    }

    @Nullable
    public static Operation load(CompoundTag tag) {
        ResourceLocation front = ResourceLocation.tryParse(tag.getString("Front"));

        if (front == null) {
            return null;
        }

        String targetType = tag.getString("TargetType");

        Operation operation = new Operation(
                tag.getString("Id"),
                OperationType.fromName(tag.getString("Type")),
                front,
                tag.getString("Target"),
                targetType.isEmpty() ? null : SectorType.fromName(targetType),
                tag.getInt("Required"),
                OperationReward.load(tag.getCompound("Reward")),
                tag.getLong("Issued"),
                tag.getLong("Expires"));

        operation.progress = tag.getInt("Progress");
        operation.state = OperationState.fromName(tag.getString("State"));
        operation.finishedAt = tag.getLong("Finished");

        return operation;
    }
}
