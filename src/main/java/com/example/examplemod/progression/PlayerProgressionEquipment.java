package com.example.examplemod.progression;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.example.examplemod.FCRegistry;

import net.minecraft.world.item.Item;

/**
 * Which wargear each stage is allowed to hold.
 *
 * <h2>A table, not a chain of ifs</h2>
 *
 * The gate is asked on every equip attempt and on a tick for what is already worn, so it has to be a
 * map lookup rather than a walk through eleven conditions. It is also the list a designer will want
 * to edit, and a list is easier to trust than a nest of branches.
 *
 * <h2>Refusal is not confiscation</h2>
 *
 * Nothing here ever deletes or replaces an item. The gate only answers "may this be used"; the event
 * that asks cancels the use and moves the piece back to the inventory. A progression system that
 * eats a player's Bolter because they were one implant short would be worse than no gate at all.
 */
public final class PlayerProgressionEquipment {
    private PlayerProgressionEquipment() {
    }

    private static final Map<Item, PlayerEvolutionStage> GATED = new HashMap<>();
    private static boolean built;

    /**
     * Built lazily on first use rather than in a static block.
     *
     * <p>{@link FCRegistry}'s {@code RegistryObject}s are not resolvable while the class loader is
     * still walking the mod's static initialisers — calling {@code get()} there is the classic
     * "registry object not present" crash. First use happens well after registration is frozen.
     */
    private static void build() {
        if (built) {
            return;
        }
        built = true;

        // Neophyte earns the Astartes sidearms; the Black Carapace is what makes them usable.
        GATED.put(FCRegistry.BOLTER.get(), PlayerEvolutionStage.NEOPHYTE);
        GATED.put(FCRegistry.CHAINSWORD.get(), PlayerEvolutionStage.NEOPHYTE);

        // Full plate is the mark of a Battle-Brother and nothing less.
        GATED.put(FCRegistry.SPACE_MARINE_HELMET.get(), PlayerEvolutionStage.SPACE_MARINE);
        GATED.put(FCRegistry.SPACE_MARINE_CHESTPLATE.get(), PlayerEvolutionStage.SPACE_MARINE);
        GATED.put(FCRegistry.SPACE_MARINE_LEGGINGS.get(), PlayerEvolutionStage.SPACE_MARINE);
        GATED.put(FCRegistry.SPACE_MARINE_BOOTS.get(), PlayerEvolutionStage.SPACE_MARINE);

        // Veteran kit: the Guard's own heavy weapon.
        GATED.put(FCRegistry.PLASMA_GUN.get(), PlayerEvolutionStage.ASTRA_VETERAN);
    }

    /**
     * The stage this item demands, or null when anyone may carry it.
     *
     * <p>Lasgun, Guardsman plate, combat knife and med-kit are deliberately absent: they are what a
     * recruit starts with, and gating them would gate the beginning of the game.
     */
    @Nullable
    public static PlayerEvolutionStage requiredStage(Item item) {
        build();
        return GATED.get(item);
    }

    /** Whether a player at {@code stage} may use {@code item}. */
    public static boolean allows(PlayerEvolutionStage stage, Item item) {
        PlayerEvolutionStage required = requiredStage(item);
        return required == null || stage.isAtLeast(required);
    }

    /**
     * Partial Power Armour: after the Black Carapace a Neophyte may wear the plate, but the suit's
     * full benefit waits for the ascension. The distinction exists so the Black Carapace has
     * something to be for.
     */
    public static boolean allowsPartialPowerArmour(PlayerEvolutionStage stage) {
        return stage.isAtLeast(PlayerEvolutionStage.NEOPHYTE);
    }
}
