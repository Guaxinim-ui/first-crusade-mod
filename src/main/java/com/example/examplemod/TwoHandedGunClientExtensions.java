package com.example.examplemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Client presentation shared by the Imperial long guns.
 *
 * <p>The item model is anchored to the trigger hand, while this custom arm pose brings the support
 * hand under the fore-end. This avoids the old one-handed/sword-like stance and keeps the weapon
 * level across the chest in third person.</p>
 */
public final class TwoHandedGunClientExtensions implements IClientItemExtensions {
    public static final TwoHandedGunClientExtensions INSTANCE = new TwoHandedGunClientExtensions();

    private static final HumanoidModel.ArmPose TWO_HANDED_GUN = HumanoidModel.ArmPose.create(
            "firstcrusade_two_handed_gun",
            true,
            TwoHandedGunClientExtensions::applyTwoHandedPose
    );

    private TwoHandedGunClientExtensions() {
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entityLiving, InteractionHand hand, ItemStack itemStack) {
        return hand == InteractionHand.MAIN_HAND ? TWO_HANDED_GUN : null;
    }

    private static void applyTwoHandedPose(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float headPitch = model.head.xRot;
        float headYaw = model.head.yRot;
        float walk = Mth.sin((entity.tickCount + 0.5F) * 0.08F) * 0.012F;

        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;

        if (rightHanded) {
            model.rightArm.xRot = -1.34F + headPitch * 0.35F + walk;
            model.rightArm.yRot = -0.36F + headYaw * 0.75F;
            model.rightArm.zRot = 0.06F;

            model.leftArm.xRot = -1.28F + headPitch * 0.30F - walk;
            model.leftArm.yRot = 0.54F + headYaw * 0.70F;
            model.leftArm.zRot = -0.08F;
        } else {
            model.leftArm.xRot = -1.34F + headPitch * 0.35F + walk;
            model.leftArm.yRot = 0.36F + headYaw * 0.75F;
            model.leftArm.zRot = -0.06F;

            model.rightArm.xRot = -1.28F + headPitch * 0.30F - walk;
            model.rightArm.yRot = -0.54F + headYaw * 0.70F;
            model.rightArm.zRot = 0.08F;
        }
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                           ItemStack itemInHand, float partialTick,
                                           float equipProcess, float swingProcess) {
        // Keeps the enlarged receiver from covering the whole screen while leaving it visibly heavy.
        poseStack.translate(0.0D, -0.08D, -0.12D);
        return false;
    }
}
