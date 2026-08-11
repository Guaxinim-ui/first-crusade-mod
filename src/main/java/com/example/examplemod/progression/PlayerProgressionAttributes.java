package com.example.examplemod.progression;

import java.util.UUID;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

/**
 * Turns the progression's totals into attribute modifiers, and does it without ever stacking them
 * twice.
 *
 * <h2>Fixed UUIDs are the whole trick</h2>
 *
 * Each attribute gets exactly one modifier from this system, under a UUID that never changes. Apply
 * is therefore always remove-then-add: whatever a previous login, respawn or recalculation left
 * behind is found by its UUID and taken off first. A random UUID per application is how a player
 * ends up with forty stacked health modifiers after forty relogs, and it is the failure this class
 * exists to make impossible.
 *
 * <h2>Health is a percentage, not a number</h2>
 *
 * Raising max health moves the ceiling but not the player's current health, so a Guardsman who gains
 * four hearts at 50% would silently drop to 30%. {@link #apply} therefore measures the fraction
 * first and restores it afterwards — the player keeps how hurt they were, not how many hearts they
 * had. It never heals them past what they had.
 */
public final class PlayerProgressionAttributes {
    private PlayerProgressionAttributes() {
    }

    private static final UUID HEALTH_ID = UUID.fromString("6f2f1a10-51b0-4c4f-8f52-0a1c2e9c7a01");
    private static final UUID ARMOR_ID = UUID.fromString("6f2f1a10-51b0-4c4f-8f52-0a1c2e9c7a02");
    private static final UUID TOUGHNESS_ID = UUID.fromString("6f2f1a10-51b0-4c4f-8f52-0a1c2e9c7a03");
    private static final UUID DAMAGE_ID = UUID.fromString("6f2f1a10-51b0-4c4f-8f52-0a1c2e9c7a04");
    private static final UUID KNOCKBACK_ID = UUID.fromString("6f2f1a10-51b0-4c4f-8f52-0a1c2e9c7a05");
    private static final UUID SPEED_ID = UUID.fromString("6f2f1a10-51b0-4c4f-8f52-0a1c2e9c7a06");
    private static final UUID STEP_ID = UUID.fromString("6f2f1a10-51b0-4c4f-8f52-0a1c2e9c7a07");

    private static final String NAME = "First Crusade progression";

    /**
     * Recalculates every attribute this system owns.
     *
     * <p>Called on login, respawn, dimension change, every purchase, every completed surgery and the
     * two transformations. It is idempotent by construction: running it twice in a row leaves the
     * player exactly as running it once did.
     */
    public static void apply(Player player, PlayerProgressionManager.Totals totals) {
        double healthFraction = player.getMaxHealth() > 0.0F
                ? player.getHealth() / player.getMaxHealth()
                : 1.0D;

        set(player, Attributes.MAX_HEALTH, HEALTH_ID, totals.maxHealth(),
                AttributeModifier.Operation.ADDITION);
        set(player, Attributes.ARMOR, ARMOR_ID, totals.armor(),
                AttributeModifier.Operation.ADDITION);
        set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, totals.armorToughness(),
                AttributeModifier.Operation.ADDITION);
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, totals.attackDamage(),
                AttributeModifier.Operation.ADDITION);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, totals.knockbackResistance(),
                AttributeModifier.Operation.ADDITION);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, totals.movementSpeedPercent(),
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        set(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID, totals.stepHeight(),
                AttributeModifier.Operation.ADDITION);

        // Restore the fraction, never more. A player at half health stays at half health.
        if (player.getMaxHealth() > 0.0F) {
            float wanted = (float) (player.getMaxHealth() * healthFraction);
            player.setHealth(Math.min(wanted, player.getMaxHealth()));
        }
    }

    /** Strips everything this system owns. Used by {@code /fcprogress reset}. */
    public static void clear(Player player) {
        remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(player, Attributes.ARMOR, ARMOR_ID);
        remove(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
        remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
        remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        remove(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID);

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void set(Player player, Attribute attribute, UUID id, double amount,
                            AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        instance.removeModifier(id);

        if (Math.abs(amount) > 1.0E-6D) {
            instance.addPermanentModifier(new AttributeModifier(id, NAME, amount, operation));
        }
    }

    private static void remove(Player player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
