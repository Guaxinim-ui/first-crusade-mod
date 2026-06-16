package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class LasgunShotRenderer extends EntityRenderer<LasgunShotEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/lasgun_shot.png");

    public LasgunShotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LasgunShotEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // Sem textura/renderização por enquanto.
        // O disparo aparece pelas partículas criadas em LasgunShotEntity.
    }

    @Override
    public ResourceLocation getTextureLocation(LasgunShotEntity entity) {
        return TEXTURE;
    }
}