package com.example.examplemod.campaign;

import com.example.examplemod.ExampleMod;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One theatre of the Crusade.
 *
 * <h2>Why a "front" and not a "planet"</h2>
 *
 * The campaign has to work for things that are not planets. The Hive World is a flat dimension a
 * city is built inside, not a world anyone is dropped onto, and the Space Hulk the roadmap ends with
 * is a procedural dungeon that drifts — it has sectors, an owner and objectives, but no orbit and no
 * surface to seed settlements on.
 *
 * <p>Making the campaign's unit a {@code Planet} would have meant either lying about those two or
 * bolting a parallel system on beside it later. A front is the general thing: a dimension, a type,
 * and a name. A planet is the common kind of front, not the only one.
 *
 * @param id        the front's identity, and the key of everything stored about it. Always the
 *                  dimension's own id, so no second naming scheme can drift out of step with it.
 * @param dimension the level this front is fought on
 * @param type      what kind of place it is, which decides how it behaves — see
 *                  {@link CampaignFrontType}
 * @param unlocked  false for a front that exists but cannot be reached yet
 */
public record CampaignFront(ResourceLocation id, ResourceKey<Level> dimension, CampaignFrontType type,
                            boolean unlocked) {

    public static CampaignFront planet(ResourceKey<Level> dimension) {
        return new CampaignFront(dimension.location(), dimension, CampaignFrontType.PLANET, true);
    }

    public static CampaignFront of(ResourceKey<Level> dimension, CampaignFrontType type) {
        return new CampaignFront(dimension.location(), dimension, type, true);
    }

    /** {@code armageddon} — the short name every command and log line uses. */
    public String path() {
        return this.id.getPath();
    }

    /**
     * The display name, which is the planet's own translation key when there is one so the War Table
     * and the navigation terminal never disagree about what a world is called.
     */
    public Component displayName() {
        return Component.translatable("planet." + ExampleMod.MODID + "." + this.id.getPath());
    }

    /** True for a front whose settlements the world seeder should populate. */
    public boolean seedsSettlements() {
        return this.type == CampaignFrontType.PLANET;
    }
}
