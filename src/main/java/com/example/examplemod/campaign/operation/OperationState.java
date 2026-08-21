package com.example.examplemod.campaign.operation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Where an order stands.
 *
 * <p>{@link #FAILED} and {@link #EXPIRED} are both endings without a reward, and they are separate
 * because they are different facts about the player: FAILED means the order was worked on and lost,
 * EXPIRED means it was never taken up. A War Table that showed one number for both would be telling
 * a commander that ignoring orders and losing battles are the same thing.
 */
public enum OperationState {
    ACTIVE("active", ChatFormatting.YELLOW),
    COMPLETED("completed", ChatFormatting.GREEN),
    FAILED("failed", ChatFormatting.RED),
    EXPIRED("expired", ChatFormatting.DARK_GRAY);

    private final String key;
    private final ChatFormatting colour;

    OperationState(String key, ChatFormatting colour) {
        this.key = key;
        this.colour = colour;
    }

    public String key() {
        return this.key;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    public Component displayName() {
        return Component.translatable("operation.firstcrusade.state." + this.key).withStyle(this.colour);
    }

    /** True once the order is over, whatever the outcome. */
    public boolean isFinished() {
        return this != ACTIVE;
    }

    public static OperationState fromName(String name) {
        if (name != null) {
            for (OperationState state : values()) {
                if (state.name().equalsIgnoreCase(name)) {
                    return state;
                }
            }
        }

        return ACTIVE;
    }
}
