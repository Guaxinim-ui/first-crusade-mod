package com.example.examplemod.crusade;

import java.util.List;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;
import com.example.examplemod.ImperialCityType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

/**
 * The regiment that raised a base's garrison, and therefore who fills its slots.
 *
 * <h2>A regiment decides who, never how many</h2>
 *
 * The garrison cap belongs to {@code SimpleImperialBaseBalance} and to the Core's level, and nothing
 * here touches it. A regiment only answers "when this base fields a soldier, what kind of soldier is
 * it?" — so switching a base from Cadian to Catachan changes the faces on the wall, not the number
 * of them. That is the whole reason this exists as a table of weights rather than as a spawner.
 *
 * <h2>Only regiments the mod can actually field</h2>
 *
 * The design lists Krieg and Valhallan too. They are not here, because a regiment whose troops do
 * not exist would field Guardsmen and be a lie told in the UI. Each entry below maps onto units that
 * are registered today; the enum is where the next one goes when its troops land.
 */
public enum ImperialRegimentType {

    /**
     * The default, and what every base founded before regiments existed reads as. Line infantry with
     * their own sergeants — the Astra Militarum at its most ordinary, which is the point.
     */
    CRUSADE_GENERIC("crusade_generic", ChatFormatting.GRAY, 0x9AA88C),

    /** Cadian shock troops: the same line kit, held to a harder standard. */
    CADIAN_LINE("cadian_line", ChatFormatting.GREEN, 0x5A7A3C),

    /** Catachan jungle fighters: light, fast, and mostly bare-armed. */
    CATACHAN_JUNGLE("catachan_jungle", ChatFormatting.DARK_GREEN, 0x3E5A2A),

    /** Forge World auxilia: line infantry stiffened with a few Skitarii. */
    FORGE_AUXILIA("forge_auxilia", ChatFormatting.RED, 0x7A1F1A),

    /** A penal legion: the Imperium spending men it has already written off. */
    PENAL("penal", ChatFormatting.GOLD, 0xA85A24);

    private final String id;
    private final ChatFormatting colour;
    private final int rgb;

    ImperialRegimentType(String id, ChatFormatting colour, int rgb) {
        this.id = id;
        this.colour = colour;
        this.rgb = rgb;
    }

    public String id() {
        return this.id;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    /** Packed RGB, for anywhere a chat colour is too coarse (banners, GUI accents). */
    public int rgb() {
        return this.rgb;
    }

    public Component displayName() {
        return Component.translatable("regiment.firstcrusade." + this.id).withStyle(this.colour);
    }

    /** The lang key prefix for this regiment's Vox flavour, filled in when the Vox lands. */
    public String voxKey() {
        return "vox.firstcrusade." + this.id;
    }

    /**
     * Rolls the next soldier this regiment fields.
     *
     * <p>Weighted by hand rather than by a data file because there are five of them and the weights
     * are design, not content. The rare slot is deliberately rare: the brief's rule is that identity
     * comes from coherence, not from a carnival of unit types in one camp.
     *
     * @return the entity type to spawn, or {@code null} to mean "the ordinary Guardsman"
     */
    public EntityType<? extends com.example.examplemod.AbstractImperialTroopEntity> rollTroop(
            RandomSource random) {
        int roll = random.nextInt(100);

        return switch (this) {
            // Jungle fighters are the body of a Catachan regiment, not its garnish.
            case CATACHAN_JUNGLE -> roll < 70 ? FCRegistry.JUNGLE_FIGHTER.get() : null;
            // A handful of Skitarii stiffening an otherwise ordinary line.
            case FORGE_AUXILIA -> roll < 20 ? FCRegistry.SKITARII_RANGER.get() : null;
            // A penal legion is penal legionnaires; the few who are not are their minders.
            case PENAL -> roll < 85 ? FCRegistry.PENAL_LEGIONNAIRE.get() : null;
            case CADIAN_LINE, CRUSADE_GENERIC -> null;
        };
    }

    /**
     * Whether this regiment promotes at all.
     *
     * <p>A penal legion has no career: the Imperium does not make sergeants of condemned men, and a
     * penal legionnaire who distinguishes himself is a penal legionnaire who is still alive. This is
     * the one place the career system is switched off wholesale.
     */
    public boolean hasCareer() {
        return this != PENAL;
    }

    /** The regiment a newly founded base is raised as, biased by what kind of world it sits on. */
    public static ImperialRegimentType forCityType(ImperialCityType cityType) {
        if (cityType == null) {
            return CRUSADE_GENERIC;
        }

        return switch (cityType.name()) {
            case "DEATH_WORLD" -> CATACHAN_JUNGLE;
            case "FORGE" -> FORGE_AUXILIA;
            case "PENAL" -> PENAL;
            case "FORTRESS" -> CADIAN_LINE;
            default -> CRUSADE_GENERIC;
        };
    }

    public static ImperialRegimentType byId(String id) {
        for (ImperialRegimentType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }

        // An id from a future build, or a corrupt tag. The generic regiment is always correct
        // enough to keep a save loading.
        return CRUSADE_GENERIC;
    }

    public static List<ImperialRegimentType> all() {
        return List.of(values());
    }

    /** Used by the appearance resolver's regiment axis once regimental art exists. */
    public String appearanceKey() {
        return ExampleMod.MODID + ":" + this.id;
    }
}
