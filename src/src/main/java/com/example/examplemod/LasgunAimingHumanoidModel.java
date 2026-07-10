package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class LasgunAimingHumanoidModel<T extends LivingEntity & LasgunAimingEntity> extends HumanoidModel<T> {
    private static final float DRAW_TICKS = 14.0F;
    private static final float SHOOT_TICKS = 5.0F;

    public LasgunAimingHumanoidModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (!entity.getMainHandItem().is(ExampleMod.LASGUN.get())) {
            return;
        }

        LasgunCombatPose pose = entity.getLasgunCombatPose();
        float partialTick = Mth.clamp(ageInTicks - (float) entity.tickCount, 0.0F, 1.0F);
        float poseTicks = entity.getLasgunCombatTicks() + partialTick;

        float aimProgress = switch (pose) {
            case IDLE -> 0.0F;
            case DRAWING -> Mth.clamp(poseTicks / DRAW_TICKS, 0.0F, 1.0F);
            case AIMING, SHOOTING, COOLDOWN -> 1.0F;
        };

        float recoil = 0.0F;
        if (pose == LasgunCombatPose.SHOOTING) {
            recoil = Mth.sin(Mth.clamp(poseTicks / SHOOT_TICKS, 0.0F, 1.0F) * Mth.PI) * 0.28F;
        }

        // Low-ready pose. This keeps the lasgun away from the arm instead of hanging straight down.
        float rightReadyX = -0.62F;
        float leftReadyX = -0.50F;
        float rightReadyY = -0.14F;
        float leftReadyY = 0.22F;
        float rightReadyZ = 0.02F;
        float leftReadyZ = -0.08F;

        // Aiming pose. Both arms go forward; the left hand visually supports the barrel.
        float rightAimX = -1.44F;
        float leftAimX = -1.34F;
        float rightAimY = -0.30F;
        float leftAimY = 0.48F;
        float rightAimZ = 0.03F;
        float leftAimZ = -0.15F;

        float headAssistY = this.head.yRot * 0.30F * aimProgress;
        float headAssistX = this.head.xRot * 0.25F * aimProgress;

        this.rightArm.xRot = Mth.lerp(aimProgress, rightReadyX, rightAimX) + headAssistX + recoil;
        this.leftArm.xRot = Mth.lerp(aimProgress, leftReadyX, leftAimX) + headAssistX + recoil * 0.45F;

        this.rightArm.yRot = Mth.lerp(aimProgress, rightReadyY, rightAimY) + headAssistY;
        this.leftArm.yRot = Mth.lerp(aimProgress, leftReadyY, leftAimY) + headAssistY;

        this.rightArm.zRot = Mth.lerp(aimProgress, rightReadyZ, rightAimZ);
        this.leftArm.zRot = Mth.lerp(aimProgress, leftReadyZ, leftAimZ);
    }
}
