package com.example.examplemod.progression.client;

import net.minecraft.network.chat.Component;

/**
 * Which tree the progression screen is showing.
 *
 * <p>Client-only, and deliberately so: the tab is a viewing preference, not state the server has any
 * business knowing. Switching tabs sends no packet — the profile the client already holds contains
 * both careers, so there is nothing to fetch.
 */
public enum ProgressionTab {
    ASTARTES("gui.firstcrusade.progression.tab.astartes"),
    COMMAND("gui.firstcrusade.progression.tab.command");

    private final String key;

    ProgressionTab(String key) {
        this.key = key;
    }

    public Component title() {
        return Component.translatable(this.key);
    }
}
