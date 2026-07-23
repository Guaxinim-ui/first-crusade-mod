package com.example.examplemod.client.model;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.ValkyrieGunshipEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class ValkyrieGunshipModel extends GeoModel<ValkyrieGunshipEntity> {
    @Override public ResourceLocation getModelResource(ValkyrieGunshipEntity animatable) { return new ResourceLocation(ExampleMod.MODID, "geo/valkyrie_gunship.geo.json"); }
    @Override public ResourceLocation getTextureResource(ValkyrieGunshipEntity animatable) { return new ResourceLocation(ExampleMod.MODID, "textures/entity/valkyrie_gunship.png"); }
    @Override public ResourceLocation getAnimationResource(ValkyrieGunshipEntity animatable) { return new ResourceLocation(ExampleMod.MODID, "animations/valkyrie_gunship.animation.json"); }
}
