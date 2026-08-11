package com.example.examplemod.progression.ork;

import java.util.UUID;

import com.example.examplemod.OrkClan;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * What being a big green git is actually worth, in numbers.
 *
 * <h2>Separate UUIDs from the Imperial pass</h2>
 *
 * {@link com.example.examplemod.progression.PlayerProgressionAttributes} owns its own modifier ids
 * and this owns different ones. That is not tidiness — a modifier is keyed by UUID, so sharing one
 * would mean an Ork's health bonus and an Astartes' health bonus overwriting each other on a player
 * who somehow held both. Two sets, and neither can touch the other's.
 *
 * <h2>Clan bonuses are the player's, not the mob's</h2>
 *
 * {@link OrkClan} already carries multipliers, and they are built for mobs — far too large for a
 * player who is also buying a whole tree. {@code OrkClan.applyTo} is deliberately <b>not</b> called
 * here. What a klan is worth to a player is the small table below, and it lives here so there is one
 * place to look when a Goff feels too strong.
 *
 * <h2>Idempotent</h2>
 *
 * Every modifier is removed before being reapplied, so running this twice leaves the player exactly
 * as running it once did. It is called from {@code recalculate}, which fires on login, respawn,
 * dimension change and every purchase — a bonus that stacked on those would double on a relog.
 */
public final class PlayerOrkProgressionAttributes {
    private PlayerOrkProgressionAttributes() {
    }

    private static final UUID HEALTH_ID = UUID.fromString("7a3c1b20-4d10-4e2f-9c31-0b1c2e9c7b01");
    private static final UUID ARMOR_ID = UUID.fromString("7a3c1b20-4d10-4e2f-9c31-0b1c2e9c7b02");
    private static final UUID TOUGHNESS_ID = UUID.fromString("7a3c1b20-4d10-4e2f-9c31-0b1c2e9c7b03");
    private static final UUID DAMAGE_ID = UUID.fromString("7a3c1b20-4d10-4e2f-9c31-0b1c2e9c7b04");
    private static final UUID KNOCKBACK_ID = UUID.fromString("7a3c1b20-4d10-4e2f-9c31-0b1c2e9c7b05");
    private static final UUID SPEED_ID = UUID.fromString("7a3c1b20-4d10-4e2f-9c31-0b1c2e9c7b06");

    private static final String NAME = "First Crusade WAAAGH";

    // ==================================================================== the klan table
    //
    // Small on purpose. A klan is flavour with a nudge attached, not a build-defining choice — the
    // tree is where power comes from.

    private static final double CLAN_MELEE = 0.05D;
    private static final double CLAN_SPEED = 0.05D;
    private static final double CLAN_HEALTH = 0.05D;

