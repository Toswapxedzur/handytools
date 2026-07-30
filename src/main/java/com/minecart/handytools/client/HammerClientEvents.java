package com.minecart.handytools.client;

import com.minecart.handytools.HandyTools;
import com.minecart.handytools.HammerItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(
        modid = HandyTools.MOD_ID,
        value = Dist.CLIENT
)
public final class HammerClientEvents {
    private HammerClientEvents() {
    }

    @SubscribeEvent
    public static void stopMovementInput(MovementInputUpdateEvent event) {
        if (!HammerItem.isPressing(event.getEntity())) {
            return;
        }

        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void stopCameraTurning(CalculatePlayerTurnEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && HammerItem.isPressing(minecraft.player)) {
            event.setMouseSensitivity(-0.2D / 0.6F);
            event.setCinematicCameraEnabled(false);
        }
    }

    @SubscribeEvent
    public static void consumeOtherKeyActions(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !HammerItem.isPressing(minecraft.player)) {
            return;
        }

        for (KeyMapping keyMapping : minecraft.options.keyMappings) {
            if (keyMapping != minecraft.options.keyUse) {
                while (keyMapping.consumeClick()) {
                    // Drain every queued action except release-compatible use.
                }
            }
        }
    }

    @SubscribeEvent
    public static void stopHotbarScrolling(
            InputEvent.MouseScrollingEvent event
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && HammerItem.isPressing(minecraft.player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void stopOtherMouseButtons(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && HammerItem.isPressing(minecraft.player)
                && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && event.getAction() != GLFW.GLFW_RELEASE) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void stopOtherInteractionKeys(
            InputEvent.InteractionKeyMappingTriggered event
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && HammerItem.isPressing(minecraft.player)
                && !event.isUseItem()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void stopScreensOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && !minecraft.player.isDeadOrDying()
                && HammerItem.isPressing(minecraft.player)) {
            event.setCanceled(true);
        }
    }
}
