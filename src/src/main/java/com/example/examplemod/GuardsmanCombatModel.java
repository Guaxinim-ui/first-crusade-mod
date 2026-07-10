package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Lightweight code animation for the Guardsman.
 *
 * This intentionally does not use GeckoLib. The combat pose is synced by GuardsmanEntity,
 * then this model rotates the vanilla humanoid arms so every Guardsman can draw, aim and fire
 * without expensive animation controllers.
 */
public class GuardsmanCombatModel<T extends GuardsmanEntity> extends HumanoidModel<T> {
    public GuardsmanCombatModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (!entity.getMainHandItem().is(ExampleMod.LASGUN.get())) {
            return;
        }

        int pose = entity.getLasgunCombatPose();

        if (pose == GuardsmanEntity.LASGUN_POSE_IDLE) {
            applyRelaxedRifleCarry(ageInTicks);
            return;
        }

        applyLasgunCombatPose(entity, pose, ageInTicks);
    }

    /**
     * Idle rifle carry: the lasgun should not look like a vertical stick glued to the arm.
     */
    private void applyRelaxedRifleCarry(float ageInTicks) {
        float breathing = Mth.sin(ageInTicks * 0.12F) * 0.018F;

        this.rightArm.xRot = Mth.lerp(0.45F, this.rightArm.xRot, -0.62F + breathing);
        this.rightArm.yRot = Mth.lerp(0.45F, this.rightArm.yRot, -0.24F);
        this.rightArm.zRot = Mth.lerp(0.45F, this.rightArm.zRot, 0.08F);

        this.leftArm.xRot = Mth.lerp(0.35F, this.leftArm.xRot, -0.38F + breathing);
        this.leftArm.yRot = Mth.lerp(0.35F, this.leftArm.yRot, 0.18F);
        this.leftArm.zRot = Mth.lerp(0.35F, this.leftArm.zRot, -0.06F);
    }

    private void applyLasgunCombatPose(T entity, int pose, float ageInTicks) {
        int poseTicks = entity.getLasgunCombatPoseTicks();

        float blend = 1.0F;
        float rightArmX = -1.56F;
        float rightArmY = -0.28F;
        float rightArmZ = 0.05F;

        float leftArmX = -1.42F;
        float leftArmY = 0.42F;
        float leftArmZ = -0.08F;

        if (pose == GuardsmanEntity.LASGUN_POSE_DRAWING) {
            blend = smoothProgress(poseTicks, GuardsmanEntity.DRAW_LASGUN_TICKS);
            rightArmX = -1.02F;
            rightArmY = -0.22F;
            rightArmZ = 0.12F;

            leftArmX = -0.76F;
            leftArmY = 0.30F;
            leftArmZ = -0.10F;
        } else if (pose == GuardsmanEntity.LASGUN_POSE_AIMING) {
            float breathing = Mth.sin(ageInTicks * 0.16F) * 0.012F;
            rightArmX += breathing;
            leftArmX += breathing;
        } else if (pose == GuardsmanEntity.LASGUN_POSE_SHOOTING) {
            float recoil = 1.0F - smoothProgress(poseTicks, GuardsmanEntity.SHOOT_LASGUN_TICKS);
            rightArmX = -1.68F + recoil * 0.22F;
            leftArmX = -1.50F + recoil * 0.16F;
            rightArmZ = 0.08F + recoil * 0.08F;
            leftArmZ = -0.10F - recoil * 0.05F;
        } else if (pose == GuardsmanEntity.LASGUN_POSE_COOLDOWN) {
            rightArmX = -1.48F;
            leftArmX = -1.34F;
        }

        this.rightArm.xRot = Mth.lerp(blend, this.rightArm.xRot, rightArmX);
        this.rightArm.yRot = Mth.lerp(blend, this.rightArm.yRot, rightArmY);
        this.rightArm.zRot = Mth.lerp(blend, this.rightArm.zRot, rightArmZ);

        this.leftArm.xRot = Mth.lerp(blend, this.leftArm.xRot, leftArmX);
        this.leftArm.yRot = Mth.lerp(blend, this.leftArm.yRot, leftArmY);
        this.leftArm.zRot = Mth.lerp(blend, this.leftArm.zRot, leftArmZ);
    }

    /**
     * Moves the default item-in-hand render point forward and away from the wrist.
     * This is the part that fixes the lasgun looking glued to the arm.
     */
    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        super.translateToHand(arm, poseStack);

        if (arm == HumanoidArm.RIGHT) {
            poseStack.translate(0.0D, 0.10D, -0.32D);
            poseStack.mulPose(Axis.XP.rotationDegrees(-7.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-4.0F));
        } else {
            poseStack.translate(0.0D, 0.10D, -0.32D);
            poseStack.mulPose(Axis.XP.rotationDegrees(-7.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(4.0F));
        }
    }

    private static float smoothProgress(int ticks, int duration) {
        if (duration <= 0) {
            return 1.0F;
        }

        float value = Mth.clamp(ticks / (float) duration, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }
}
