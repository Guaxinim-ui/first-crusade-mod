package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class OrkNobRenderer extends HumanoidMobRenderer<OrkNobEntity, HumanoidModel<OrkNobEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/ork_nob.png");

    public OrkNobRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(OrkNobEntity entity) {
        return TEXTURE;
    }
}