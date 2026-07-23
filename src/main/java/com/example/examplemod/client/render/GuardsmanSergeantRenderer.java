package com.example.examplemod.client.render;

import com.example.examplemod.FCGeoRenderer;
import com.example.examplemod.unit.imperium.GuardsmanSergeantEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renderer for the Guardsman Sergeant. Like the Rifleman's, this is a one-liner over
 * {@link FCGeoRenderer}: the asset triplet is resolved from the name and the per-variant texture
 * comes from {@code GeoVariantTextured}, so there is nothing bespoke to do here.
 */
public class GuardsmanSergeantRenderer extends FCGeoRenderer<GuardsmanSergeantEntity> {

    public GuardsmanSergeantRenderer(EntityRendererProvider.Context context) {
        super(context, "guardsman_sergeant", 0.45F);
    }
}
