package com.example.examplemod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * GeckoLib renderer for the Space Marine. Binds the "space_marine" asset name
 * (geo/space_marine.geo.json, textures/entity/space_marine.png,
 * animations/space_marine.animation.json). The power armour — pauldrons, backpack,
 * gorget, chainsword — is modelled in the geo file itself.
 */
public class SpaceMarineRenderer extends FCGeoRenderer<SpaceMarineEntity> {
    public SpaceMarineRenderer(EntityRendererProvider.Context context) {
        super(context, "space_marine", 0.7F);
    }
}
