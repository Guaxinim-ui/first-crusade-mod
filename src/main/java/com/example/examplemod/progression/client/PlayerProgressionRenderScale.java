package com.example.examplemod.progression.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.progression.PlayerBody;
import com.example.examplemod.progression.PlayerProgressionSizeManager;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes an Astartes actually <i>look</i> like one.
 *
 * <h2>Why the size event was never enough</h2>
 *
 * {@link PlayerProgressionSizeManager} grows the player's {@code EntityDimensions}, and that reaches
 * the hitbox, the collision box, the eye height, the first-person camera, the shadow and the
 * nameplate — everything except the thing you actually look at. {@code PlayerRenderer} draws the
 * model at a hard-coded {@code 0.9375} and never consults the entity's dimensions. So before this
 * class a Neophyte occupied 2.30 blocks, could not walk through a door, saw the world from a
 * Space Marine's eye level, and was drawn exactly the size of a Guardsman.
 *
 * <p>The model therefore has to be scaled where models are scaled: on the pose stack, around the
 * render.
 *
 * <h2>Uniform, and measured from the feet</h2>
 *
 * The scale is the stage's <i>height</i> ratio applied to all three axes. The stage is also wider
 * than vanilla (0.84 against 0.60), but scaling X and Z by that separate ratio would stretch the
 * model into a different shape rather than a bigger person — a hitbox slightly wider than the body
 * inside it is ordinary in Minecraft, a squashed player is not.
 *
 * <p>{@code scale()} multiplies about the pose stack's origin, and for entity rendering that origin
 * is the entity's feet. So the model grows upward from the ground with no translation needed, and
 * an Astartes stands on the floor rather than sunk into it.
 *
 * <h2>Push and pop, bracketed</h2>
 *
 * {@code Pre} is cancellable. If this class pushed at default priority and something else then
 * cancelled the render, {@code Post} would never fire and the pose stack would leak a matrix into
 * the rest of the frame — which shows up as the entire world tilting. Pushing at
 * {@link EventPriority#LOWEST} means every cancellation has already had its say by the time this
 * runs, and popping at {@link EventPriority#HIGHEST} keeps the pair tight. The remembered scale is a
 * plain field because rendering happens on one thread and player renders do not nest.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PlayerProgressionRenderScale {
    private PlayerProgressionRenderScale() {
    }

    /** The scale applied by the last {@code Pre}, so {@code Post} knows whether it owes a pop. */
    private static float pushedScale = 1.0F;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        float scale = scaleFor(event.getEntity());
        pushedScale = scale;

        if (scale == 1.0F) {
            return;
        }

        event.getPoseStack().pushPose();
        event.getPoseStack().scale(scale, scale, scale);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (pushedScale == 1.0F) {
            return;
        }

        pushedScale = 1.0F;
        event.getPoseStack().popPose();
    }

    /**
     * How much bigger this player is drawn.
     *
     * <p>Reads the same body the size event reads — on the client that is the copy the server sent,
     * so every client in render distance draws the same body, and nobody's client decides on its own
     * that it is four blocks tall.
     */
    private static float scaleFor(Player player) {
        PlayerBody body = PlayerProgressionSizeManager.bodyOf(player);

        // Reads the same body the collision box reads, so the model an onlooker sees and the box
        // they walk into are never two different sizes — whichever ladder produced it.
        return body.isVanilla() ? 1.0F : body.heightScale();
    }
}
