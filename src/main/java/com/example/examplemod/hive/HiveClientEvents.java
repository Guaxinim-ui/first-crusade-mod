package com.example.examplemod.hive;

import com.example.examplemod.ExampleMod;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only wiring for the detailing pack:
 *  - registers a no-op renderer for the invisible {@link HiveSeatEntity} (Forge crashes
 *    without one, even for entities that draw nothing);
 *  - puts toxic sludge and the see-through decoration blocks on the translucent/cutout
 *    render layers so they display correctly.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HiveClientEvents {

    private HiveClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HiveEntities.SEAT.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(HiveFluids.TOXIC_SLUDGE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(HiveFluids.TOXIC_SLUDGE_FLOWING.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.TOXIC_SLUDGE_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.SOLID_TOXIC_SLUDGE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.COOLANT_TANK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.HIVE_CHAIR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.HIVE_BENCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.HIVE_RUG.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.SHELF_UNIT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.HANGING_HIVE_LAMP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.CATHEDRAL_BRAZIER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.WARNING_BEACON.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.INDUSTRIAL_CHAIN.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.CABLE_BUNDLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.HUGE_HIVE_PIPE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(HiveBlocks.MAIN_PIPE_TRUNK.get(), RenderType.cutout());
        });
    }

    // Fluid client textures/tint are registered by HiveFluids via FluidType.initializeClient
    // (the Forge 1.20.1 mechanism). Forge 1.20.4+ replaced that with RegisterClientExtensionsEvent.

    /** Draws nothing — the seat is an invisible mount point. */
    private static class NoopRenderer extends EntityRenderer<HiveSeatEntity> {
        protected NoopRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(HiveSeatEntity entity) {
            return new ResourceLocation("minecraft", "textures/misc/white.png");
        }

        @Override
        public boolean shouldRender(HiveSeatEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                    double x, double y, double z) {
            return false;
        }
    }
}
