package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CustodesRenderer extends HumanoidMobRenderer<CustodesEntity, HumanoidModel<CustodesEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/custodes.png");

    // Golden giants of the Adeptus Custodes: even larger than a Space Marine.
    private static final float SCALE = 1.45F;

    public CustodesRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(EliteModelLayers.CUSTODES)), 1.0F);
    }

    @Override
    protected void scale(CustodesEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(CustodesEntity entity) {
        return TEXTURE;
    }
}
