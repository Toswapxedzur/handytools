package com.minecart.handytools.client;

import com.minecart.handytools.HandyTools;
import com.minecart.handytools.HammerItem;
import com.minecart.handytools.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(
        modid = HandyTools.MOD_ID,
        value = Dist.CLIENT
)
public final class HammerClientExtensions {
    private static final IClientItemExtensions EXTENSIONS =
            new IClientItemExtensions() {
                @Override
                public HumanoidModel.ArmPose getArmPose(
                        LivingEntity entity,
                        InteractionHand hand,
                        ItemStack stack
                ) {
                    if (HammerItem.isPressing(entity)
                            && entity.getUsedItemHand() == hand) {
                        return HammerArmPose.PRESS.getValue();
                    }
                    return null;
                }

                @Override
                public boolean applyForgeHandTransform(
                        PoseStack poseStack,
                        LocalPlayer player,
                        HumanoidArm arm,
                        ItemStack stack,
                        float partialTick,
                        float equipProgress,
                        float swingProgress
                ) {
                    HumanoidArm usedArm =
                            player.getUsedItemHand() == InteractionHand.MAIN_HAND
                                    ? player.getMainArm()
                                    : player.getMainArm().getOpposite();
                    if (!HammerItem.isPressing(player) || arm != usedArm) {
                        return false;
                    }

                    HammerRenderContext.captureFirstPersonBodyTransform(
                            poseStack,
                            player,
                            arm
                    );

                    float direction =
                            arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;

                    poseStack.translate(
                            direction * 0.56F,
                            -0.52F - equipProgress * 0.6F,
                            -0.72F
                    );
                    poseStack.mulPose(
                            Axis.YP.rotationDegrees(direction * 45.0F)
                    );
                    return true;
                }
            };

    private HammerClientExtensions() {
    }

    @SubscribeEvent
    public static void registerClientExtensions(
            RegisterClientExtensionsEvent event
    ) {
        List<Item> hammers = ModItems.HAMMERS.stream()
                .map(holder -> holder.get())
                .toList();
        event.registerItem(EXTENSIONS, hammers.toArray(Item[]::new));
    }
}
