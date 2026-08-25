package com.example.examplemod.hive.pop;

import java.util.EnumMap;
import java.util.Map;

import com.example.examplemod.ExampleMod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for the Hive's five new dwellers (spec §19).
 *
 * <h2>Built from the enum, not written out five times</h2>
 *
 * Every role gets the same treatment — an entity type, a spawn egg, an attribute entry — and the
 * only things that differ are already in {@link HiveRole}. So the registration is a loop over the
 * enum rather than five near-identical blocks, which means adding the sixth role is one enum
 * constant and nothing here.
 *
 * <p>The two egg colours come from the role's own palette rather than being invented at this layer;
 * they are the same colours {@code tools/generate_hive_dweller_textures.py} paints the mob in, so an
 * egg looks like the thing it spawns.
 */
public final class FCHiveDwellers {

    private FCHiveDwellers() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    private static final Map<HiveRole, RegistryObject<EntityType<HiveDwellerEntity>>> TYPES =
            new EnumMap<>(HiveRole.class);

    private static final Map<HiveRole, RegistryObject<Item>> EGGS =
            new EnumMap<>(HiveRole.class);

    /** Egg shell and spots per role, matching the texture generator's palette. */
    private static final Map<HiveRole, int[]> EGG_COLOURS = new EnumMap<>(HiveRole.class);

    static {
        EGG_COLOURS.put(HiveRole.WORKER, new int[] {0x3B4A5E, 0xD8A516});
        EGG_COLOURS.put(HiveRole.MERCHANT, new int[] {0x7A2E2E, 0xC8A93A});
        EGG_COLOURS.put(HiveRole.MECHANICUS_WORKER, new int[] {0x8E1F1F, 0x6A7076});
        EGG_COLOURS.put(HiveRole.PRIEST, new int[] {0x222024, 0xA69C7D});
        EGG_COLOURS.put(HiveRole.GANG_MEMBER, new int[] {0x332B2B, 0x7FD69A});

        for (HiveRole role : HiveRole.values()) {
            registerRole(role);
        }
    }

    private static void registerRole(HiveRole role) {
        String name = role.entityName();

        // MobCategory.MISC and not MONSTER even for the ganger: category drives vanilla's natural
        // spawning and despawn, and every one of these is placed by the marker system or by hand.
        // Filing them as monsters would let the vanilla spawner fill the Hive with gangers wherever
        // it happened to be dark.
        RegistryObject<EntityType<HiveDwellerEntity>> type = ENTITY_TYPES.register(name,
                () -> EntityType.Builder.of(HiveDwellerEntity::new, MobCategory.MISC)
                        .sized(0.6F, 1.95F)
                        .clientTrackingRange(8)
                        .build(ExampleMod.MODID + ":" + name));

        TYPES.put(role, type);

        int[] colours = EGG_COLOURS.get(role);

        EGGS.put(role, ITEMS.register(name + "_spawn_egg",
                () -> new ForgeSpawnEggItem(type, colours[0], colours[1], new Item.Properties())));
    }

    public static EntityType<HiveDwellerEntity> typeFor(HiveRole role) {
        return TYPES.get(role).get();
    }

    /**
     * The role an entity type belongs to.
     *
     * <h2>Why the role is read from the type and not passed to the constructor</h2>
     *
     * It was passed in at first, and it did not work: {@code Mob}'s constructor calls
     * {@code registerGoals()}, so the goal list is built <b>during</b> {@code super(...)} — before
     * the subclass has assigned any field. Every dweller threw a NullPointerException on
     * {@code role.hostile()} and {@code /summon} answered only "Unable to summon entity". The entity
     * type, by contrast, is set by {@code Entity}'s constructor before {@code registerGoals} runs,
     * so it is the one thing that <i>is</i> available that early.
     *
     * <p>The map is built on first use rather than in the static block, because the types do not
     * exist until Forge has fired the registry event and {@code RegistryObject.get()} would throw.
     */
    public static HiveRole roleOf(EntityType<?> type) {
        Map<EntityType<?>, HiveRole> map = reverse;

        if (map == null) {
            map = new java.util.IdentityHashMap<>();

            for (HiveRole role : HiveRole.values()) {
                map.put(typeFor(role), role);
            }

            reverse = map;
        }

        // A dweller whose type is not ours cannot happen, but answering with the commonest role
        // beats throwing inside a constructor: the mob renders as a worker and the world keeps
        // running.
        return map.getOrDefault(type, HiveRole.WORKER);
    }

    private static volatile Map<EntityType<?>, HiveRole> reverse;

    public static Item eggFor(HiveRole role) {
        return EGGS.get(role).get();
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(FCHiveDwellers::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        for (HiveRole role : HiveRole.values()) {
            event.put(typeFor(role), HiveDwellerEntity.createAttributes(role).build());
        }
    }
}
