package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SpaceMarineRenderer extends HumanoidMobRenderer<SpaceMarineEntity, HumanoidModel<SpaceMarineEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/space_marine.png");

    // Space Marines tower over Guardsmen: render the model noticeably larger and bulkier.
    private static final float SCALE = 1.3F;

    public SpaceMarineRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(EliteModelLayers.SPACE_MARINE)), 0.75F);
    }

    @Override
    protected void scale(SpaceMarineEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(SpaceMarineEntity entity) {
        return TEXTURE;
    }
}