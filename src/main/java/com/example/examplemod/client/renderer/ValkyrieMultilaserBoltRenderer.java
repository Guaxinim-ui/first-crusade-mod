package com.example.examplemod.client.renderer;

import com.example.examplemod.entity.projectile.ValkyrieMultilaserBoltEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public final class ValkyrieMultilaserBoltRenderer extends ThrownItemRenderer<ValkyrieMultilaserBoltEntity> {
    public ValkyrieMultilaserBoltRenderer(EntityRendererProvider.Context context) {
        super(context, 1.15F, true);
    }
}
