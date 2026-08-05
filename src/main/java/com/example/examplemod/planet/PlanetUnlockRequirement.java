package com.example.examplemod.planet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * One condition a player must meet before a destination becomes reachable.
 *
 * <h2>Declared text, granted by event</h2>
 *
 * A requirement is <b>a description plus a trigger name</b>, not a live predicate. That is a
 * deliberate split, and it is what keeps the system honest while most of the campaign does not exist
 * yet: the terminal can always tell the player exactly what is missing, and the unlock itself
 * happens when something in the world actually reports the achievement to
 * {@link PlanetUnlockData#unlock}.
 *
 * <p>The alternative — a predicate evaluated every time the screen opens — reads better in a class
 * diagram and is worse in practice here: it would have to answer questions the mod cannot yet ask
 * ("has this player completed a mission?"), and every unanswerable one would either lie or throw.
 * A trigger that nothing fires yet simply leaves the door shut, and the tooltip says why.
 *
 * @param trigger    stable id of what grants it — matched by {@link PlanetUnlockEvents}
 * @param descriptionKey translation key of the human-readable line shown in the tooltip
 */
public record PlanetUnlockRequirement(String trigger, String descriptionKey) {

    /** Granted the moment a player first uses any Spaceport. */
    public static final String TRIGGER_FIRST_LAUNCH = "first_launch";

    /** Granted by killing an Ork Nob, Warboss or Meganob — the mod's "you have fought Orks" mark. */
    public static final String TRIGGER_ORK_CHAMPION_SLAIN = "ork_champion_slain";

    /** Granted by holding Crusadium Plate, the mod's advanced-industry output. */
    public static final String TRIGGER_CRUSADIUM_FORGED = "crusadium_forged";

    /** Granted by visiting a hive district — reserved for the Hive layer to fire. */
    public static final String TRIGGER_HIVE_SURVEYED = "hive_surveyed";

    /** Nothing in the mod fires this yet; it exists so the requirement can be shown honestly. */
    public static final String TRIGGER_CAMPAIGN = "campaign";

    /** Only an operator command can grant it. */
    public static final String TRIGGER_ADMIN = "admin";

    public static PlanetUnlockRequirement of(String trigger, String descriptionKey) {
        return new PlanetUnlockRequirement(trigger, descriptionKey);
    }

    public Component description() {
        return Component.translatable(this.descriptionKey);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.trigger, 64);
        buffer.writeUtf(this.descriptionKey, 128);
    }

    public static PlanetUnlockRequirement read(FriendlyByteBuf buffer) {
        return new PlanetUnlockRequirement(buffer.readUtf(64), buffer.readUtf(128));
    }
}
