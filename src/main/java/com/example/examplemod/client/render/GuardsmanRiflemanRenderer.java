package com.example.examplemod.client.render;

import com.example.examplemod.FCGeoRenderer;
import com.example.examplemod.unit.imperium.GuardsmanRiflemanEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renderer for the Guardsman Rifleman.
 *
 * <p>Deliberately a one-liner over {@link FCGeoRenderer}. An earlier version overrode
 * {@code preRender} to apply per-unit scaling from the entity's variant, but that method's exact
 * signature could not be verified against GeckoLib 4.4.8 here, and no GeckoLib renderer in this
 * project scales today — the two that do ({@code ImperialCitizenRenderer},
 * {@code RobouteGuillimanRenderer}) extend vanilla's {@code MobRenderer}, which is a different base
 * class with a different hook.</p>
 *
 * <p>Risking a compile error on the whole client for a cosmetic ±6% size difference is a bad trade,
 * so scaling is deferred. {@link com.example.examplemod.unit.profile.FCVariantSet#scaleFor} and the
 * entity's {@code getVariantScale()} are already in place; once the build is confirmed green, this
 * class is where that hook goes back in.</p>
 *
 * <p>Texture variation is unaffected and works now: {@link com.example.examplemod.FCGeoModel} resolves it
 * through {@code GeoVariantTextured}, which the entity implements.</p>
 */
public class GuardsmanRiflemanRenderer extends FCGeoRenderer<GuardsmanRiflemanEntity> {

    public GuardsmanRiflemanRenderer(EntityRendererProvider.Context context) {
        super(context, "guardsman_rifleman", 0.4F);
    }
}
