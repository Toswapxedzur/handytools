package com.minecart.handytools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

final class ContextSwitchingBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel guiModel;

    ContextSwitchingBakedModel(BakedModel worldModel, BakedModel guiModel) {
        super(worldModel);
        this.guiModel = guiModel;
    }

    @Override
    public BakedModel applyTransform(
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            boolean leftHand
    ) {
        BakedModel selected =
                displayContext == ItemDisplayContext.GUI ? guiModel : originalModel;
        return selected.applyTransform(displayContext, poseStack, leftHand);
    }
}
