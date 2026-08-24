package com.example.examplemod.campaign.convoy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Where a convoy is in the one journey it ever makes.
 *
 * <p>Three states and no more, because a convoy has exactly one decision in its life: it gets there
 * or it does not. There is no MUSTERING here the way {@link
 * com.example.examplemod.campaign.force.DeploymentState} has one — a deployment gathers troops that
 * have to come from somewhere, while a relief convoy is dispatched the moment the lane fails, and a
 * gathering phase would only add a window in which nothing can be done about it.
 */
public enum ConvoyState {

    /** On the run, losing integrity to the war it is crossing. */
    IN_TRANSIT("in_transit", ChatFormatting.YELLOW),

    /** Made it. Whatever integrity was left decides how much of the cargo landed. */
    ARRIVED("arrived", ChatFormatting.GREEN),

    /** Integrity reached zero. The cargo is gone and the lane is still cut. */
    LOST("lost", ChatFormatting.RED);

    private final String key;
    private final ChatFormatting colour;

    ConvoyState(String key, ChatFormatting colour) {
        this.key = key;
        this.colour = colour;
    }

    public String key() {
        return this.key;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    public boolean isFinished() {
        return this != IN_TRANSIT;
    }

    public Component displayName() {
        return Component.translatable("convoy.firstcrusade.state." + this.key).withStyle(this.colour);
    }

    public static ConvoyState fromName(String name) {
        if (name != null) {
            for (ConvoyState state : values()) {
                if (state.name().equalsIgnoreCase(name)) {
                    return state;
                }
            }
        }

        return IN_TRANSIT;
    }
}
