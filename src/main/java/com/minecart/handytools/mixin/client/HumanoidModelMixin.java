package com.minecart.handytools.mixin.client;

import com.minecart.handytools.client.HammerArmPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void handytools$projectArmsToHammerHandle(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callbackInfo
    ) {
        HammerArmPose.applyHandleLineProjection(
                (HumanoidModel<?>)(Object)this,
                entity
        );
    }
}
