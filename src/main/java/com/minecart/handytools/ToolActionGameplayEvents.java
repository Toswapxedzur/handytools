package com.minecart.handytools;

import com.minecart.handytools.network.ToolActionNetworking;
import com.minecart.handytools.toolaction.PhasedToolAction;
import com.minecart.handytools.toolaction.ToolActionManager;
import com.minecart.handytools.toolaction.ToolActionState;
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
    // Partitioned by logical side for the same reason as ToolActionManager:
    // Entity#equals/#hashCode key on the entity id, so the singleplayer client
    // and server players would otherwise share (and race) one lock record.
    private static final Map<Player, LockedPlayerState> CLIENT_LOCKED_PLAYERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, LockedPlayerState> SERVER_LOCKED_PLAYERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ToolActionGameplayEvents() {
    }

    private static Map<Player, LockedPlayerState> lockedPlayers(Player player) {
        return player.level().isClientSide
                ? CLIENT_LOCKED_PLAYERS
                : SERVER_LOCKED_PLAYERS;
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
        lockedPlayers(event.getEntity()).remove(event.getEntity());
        ToolActionManager.cancel(event.getEntity());
    }

    @SubscribeEvent
    public static void clearActionOnRespawn(PlayerEvent.Clone event) {
        // The pre-respawn player entity is discarded; drop its stale action and
        // lock so a reused entity id cannot inherit them on the new player.
        Player original = event.getOriginal();
        lockedPlayers(original).remove(original);
        ToolActionManager.cancel(original);
    }

    @SubscribeEvent
    public static void syncActionWhenTrackingStarts(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer receivingPlayer
                && event.getTarget() instanceof Player actingPlayer) {
            ToolActionManager.syncStateTo(receivingPlayer, actingPlayer);
        }
    }

    @SubscribeEvent
    public static void clearSyncedActionWhenTrackingStops(PlayerEvent.StopTracking event) {
        // Tell the observer to forget the acting player so no stale phase
        // snapshot lingers (its later inactive packet would never reach an
        // observer that is no longer tracking it).
        if (event.getEntity() instanceof ServerPlayer receivingPlayer
                && event.getTarget() instanceof Player actingPlayer) {
            ToolActionNetworking.sendInactiveTo(receivingPlayer, actingPlayer);
        }
    }

    private static void applyMovementLock(Player player) {
        Map<Player, LockedPlayerState> lockedPlayers = lockedPlayers(player);
        if (!ToolActionManager.isActive(player)) {
            lockedPlayers.remove(player);
            return;
        }

        LockedPlayerState state = lockedPlayers.computeIfAbsent(
                player,
                LockedPlayerState::capture
        );

        if (!state.dimension().equals(player.level().dimension())) {
            lockedPlayers.put(player, LockedPlayerState.capture(player));
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
        player.setYHeadRot(state.headYRot());
        player.yHeadRotO = state.headYRot();
        state.bodyFacing().apply(
                player,
                ToolActionManager.getState(player).orElse(null)
        );
        player.fallDistance = state.fallDistance();
    }

    private record LockedPlayerState(
            ResourceKey<Level> dimension,
            Vec3 position,
            float yRot,
            float xRot,
            float headYRot,
            BodyFacing bodyFacing,
            int selectedSlot,
            float fallDistance
    ) {
        private static LockedPlayerState capture(Player player) {
            float targetYRot = ToolActionManager.getState(player)
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
                    player.yHeadRot,
                    new BodyFacing(player.yBodyRot, targetYRot),
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

    private static final class BodyFacing {
        private final float startYRot;
        private final float targetYRot;
        private float lastYRot;
        private float releaseFromYRot = Float.NaN;

        private BodyFacing(float startYRot, float targetYRot) {
            this.startYRot = startYRot;
            this.targetYRot = targetYRot;
            this.lastYRot = startYRot;
        }

        private void apply(Player player, ToolActionState state) {
            float nextYRot = rotationFor(state);
            player.yBodyRotO = lastYRot;
            player.yBodyRot = nextYRot;
            lastYRot = nextYRot;
        }

        private float rotationFor(ToolActionState state) {
            if (state == null) {
                return lastYRot;
            }
            float progress = state.cubicEaseInOutProgress(0.0F);
            return switch (state.phase()) {
                case PREPARATION -> Mth.rotLerp(
                        progress,
                        startYRot,
                        targetYRot
                );
                case OPERATION_RAISE, OPERATION_DESCEND -> targetYRot;
                case RELEASE -> {
                    // Ease back from the body's ACTUAL yaw when release began,
                    // not from targetYRot: a pre-apex (hybrid) release can enter
                    // RELEASE mid-turn, and anchoring at targetYRot would snap
                    // the body to the block before easing back.
                    if (Float.isNaN(releaseFromYRot)) {
                        releaseFromYRot = lastYRot;
                    }
                    yield Mth.rotLerp(progress, releaseFromYRot, startYRot);
                }
            };
        }
    }
}
