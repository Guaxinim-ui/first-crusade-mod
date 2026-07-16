package com.example.examplemod.hive;

import com.example.examplemod.ExampleMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Registro dos StructureProcessorTypes da Hive. Ligado pelo HiveBlocks.register(). */
public final class HiveProcessors {

    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, ExampleMod.MODID);

    public static final RegistryObject<StructureProcessorType<HiveMarkerProcessor>> HIVE_MARKER =
            PROCESSORS.register("hive_marker", () -> () -> HiveMarkerProcessor.CODEC);

    private HiveProcessors() {
    }

    public static void register(IEventBus modEventBus) {
        PROCESSORS.register(modEventBus);
    }
}
