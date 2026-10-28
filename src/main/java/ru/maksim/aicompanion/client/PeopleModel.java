package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
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
import net.minecraft.world.entity.HumanoidArm;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.HumanEntity;

/**
 * The player model with knees and elbows, on the ordinary skin layout: each limb is two boxes (thigh and shin, upper
 * arm and forearm) that overlap a pixel at the joint, so a bent knee has no gap and a straight one looks like vanilla.
 * A hair-thin cap at each hand and foot keeps the skin's own hand-end and sole pixels there. With joints the story's
 * people can really sit: on a chair with their feet down, at a desk with their forearms on it, L crouched on his seat,
 * Near on the floor with one knee up, Mello sprawled on a sofa.
 */
public final class PeopleModel extends PlayerModel<HumanEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(AiCompanion.MODID, "people"), "main");
    private static final float LIMB = 7, JOINT = 6; // a limb box is 7 px, the next starts at 6 (1 px of overlap)
    private final ModelPart rightShin, leftShin, rightForearm, leftForearm, rightPantsShin, leftPantsShin, rightSleeveFore, leftSleeveFore;
    float lean; // radians the whole body leans forward about the hips (the renderer applies it; the legs here make up for it)

    public PeopleModel(ModelPart root) {
        super(root, false);
        rightShin = rightLeg.getChild("shin");
        leftShin = leftLeg.getChild("shin");
        rightForearm = rightArm.getChild("forearm");
        leftForearm = leftArm.getChild("forearm");
        rightPantsShin = rightPants.getChild("shin");
        leftPantsShin = leftPants.getChild("shin");
        rightSleeveFore = rightSleeve.getChild("forearm");
        leftSleeveFore = leftSleeve.getChild("forearm");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition root = mesh.getRoot();
        // legs: thigh 0..7, the knee at 6, shin -1..6, the sole kept by its cap
        leg(root, "right_leg", "right_pants", -1.9F, 0, 16, 0, 32);
        leg(root, "left_leg", "left_pants", 1.9F, 16, 48, 0, 48);
        // arms: upper arm -2..5 below the shoulder, the elbow at 4, forearm -1..6
        arm(root, "right_arm", "right_sleeve", -5, -1, 40, 16, 40, 32);
        arm(root, "left_arm", "left_sleeve", 5, 1, 32, 48, 48, 48);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void leg(PartDefinition root, String name, String pants, float x, int u, int v, int pu, int pv) {
        PartDefinition thigh = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(-2, 0, -2, 4, LIMB, 4),
                PartPose.offset(x, 12, 0));
        thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(u, v + 5).addBox(-2, -1, -2, 4, LIMB, 4)
                .texOffs(u, v).addBox(-2, 5.995F, -2, 4, 0.01F, 4), PartPose.offset(0, JOINT, 0));
        CubeDeformation over = new CubeDeformation(0.25F);
        PartDefinition p = root.addOrReplaceChild(pants, CubeListBuilder.create().texOffs(pu, pv).addBox(-2, 0, -2, 4, LIMB, 4, over),
                PartPose.offset(x, 12, 0));
        p.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(pu, pv + 5).addBox(-2, -1, -2, 4, 5.5F, 4, over), PartPose.offset(0, JOINT, 0));
    }

    /** {@code cx}: the arm's centre line, a pixel off the shoulder's pivot (the boxes are 4 wide around it). */
    private static void arm(PartDefinition root, String name, String sleeve, float x, float cx, int u, int v, int su, int sv) {
        PartDefinition upper = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(cx - 2, -2, -2, 4, LIMB, 4),
                PartPose.offset(x, 2, 0));
        upper.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(u, v + 5).addBox(-2, -1, -2, 4, LIMB, 4)
                .texOffs(u, v).addBox(-2, 5.995F, -2, 4, 0.01F, 4), PartPose.offset(cx, 4, 0));
        CubeDeformation over = new CubeDeformation(0.25F);
        PartDefinition s = root.addOrReplaceChild(sleeve, CubeListBuilder.create().texOffs(su, sv).addBox(cx - 2, -2, -2, 4, LIMB, 4, over),
                PartPose.offset(x, 2, 0));
        s.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(su, sv + 5).addBox(-2, -1, -2, 4, 4.5F, 4, over), PartPose.offset(cx, 4, 0));
    }

    @Override
    public void setupAnim(HumanEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        for (ModelPart p : new ModelPart[]{rightArm, leftArm, rightLeg, leftLeg}) { // one model draws everyone: nothing left over
            set(p, 0, 0, 0);
        }
        head.zRot = 0;
        super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
        lean = 0;
        // walking: the knee folds as the leg swings through, the elbows stay a little bent
        float phase = limbSwing * 0.6662F;
        rightShin.xRot = Math.max(0, Mth.sin(phase)) * 1.1F * limbSwingAmount;
        leftShin.xRot = Math.max(0, -Mth.sin(phase)) * 1.1F * limbSwingAmount;
        rightShin.yRot = leftShin.yRot = rightShin.zRot = leftShin.zRot = 0;
        rightForearm.xRot = leftForearm.xRot = -0.18F;
        rightForearm.yRot = leftForearm.yRot = rightForearm.zRot = leftForearm.zRot = 0;
        HumanEntity.Stance stance = e.stance();
        if (stance == HumanEntity.Stance.STAND && e.role() == HumanEntity.Role.L) {
            hunch(limbSwingAmount);
        } else {
            Poses.apply(this, stance, age, e.getId());
        }
        head.xRot -= lean * (stance == HumanEntity.Stance.PERCH ? 0.6F : 1); // the renderer tips the whole body: the eyes still look ahead
        rightPants.copyFrom(rightLeg);
        leftPants.copyFrom(leftLeg);
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
        rightPantsShin.copyFrom(rightShin);
        leftPantsShin.copyFrom(leftShin);
        rightSleeveFore.copyFrom(rightForearm);
        leftSleeveFore.copyFrom(leftForearm);
        jacket.copyFrom(body);
        hat.copyFrom(head);
    }

    /** L on his feet: stooped, hands in his pockets, no swing of the arms. */
    private void hunch(float walking) {
        lean = 0.22F;
        rightLeg.xRot -= lean; // the legs walk upright under the stoop
        leftLeg.xRot -= lean;
        set(rightArm, 0.12F, 0, 0.1F);
        set(leftArm, 0.12F, 0, -0.1F);
        rightForearm.xRot = leftForearm.xRot = -0.45F;
    }

    static void set(ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    ModelPart shin(boolean right) {
        return right ? rightShin : leftShin;
    }

    ModelPart forearm(boolean right) {
        return right ? rightForearm : leftForearm;
    }

    /** The held item sits in the hand at the end of the forearm, not where a straight arm would end. */
    @Override
    public void translateToHand(HumanoidArm side, PoseStack pose) {
        ModelPart upper = getArm(side), fore = side == HumanoidArm.RIGHT ? rightForearm : leftForearm;
        upper.translateAndRotate(pose);
        fore.translateAndRotate(pose);
        pose.translate(-fore.x / 16.0F, -fore.y / 16.0F, -fore.z / 16.0F); // as if the whole arm turned at the elbow
    }
}
