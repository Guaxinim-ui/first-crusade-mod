package com.example.examplemod.campaign.wartable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * What a commander may tell the Crusade to do from the War Table.
 *
 * <h2>Three orders, not eight</h2>
 *
 * The brief lists defend, attack, reinforce, transfer, withdraw, protect a city, attack a camp and
 * escort. Most of those are the same order with a different target: "protect the city" is defend on
 * the sector the city sits in, and "attack the Ork camp" is assault on the sector the camp presses.
 * Collapsing them means one validation path and one cost table rather than eight that have to be
 * kept in agreement — and it means a sector is the only thing an order can ever name, which is what
 * makes every order checkable against the same question.
 *
 * <p>Escort and transfer are genuinely different and are deliberately absent: escort needs a convoy
 * to exist, and transfer needs troops to have a location to move <i>from</i>, which the strategic
 * layer does not model yet. Shipping them as buttons that quietly did something else would be worse
 * than not shipping them.
 *
 * <h2>Cost is War Support</h2>
 *
 * Every order is paid for out of the nearest Command Core's War Support — the pool that already buys
 * reinforcements, fortification and specialists. That is deliberate: it makes commanding the wider
 * war compete with defending your own city, which is the trade the whole campaign is about. It is
 * never paid in gene-seed; that ladder stays separate.
 */
public enum WarTableOrder {
    /** Hold a sector the Imperium already has. Cheap, and it presses nothing. */
    DEFEND("defend", 4, 10, ChatFormatting.GOLD),

    /** Take a sector the enemy holds. The expensive one. */
    ASSAULT("assault", 10, 18, ChatFormatting.RED),

    /** Feed strength into a sector already being fought over. */
    REINFORCE("reinforce", 6, 12, ChatFormatting.AQUA);

    private final String key;
    private final int warSupportCost;
    private final int strength;
    private final ChatFormatting colour;

    WarTableOrder(String key, int warSupportCost, int strength, ChatFormatting colour) {
        this.key = key;
        this.warSupportCost = warSupportCost;
        this.strength = strength;
        this.colour = colour;
    }

    public String key() {
        return this.key;
    }

    public int warSupportCost() {
        return this.warSupportCost;
    }

    /** The strength of the deployment this order raises. */
    public int strength() {
        return this.strength;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    public Component displayName() {
        return Component.translatable("order.firstcrusade." + this.key).withStyle(this.colour);
    }

    /** True for an order that only makes sense against ground the enemy holds. */
    public boolean needsEnemyTarget() {
        return this == ASSAULT;
    }

    /** True for an order that only makes sense against ground the Imperium holds. */
    public boolean needsFriendlyTarget() {
        return this == DEFEND;
    }

    public static WarTableOrder fromName(String name) {
        if (name != null) {
            for (WarTableOrder order : values()) {
                if (order.name().equalsIgnoreCase(name) || order.key.equalsIgnoreCase(name)) {
                    return order;
                }
            }
        }

        return DEFEND;
    }
}
