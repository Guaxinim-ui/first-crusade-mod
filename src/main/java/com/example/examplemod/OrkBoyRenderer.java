package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class OrkBoyRenderer extends HumanoidMobRenderer<OrkBoyEntity, HumanoidModel<OrkBoyEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/ork_boy.png");

    public OrkBoyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.55F);
    }

    @Override
    public ResourceLocation getTextureLocation(OrkBoyEntity entity) {
        return TEXTURE;
    }
}