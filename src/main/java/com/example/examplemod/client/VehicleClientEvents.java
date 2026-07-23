package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.client.model.ImperialBattleTankModel;
import com.example.examplemod.client.renderer.ImperialBattleTankRenderer;
import com.example.examplemod.registry.ModVehicleEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VehicleClientEvents {
    private VehicleClientEvents() {
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                ImperialBattleTankModel.LAYER_LOCATION,
                ImperialBattleTankModel::createBodyLayer
        );
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModVehicleEntities.IMPERIAL_BATTLE_TANK.get(),
                ImperialBattleTankRenderer::new
        );

        event.registerEntityRenderer(
                ModVehicleEntities.TANK_SHELL.get(),
                context -> new ThrownItemRenderer<>(context, 1.35F, true)
        );
    }
}
