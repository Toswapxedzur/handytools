package com.minecart.handytools.client;

import com.minecart.handytools.HammerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HammerRenderContext {
    // Model Y -10 is six pixels beyond the handle end at Y -4. Baked item
    // coordinates are centered on Y 8, placing that pivot 18 pixels below zero.
    static final float PRESS_PIVOT_OFFSET = 18.0F / 16.0F;
    private static final float PLAYER_MODEL_FLOOR_Y = 1.501F;
    private static final float BODY_PIVOT_HEIGHT = 1.5F;
    private static final ThreadLocal<Matrix4f> THIRD_PERSON_BODY_TRANSFORM =
            new ThreadLocal<>();
    private static final ThreadLocal<RenderState> RENDER_STATE =
            new ThreadLocal<>();

    private HammerRenderContext() {
    }

    public static void captureThirdPersonBodyTransform(PoseStack poseStack) {
        THIRD_PERSON_BODY_TRANSFORM.set(
                new Matrix4f(poseStack.last().pose())
        );
    }

    public static void captureFirstPersonBodyTransform(
            PoseStack poseStack,
            LocalPlayer player,
            HumanoidArm arm
    ) {
        Matrix4f viewTransform = new Matrix4f(poseStack.last().pose());
        Vector3f bodyPivot = viewTransform.transformPosition(
                new Vector3f(
                        0.0F,
                        BODY_PIVOT_HEIGHT - player.getEyeHeight(),
                        0.0F
                )
        );
        ItemDisplayContext displayContext = arm == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        RENDER_STATE.set(
                new RenderState(player, displayContext, bodyPivot)
        );
    }

    public static void begin(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext
    ) {
        if (isThirdPerson(displayContext)
                && stack.getItem() instanceof HammerItem
                && HammerItem.isPressing(entity)
                && HammerItem.getPressTarget(entity).isPresent()) {
            Matrix4f bodyTransform = THIRD_PERSON_BODY_TRANSFORM.get();
            if (bodyTransform != null) {
                RENDER_STATE.set(
                        new RenderState(
                                entity,
                                displayContext,
                                getThirdPersonBodyPivot(bodyTransform)
                        )
                );
            } else {
                RENDER_STATE.remove();
            }
        } else {
            RENDER_STATE.remove();
        }
        THIRD_PERSON_BODY_TRANSFORM.remove();
    }

    public static void end() {
        RENDER_STATE.remove();
    }

    public static void applyModelTransform(
            PoseStack poseStack,
            ItemDisplayContext displayContext
    ) {
        if (!isThirdPerson(displayContext) && !displayContext.firstPerson()) {
            return;
        }

        RenderState renderState = RENDER_STATE.get();
        if (renderState == null
                || renderState.displayContext() != displayContext) {
            return;
        }
        if (displayContext.firstPerson()) {
            RENDER_STATE.remove();
        }

        float partialTick = Minecraft.getInstance()
                .getTimer()
                .getGameTimeDeltaPartialTick(true);
        float cubicProgress =
                HammerItem.getPressProgress(renderState.entity(), partialTick);
        float swingDegrees =
                HammerItem.getPressStopAngleDegrees(renderState.entity())
                        * cubicProgress;
        float direction = isRightHand(displayContext) ? 1.0F : -1.0F;

        applyBodyAnchoredRotation(
                poseStack,
                renderState.bodyPivot(),
                direction * swingDegrees
        );
    }

    private static Vector3f getThirdPersonBodyPivot(Matrix4f bodyTransform) {
        Vector3f floorCenter = bodyTransform.transformPosition(
                new Vector3f(0.0F, PLAYER_MODEL_FLOOR_Y, 0.0F)
        );
        Vector3f worldUp = bodyTransform.transformDirection(
                new Vector3f(0.0F, -1.0F, 0.0F)
        ).normalize();
        return floorCenter.fma(BODY_PIVOT_HEIGHT, worldUp);
    }

    private static void applyBodyAnchoredRotation(
            PoseStack poseStack,
            Vector3f bodyPivot,
            float swingDegrees
    ) {
        Matrix4f itemTransform = new Matrix4f(poseStack.last().pose());
        Vector3f modelPivot = new Vector3f(
                0.0F,
                -PRESS_PIVOT_OFFSET,
                0.0F
        );
        Vector3f renderedPivot = itemTransform.transformPosition(
                new Vector3f(modelPivot)
        );
        Vector3f bodyOffset = new Vector3f(bodyPivot).sub(renderedPivot);
        Vector3f rotationAxis = itemTransform.transformDirection(
                new Vector3f(0.0F, 0.0F, 1.0F)
        ).normalize();

        Quaternionf rotation = new Quaternionf().rotateAxis(
                (float) Math.toRadians(swingDegrees),
                rotationAxis
        );
        Matrix4f anchoredWorldTransform = new Matrix4f()
                .translation(bodyPivot)
                .rotate(rotation)
                .translate(
                        -bodyPivot.x(),
                        -bodyPivot.y(),
                        -bodyPivot.z()
                )
                .translate(bodyOffset);
        Matrix4f localCorrection = new Matrix4f(itemTransform)
                .invert()
                .mul(anchoredWorldTransform)
                .mul(itemTransform);
        poseStack.mulPose(localCorrection);
    }

    private static boolean isRightHand(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext
                == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private static boolean isThirdPerson(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext
                == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private record RenderState(
            LivingEntity entity,
            ItemDisplayContext displayContext,
            Vector3f bodyPivot
    ) {
    }
}
