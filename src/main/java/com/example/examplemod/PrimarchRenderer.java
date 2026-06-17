package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PrimarchRenderer extends HumanoidMobRenderer<PrimarchEntity, HumanoidModel<PrimarchEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/primarch.png");

    // Towering scale: the Primarch dwarfs even a Space Marine.
    private static final float SCALE = 2.0F;

    public PrimarchRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(EliteModelLayers.PRIMARCH)), 1.6F);
    }

    @Override
    protected void scale(PrimarchEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(PrimarchEntity entity) {
        return TEXTURE;
    }
}
