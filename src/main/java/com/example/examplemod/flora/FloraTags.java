package com.example.examplemod.flora;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags for the vegetation set. Two families:
 *
 * <ul>
 *   <li><b>Ground rules</b> ({@code flora_ground_*}) — what a plant is allowed to grow on.
 *       Datapack-driven on purpose: a modpack can let Imperial grass take root on a new soil
 *       block without touching the mod.</li>
 *   <li><b>Membership</b> ({@code flora}, {@code flora_grass}, ...) — how other systems address
 *       the set as a whole. The regional decorator and the future dynamic-ambience pass both
 *       query these instead of hardcoding block lists.</li>
 * </ul>
 *
 * The tag JSON lives in {@code data/firstcrusade/tags/blocks/}.
 */
public final class FloraTags {
    private FloraTags() {
    }

    public static final TagKey<Block> GROUND_NATURAL = create("flora_ground_natural");
    public static final TagKey<Block> GROUND_INDUSTRIAL = create("flora_ground_industrial");
    public static final TagKey<Block> GROUND_ANY = create("flora_ground_any");

    public static final TagKey<Block> FLORA = create("flora");

    /**
     * The mod's trees — logs and canopies alike.
     *
     * <p>Separate from {@link #FLORA} on purpose. Ordinary redecoration must never touch a tree:
     * a log is a solid block a player may well have built with, and the phase rules are explicit
     * that redecoration only sweeps {@code firstcrusade:flora}. This tag exists for the operations
     * that <i>are</i> meant to remove trees — the {@code cleartrees} admin command — and for a
     * conquest's tree removal to state what it is looking at.
     */
    public static final TagKey<Block> FLORA_TREE = create("flora_tree");
    public static final TagKey<Block> FLORA_GRASS = create("flora_grass");
    public static final TagKey<Block> FLORA_FLOWER = create("flora_flower");
    public static final TagKey<Block> FLORA_FUNGUS = create("flora_fungus");
    public static final TagKey<Block> FLORA_LICHEN = create("flora_lichen");

    /**
     * Ork growth: the ground cover the green tide drags with it.
     *
     * <p>This is what replaced vanilla sculk as the mark of Ork-held ground. Imperial units
     * standing in it are slowed and worn down; Orks thrive on it (see
     * {@code FirstCrusadeForgeEvents.onLivingTick}).
     */
    public static final TagKey<Block> ORK_GROWTH = create("ork_growth");
    public static final TagKey<Block> FLORA_DETRITUS = create("flora_detritus");

    private static TagKey<Block> create(String name) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(ExampleMod.MODID, name));
    }
}
