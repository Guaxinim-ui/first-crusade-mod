package com.example.examplemod;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup for First Crusade: menu screens, entity renderers and model layer definitions.
 * Extracted from {@link ExampleMod} so the main mod class no longer mixes client wiring with
 * registration, network and server events. Auto-registered on the mod event bus (client dist) via
 * {@link Mod.EventBusSubscriber}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FirstCrusadeClientEvents {
    private FirstCrusadeClientEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(FCRegistry.IMPERIAL_COMMAND_CORE_MENU.get(), ImperialCommandCoreScreen::new);
            MenuScreens.register(FCRegistry.STRATEGIUM_MENU.get(), StrategiumScreen::new);
            MenuScreens.register(FCRegistry.ORK_CAMP_MENU.get(), OrkCampScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RobouteGuillimanModel.LAYER_LOCATION, RobouteGuillimanModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FCRegistry.IMPERIAL_CITIZEN.get(), ImperialCitizenRenderer::new);
        event.registerEntityRenderer(FCRegistry.LASGUN_SHOT.get(), LasgunShotRenderer::new);
        event.registerEntityRenderer(FCRegistry.GUARDSMAN.get(), GuardsmanRenderer::new);
        event.registerEntityRenderer(FCRegistry.SPACE_MARINE.get(), SpaceMarineRenderer::new);
        event.registerEntityRenderer(FCRegistry.CUSTODES.get(), CustodesRenderer::new);
        event.registerEntityRenderer(FCRegistry.PRIMARCH.get(), PrimarchRenderer::new);
        event.registerEntityRenderer(FCRegistry.ORK_BOY.get(), OrkBoyRenderer::new);
        event.registerEntityRenderer(FCRegistry.ORK_NOB.get(), OrkNobRenderer::new);
        event.registerEntityRenderer(FCRegistry.WARBOSS.get(), WarbossRenderer::new);
        event.registerEntityRenderer(FCRegistry.MEGANOB.get(), MeganobRenderer::new);
        event.registerEntityRenderer(FCRegistry.GRETCHIN.get(), GretchinRenderer::new);
        event.registerEntityRenderer(FCRegistry.KILLA_KAN.get(), KillaKanRenderer::new);
        event.registerEntityRenderer(FCRegistry.ROBOUTE_GUILLIMAN.get(), RobouteGuillimanRenderer::new);
        event.registerEntityRenderer(FCRegistry.SKITARII_RANGER.get(), SkitariiRangerRenderer::new);
        event.registerEntityRenderer(FCRegistry.KASRKIN.get(), KasrkinRenderer::new);
        event.registerEntityRenderer(FCRegistry.ENFORCER.get(), EnforcerRenderer::new);
        event.registerEntityRenderer(FCRegistry.MINE_GUARD.get(), MineGuardRenderer::new);
        event.registerEntityRenderer(FCRegistry.AGRI_MILITIA.get(), AgriMilitiaRenderer::new);
        event.registerEntityRenderer(FCRegistry.SISTER_OF_BATTLE.get(), SisterOfBattleRenderer::new);
        event.registerEntityRenderer(FCRegistry.PENAL_LEGIONNAIRE.get(), PenalLegionnaireRenderer::new);
        event.registerEntityRenderer(FCRegistry.JUNGLE_FIGHTER.get(), JungleFighterRenderer::new);
        event.registerEntityRenderer(FCRegistry.FEUDAL_KNIGHT.get(), FeudalKnightRenderer::new);
        event.registerEntityRenderer(FCRegistry.CITY_COMMANDER.get(), CityCommanderRenderer::new);
    }
}
