package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SpaceMarineRenderer extends HumanoidMobRenderer<SpaceMarineEntity, HumanoidModel<SpaceMarineEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/space_marine.png");

    public SpaceMarineRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.75F);
    }

    @Override
    public ResourceLocation getTextureLocation(SpaceMarineEntity entity) {
        return TEXTURE;
    }
}