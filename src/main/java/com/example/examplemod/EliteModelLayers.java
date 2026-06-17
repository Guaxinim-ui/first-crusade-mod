package com.example.examplemod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom "power armour" layer definitions for the elite Imperial units. They reuse the vanilla
 * humanoid skeleton (so walk/attack animations work) but bolt on bulky armour parts: oversized
 * pauldrons, a backpack with vents, and per-unit flourishes (Custodes crest, Primarch cape + halo).
 *
 * The extra parts sample flat colour blocks painted on a 128x128 texture by tools/TextureGen.java:
 *   ARMOR block at (64,0), TRIM at (64,32), CLOTH at (64,96). Keep these in sync with the generator.
 */
public final class EliteModelLayers {
    public static final ModelLayerLocation SPACE_MARINE =
            new ModelLayerLocation(new ResourceLocation(ExampleMod.MODID, "space_marine"), "main");
    public static final ModelLayerLocation CUSTODES =
            new ModelLayerLocation(new ResourceLocation(ExampleMod.MODID, "custodes"), "main");
    public static final ModelLayerLocation PRIMARCH =
            new ModelLayerLocation(new ResourceLocation(ExampleMod.MODID, "primarch"), "main");

    private static final int TEX_W = 128;
    private static final int TEX_H = 128;

    private EliteModelLayers() {
    }

    public static LayerDefinition createSpaceMarineLayer() {
        return createLayer(new CubeDeformation(0.8F), false, false, false);
    }

    public static LayerDefinition createCustodesLayer() {
        return createLayer(new CubeDeformation(1.0F), false, false, true);
    }

    public static LayerDefinition createPrimarchLayer() {
        return createLayer(new CubeDeformation(1.2F), true, true, false);
    }

    private static LayerDefinition createLayer(CubeDeformation deform, boolean cape, boolean halo, boolean crest) {
        MeshDefinition mesh = HumanoidModel.createMesh(deform, 0.0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.getChild("head");
        PartDefinition body = root.getChild("body");
        PartDefinition rightArm = root.getChild("right_arm");
        PartDefinition leftArm = root.getChild("left_arm");

        // Oversized shoulder pauldrons (sample the ARMOR block at 64,0).
        rightArm.addOrReplaceChild("right_pauldron", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-4.5F, -3.5F, -3.0F, 6, 5, 6, new CubeDeformation(0.6F)),
                PartPose.ZERO);
        leftArm.addOrReplaceChild("left_pauldron", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-1.5F, -3.5F, -3.0F, 6, 5, 6, new CubeDeformation(0.6F)),
                PartPose.ZERO);

        // Backpack with two exhaust vents (ARMOR block).
        body.addOrReplaceChild("backpack", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-4.0F, 0.5F, 2.0F, 8, 9, 4), PartPose.ZERO);
        body.addOrReplaceChild("vent_left", CubeListBuilder.create()
                .texOffs(64, 0).addBox(1.5F, -2.0F, 3.0F, 2, 3, 2), PartPose.ZERO);
        body.addOrReplaceChild("vent_right", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-3.5F, -2.0F, 3.0F, 2, 3, 2), PartPose.ZERO);

        if (crest) {
            // Tall guardian crest along the top of the helmet (TRIM block).
            head.addOrReplaceChild("crest", CubeListBuilder.create()
                    .texOffs(64, 32).addBox(-1.0F, -13.0F, -2.0F, 2, 6, 8), PartPose.ZERO);
        }

        if (halo) {
            // Iconic halo plate behind the head (TRIM block).
            head.addOrReplaceChild("halo", CubeListBuilder.create()
                    .texOffs(64, 32).addBox(-6.0F, -15.0F, 2.5F, 12, 12, 1), PartPose.ZERO);
        }

        if (cape) {
            // Heavy cape hanging from the shoulders down the back (CLOTH block).
            body.addOrReplaceChild("cape", CubeListBuilder.create()
                    .texOffs(64, 96).addBox(-5.0F, 0.0F, 3.0F, 10, 18, 1),
                    PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.08F, 0.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }
}
