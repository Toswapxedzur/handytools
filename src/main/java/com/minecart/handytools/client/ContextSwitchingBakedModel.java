package com.minecart.handytools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

final class ContextSwitchingBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel flatModel;

    ContextSwitchingBakedModel(BakedModel thirdPersonModel, BakedModel flatModel) {
        super(thirdPersonModel);
        this.flatModel = flatModel;
    }

    @Override
    public BakedModel applyTransform(
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            boolean leftHand
    ) {
        boolean isThirdPerson =
                displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                        || displayContext
                        == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        BakedModel selected = isThirdPerson ? originalModel : flatModel;
        BakedModel transformed = selected.applyTransform(
                displayContext,
                poseStack,
                leftHand
        );
        if (isThirdPerson) {
            HammerRenderContext.applyThirdPersonTransform(poseStack);
        }
        return transformed;
    }
}
