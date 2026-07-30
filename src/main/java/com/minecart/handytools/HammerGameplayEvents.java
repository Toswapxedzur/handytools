package com.minecart.handytools;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
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
public final class HammerGameplayEvents {
    private static final Map<Player, LockedPlayerState> LOCKED_PLAYERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private HammerGameplayEvents() {
    }

    @SubscribeEvent
    public static void prioritizeHammerOnBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (HammerItem.isPressing(event.getEntity())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }

        if (event.getItemStack().getItem() instanceof HammerItem) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.TRUE);
        }
    }

    @SubscribeEvent
    public static void preventItemInteractionWhilePressing(
            PlayerInteractEvent.RightClickItem event
    ) {
        if (HammerItem.isPressing(event.getEntity())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventEntityInteractionWhilePressing(
            PlayerInteractEvent.EntityInteract event
    ) {
        if (HammerItem.isPressing(event.getEntity())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventSpecificEntityInteractionWhilePressing(
            PlayerInteractEvent.EntityInteractSpecific event
    ) {
        if (HammerItem.isPressing(event.getEntity())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventMiningWhilePressing(
            PlayerInteractEvent.LeftClickBlock event
    ) {
        if (HammerItem.isPressing(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void preventEntityAttackWhilePressing(AttackEntityEvent event) {
        if (HammerItem.isPressing(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void lockPlayerBeforeTick(PlayerTickEvent.Pre event) {
        applyMovementLock(event.getEntity());
    }

    @SubscribeEvent
    public static void lockPlayerAfterTick(PlayerTickEvent.Post event) {
        applyMovementLock(event.getEntity());
    }

    @SubscribeEvent
    public static void clearLockOnLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOCKED_PLAYERS.remove(event.getEntity());
    }

    private static void applyMovementLock(Player player) {
        if (!HammerItem.isPressing(player)) {
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
        player.yBodyRot = state.yRot();
        player.yBodyRotO = state.yRot();
        player.fallDistance = state.fallDistance();
    }

    private record LockedPlayerState(
            ResourceKey<Level> dimension,
            Vec3 position,
            float yRot,
            float xRot,
            int selectedSlot,
            float fallDistance
    ) {
        private static LockedPlayerState capture(Player player) {
            return new LockedPlayerState(
                    player.level().dimension(),
                    player.position(),
                    player.getYRot(),
                    player.getXRot(),
                    player.getInventory().selected,
                    player.fallDistance
            );
        }
    }
}
