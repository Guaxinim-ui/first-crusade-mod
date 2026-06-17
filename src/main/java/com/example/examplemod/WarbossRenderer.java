package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class WarbossRenderer extends HumanoidMobRenderer<WarbossEntity, HumanoidModel<WarbossEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/warboss.png");

    // Bigger than a Nob, fitting the biggest Ork of the camp.
    public WarbossRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 1.2F);
    }

    @Override
    public ResourceLocation getTextureLocation(WarbossEntity entity) {
        return TEXTURE;
    }
}
