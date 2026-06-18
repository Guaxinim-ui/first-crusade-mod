package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class FeudalKnightRenderer extends HumanoidMobRenderer<FeudalKnightEntity, HumanoidModel<FeudalKnightEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/feudal_knight.png");

    public FeudalKnightRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(FeudalKnightEntity entity) {
        return TEXTURE;
    }
}
