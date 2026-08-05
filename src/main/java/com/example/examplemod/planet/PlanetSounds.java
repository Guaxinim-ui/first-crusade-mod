package com.example.examplemod.planet;

import com.example.examplemod.ExampleMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The navigation terminal's seven sounds.
 *
 * <h2>Registered here, sourced from vanilla</h2>
 *
 * Every event below is a real, mod-owned {@link SoundEvent} with its own id, so calling code and
 * datapacks address {@code firstcrusade:planet_selected} and never a vanilla name. What each one
 * currently <i>plays</i> is a vanilla file, mapped in {@code sounds.json}.
 *
 * <p>That split is the point. Shipping recorded audio for a menu would mean seven placeholder
 * {@code .ogg} files that nobody wants to keep, and pointing the code straight at
 * {@code SoundEvents.UI_BUTTON_CLICK} would mean every call site has to be found and edited when
 * real audio arrives. With the indirection, replacing the audio is a one-line change per sound in
 * {@code sounds.json} and no Java at all.
 *
 * <p>The vanilla sources were chosen to sound like a machine rather than like a menu: beacon and
 * conduit tones for the console, respawn-anchor charging for the launch, and the anvil's refusal for
 * a locked destination.
 */
public final class PlanetSounds {
    private PlanetSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ExampleMod.MODID);

    /** The console waking up. */
    public static final RegistryObject<SoundEvent> TERMINAL_OPEN = register("planet_terminal_open");

    /** A destination highlighted in the list. */
    public static final RegistryObject<SoundEvent> SELECTED = register("planet_selected");

    /** A destination the player may not use. */
    public static final RegistryObject<SoundEvent> LOCKED = register("planet_locked");

    /** The confirmation dialog accepting. */
    public static final RegistryObject<SoundEvent> TRAVEL_CONFIRM = register("planet_travel_confirm");

    /** The confirmation dialog dismissed. */
    public static final RegistryObject<SoundEvent> TRAVEL_CANCEL = register("planet_travel_cancel");

    /** The launch sequence beginning. */
    public static final RegistryObject<SoundEvent> TRAVEL_START = register("planet_travel_start");

    /** A launch refused by the server. */
    public static final RegistryObject<SoundEvent> TRAVEL_ERROR = register("planet_travel_error");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(ExampleMod.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
