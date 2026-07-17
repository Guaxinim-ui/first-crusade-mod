package com.example.examplemod.hive.city;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Static holder for the {@code firstcrusade:hive_world} dimension resource keys.
 *
 * <p>The dimension itself is defined by the datapack JSONs under
 * {@code data/firstcrusade/dimension/hive_world.json} +
 * {@code data/firstcrusade/dimension_type/hive_world.json}; these keys are only how the
 * running server refers to that dimension (e.g. {@code server.getLevel(HiveWorld.LEVEL)}).
 *
 * <p>Vertical envelope (must match the dimension_type JSON): min_y = -64, height = 576,
 * so Y runs -64..511. All district stacking math in {@link HiveCityLayout} is expressed
 * relative to {@link #GROUND_Y} (the top of the cargo/gate base, y=0) so that changing the
 * envelope only touches these constants.
 */
public final class HiveWorld {
    private HiveWorld() {}

    public static final ResourceLocation ID =
            new ResourceLocation(ExampleMod.MODID, "hive_world");

    /** Key for the dimension (Level) itself. */
    public static final ResourceKey<Level> LEVEL =
            ResourceKey.create(Registries.DIMENSION, ID);

    /** Key for the dimension_type. */
    public static final ResourceKey<DimensionType> DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, ID);

    // ---- vertical envelope (keep in sync with dimension_type/hive_world.json) ----
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 511;      // min_y + height - 1

    /** Y of the Underhive floor (its lowest module sits here). */
    public static final int UNDERHIVE_Y = -64;

    /** Y of the top of the cargo/gate base = reference "street level" of the city. */
    public static final int GROUND_Y = 0;

    /** Height of one stacked district level (module height). */
    public static final int LEVEL_HEIGHT = 64;

    /** Horizontal cell size (module footprint edge) used by the layout grid. */
    public static final int CELL = 64;
}
