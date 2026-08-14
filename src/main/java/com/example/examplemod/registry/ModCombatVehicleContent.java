package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.SentinelWalkerEntity;
import com.example.examplemod.entity.ValkyrieGunshipEntity;
import com.example.examplemod.entity.projectile.SentinelCannonBoltEntity;
import com.example.examplemod.entity.projectile.SentinelMissileEntity;
import com.example.examplemod.entity.projectile.ValkyrieMultilaserBoltEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModCombatVehicleContent {
    private ModCombatVehicleContent() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final RegistryObject<EntityType<ValkyrieGunshipEntity>> VALKYRIE_GUNSHIP =
            ENTITY_TYPES.register("valkyrie_gunship", () ->
                    EntityType.Builder.of(ValkyrieGunshipEntity::new, MobCategory.CREATURE)
                            .sized(5.8F, 3.4F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .fireImmune()
                            .build(ExampleMod.MODID + ":valkyrie_gunship"));

    public static final RegistryObject<EntityType<SentinelWalkerEntity>> SENTINEL_WALKER =
            ENTITY_TYPES.register("sentinel_walker", () ->
                    EntityType.Builder.of(SentinelWalkerEntity::new, MobCategory.CREATURE)
                            .sized(3.2F, 5.4F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .fireImmune()
                            .build(ExampleMod.MODID + ":sentinel_walker"));

    public static final RegistryObject<EntityType<ValkyrieMultilaserBoltEntity>> VALKYRIE_MULTILASER_BOLT =
            ENTITY_TYPES.register("valkyrie_multilaser_bolt", () ->
                    EntityType.Builder.<ValkyrieMultilaserBoltEntity>of(ValkyrieMultilaserBoltEntity::new, MobCategory.MISC)
                            // Chunks, not blocks — see the missile below. 96 meant 1536 blocks of
                            // tracking with a position packet every tick. The bolt is fired at a
                            // constant 3.2 blocks/tick, never steers, and dies after 80 ticks, so
                            // the client replays the entire flight from the spawn packet.
                            .sized(0.25F, 0.25F).clientTrackingRange(10).updateInterval(10)
                            .build(ExampleMod.MODID + ":valkyrie_multilaser_bolt"));

    public static final RegistryObject<EntityType<SentinelCannonBoltEntity>> SENTINEL_CANNON_BOLT =
            ENTITY_TYPES.register("sentinel_cannon_bolt", () ->
                    EntityType.Builder.<SentinelCannonBoltEntity>of(SentinelCannonBoltEntity::new, MobCategory.MISC)
                            // Same correction as its two neighbours: 96 chunks was 1536 blocks.
                            // Constant 2.8 blocks/tick, unguided, 70-tick life.
                            .sized(0.30F, 0.30F).clientTrackingRange(10).updateInterval(10)
                            .build(ExampleMod.MODID + ":sentinel_cannon_bolt"));

    public static final RegistryObject<EntityType<SentinelMissileEntity>> SENTINEL_MISSILE =
            ENTITY_TYPES.register("sentinel_missile", () ->
                    EntityType.Builder.<SentinelMissileEntity>of(SentinelMissileEntity::new, MobCategory.MISC)
                            // Chunks, not blocks: this was 96, meaning 1536 blocks. Same unit
                            // mix-up as the las-bolt had. 10 chunks is 160 blocks, generous for a
                            // missile and still a hundredth of the area it was tracking before.
                            // The missile is fired with a constant velocity and never steers, so
                            // the client reproduces its whole flight from the spawn packet and the
                            // per-tick updates bought nothing.
                            .sized(0.45F, 0.45F).clientTrackingRange(10).updateInterval(10)
                            .build(ExampleMod.MODID + ":sentinel_missile"));

    public static final RegistryObject<Item> VALKYRIE_GUNSHIP_SPAWN_EGG = ITEMS.register(
            "valkyrie_gunship_spawn_egg",
            () -> new ForgeSpawnEggItem(VALKYRIE_GUNSHIP, 0x344533, 0xB8B3A2, new Item.Properties()));

    public static final RegistryObject<Item> SENTINEL_WALKER_SPAWN_EGG = ITEMS.register(
            "sentinel_walker_spawn_egg",
            () -> new ForgeSpawnEggItem(SENTINEL_WALKER, 0x344533, 0x9B1E22, new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
