package com.minecart.handytools.client;

import com.minecart.handytools.HandyTools;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(
        modid = HandyTools.MOD_ID,
        value = Dist.CLIENT
)
public final class HammerModelEvents {
    private static final List<String> HAMMER_IDS = List.of(
            "wooden_hammer",
            "cobblestone_hammer",
            "iron_hammer",
            "gold_hammer",
            "diamond_hammer",
            "netherite_hammer"
    );

    private HammerModelEvents() {
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        HAMMER_IDS.forEach(id -> event.register(flatModel(id)));
    }

    @SubscribeEvent
    public static void installContextSwitchingModels(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();

        for (String id : HAMMER_IDS) {
            ModelResourceLocation itemLocation =
                    ModelResourceLocation.inventory(HandyTools.id(id));
            ModelResourceLocation flatLocation = flatModel(id);
            BakedModel thirdPersonModel = models.get(itemLocation);
            BakedModel flatModel = models.get(flatLocation);

            if (thirdPersonModel == null || flatModel == null) {
                throw new IllegalStateException(
                        "Missing Handy Tools model pair for " + id
                );
            }

            models.put(
                    itemLocation,
                    new ContextSwitchingBakedModel(
                            thirdPersonModel,
                            flatModel
                    )
            );
        }
    }

    private static ModelResourceLocation flatModel(String id) {
        return ModelResourceLocation.standalone(
                HandyTools.id("item/" + id + "_inventory")
        );
    }
}
