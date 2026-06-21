package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GuardsmanRenderer extends HumanoidMobRenderer<GuardsmanEntity, HumanoidModel<GuardsmanEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/guardsman.png");

    // NOTE: the custom GuardsmanModel (owner's Blockbench UVs) is kept dormant until the matching
    // texture is supplied; until then we use the standard humanoid model so the placeholder renders.
    public GuardsmanRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(GuardsmanEntity entity) {
        return TEXTURE;
    }
}