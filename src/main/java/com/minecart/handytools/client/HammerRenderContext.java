package com.minecart.handytools.client;

import com.minecart.handytools.HammerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class HammerRenderContext {
    // Model Y -10 is six pixels beyond the handle end at Y -4. Baked item
    // coordinates are centered on Y 8, placing that pivot 18 pixels below zero.
    static final float PRESS_PIVOT_OFFSET = 18.0F / 16.0F;
    private static final ThreadLocal<LivingEntity> RENDERED_ENTITY =
            new ThreadLocal<>();

    private HammerRenderContext() {
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
            RENDERED_ENTITY.set(entity);
        } else {
            RENDERED_ENTITY.remove();
        }
    }

    public static void end() {
        RENDERED_ENTITY.remove();
    }

    public static void applyThirdPersonTransform(PoseStack poseStack) {
        LivingEntity entity = RENDERED_ENTITY.get();
        if (entity == null) {
            return;
        }

        float partialTick = Minecraft.getInstance()
                .getTimer()
                .getGameTimeDeltaPartialTick(true);
        float cubicProgress =
                HammerItem.getPressProgress(entity, partialTick);
        float swingDegrees =
                HammerItem.getPressStopAngleDegrees(entity) * cubicProgress;

        poseStack.translate(0.0F, -PRESS_PIVOT_OFFSET, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-swingDegrees));
        poseStack.translate(0.0F, PRESS_PIVOT_OFFSET, 0.0F);
    }

    private static boolean isThirdPerson(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext
                == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }
}
