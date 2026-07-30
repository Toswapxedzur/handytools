package com.minecart.handytools.client;

import com.minecart.handytools.HammerItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

public final class HammerArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> PRESS =
            new EnumProxy<>(
                    HumanoidModel.ArmPose.class,
                    true,
                    (IArmPoseTransformer) HammerArmPose::applyPressPose
            );

    private HammerArmPose() {
    }

    private static void applyPressPose(
            HumanoidModel<?> model,
            LivingEntity entity,
            HumanoidArm ignoredArm
    ) {
        float progress = HammerItem.getPressProgress(entity, 0.0F);
        float armPitch = Mth.lerp(progress, -2.55F, -1.05F);
        float inwardTilt = Mth.lerp(progress, 0.24F, 0.38F);

        poseArm(model.rightArm, armPitch, -inwardTilt, -0.12F);
        poseArm(model.leftArm, armPitch, inwardTilt, 0.12F);
    }

    private static void poseArm(
            ModelPart arm,
            float xRot,
            float zRot,
            float yRot
    ) {
        arm.xRot = xRot;
        arm.yRot = yRot;
        arm.zRot = zRot;
    }
}
