package com.example.examplemod.client.model;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.SentinelWalkerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class SentinelWalkerModel extends GeoModel<SentinelWalkerEntity> {
    @Override public ResourceLocation getModelResource(SentinelWalkerEntity animatable) { return new ResourceLocation(ExampleMod.MODID, "geo/sentinel_walker.geo.json"); }
    @Override public ResourceLocation getTextureResource(SentinelWalkerEntity animatable) { return new ResourceLocation(ExampleMod.MODID, "textures/entity/sentinel_walker.png"); }
    @Override public ResourceLocation getAnimationResource(SentinelWalkerEntity animatable) { return new ResourceLocation(ExampleMod.MODID, "animations/sentinel_walker.animation.json"); }
}
