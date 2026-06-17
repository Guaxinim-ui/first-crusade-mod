package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PrimarchRenderer extends HumanoidMobRenderer<PrimarchEntity, HumanoidModel<PrimarchEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/primarch.png");

    // Towering scale: the Primarch dwarfs even a Space Marine.
    public PrimarchRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 1.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(PrimarchEntity entity) {
        return TEXTURE;
    }
}
