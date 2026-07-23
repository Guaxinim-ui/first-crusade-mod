package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.client.renderer.SentinelCannonBoltRenderer;
import com.example.examplemod.client.renderer.SentinelMissileRenderer;
import com.example.examplemod.client.renderer.SentinelWalkerRenderer;
import com.example.examplemod.client.renderer.ValkyrieGunshipRenderer;
import com.example.examplemod.client.renderer.ValkyrieMultilaserBoltRenderer;
import com.example.examplemod.entity.SentinelWalkerEntity;
import com.example.examplemod.entity.ValkyrieGunshipEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CombatVehicleModEvents {
    private CombatVehicleModEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModCombatVehicleContent.VALKYRIE_GUNSHIP.get(), ValkyrieGunshipEntity.createAttributes().build());
        event.put(ModCombatVehicleContent.SENTINEL_WALKER.get(), SentinelWalkerEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
            event.accept(ModCombatVehicleContent.VALKYRIE_GUNSHIP_SPAWN_EGG);
            event.accept(ModCombatVehicleContent.SENTINEL_WALKER_SPAWN_EGG);
        }
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Client {
        private Client() {}

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModCombatVehicleContent.VALKYRIE_GUNSHIP.get(), ValkyrieGunshipRenderer::new);
            event.registerEntityRenderer(ModCombatVehicleContent.SENTINEL_WALKER.get(), SentinelWalkerRenderer::new);
            event.registerEntityRenderer(ModCombatVehicleContent.VALKYRIE_MULTILASER_BOLT.get(), ValkyrieMultilaserBoltRenderer::new);
            event.registerEntityRenderer(ModCombatVehicleContent.SENTINEL_CANNON_BOLT.get(), SentinelCannonBoltRenderer::new);
            event.registerEntityRenderer(ModCombatVehicleContent.SENTINEL_MISSILE.get(), SentinelMissileRenderer::new);
        }
    }
}
