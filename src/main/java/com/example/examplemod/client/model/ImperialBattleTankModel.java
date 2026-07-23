package com.example.examplemod.client.model;

import com.example.examplemod.entity.vehicle.ImperialBattleTankEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class ImperialBattleTankModel extends HierarchicalModel<ImperialBattleTankEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation("firstcrusade", "imperial_battle_tank"), "main");

    private final ModelPart root;
    private final ModelPart hull;
    private final ModelPart leftTrack;
    private final ModelPart rightTrack;
    private final ModelPart leftWheels;
    private final ModelPart rightWheels;
    private final ModelPart[] leftWheelParts;
    private final ModelPart[] rightWheelParts;
    private final ModelPart turret;
    private final ModelPart cannonPitch;
    private final ModelPart barrelRecoil;
    private final ModelPart loaderHatch;
    private final ModelPart antenna;

    public ImperialBattleTankModel(ModelPart root) {
        this.root = root;
        this.hull = root.getChild("hull");
        this.leftTrack = root.getChild("left_track");
        this.rightTrack = root.getChild("right_track");
        this.leftWheels = this.leftTrack.getChild("left_wheels");
        this.rightWheels = this.rightTrack.getChild("right_wheels");
        this.leftWheelParts = new ModelPart[] {
                this.leftWheels.getChild("left_wheel_1_pivot"),
                this.leftWheels.getChild("left_wheel_2_pivot"),
                this.leftWheels.getChild("left_wheel_3_pivot"),
                this.leftWheels.getChild("left_wheel_4_pivot"),
                this.leftWheels.getChild("left_wheel_5_pivot")
        };
        this.rightWheelParts = new ModelPart[] {
                this.rightWheels.getChild("right_wheel_1_pivot"),
                this.rightWheels.getChild("right_wheel_2_pivot"),
                this.rightWheels.getChild("right_wheel_3_pivot"),
                this.rightWheels.getChild("right_wheel_4_pivot"),
                this.rightWheels.getChild("right_wheel_5_pivot")
        };
        this.turret = root.getChild("turret");
        this.cannonPitch = this.turret.getChild("cannon_pitch");
        this.barrelRecoil = this.cannonPitch.getChild("barrel_recoil");
        this.loaderHatch = this.turret.getChild("loader_hatch");
        this.antenna = this.turret.getChild("antenna");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition hull = root.addOrReplaceChild("hull",
                CubeListBuilder.create()
                .texOffs(425, 118).addBox(-29.0F, -8.0F, -38.0F, 58.0F, 14.0F, 76.0F, new CubeDeformation(0.0F))
                .texOffs(696, 118).addBox(-26.0F, -18.0F, -32.0F, 52.0F, 10.0F, 62.0F, new CubeDeformation(0.0F))
                .texOffs(298, 268).addBox(-27.0F, -17.0F, -46.0F, 54.0F, 12.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(437, 268).addBox(-26.0F, -18.0F, 30.0F, 52.0F, 13.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(457, 3).addBox(-38.0F, -16.0F, -42.0F, 9.0F, 18.0F, 84.0F, new CubeDeformation(0.0F))
                .texOffs(646, 3).addBox(29.0F, -16.0F, -42.0F, 9.0F, 18.0F, 84.0F, new CubeDeformation(0.0F))
                .texOffs(284, 299).addBox(-31.0F, -3.0F, -50.0F, 62.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(3, 299).addBox(-20.0F, -20.0F, 39.0F, 40.0F, 10.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(786, 268).addBox(-23.0F, -31.0F, 34.0F, 6.0F, 14.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(815, 268).addBox(17.0F, -31.0F, 34.0F, 6.0F, 14.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(102, 299).addBox(-8.0F, -23.0F, -47.0F, 16.0F, 13.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(450, 299).addBox(-24.0F, -17.0F, -48.0F, 7.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(473, 299).addBox(17.0F, -17.0F, -48.0F, 7.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        PartDefinition left_track = root.addOrReplaceChild("left_track",
                CubeListBuilder.create()
                .texOffs(3, 3).addBox(-8.0F, -8.0F, -48.0F, 16.0F, 16.0F, 96.0F, new CubeDeformation(0.0F))
                .texOffs(3, 118).addBox(-9.0F, -12.0F, -43.0F, 18.0F, 6.0F, 86.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-35.0F, -10.0F, 0.0F));
        PartDefinition right_track = root.addOrReplaceChild("right_track",
                CubeListBuilder.create()
                .texOffs(230, 3).addBox(-8.0F, -8.0F, -48.0F, 16.0F, 16.0F, 96.0F, new CubeDeformation(0.0F))
                .texOffs(214, 118).addBox(-9.0F, -12.0F, -43.0F, 18.0F, 6.0F, 86.0F, new CubeDeformation(0.0F)),
                PartPose.offset(35.0F, -10.0F, 0.0F));
        PartDefinition left_wheels = left_track.addOrReplaceChild("left_wheels",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        left_wheels.addOrReplaceChild("left_wheel_1_pivot",
                CubeListBuilder.create().texOffs(771, 122).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -34.0F));
        left_wheels.addOrReplaceChild("left_wheel_2_pivot",
                CubeListBuilder.create().texOffs(947, 122).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -17.0F));
        left_wheels.addOrReplaceChild("left_wheel_3_pivot",
                CubeListBuilder.create().texOffs(67, 153).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        left_wheels.addOrReplaceChild("left_wheel_4_pivot",
                CubeListBuilder.create().texOffs(243, 153).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 17.0F));
        left_wheels.addOrReplaceChild("left_wheel_5_pivot",
                CubeListBuilder.create().texOffs(419, 153).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 34.0F));
        PartDefinition right_wheels = right_track.addOrReplaceChild("right_wheels",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        right_wheels.addOrReplaceChild("right_wheel_1_pivot",
                CubeListBuilder.create().texOffs(859, 122).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -34.0F));
        right_wheels.addOrReplaceChild("right_wheel_2_pivot",
                CubeListBuilder.create().texOffs(3, 153).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, -17.0F));
        right_wheels.addOrReplaceChild("right_wheel_3_pivot",
                CubeListBuilder.create().texOffs(155, 153).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        right_wheels.addOrReplaceChild("right_wheel_4_pivot",
                CubeListBuilder.create().texOffs(331, 153).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 17.0F));
        right_wheels.addOrReplaceChild("right_wheel_5_pivot",
                CubeListBuilder.create().texOffs(507, 153).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 34.0F));
        PartDefinition turret = root.addOrReplaceChild("turret",
                CubeListBuilder.create()
                .texOffs(3, 213).addBox(-22.0F, -4.0F, -22.0F, 44.0F, 8.0F, 44.0F, new CubeDeformation(0.0F))
                .texOffs(182, 213).addBox(-18.0F, -14.0F, -18.0F, 36.0F, 10.0F, 36.0F, new CubeDeformation(0.0F))
                .texOffs(855, 268).addBox(-19.0F, -13.0F, -28.0F, 38.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(329, 213).addBox(-25.0F, -12.0F, -18.0F, 7.0F, 11.0F, 34.0F, new CubeDeformation(0.0F))
                .texOffs(414, 213).addBox(18.0F, -12.0F, -18.0F, 7.0F, 11.0F, 34.0F, new CubeDeformation(0.0F))
                .texOffs(954, 268).addBox(-8.0F, -20.0F, -5.0F, 16.0F, 6.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(249, 299).addBox(-7.0F, -18.0F, -29.0F, 14.0F, 11.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -29.0F, 0.0F));
        PartDefinition cannon_pitch = turret.addOrReplaceChild("cannon_pitch",
                CubeListBuilder.create()
                .texOffs(739, 268).addBox(11.0F, -1.0F, -12.0F, 5.0F, 5.0F, 17.0F, new CubeDeformation(0.0F))
                .texOffs(655, 213).addBox(12.0F, 0.0F, -38.0F, 3.0F, 3.0F, 26.0F, new CubeDeformation(0.0F))
                .texOffs(568, 268).addBox(-11.0F, -7.0F, -4.0F, 22.0F, 14.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -7.0F, -26.0F));
        PartDefinition barrel_recoil = cannon_pitch.addOrReplaceChild("barrel_recoil",
                CubeListBuilder.create()
                .texOffs(499, 213).addBox(-6.0F, -6.0F, -30.0F, 12.0F, 12.0F, 26.0F, new CubeDeformation(0.0F))
                .texOffs(578, 213).addBox(-5.0F, -5.0F, -57.0F, 10.0F, 10.0F, 27.0F, new CubeDeformation(0.0F))
                .texOffs(690, 268).addBox(-4.0F, -4.0F, -72.0F, 8.0F, 8.0F, 15.0F, new CubeDeformation(0.0F))
                .texOffs(635, 268).addBox(-8.0F, -7.0F, -82.0F, 16.0F, 14.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(143, 299).addBox(-6.0F, -5.0F, -88.0F, 12.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition loader_hatch = turret.addOrReplaceChild("loader_hatch",
                CubeListBuilder.create()
                .texOffs(182, 299).addBox(-9.0F, -2.0F, -14.0F, 18.0F, 2.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -14.0F, 13.0F));
        PartDefinition antenna = turret.addOrReplaceChild("antenna",
                CubeListBuilder.create()
                .texOffs(423, 299).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(844, 268).addBox(-1.0F, -22.0F, -1.0F, 2.0F, 19.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(496, 299).addBox(-2.0F, -26.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(14.0F, -10.0F, 9.0F));
        PartDefinition muzzle = barrel_recoil.addOrReplaceChild("muzzle",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, -68.0F));

        return LayerDefinition.create(mesh, 1024, 1024);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(ImperialBattleTankEntity tank, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);

        float relativeTurretYaw = Mth.wrapDegrees(tank.getTurretYaw() - tank.getYRot());
        this.turret.yRot = relativeTurretYaw * Mth.DEG_TO_RAD;
        this.cannonPitch.xRot = tank.getCannonPitch() * Mth.DEG_TO_RAD;

        float horizontalSpeed = (float)Math.sqrt(
                tank.getDeltaMovement().x * tank.getDeltaMovement().x +
                tank.getDeltaMovement().z * tank.getDeltaMovement().z
        );
        float moveStrength = Mth.clamp(horizontalSpeed * 5.0F, 0.0F, 1.0F);
        float wheelSpin = ageInTicks * Mth.clamp(horizontalSpeed * 18.0F, 0.0F, 3.6F);

        for (ModelPart wheel : this.leftWheelParts) {
            wheel.xRot = wheelSpin;
        }
        for (ModelPart wheel : this.rightWheelParts) {
            wheel.xRot = wheelSpin;
        }

        if (moveStrength > 0.01F) {
            float phase = ageInTicks * 0.72F;
            float suspension = Mth.sin(phase) * 0.12F * moveStrength;

            this.hull.y -= suspension;
            this.hull.xRot = Mth.sin(phase * 0.55F) * 0.005F * moveStrength;
            this.hull.zRot = Mth.cos(phase * 0.70F) * 0.0035F * moveStrength;

            this.leftTrack.y += Mth.sin(phase + Mth.HALF_PI) * 0.045F * moveStrength;
            this.rightTrack.y += Mth.sin(phase - Mth.HALF_PI) * 0.045F * moveStrength;

            this.turret.xRot = Mth.sin(phase * 0.60F) * 0.0025F * moveStrength;
            this.turret.zRot = Mth.cos(phase * 0.65F) * 0.0025F * moveStrength;

            this.antenna.zRot = Mth.sin(ageInTicks * 0.92F) * 0.055F * moveStrength;
        }

        this.barrelRecoil.z += tank.getRecoil() * 7.0F;

        float reload = tank.getReloadProgress(1.0F);
        if (reload > 0.0F) {
            float openAmount = Mth.sin(reload * Mth.PI);
            this.loaderHatch.xRot = -openAmount * 1.25F;
            this.cannonPitch.xRot += -openAmount * 0.10F;
        }

        if (tank.getActionState() == ImperialBattleTankEntity.ACTION_IDLE && tank.getTarget() == null) {
            this.antenna.zRot += Mth.sin(ageInTicks * 0.13F) * 0.025F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
