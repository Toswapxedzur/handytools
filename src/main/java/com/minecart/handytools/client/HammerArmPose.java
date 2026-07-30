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
        float targetPitch = getTargetPitch(entity);
        float armPitch = Mth.lerp(progress, -2.6F, targetPitch);
        float inwardTilt = Mth.lerp(progress, 0.2F, 0.46F);
        float handleYaw = Mth.lerp(progress, 0.08F, 0.2F);

        poseArm(model.rightArm, armPitch, -inwardTilt, -handleYaw);
        poseArm(model.leftArm, armPitch, inwardTilt, handleYaw);
    }

    private static float getTargetPitch(LivingEntity entity) {
        return HammerItem.getPressTarget(entity)
                .map(target -> {
                    double horizontal = target.contactPoint()
                            .multiply(1.0D, 0.0D, 1.0D)
                            .distanceTo(
                                    entity.position()
                                            .multiply(1.0D, 0.0D, 1.0D)
                            );
                    double shoulderAboveTarget =
                            entity.getY() + 1.45D - target.contactPoint().y;
                    float pitch = (float) -Math.atan2(
                            horizontal,
                            Math.max(shoulderAboveTarget, 0.05D)
                    );
                    return Mth.clamp(pitch, -1.7F, -0.45F);
                })
                .orElse(-1.05F);
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