    /**
     * Reapplies everything the Ork progression owns.
     *
     * <p>Stage bonuses stack up the ladder: a Warboss keeps what he earned as a Nob rather than
     * trading it in, which is why these are summed rather than switched on the current stage alone.
     */
    public static void apply(Player player, PlayerOrkProgressionProfile ork) {
        double health = 0.0D;
        double armor = 0.0D;
        double toughness = 0.0D;
        double melee = 0.0D;
        double knockback = 0.0D;

        PlayerOrkEvolutionStage stage = ork.stage();

        if (stage.isAtLeast(PlayerOrkEvolutionStage.BIG_BOY)) {
            health += PlayerOrkProgressionBalance.BIG_BOY_HEALTH;
        }
        if (stage.isAtLeast(PlayerOrkEvolutionStage.ORK_NOB)) {
            health += PlayerOrkProgressionBalance.NOB_HEALTH;
            melee += PlayerOrkProgressionBalance.NOB_MELEE;
            knockback += PlayerOrkProgressionBalance.NOB_KNOCKBACK_RESIST;
        }
        if (stage.isAtLeast(PlayerOrkEvolutionStage.BIG_NOB)) {
            health += PlayerOrkProgressionBalance.BIG_NOB_HEALTH;
            armor += PlayerOrkProgressionBalance.BIG_NOB_ARMOR;
            melee += PlayerOrkProgressionBalance.BIG_NOB_MELEE;
            knockback += PlayerOrkProgressionBalance.BIG_NOB_KNOCKBACK_RESIST;
        }
        if (stage.isAtLeast(PlayerOrkEvolutionStage.WARBOSS)) {
            health += PlayerOrkProgressionBalance.WARBOSS_HEALTH;
            armor += PlayerOrkProgressionBalance.WARBOSS_ARMOR;
            toughness += PlayerOrkProgressionBalance.WARBOSS_TOUGHNESS;
            melee += PlayerOrkProgressionBalance.WARBOSS_MELEE;
            knockback += PlayerOrkProgressionBalance.WARBOSS_KNOCKBACK_RESIST;
        }

        // Node ranks that map straight onto an attribute. Everything conditional — damage against
        // elites, dakka cooldowns, loot — is decided at the moment it applies, not here.
        health += ork.rank("thick_skin") * 2.0D;
        health += ork.rank("stay_standing") * 1.0D;
        armor += ork.rank("scrap_armour") * 1.0D;
        armor += ork.rank("thick_plate") * 1.0D;
        toughness += ork.rank("thick_plate") * 0.5D;
        knockback += ork.rank("stay_standing") * 0.03D;

        // MEGA PLATIN. Still reserved for real Mega Armour, but no longer reserved for nothing: it
        // pays in the handling that armour would give — plate, toughness and being hard to shift —
        // so the node does something the day it is bought rather than the day the item ships.
        if (ork.rank("mega_platin") > 0) {
            armor += PlayerOrkProgressionBalance.MEGA_PLATIN_ARMOR;
            toughness += PlayerOrkProgressionBalance.MEGA_PLATIN_TOUGHNESS;
            knockback += PlayerOrkProgressionBalance.MEGA_PLATIN_KNOCKBACK;
        }

        // DA BIGGEST — the endgame answer to being moved at all.
        if (ork.rank("da_biggest") > 0) {
            health += PlayerOrkProgressionBalance.DA_BIGGEST_HEALTH;
            knockback += PlayerOrkProgressionBalance.DA_BIGGEST_KNOCKBACK;
        }

        double healthMultiplier = 0.0D;
        double speedMultiplier = ork.rank("run_to_krump") * 0.02D;

        // RUN AN 'IT — the legs half of the node. The damage half is conditional on actually
        // sprinting, so it lives in PlayerOrkCombatModifiers where the swing can be inspected.
        if (ork.rank("run_and_hit") > 0) {
            speedMultiplier += PlayerOrkProgressionBalance.RUN_AND_HIT_SPEED;
        }

        // harder_hitting used to be approximated here as a flat number, which is not a percentage
        // of anything. It — and big_choppa, krump_everything and doesnt_hurt_much — now live in
        // PlayerOrkCombatEvents, where a real multiplier can be applied to a real blow.
        double meleeMultiplier = 0.0D;

        OrkClan clan = ork.clan();
        if (clan != null) {
            switch (clan) {
                case GOFFS -> meleeMultiplier += CLAN_MELEE;
                case EVIL_SUNZ -> speedMultiplier += CLAN_SPEED;
                case SNAKEBITES -> healthMultiplier += CLAN_HEALTH;
                // Bad Moons and Deathskulls pay in Teef and loot, which are not attributes. Their
                // bonuses live where the rewards are handed out, so nothing is applied here.
                default -> { }
            }
        }

        set(player, Attributes.MAX_HEALTH, HEALTH_ID, health, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.ARMOR, ARMOR_ID, armor, AttributeModifier.Operation.ADDITION);
        set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, toughness,
                AttributeModifier.Operation.ADDITION);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, knockback,
                AttributeModifier.Operation.ADDITION);

        // Melee and speed are proportional so they stay meaningful whatever else is stacked on.
        set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, melee + meleeMultiplier * 4.0D,
                AttributeModifier.Operation.ADDITION);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, speedMultiplier,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        if (healthMultiplier > 0.0D) {
            // Applied on top of the flat pool rather than as a second modifier on the same id.
            AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
            if (instance != null) {
                instance.removeModifier(HEALTH_ID);
                instance.addPermanentModifier(new AttributeModifier(HEALTH_ID, NAME,
                        health + instance.getBaseValue() * healthMultiplier,
                        AttributeModifier.Operation.ADDITION));
            }
        }

        // A player whose max health just fell must not be left above it.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Strips everything this system owns. Used by {@code /fcorkprogress reset}. */
    public static void clear(Player player) {
        remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(player, Attributes.ARMOR, ARMOR_ID);
        remove(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
        remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
        remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);

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
