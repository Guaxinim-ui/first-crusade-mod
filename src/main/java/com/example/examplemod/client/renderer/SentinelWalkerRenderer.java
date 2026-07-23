package com.example.examplemod.client.renderer;

import com.example.examplemod.client.model.SentinelWalkerModel;
import com.example.examplemod.entity.SentinelWalkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class SentinelWalkerRenderer extends GeoEntityRenderer<SentinelWalkerEntity> {
    public SentinelWalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new SentinelWalkerModel());
        this.shadowRadius = 1.6F;
    }

    @Override
    public void render(SentinelWalkerEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // The geo model's lowest cubes sit 5.5px below the feet (Y=0), so the walker's feet sank
        // into the ground. Lift the whole render 5.5/16 of a block to plant the feet on the surface.
        poseStack.pushPose();
        poseStack.translate(0.0D, 5.5D / 16.0D, 0.0D);
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
