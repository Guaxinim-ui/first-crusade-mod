package com.example.examplemod;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AgriMilitiaRenderer extends HumanoidMobRenderer<AgriMilitiaEntity, LasgunAimingHumanoidModel<AgriMilitiaEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/agri_militia.png");

    public AgriMilitiaRenderer(EntityRendererProvider.Context context) {
        super(context, new LasgunAimingHumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(AgriMilitiaEntity entity) {
        return TEXTURE;
    }
}
