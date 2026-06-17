package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class KillaKanRenderer extends HumanoidMobRenderer<KillaKanEntity, HumanoidModel<KillaKanEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ExampleMod.MODID, "textures/entity/killa_kan.png");

    // Bulky placeholder until a dedicated walker model exists.
    public KillaKanRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 1.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(KillaKanEntity entity) {
        return TEXTURE;
    }
}
