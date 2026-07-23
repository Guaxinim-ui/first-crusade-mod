package com.example.examplemod.client.renderer;

import com.example.examplemod.entity.projectile.SentinelMissileEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public final class SentinelMissileRenderer extends ThrownItemRenderer<SentinelMissileEntity> {
    public SentinelMissileRenderer(EntityRendererProvider.Context context) {
        super(context, 1.25F, false);
    }
}
