package com.minecart.handytools;

import com.minecart.handytools.toolaction.PhasedToolAction;
import com.minecart.handytools.toolaction.ToolActionManager;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HandyTools.MOD_ID)
public final class ToolActionGameplayEvents {
    private static final Map<Player, LockedPlayerState> LOCKED_PLAYERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ToolActionGameplayEvents() {
    }

    @SubscribeEvent
    public static void prioritizeToolActionOnBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (ToolActionManager.isActive(event.getEntity())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }

        if (event.getItemStack().getItem() instanceof PhasedToolAction action
                && action.findActionTarget(
                        event.getLevel(),
                        event.getEntity(),
                        event.getPos()
                ).isPresent()) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.TRUE);
        }
    }

    @SubscribeEvent
    public static void preventItemInteractionWhileActing(
            PlayerInteractEvent.RightClickItem event
    ) {
        if (ToolActionManager.isActive(event.getEntity())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventEntityInteractionWhileActing(
            PlayerInteractEvent.EntityInteract event
    ) {
        if (ToolActionManager.isActive(event.getEntity())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventSpecificEntityInteractionWhileActing(
            PlayerInteractEvent.EntityInteractSpecific event
    ) {
        if (ToolActionManager.isActive(event.getEntity())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventMiningWhileActing(
            PlayerInteractEvent.LeftClickBlock event
    ) {
        if (ToolActionManager.isActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventEntityAttackWhileActing(
            AttackEntityEvent event
    ) {
        if (ToolActionManager.isActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void lockPlayerBeforeTick(PlayerTickEvent.Pre event) {
        applyMovementLock(event.getEntity());
    }

    @SubscribeEvent
    public static void advanceAndLockPlayerAfterTick(PlayerTickEvent.Post event) {
        ToolActionManager.tick(event.getEntity());
        applyMovementLock(event.getEntity());
    }

    @SubscribeEvent
    public static void clearActionOnLogout(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        LOCKED_PLAYERS.remove(event.getEntity());
        ToolActionManager.cancel(event.getEntity());
    }

    @SubscribeEvent
    public static void syncActionWhenTrackingStarts(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer receivingPlayer
                && event.getTarget() instanceof Player actingPlayer) {
            ToolActionManager.syncStateTo(receivingPlayer, actingPlayer);
        }
    }

    private static void applyMovementLock(Player player) {
        if (!ToolActionManager.isActive(player)) {
            LOCKED_PLAYERS.remove(player);
            return;
        }

        LockedPlayerState state = LOCKED_PLAYERS.computeIfAbsent(
                player,
                LockedPlayerState::capture
        );

        if (!state.dimension().equals(player.level().dimension())) {
            LOCKED_PLAYERS.put(player, LockedPlayerState.capture(player));
            return;
        }

        if (!player.isPassenger()) {
            player.setPos(state.position());
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.getInventory().selected = state.selectedSlot();

        player.setYRot(state.yRot());
        player.setXRot(state.xRot());
        player.yRotO = state.yRot();
        player.xRotO = state.xRot();
        player.setYHeadRot(state.yRot());
        player.yHeadRotO = state.yRot();
        player.yBodyRot = state.bodyYRot();
        player.yBodyRotO = state.bodyYRot();
        player.fallDistance = state.fallDistance();
    }

    private record LockedPlayerState(
            ResourceKey<Level> dimension,
            Vec3 position,
            float yRot,
            float xRot,
            float bodyYRot,
            int selectedSlot,
            float fallDistance
    ) {
        private static LockedPlayerState capture(Player player) {
            float bodyYRot = ToolActionManager.getState(player)
                    .map(state -> yawToward(
                            player.position(),
                            state.target().blockCenter()
                    ))
                    .orElse(player.yBodyRot);
            return new LockedPlayerState(
                    player.level().dimension(),
                    player.position(),
                    player.getYRot(),
                    player.getXRot(),
                    bodyYRot,
                    player.getInventory().selected,
                    player.fallDistance
            );
        }

        private static float yawToward(Vec3 origin, Vec3 target) {
            Vec3 offset = target.subtract(origin);
            return (float) (Mth.atan2(offset.z, offset.x)
                    * Mth.RAD_TO_DEG) - 90.0F;
        }
    }
}
