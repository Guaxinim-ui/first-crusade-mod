package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RobouteGuillimanRenderer extends MobRenderer<RobouteGuillimanEntity, RobouteGuillimanModel<RobouteGuillimanEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            ExampleMod.MODID,
            "textures/entity/roboute_guilliman_uv.png"
    );

    public RobouteGuillimanRenderer(EntityRendererProvider.Context context) {
        super(context, new RobouteGuillimanModel<>(context.bakeLayer(RobouteGuillimanModel.LAYER_LOCATION)), 0.9F);
    }

    @Override
    protected void scale(RobouteGuillimanEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.15F, 1.15F, 1.15F);
    }

    @Override
    public ResourceLocation getTextureLocation(RobouteGuillimanEntity entity) {
        return TEXTURE;
    }
}
