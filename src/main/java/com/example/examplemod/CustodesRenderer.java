package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CustodesRenderer extends HumanoidMobRenderer<CustodesEntity, HumanoidModel<CustodesEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/custodes.png");

    public CustodesRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(CustodesEntity entity) {
        return TEXTURE;
    }
}
