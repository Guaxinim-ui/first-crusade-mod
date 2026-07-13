package com.example.examplemod;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class KasrkinRenderer extends HumanoidMobRenderer<KasrkinEntity, LasgunAimingHumanoidModel<KasrkinEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/kasrkin.png");

    public KasrkinRenderer(EntityRendererProvider.Context context) {
        super(context, new LasgunAimingHumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(KasrkinEntity entity) {
        return TEXTURE;
    }
}
