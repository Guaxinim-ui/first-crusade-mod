package com.example.examplemod.client.renderer;

import com.example.examplemod.entity.projectile.SentinelCannonBoltEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public final class SentinelCannonBoltRenderer extends ThrownItemRenderer<SentinelCannonBoltEntity> {
    public SentinelCannonBoltRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, true);
    }
}
