package com.minecart.handytools.client;

import com.minecart.handytools.HammerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HammerArmPose {
    private static final float PLAYER_MODEL_PIVOT_Y = 0.016F;
    private static final float ARM_JOINT_TO_HAND_LENGTH = 10.0F;
    private static final float HANDLE_START_DISTANCE = 6.0F;
    private static final float HANDLE_END_DISTANCE = 18.0F;

    public static final EnumProxy<HumanoidModel.ArmPose> PRESS =
            new EnumProxy<>(
                    HumanoidModel.ArmPose.class,
                    true,
                    (IArmPoseTransformer) HammerArmPose::applyPressPose
            );

    private HammerArmPose() {
    }

    public static void applyHandleLineProjection(
            HumanoidModel<?> model,
            LivingEntity entity
    ) {
        if (!HammerItem.isPressing(entity)
                || HammerItem.getPressTarget(entity).isEmpty()) {
            return;
        }

        float partialTick = Minecraft.getInstance()
                .getTimer()
                .getGameTimeDeltaPartialTick(true);
        float progress = HammerItem.getPressProgress(entity, partialTick);
        float targetPitch = HammerItem.getPressContactPitchDegrees(entity);
        float pitch = Mth.lerp(
                progress,
                HammerItem.PRESS_START_PITCH_DEGREES,
                targetPitch
        );
        Vector3f handleAxis = getHandleAxis(entity, partialTick, pitch);
        Vector3f hammerPivot = new Vector3f(
                0.0F,
                PLAYER_MODEL_PIVOT_Y,
                0.0F
        );

        projectArmToHandleLine(model.rightArm, hammerPivot, handleAxis);
        projectArmToHandleLine(model.leftArm, hammerPivot, handleAxis);
    }

    private static void applyPressPose(
            HumanoidModel<?> model,
            LivingEntity entity,
            HumanoidArm ignoredArm
    ) {
        float partialTick = Minecraft.getInstance()
                .getTimer()
                .getGameTimeDeltaPartialTick(true);
        float progress = HammerItem.getPressProgress(entity, partialTick);
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

    private static Vector3f getHandleAxis(
            LivingEntity entity,
            float partialTick,
            float pitch
    ) {
        Vector3f modelUp = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f modelForward = new Vector3f(0.0F, 0.0F, -1.0F);
        float renderedBodyYaw = Mth.rotLerp(
                partialTick,
                entity.yBodyRotO,
                entity.yBodyRot
        );
        float targetYaw = HammerItem.getTargetFacingYaw(entity);
        modelForward.rotateAxis(
                (renderedBodyYaw - targetYaw) * Mth.DEG_TO_RAD,
                modelUp.x(),
                modelUp.y(),
                modelUp.z()
        );

        float angleFromVertical = -pitch * Mth.DEG_TO_RAD;
        return modelUp.mul(Mth.cos(angleFromVertical))
                .fma(Mth.sin(angleFromVertical), modelForward)
                .normalize();
    }

    private static void projectArmToHandleLine(
            ModelPart arm,
            Vector3f hammerPivot,
            Vector3f handleAxis
    ) {
        Vector3f shoulder = new Vector3f(arm.x, arm.y, arm.z);
        Vector3f shoulderFromPivot = new Vector3f(shoulder)
                .sub(hammerPivot);
        float closestLineDistance = handleAxis.dot(shoulderFromPivot);
        float perpendicularDistanceSquared = Math.max(
                shoulderFromPivot.lengthSquared()
                        - closestLineDistance * closestLineDistance,
                0.0F
        );
        float reachSquared = ARM_JOINT_TO_HAND_LENGTH
                * ARM_JOINT_TO_HAND_LENGTH;
        float gripDistance;

        if (perpendicularDistanceSquared <= reachSquared) {
            float intersectionOffset = Mth.sqrt(
                    reachSquared - perpendicularDistanceSquared
            );
            float firstIntersection = closestLineDistance
                    - intersectionOffset;
            float secondIntersection = closestLineDistance
                    + intersectionOffset;
            float referenceDistance = getCurrentHandProjection(
                    arm,
                    shoulder,
                    hammerPivot,
                    handleAxis
            );
            gripDistance = selectHandleIntersection(
                    firstIntersection,
                    secondIntersection,
                    referenceDistance
            );
        } else {
            gripDistance = Mth.clamp(
                    closestLineDistance,
                    HANDLE_START_DISTANCE,
                    HANDLE_END_DISTANCE
            );
        }

        Vector3f grip = new Vector3f(handleAxis)
                .mul(gripDistance)
                .add(hammerPivot);
        Vector3f armDirection = grip.sub(shoulder).normalize();
        arm.xRot = (float) Math.asin(
                Mth.clamp(armDirection.z(), -1.0F, 1.0F)
        );
        arm.yRot = 0.0F;
        arm.zRot = (float) Mth.atan2(
                -armDirection.x(),
                armDirection.y()
        );
    }

    private static float getCurrentHandProjection(
            ModelPart arm,
            Vector3f shoulder,
            Vector3f hammerPivot,
            Vector3f handleAxis
    ) {
        Vector3f currentArmDirection = new Quaternionf()
                .rotationZYX(arm.zRot, arm.yRot, arm.xRot)
                .transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f currentHand = currentArmDirection
                .mul(ARM_JOINT_TO_HAND_LENGTH)
                .add(shoulder);
        return handleAxis.dot(currentHand.sub(hammerPivot));
    }

    private static float selectHandleIntersection(
            float firstIntersection,
            float secondIntersection,
            float referenceDistance
    ) {
        boolean firstOnHandle = isOnHandle(firstIntersection);
        boolean secondOnHandle = isOnHandle(secondIntersection);
        if (firstOnHandle && secondOnHandle) {
            return Math.abs(firstIntersection - referenceDistance)
                    <= Math.abs(secondIntersection - referenceDistance)
                    ? firstIntersection
                    : secondIntersection;
        }
        if (firstOnHandle) {
            return firstIntersection;
        }
        if (secondOnHandle) {
            return secondIntersection;
        }
        return Mth.clamp(
                closestToHandle(firstIntersection, secondIntersection),
                HANDLE_START_DISTANCE,
                HANDLE_END_DISTANCE
        );
    }

    private static boolean isOnHandle(float distance) {
        return distance >= HANDLE_START_DISTANCE
                && distance <= HANDLE_END_DISTANCE;
    }

    private static float closestToHandle(float first, float second) {
        float handleMiddle = (HANDLE_START_DISTANCE + HANDLE_END_DISTANCE)
                * 0.5F;
        return Math.abs(first - handleMiddle) <= Math.abs(second - handleMiddle)
                ? first
                : second;
    }
}
