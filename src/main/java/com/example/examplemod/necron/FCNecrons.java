package com.example.examplemod.necron;

import com.example.examplemod.ExampleMod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Necrons' own registry.
 *
 * <h2>Separate from FCRegistry on purpose</h2>
 *
 * Same reason the fauna has its own: {@code FCRegistry} is already thousands of lines, and a faction
 * that arrives whole — three units, their attributes and their renderers — is easier to read, and to
 * remove, as one file than as three more entries scattered through that one.
 *
 * <h2>No spawn eggs</h2>
 *
 * Deliberate. Every other faction in the mod has them, and the Necrons are the one faction whose
 * whole point is that they arrive when the tomb decides. An egg would make the awakening — the
 * hundred-point clock the campaign already keeps — a thing you can skip, which is the one way to
 * make it mean nothing. {@code /summon} still works for testing.
 */
public final class FCNecrons {

    private FCNecrons() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    /** Rank and file. 1.9 blocks tall and narrow — the model is 28 units with a 5-wide chest. */
    public static final RegistryObject<EntityType<NecronWarriorEntity>> NECRON_WARRIOR =
            ENTITY_TYPES.register("necron_warrior",
                    () -> EntityType.Builder.of(NecronWarriorEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.9F)
                            .clientTrackingRange(10)
                            .build(ExampleMod.MODID + ":necron_warrior"));

    /** The crown. Taller and wider: 2.4 blocks, so he is findable in a phalanx. */
    public static final RegistryObject<EntityType<NecronOverlordEntity>> NECRON_OVERLORD =
            ENTITY_TYPES.register("necron_overlord",
                    () -> EntityType.Builder.of(NecronOverlordEntity::new, MobCategory.MONSTER)
                            .sized(0.9F, 2.4F)
                            .clientTrackingRange(12)
                            .build(ExampleMod.MODID + ":necron_overlord"));

    /** Knee-high and wide — the hitbox matches the domed shell rather than a cube. */
    public static final RegistryObject<EntityType<NecronScarabEntity>> NECRON_SCARAB =
            ENTITY_TYPES.register("necron_scarab",
                    () -> EntityType.Builder.of(NecronScarabEntity::new, MobCategory.MONSTER)
                            .sized(0.8F, 0.5F)
                            .clientTrackingRange(8)
                            .build(ExampleMod.MODID + ":necron_scarab"));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(FCNecrons::registerAttributes);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(NECRON_WARRIOR.get(), NecronWarriorEntity.createAttributes().build());
        event.put(NECRON_OVERLORD.get(), NecronOverlordEntity.createAttributes().build());
        event.put(NECRON_SCARAB.get(), NecronScarabEntity.createAttributes().build());
    }
}
