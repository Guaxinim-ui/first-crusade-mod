package com.example.examplemod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * Generic GeckoLib model for the mod's custom mobs. It resolves all three asset paths from a single
 * short name, so adding a new mob needs no new model class — just the three Blockbench files:
 *
 * <pre>
 *   name = "ork_boy"  ->  assets/firstcrusade/geo/ork_boy.geo.json
 *                         assets/firstcrusade/textures/entity/ork_boy.png
 *                         assets/firstcrusade/animations/ork_boy.animation.json
 * </pre>
 *
 * If the mob implements {@link GeoVariantTextured}, its variant texture is used instead of the
 * default one — that is the seam for ork_boy_1.png / guardsman_krieg.png style variations later.
 *
 * @param <T> the mob type (must be a {@link Mob} that is also a {@link GeoEntity})
 */
public class FCGeoModel<T extends Mob & GeoEntity> extends GeoModel<T> {
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public FCGeoModel(String name) {
        this.model = new ResourceLocation(ExampleMod.MODID, "geo/" + name + ".geo.json");
        this.texture = new ResourceLocation(ExampleMod.MODID, "textures/entity/" + name + ".png");
        this.animation = new ResourceLocation(ExampleMod.MODID, "animations/" + name + ".animation.json");
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return this.model;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        if (animatable instanceof GeoVariantTextured variant) {
            return variant.getVariantTexture();
        }
        return this.texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return this.animation;
    }
}
