package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.vehicle.ImperialBattleTankEntity;
import com.example.examplemod.entity.vehicle.TankShellEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModVehicleEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final RegistryObject<EntityType<ImperialBattleTankEntity>> IMPERIAL_BATTLE_TANK =
            ENTITY_TYPES.register("imperial_battle_tank",
                    () -> EntityType.Builder.of(ImperialBattleTankEntity::new, MobCategory.CREATURE)
                            .sized(5.5F, 4.0F)
                            .clientTrackingRange(12)
                            .updateInterval(2)
                            .fireImmune()
                            .build(ExampleMod.MODID + ":imperial_battle_tank"));

    public static final RegistryObject<EntityType<TankShellEntity>> TANK_SHELL =
            ENTITY_TYPES.register("tank_shell",
                    () -> EntityType.Builder.<TankShellEntity>of(TankShellEntity::new, MobCategory.MISC)
                            .sized(0.35F, 0.35F)
                            // Chunks, not blocks: 96 meant 1536 blocks of tracking with a position
                            // packet every tick, for every player inside a kilometre and a half.
                            // Deliberately looser than the unguided bolts (10/10): the shell is
                            // ballistic — it carries drag and gravity in its own tick() — so the
                            // client is integrating an arc rather than replaying a straight line,
                            // and a shorter correction interval keeps that arc honest over its
                            // 160-tick life. 12 chunks is 192 blocks, still a sixty-fourth of the
                            // area it was tracking before.
                            .clientTrackingRange(12)
                            .updateInterval(5)
                            .build(ExampleMod.MODID + ":tank_shell"));

    public static final RegistryObject<Item> IMPERIAL_BATTLE_TANK_SPAWN_EGG =
            ITEMS.register("imperial_battle_tank_spawn_egg",
                    () -> new ForgeSpawnEggItem(
                            IMPERIAL_BATTLE_TANK,
                            0x3F4936,
                            0xB9933F,
                            new Item.Properties()
                    ));

    private ModVehicleEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(IMPERIAL_BATTLE_TANK.get(), ImperialBattleTankEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(IMPERIAL_BATTLE_TANK_SPAWN_EGG);
        }
    }
}
