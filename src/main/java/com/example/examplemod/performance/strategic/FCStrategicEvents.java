package com.example.examplemod.performance.strategic;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the strategic layer from the server's own tick.
 *
 * <p>A level tick rather than a server tick, because battles belong to a level: a war on Cadia must
 * not be resolved by a pass that happens to be iterating Armageddon. The END phase so that a battle
 * materialising this tick sees a world that has already moved.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FCStrategicEvents {

    private FCStrategicEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || !FirstCrusadePerformanceConfig.strategicEnabled()) {
            return;
        }

        FCStrategicBattleData.get(level).tick(level);
    }
}
