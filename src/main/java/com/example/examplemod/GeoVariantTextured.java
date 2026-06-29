package com.example.examplemod;

import net.minecraft.resources.ResourceLocation;

/**
 * Optional hook for GeckoLib mobs that want more than one texture (visual variations) on the same
 * model. A mob implements this and returns the texture for its current variant; {@link FCGeoModel}
 * will use it instead of the default "textures/entity/&lt;name&gt;.png".
 *
 * <p>Example (future use): an Ork Boy could pick between ork_boy_1.png / ork_boy_2.png per spawn,
 * and a Guardsman could return guardsman_cadian.png or guardsman_krieg.png by regiment — all without
 * touching the model or renderer.
 */
public interface GeoVariantTextured {
    ResourceLocation getVariantTexture();
}
