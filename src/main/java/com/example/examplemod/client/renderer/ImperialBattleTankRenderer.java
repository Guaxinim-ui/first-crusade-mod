package com.example.examplemod.client.renderer;

import com.example.examplemod.client.model.ImperialBattleTankModel;
import com.example.examplemod.entity.vehicle.ImperialBattleTankEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ImperialBattleTankRenderer
        extends MobRenderer<ImperialBattleTankEntity, ImperialBattleTankModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("firstcrusade", "textures/entity/imperial_battle_tank.png");

    public ImperialBattleTankRenderer(EntityRendererProvider.Context context) {
        super(context, new ImperialBattleTankModel(
                context.bakeLayer(ImperialBattleTankModel.LAYER_LOCATION)), 2.4F);
    }

    @Override
    protected void scale(ImperialBattleTankEntity tank, PoseStack poseStack, float partialTick) {
        // The model's tracks sit at model-Y ~-8, which renders ~2 blocks above the ground (the tank
        // floated). scale() runs in the renderer's flipped (-1,-1,1) space, where +Y is world-down,
        // so a +2.0 translate drops the tank so its tracks rest on the ground. (Tune ±0.1 if needed.)
        poseStack.translate(0.0F, 2.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(ImperialBattleTankEntity tank) {
        return TEXTURE;
    }
}
