package com.example.examplemod.progression;

import com.example.examplemod.ExampleMod;

import net.minecraft.resources.ResourceLocation;

/**
 * The nine pictures of the Imperial Command tree, and the only place their paths are written.
 *
 * <p>Same contract as {@link PlayerProgressionIcon}: the file's true size travels beside its path,
 * because {@code blit} needs both the source rectangle and the destination one and a mismatch is the
 * classic stretched-quarter-of-a-texture bug. They live in their own folder so the two trees can
 * never fight over a name.
 */
public enum PlayerCommanderIcon {

    AUTHORITY_CAP("command_authority_cap"),
    SQUAD_VOX("command_squad_vox"),
    REINFORCED_SQUAD("command_reinforced_squad"),
    COMBAT_SECTION("command_combat_section"),
    ASSAULT_PLATOON("command_assault_platoon"),
    FIELD_SERGEANT("command_field_sergeant"),
    PRIORITY_VOX("command_priority_vox"),
    FORWARD_INSERTION("command_forward_insertion"),
    COORDINATED_ASSAULT("command_coordinated_assault");

    /** All command icons are drawn on the same 40x40 grid as the Astartes ones. */
    public static final int SIZE = 40;

    private static final String ROOT = "textures/gui/progression/commander/";

    private final String file;
    private final ResourceLocation texture;

    PlayerCommanderIcon(String file) {
        this.file = file;
        this.texture = new ResourceLocation(ExampleMod.MODID, ROOT + file + ".png");
    }

    public ResourceLocation texture() {
        return this.texture;
    }

    public int width() {
        return SIZE;
    }

    public int height() {
        return SIZE;
    }

    /** The file name without extension — what {@code generate_commander_icons.py} writes. */
    public String file() {
        return this.file;
    }
}
