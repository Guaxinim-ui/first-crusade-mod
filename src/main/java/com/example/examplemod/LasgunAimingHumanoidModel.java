package com.example.examplemod;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Two-handed lasgun animation for Imperial ranged troops.
 *
 * <p>The lasgun remains attached to the right hand through Minecraft's normal held-item layer.
 * This model only moves the arms: a low-ready stance while idle, a visible raise animation when a
 * target enters firing range, the vanilla pillager/crossbow aim pose, and a short recoil after each
 * shot.</p>
 */
public class LasgunAimingHumanoidModel<T extends LivingEntity & LasgunAimingEntity> extends HumanoidModel<T> {
    private static final float DRAW_TICKS = ImperialLasgunAttackGoal.DRAW_TICKS;
    private static final float SHOOT_TICKS = ImperialLasgunAttackGoal.SHOOT_TICKS;

    public LasgunAimingHumanoidModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (!entity.getMainHandItem().is(FCRegistry.LASGUN.get())) {
            return;
        }

        LasgunCombatPose pose = entity.getLasgunCombatPose();
        float partialTick = Mth.clamp(ageInTicks - (float) entity.tickCount, 0.0F, 1.0F);
        float poseTicks = entity.getLasgunCombatTicks() + partialTick;

        float raiseProgress = switch (pose) {
            case IDLE -> 0.0F;
            case DRAWING -> Mth.clamp(poseTicks / DRAW_TICKS, 0.0F, 1.0F);
            case AIMING, SHOOTING, COOLDOWN -> 1.0F;
        };

        // Smooth start and finish so the weapon does not snap between poses.
        raiseProgress = raiseProgress * raiseProgress * (3.0F - 2.0F * raiseProgress);

        // Capture Minecraft's proven pillager/crossbow two-handed aiming pose.
        AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);

        float aimRightX = this.rightArm.xRot;
        float aimRightY = this.rightArm.yRot;
        float aimRightZ = this.rightArm.zRot;
        float aimLeftX = this.leftArm.xRot;
        float aimLeftY = this.leftArm.yRot;
        float aimLeftZ = this.leftArm.zRot;

        /*
         * Low-ready pose. The right hand remains on the pistol grip while the left hand stays near
         * the fore-end. Because the item is anchored to the right hand, interpolating these angles
         * visibly lifts the entire rifle into the aiming position.
         */
        float readyRightX = -0.82F + this.head.xRot * 0.20F;
        float readyRightY = -0.24F + this.head.yRot * 0.35F;
        float readyRightZ = 0.02F;

        float readyLeftX = -0.78F + this.head.xRot * 0.20F;
        float readyLeftY = 0.48F + this.head.yRot * 0.35F;
        float readyLeftZ = -0.02F;

        this.rightArm.xRot = Mth.lerp(raiseProgress, readyRightX, aimRightX);
        this.rightArm.yRot = Mth.lerp(raiseProgress, readyRightY, aimRightY);
        this.rightArm.zRot = Mth.lerp(raiseProgress, readyRightZ, aimRightZ);

        this.leftArm.xRot = Mth.lerp(raiseProgress, readyLeftX, aimLeftX);
        this.leftArm.yRot = Mth.lerp(raiseProgress, readyLeftY, aimLeftY);
        this.leftArm.zRot = Mth.lerp(raiseProgress, readyLeftZ, aimLeftZ);

        // Short recoil while keeping both hands around the weapon.
        if (pose == LasgunCombatPose.SHOOTING) {
            float recoilProgress = Mth.clamp(poseTicks / SHOOT_TICKS, 0.0F, 1.0F);
            float recoil = Mth.sin(recoilProgress * Mth.PI);

            this.rightArm.xRot += recoil * 0.14F;
            this.leftArm.xRot += recoil * 0.08F;
            this.rightArm.yRot += recoil * 0.025F;
            this.leftArm.yRot -= recoil * 0.015F;
        }
    }
}
