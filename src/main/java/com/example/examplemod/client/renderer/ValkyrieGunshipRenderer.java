package com.example.examplemod.client.renderer;

import com.example.examplemod.client.model.ValkyrieGunshipModel;
import com.example.examplemod.entity.ValkyrieGunshipEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class ValkyrieGunshipRenderer extends GeoEntityRenderer<ValkyrieGunshipEntity> {
    public ValkyrieGunshipRenderer(EntityRendererProvider.Context context) {
        super(context, new ValkyrieGunshipModel());
        this.shadowRadius = 4.0F;
    }
}
