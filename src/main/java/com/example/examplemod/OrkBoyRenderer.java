package com.example.examplemod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * GeckoLib renderer for the Ork Boy. All the work lives in {@link FCGeoRenderer} / {@link FCGeoModel};
 * this just binds the "ork_boy" asset name (geo/ork_boy.geo.json, textures/entity/ork_boy.png,
 * animations/ork_boy.animation.json).
 */
public class OrkBoyRenderer extends FCGeoRenderer<OrkBoyEntity> {
    public OrkBoyRenderer(EntityRendererProvider.Context context) {
        super(context, "ork_boy", 0.55F);
    }
}
