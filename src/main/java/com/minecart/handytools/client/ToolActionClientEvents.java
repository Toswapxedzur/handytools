package com.minecart.handytools.client;

import com.minecart.handytools.HammerItem;
import com.minecart.handytools.HandyTools;
import com.minecart.handytools.network.SyncedToolActionStates;
import com.minecart.handytools.toolaction.ToolActionManager;
import com.minecart.handytools.toolaction.ToolActionState;
import java.util.Optional;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(
        modid = HandyTools.MOD_ID,
        value = Dist.CLIENT
)
public final class ToolActionClientEvents {
    // POV-follows-head: only when First-person Model renders the third-person
    // body in first person does dragging the camera with the head read right;
    // vanilla floating hands must stay level.
    private static final boolean FIRST_PERSON_MODEL_LOADED =
            ModList.get().isLoaded("firstperson");
    private static final float MAX_CAM_PITCH_DEG = 16.0F; // hard safety clamp
    private static final float CONTACT_PITCH = 6.0F;      // look down into the strike
    private static final float OVERHEAD_PITCH = -10.0F;   // look up on the wind-up
    private static float smoothedCamPitch;

    private ToolActionClientEvents() {
    }

    /**
     * Adds a bounded, additive first-person camera pitch that follows the
     * hammer's head/body drag, so the view is yanked with the swing. Render
     * only (never writes back to the entity), so it cannot desync or fight the
     * movement/camera lock; only active with First-person Model installed.
     */
    @SubscribeEvent
    public static void dragCameraWithHead(ViewportEvent.ComputeCameraAngles event) {
        if (!FIRST_PERSON_MODEL_LOADED) {
            return;
        }
        float target = eligibleTargetPitch((float) event.getPartialTick());
        smoothedCamPitch = approachPitch(smoothedCamPitch, target);
        if (target == 0.0F && Math.abs(smoothedCamPitch) < 0.01F) {
            smoothedCamPitch = 0.0F;
            return; // settled and inactive: leave the camera untouched
        }
        float offset = Mth.clamp(smoothedCamPitch, -MAX_CAM_PITCH_DEG, MAX_CAM_PITCH_DEG);
        // Clamp the COMPOSED pitch: the player often starts a strike looking
        // steeply down at the block, so base + offset must not cross vertical.
        event.setPitch(Mth.clamp(event.getPitch() + offset, -90.0F, 90.0F));
    }

    private static float eligibleTargetPitch(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.getCameraEntity() != minecraft.player
                || minecraft.options.getCameraType() != CameraType.FIRST_PERSON
                || !ToolActionManager.isActive(minecraft.player)) {
            return 0.0F;
        }
        Optional<ToolActionState> state = ToolActionManager.getState(minecraft.player);
        if (state.isEmpty()
                || !(minecraft.player.getItemInHand(state.get().hand()).getItem()
                        instanceof HammerItem)) {
            return 0.0F;
        }
        return targetPitch(state.get(), partialTick);
    }

    private static float targetPitch(ToolActionState state, float partialTick) {
        float progress = state.cubicEaseInOutProgress(partialTick);
        return switch (state.phase()) {
            case PREPARATION -> Mth.lerp(progress, 0.0F, CONTACT_PITCH);
            case OPERATION_RAISE -> Mth.lerp(progress, CONTACT_PITCH, OVERHEAD_PITCH);
            case OPERATION_DESCEND -> Mth.lerp(progress, OVERHEAD_PITCH, CONTACT_PITCH);
            // Ease to level from wherever the view actually is (the smoother
            // carries it): a pre-apex release can enter from the reared-back
            // pitch, so a hardcoded CONTACT_PITCH start would dip it down first.
            case RELEASE -> 0.0F;
        };
    }

    private static float approachPitch(float current, float target) {
        float deltaTicks = (float) Minecraft.getInstance().getTimer()
                .getRealtimeDeltaTicks();
        float alpha = Mth.clamp(deltaTicks / 1.5F, 0.0F, 1.0F);
        return Mth.lerp(alpha, current, target);
    }

    @SubscribeEvent
    public static void stopMovementInput(MovementInputUpdateEvent event) {
        if (!ToolActionManager.isActive(event.getEntity())) {
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
                && ToolActionManager.isActive(minecraft.player)) {
            event.setMouseSensitivity(-0.2D / 0.6F);
            event.setCinematicCameraEnabled(false);
        }
    }

    @SubscribeEvent
    public static void consumeOtherKeyActions(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !ToolActionManager.isActive(minecraft.player)) {
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
                && ToolActionManager.isActive(minecraft.player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void stopOtherMouseButtons(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && ToolActionManager.isActive(minecraft.player)
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
                && ToolActionManager.isActive(minecraft.player)
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
                && ToolActionManager.isActive(minecraft.player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void clearSyncedActionsOnLogout(
            ClientPlayerNetworkEvent.LoggingOut event
    ) {
        SyncedToolActionStates.clear();
        smoothedCamPitch = 0.0F;
    }
}
