package com.example.examplemod;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class KasrkinRenderer extends HumanoidMobRenderer<KasrkinEntity, LasgunAimingHumanoidModel<KasrkinEntity>> {
    public KasrkinRenderer(EntityRendererProvider.Context context) {
        super(context, new LasgunAimingHumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    /**
     * The texture is asked for per entity, not per class: this renderer draws every soldier of its
     * type, and they are not meant to look alike. {@link ImperialTroopAppearance} turns the unit's
     * kind, regiment, individual variant and career grade into one pre-built ResourceLocation, so
     * this stays two map lookups and an array index no matter how many are on screen.
     */
    @Override
    public ResourceLocation getTextureLocation(KasrkinEntity entity) {
        return ImperialTroopAppearance.texture(entity);
    }
}
