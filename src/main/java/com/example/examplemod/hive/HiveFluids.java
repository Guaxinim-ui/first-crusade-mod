package com.example.examplemod.hive;

import com.example.examplemod.ExampleMod;

import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Toxic sludge fluid family: FluidType + still + flowing. The placed LiquidBlock (with
 * contact damage) and the bucket item both live in {@link HiveBlocks} so they share the
 * block/item registries and creative tab; the fluid mechanics live here.
 *
 * Deliberately a re-tinted, thicker water — no custom render pipeline, standard spread.
 */
public final class HiveFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, ExampleMod.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, ExampleMod.MODID);

    private static final ResourceLocation STILL_TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "block/toxic_sludge_still");
    private static final ResourceLocation FLOW_TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "block/toxic_sludge_flow");

    // Forge 1.20.1: client textures/tint for a fluid are supplied by overriding
    // FluidType.initializeClient (there is no RegisterClientExtensionsEvent until Forge 1.20.4+).
    // The override is only invoked on the client, so IClientFluidTypeExtensions never loads server-side.
    public static final RegistryObject<FluidType> TOXIC_SLUDGE_TYPE =
            FLUID_TYPES.register("toxic_sludge", () -> new FluidType(FluidType.Properties.create()
                    .density(1600)
                    .viscosity(2400)
                    .temperature(320)
                    .lightLevel(4)
                    .canSwim(true)
                    .canDrown(true)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)) {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        @Override
                        public ResourceLocation getStillTexture() {
                            return STILL_TEXTURE;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return FLOW_TEXTURE;
                        }

                        @Override
                        public int getTintColor() {
                            return 0xCC5FBF3A;
                        }
                    });
                }
            });

    public static final RegistryObject<FlowingFluid> TOXIC_SLUDGE =
            FLUIDS.register("toxic_sludge", () -> new ToxicSludgeFluid.Source(makeProperties()));
    public static final RegistryObject<FlowingFluid> TOXIC_SLUDGE_FLOWING =
            FLUIDS.register("toxic_sludge_flowing", () -> new ToxicSludgeFluid.Flowing(makeProperties()));

    private static ForgeFlowingFluid.Properties makeProperties() {
        return new ForgeFlowingFluid.Properties(TOXIC_SLUDGE_TYPE, TOXIC_SLUDGE, TOXIC_SLUDGE_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(HiveBlocks.TOXIC_SLUDGE_BLOCK)
                .bucket(HiveBlocks.TOXIC_SLUDGE_BUCKET);
    }

    private HiveFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
