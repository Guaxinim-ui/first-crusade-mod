package com.example.examplemod.flora;

import java.util.function.Supplier;

import com.example.examplemod.ExampleMod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The soils that give a natural region its floor.
 *
 * <p>Separate from {@link FCFlora} because these are not plants and do not behave like plants: they
 * are solid, they are dug rather than swept, and — unlike everything in {@code FCFlora} — they are
 * <b>not</b> replaceable. A settlement clearing its footprint must not delete the ground it is about
 * to build on.
 *
 * <h2>Why the mod needs its own soil at all</h2>
 *
 * Grass colour is a biome property, so a biome can be tinted grey or yellow without any new block.
 * What tinting cannot do is change the <i>material</i>: a salt flat whose floor is still
 * {@code grass_block} reads as a recoloured meadow no matter how pale the tint, and a marsh needs
 * ground that looks wet. These two are placed by the surface rule in
 * {@code data/minecraft/worldgen/noise_settings/overworld.json}, so they exist from the moment the
 * chunk is carved — not painted on afterwards by the runtime decorator.
 *
 * <p>Both blocks are in {@code firstcrusade:flora_ground_natural}, which is what lets the region's
 * own plants take root on them. Without that tag the biome would generate a bare floor: every
 * plant's {@code would_survive} check would fail and every patch feature would place nothing.
 */
public final class FCFloraGround {
    private FCFloraGround() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    /**
     * The saturated black floor of a sump marsh. Soft, so a shovel goes through it quickly, and
     * quiet underfoot.
     */
    public static final RegistryObject<Block> SUMP_MUD = register("sump_mud",
            () -> new Block(soil(MapColor.TERRACOTTA_BLACK, SoundType.MUD, 0.55F)));

    /**
     * Dried salt over hard pan. Brittle rather than soft — it breaks like crust, which is why it
     * takes the sand sound rather than the gravel one.
     */
    public static final RegistryObject<Block> SALT_CRUST = register("salt_crust",
            () -> new Block(soil(MapColor.SNOW, SoundType.SAND, 0.5F)));

    /**
     * Ordinary diggable ground: no tool requirement, so it never becomes an unbreakable floor for a
     * player who happens to arrive without a shovel.
     */
    private static BlockBehaviour.Properties soil(MapColor color, SoundType sound, float strength) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(strength)
                .sound(sound);
    }

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> registered = BLOCKS.register(name, block);
        ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    /** Called once from the ExampleMod constructor, alongside {@code FCFlora.register}. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
