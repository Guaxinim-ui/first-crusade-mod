package com.example.examplemod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * GeckoLib renderer for the Custodian Guard. Binds the "custodes" asset name
 * (geo/custodes.geo.json, textures/entity/custodes.png, animations/custodes.animation.json).
 * The golden auramite plate, guardian crest and spear are bones in the geo model.
 */
public class CustodesRenderer extends FCGeoRenderer<CustodesEntity> {
    public CustodesRenderer(EntityRendererProvider.Context context) {
        super(context, "custodes", 0.75F);
    }
}
