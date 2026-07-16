package com.example.examplemod.hive;

import com.example.examplemod.ExampleMod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity registry for the Hive. Currently only the invisible seat used by chairs.
 * Wired from {@link HiveBlocks#register}.
 */
public final class HiveEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final RegistryObject<EntityType<HiveSeatEntity>> SEAT =
            ENTITIES.register("hive_seat", () -> EntityType.Builder
                    .<HiveSeatEntity>of(HiveSeatEntity::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSummon()
                    .build("hive_seat"));

    private HiveEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
