package com.example.examplemod.hive;

import com.example.examplemod.ExampleMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags used by the Hive City systems.
 *
 * PIPE_CONNECTABLE drives the visual auto-connection of {@link HivePipeBlock}: any block in
 * data/firstcrusade/tags/blocks/pipe_connectable.json grows a pipe arm towards it. Add future
 * machines (coolant tanks, generators, etc.) to the JSON tag — no code change needed.
 */
public final class HiveTags {

    public static final TagKey<Block> PIPE_CONNECTABLE =
            BlockTags.create(new ResourceLocation(ExampleMod.MODID, "pipe_connectable"));

    private HiveTags() {
    }
}
