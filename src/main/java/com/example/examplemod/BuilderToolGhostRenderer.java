package com.example.examplemod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the MineColonies-style translucent placement ghost while the player holds the City Builder
 * Tool: a coloured box over the selected structure's footprint at the block being aimed at —
 * green when it can be built there, red when the ground is bad or the space is blocked.
 *
 * Client-only, Forge event bus. Placement itself is server-authoritative (the Core re-validates),
 * so this is purely a visual guide.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BuilderToolGhostRenderer {
    private BuilderToolGhostRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) {
            return;
        }

        ItemStack tool = findTool(player);
        if (tool == null) {
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        CityStructureType type = CityBuilderToolItem.getSelectedStructure(tool);
        BlockPos center = hit.getBlockPos().relative(hit.getDirection());
        int radius = type.getFootprintRadius();
        int height = type.getFootprintHeight();

        boolean ok = CityBuilderPlacement.isAreaBuildable(mc.level, center, radius, height);

        double minX = center.getX() - radius;
        double minY = center.getY();
        double minZ = center.getZ() - radius;
        double maxX = center.getX() + radius + 1;
        double maxY = center.getY() + height;
        double maxZ = center.getZ() + radius + 1;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        float r = ok ? 0.25F : 0.95F;
        float g = ok ? 0.90F : 0.25F;
        float b = ok ? 0.35F : 0.20F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addBox(buffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 0.28F);
        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static ItemStack findTool(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof CityBuilderToolItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof CityBuilderToolItem) {
            return off;
        }
        return null;
    }

    private static void addBox(BufferBuilder buffer, Matrix4f m,
                               double x0, double y0, double z0, double x1, double y1, double z1,
                               float r, float g, float b, float a) {
        float fx0 = (float) x0, fy0 = (float) y0, fz0 = (float) z0;
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;

        // bottom
        quad(buffer, m, fx0, fy0, fz0, fx1, fy0, fz0, fx1, fy0, fz1, fx0, fy0, fz1, r, g, b, a);
        // top
        quad(buffer, m, fx0, fy1, fz0, fx0, fy1, fz1, fx1, fy1, fz1, fx1, fy1, fz0, r, g, b, a);
        // north
        quad(buffer, m, fx0, fy0, fz0, fx0, fy1, fz0, fx1, fy1, fz0, fx1, fy0, fz0, r, g, b, a);
        // south
        quad(buffer, m, fx0, fy0, fz1, fx1, fy0, fz1, fx1, fy1, fz1, fx0, fy1, fz1, r, g, b, a);
        // west
        quad(buffer, m, fx0, fy0, fz0, fx0, fy0, fz1, fx0, fy1, fz1, fx0, fy1, fz0, r, g, b, a);
        // east
        quad(buffer, m, fx1, fy0, fz0, fx1, fy1, fz0, fx1, fy1, fz1, fx1, fy0, fz1, r, g, b, a);
    }

    private static void quad(BufferBuilder buffer, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        buffer.vertex(m, x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(m, x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.vertex(m, x3, y3, z3).color(r, g, b, a).endVertex();
    }
}
