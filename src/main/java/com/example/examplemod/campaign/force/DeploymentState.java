package com.example.examplemod.campaign.force;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Where a strategic deployment is in its life.
 *
 * <h2>Why mustering is a state and not an instant</h2>
 *
 * An order given at the War Table does not put troops on the target that second. It gathers them,
 * then moves them, then commits them. That delay is the whole reason a defensive order can arrive
 * too late and an enemy offensive can be intercepted — with no delay, the strategic layer would be a
 * button that teleports a result, and there would be nothing for a player to react to.
 *
 * <p>Every state is a real branch in {@link DeploymentManager}: MUSTERING and MOVING do nothing but
 * count down, COMMITTED is the only one that applies pressure or materialises, and SPENT exists so a
 * finished deployment stays visible on the War Table for a moment instead of vanishing mid-glance.
 */
public enum DeploymentState {
    /** Gathering at the origin. Not yet on its way, and cancellable. */
    MUSTERING("mustering", ChatFormatting.GRAY),

    /** On the march. Committed to the target, but not yet in contact. */
    MOVING("moving", ChatFormatting.YELLOW),

    /** In contact: applying pressure, and materialising if a player is near enough to see it. */
    COMMITTED("committed", ChatFormatting.GOLD),

    /** Used up — the strength is gone, or the target changed hands. */
    SPENT("spent", ChatFormatting.DARK_GRAY);

    private final String key;
    private final ChatFormatting colour;

    DeploymentState(String key, ChatFormatting colour) {
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
        return Component.translatable("deployment.firstcrusade.state." + this.key).withStyle(this.colour);
    }

    public boolean isActive() {
        return this != SPENT;
    }

    public static DeploymentState fromName(String name) {
        if (name != null) {
            for (DeploymentState state : values()) {
                if (state.name().equalsIgnoreCase(name)) {
                    return state;
                }
            }
        }

        return MUSTERING;
    }
}
