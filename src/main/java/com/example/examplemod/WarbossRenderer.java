package com.example.examplemod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * GeckoLib renderer for the Ork Warboss. Binds the "warboss" asset name
 * (geo/warboss.geo.json, textures/entity/warboss.png, animations/warboss.animation.json).
 */
public class WarbossRenderer extends FCGeoRenderer<WarbossEntity> {
    public WarbossRenderer(EntityRendererProvider.Context context) {
        super(context, "warboss", 0.9F);
    }
}
