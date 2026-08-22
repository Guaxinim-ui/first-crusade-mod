package com.example.examplemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * The mod's own damage types.
 *
 * <h2>Why not just borrow a vanilla one</h2>
 *
 * A damage type is not only a number — it carries the death message. Choking on Armageddon's ash
 * with {@code dryOut} would tell the server "so-and-so dried out", and a death message that does not
 * match how you died reads as a bug in exactly the moment a player is paying most attention.
 *
 * <p>Damage types are datapack registry entries in 1.20, so the definition is the JSON in
 * {@code data/firstcrusade/damage_type/} and this class only holds the keys to look them up with.
 * The message itself is {@code death.attack.<message_id>} in the language files.
 */
public final class FCDamageTypes {

    private FCDamageTypes() {
    }

    /** The ash worlds: Armageddon and the Forge World, for a traveller with nothing over their face. */
    public static final ResourceKey<DamageType> ASH_CHOKE = key("ash_choke");

    /**
     * Builds a source for one of these keys.
     *
     * <p>{@code DamageSources.source(...)} is not accessible outside its package in 1.20.1, so the
     * holder is looked up from the level's own registry access — which is also the only place a
     * datapack registry entry can come from, and the reason this takes a level rather than being a
     * constant.
     */
    public static net.minecraft.world.damagesource.DamageSource of(
            net.minecraft.world.level.Level level, ResourceKey<DamageType> type) {
        return new net.minecraft.world.damagesource.DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ExampleMod.MODID, name));
    }
}
