package com.example.examplemod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Weapon sounds authored specifically for First Crusade. */
public final class FCWeaponSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ExampleMod.MODID);

    public static final RegistryObject<SoundEvent> BOLTER_FIRE = register("bolter_fire");
    public static final RegistryObject<SoundEvent> BOLTER_IMPACT = register("bolter_impact");
    public static final RegistryObject<SoundEvent> BOLTER_AIM = register("bolter_aim");
    public static final RegistryObject<SoundEvent> BOLTER_RELOAD = register("bolter_reload");
    public static final RegistryObject<SoundEvent> CHAIN_SWORD_START = register("chainsword_start");
    public static final RegistryObject<SoundEvent> CHAIN_SWORD_LOOP = register("chainsword_loop");
    public static final RegistryObject<SoundEvent> CHAIN_SWORD_STOP = register("chainsword_stop");
    public static final RegistryObject<SoundEvent> CHAIN_SWORD_HIT = register("chainsword_hit");
    public static final RegistryObject<SoundEvent> CHOPPA_SWING = register("choppa_swing");
    public static final RegistryObject<SoundEvent> CHOPPA_HIT = register("choppa_hit");
    public static final RegistryObject<SoundEvent> POWER_KLAW_CHARGE = register("power_klaw_charge");
    public static final RegistryObject<SoundEvent> POWER_KLAW_CRUSH = register("power_klaw_crush");

    private FCWeaponSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(ExampleMod.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
