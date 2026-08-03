package com.minecart.handytools;

import com.minecart.handytools.client.HammerAnimationClientSetup;
import com.minecart.handytools.network.ToolActionNetworking;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(HandyTools.MOD_ID)
public final class HandyTools {
    public static final String MOD_ID = "handytools";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HandyTools(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabContents);
        modEventBus.addListener(ToolActionNetworking::registerPayloads);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            HammerAnimationClientSetup.register(modEventBus);
        }
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            ModItems.HAMMERS.forEach(hammer -> event.accept(hammer.get()));
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
