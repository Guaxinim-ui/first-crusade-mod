package com.example.examplemod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * GeckoLib renderer for the Primarch. Binds the "primarch" asset name
 * (geo/primarch.geo.json, textures/entity/primarch.png, animations/primarch.animation.json).
 * Halo, cape, laurel crown and the power sword are bones in the geo model.
 */
public class PrimarchRenderer extends FCGeoRenderer<PrimarchEntity> {
    public PrimarchRenderer(EntityRendererProvider.Context context) {
        super(context, "primarch", 1.0F);
    }
}
