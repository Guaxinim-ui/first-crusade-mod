package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GretchinRenderer extends HumanoidMobRenderer<GretchinEntity, HumanoidModel<GretchinEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/gretchin.png");

    // Small and scrawny.
    public GretchinRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.55F);
    }

    @Override
    public ResourceLocation getTextureLocation(GretchinEntity entity) {
        return TEXTURE;
    }
}
