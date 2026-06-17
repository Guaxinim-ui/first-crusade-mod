package com.example.examplemod;

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

public class RobouteGuillimanModel<T extends RobouteGuillimanEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(ExampleMod.MODID, "roboute_guilliman"),
            "main"
    );

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart halo;
    private final ModelPart sword;

    public RobouteGuillimanModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.halo = root.getChild("halo");
        this.sword = root.getChild("sword");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();

        CubeDeformation armor = new CubeDeformation(0.15F);
        CubeDeformation heavyArmor = new CubeDeformation(0.35F);

        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(230, 35)
                        .addBox(-5.0F, -8.0F, -5.0F, 10.0F, 10.0F, 10.0F, armor),
                PartPose.offset(0.0F, -31.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(350, 75)
                        .addBox(-8.0F, -18.0F, -5.0F, 16.0F, 22.0F, 10.0F, heavyArmor)
                        .texOffs(350, 140)
                        .addBox(-9.0F, -20.0F, -6.0F, 18.0F, 5.0F, 12.0F, armor)
                        .texOffs(360, 510)
                        .addBox(-6.0F, 4.0F, -4.0F, 12.0F, 9.0F, 8.0F, armor),
                PartPose.offset(0.0F, -15.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create()
                        .texOffs(55, 250)
                        .addBox(-6.0F, -3.0F, -4.0F, 7.0F, 23.0F, 8.0F, heavyArmor)
                        .texOffs(75, 150)
                        .addBox(-8.0F, -6.0F, -5.0F, 10.0F, 8.0F, 10.0F, armor),
                PartPose.offset(-10.0F, -30.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create()
                        .texOffs(725, 290)
                        .addBox(-1.0F, -3.0F, -4.0F, 7.0F, 23.0F, 8.0F, heavyArmor)
                        .texOffs(760, 175)
                        .addBox(-2.0F, -6.0F, -5.0F, 10.0F, 8.0F, 10.0F, armor),
                PartPose.offset(10.0F, -30.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(80, 640)
                        .addBox(-4.5F, 0.0F, -4.5F, 8.0F, 27.0F, 9.0F, heavyArmor)
                        .texOffs(70, 845)
                        .addBox(-5.0F, 20.0F, -6.0F, 9.0F, 8.0F, 12.0F, armor),
                PartPose.offset(-4.0F, -3.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(190, 640)
                        .addBox(-3.5F, 0.0F, -4.5F, 8.0F, 27.0F, 9.0F, heavyArmor)
                        .texOffs(185, 845)
                        .addBox(-4.0F, 20.0F, -6.0F, 9.0F, 8.0F, 12.0F, armor),
                PartPose.offset(4.0F, -3.0F, 0.0F)
        );

        PartDefinition halo = root.addOrReplaceChild(
                "halo",
                CubeListBuilder.create()
                        .texOffs(820, 45)
                        .addBox(-10.0F, -10.0F, 1.5F, 20.0F, 20.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -34.0F, 5.0F)
        );

        halo.addOrReplaceChild(
                "halo_top_spike",
                CubeListBuilder.create()
                        .texOffs(900, 35)
                        .addBox(-1.0F, -16.0F, 1.4F, 2.0F, 8.0F, 1.0F),
                PartPose.ZERO
        );

        halo.addOrReplaceChild(
                "halo_left_spike",
                CubeListBuilder.create()
                        .texOffs(900, 50)
                        .addBox(-16.0F, -1.0F, 1.4F, 8.0F, 2.0F, 1.0F),
                PartPose.ZERO
        );

        halo.addOrReplaceChild(
                "halo_right_spike",
                CubeListBuilder.create()
                        .texOffs(900, 60)
                        .addBox(8.0F, -1.0F, 1.4F, 8.0F, 2.0F, 1.0F),
                PartPose.ZERO
        );

        root.addOrReplaceChild(
                "sword",
                CubeListBuilder.create()
                        .texOffs(760, 290)
                        .addBox(-1.0F, -34.0F, -1.0F, 2.0F, 40.0F, 2.0F)
                        .texOffs(780, 300)
                        .addBox(-3.0F, -38.0F, -0.7F, 6.0F, 6.0F, 1.4F),
                PartPose.offset(-15.0F, -11.0F, -4.0F)
        );

        return LayerDefinition.create(meshDefinition, 1024, 1024);
    }

    @Override
    public void setupAnim(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180.0F);
        this.head.xRot = headPitch * ((float) Math.PI / 180.0F);

        this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 0.75F * limbSwingAmount;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.75F * limbSwingAmount;

        this.rightArm.xRot = -0.25F + Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.35F * limbSwingAmount;
        this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 0.35F * limbSwingAmount;

        this.body.yRot = 0.0F;

        this.halo.yRot = this.head.yRot * 0.35F;
        this.halo.xRot = this.head.xRot * 0.15F;

        this.sword.xRot = -0.55F + Mth.sin(ageInTicks * 0.07F) * 0.03F;
        this.sword.yRot = 0.25F;
        this.sword.zRot = -0.15F;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
