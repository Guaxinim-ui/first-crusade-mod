package com.example.examplemod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Generic GeckoLib renderer for the mod's custom mobs. Pairing it with {@link FCGeoModel} means a new
 * mob's renderer is a one-liner:
 *
 * <pre>
 *   public class OrkBoyRenderer extends FCGeoRenderer&lt;OrkBoyEntity&gt; {
 *       public OrkBoyRenderer(EntityRendererProvider.Context context) {
 *           super(context, "ork_boy", 0.55F);
 *       }
 *   }
 * </pre>
 *
 * Held items and armour are NOT rendered automatically by GeckoLib (unlike the vanilla humanoid
 * renderer). To show a weapon later, add a bone to the model and attach a GeckoLib item render layer.
 *
 * @param <T> the mob type (must be a {@link Mob} that is also a {@link GeoEntity})
 */
public class FCGeoRenderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T> {
    public FCGeoRenderer(EntityRendererProvider.Context context, String name, float shadowRadius) {
        super(context, new FCGeoModel<>(name));
        this.shadowRadius = shadowRadius;
    }
}
