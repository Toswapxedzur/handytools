package com.minecart.handytools.toolaction;

import com.minecart.handytools.network.ToolActionNetworking;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Owns the shared phased-action state machine for all compatible tools. */
public final class ToolActionManager {
    private static final Map<Player, ActiveAction> ACTIVE_ACTIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ToolActionManager() {
    }

    public static boolean start(
            Player player,
            InteractionHand hand,
            PhasedToolAction action,
            ToolActionTarget target
    ) {
        synchronized (ACTIVE_ACTIONS) {
            if (ACTIVE_ACTIONS.containsKey(player)) {
                return false;
            }

            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || stack.getItem() != action) {
                return false;
            }

            ActiveAction active = new ActiveAction(
                    action,
                    stack.getItem(),
                    player.level().dimension(),
                    hand,
                    target,
                    action.actionDurations(stack)
            );
            ACTIVE_ACTIONS.put(player, active);
            notifyPhaseStarted(player, active);
            return true;
        }
    }

    public static void tick(Player player) {
        synchronized (ACTIVE_ACTIONS) {
            ActiveAction active = ACTIVE_ACTIONS.get(player);
            if (active == null) {
                return;
            }

            if (player.isRemoved()
                    || !player.level().dimension().equals(active.dimension)
                    || !hasActionItem(player, active)) {
                ACTIVE_ACTIONS.remove(player);
                if (!player.level().isClientSide) {
                    ToolActionNetworking.sendInactive(player);
                }
                return;
            }

            switch (active.timeline.tick()) {
                case CONTINUE -> {
                }
                case PHASE_CHANGED -> notifyPhaseStarted(player, active);
                case IMPACT -> {
                    if (!player.level().isClientSide) {
                        active.action.onServerImpact(
                                context(player, active)
                        );
                    }
                    notifyPhaseStarted(player, active);
                }
                case FINISHED -> finish(player, active);
            }
        }
    }

    public static void requestRelease(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return;
        }

        synchronized (ACTIVE_ACTIONS) {
            ActiveAction active = ACTIVE_ACTIONS.get(player);
            if (active != null) {
                requestRelease(player, active);
            }
        }
    }

    public static boolean isActive(LivingEntity entity) {
        return entity instanceof Player player
                && ACTIVE_ACTIONS.containsKey(player);
    }

    public static Optional<ToolActionState> getState(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return Optional.empty();
        }

        synchronized (ACTIVE_ACTIONS) {
            ActiveAction active = ACTIVE_ACTIONS.get(player);
            return active == null
                    ? Optional.empty()
                    : Optional.of(active.snapshot());
        }
    }

    public static void cancel(LivingEntity entity) {
        if (entity instanceof Player player) {
            synchronized (ACTIVE_ACTIONS) {
                ActiveAction removed = ACTIVE_ACTIONS.remove(player);
                if (removed != null && !player.level().isClientSide
                        && !player.isRemoved()) {
                    ToolActionNetworking.sendInactive(player);
                }
            }
        }
    }

    public static void syncStateTo(
            net.minecraft.server.level.ServerPlayer receivingPlayer,
            Player actingPlayer
    ) {
        Optional<ToolActionState> state = getState(actingPlayer);
        if (state.isPresent()) {
            ToolActionNetworking.sendStateTo(
                    receivingPlayer,
                    actingPlayer,
                    state.get()
            );
        } else {
            ToolActionNetworking.sendInactiveTo(
                    receivingPlayer,
                    actingPlayer
            );
        }
    }

    private static void requestRelease(Player player, ActiveAction active) {
        // Reach contact first so release always joins at a zero-velocity pose.
        active.timeline.requestRelease();
    }

    private static void notifyPhaseStarted(Player player, ActiveAction active) {
        if (!player.level().isClientSide) {
            active.action.onServerPhaseStarted(
                    context(player, active),
                    active.timeline.phase()
            );
            ToolActionNetworking.sendState(player, active.snapshot());
        }
    }

    private static void finish(Player player, ActiveAction active) {
        ACTIVE_ACTIONS.remove(player);
        if (!player.level().isClientSide) {
            active.action.onServerActionFinished(context(player, active));
            ToolActionNetworking.sendInactive(player);
        }
    }

    private static boolean hasActionItem(Player player, ActiveAction active) {
        return player.getItemInHand(active.hand).getItem() == active.item;
    }

    private static ToolActionContext context(
            Player player,
            ActiveAction active
    ) {
        return new ToolActionContext(
                player,
                active.hand,
                player.getItemInHand(active.hand),
                active.target,
                active.timeline.completedCycles()
        );
    }

    private static final class ActiveAction {
        private final PhasedToolAction action;
        private final Item item;
        private final ResourceKey<Level> dimension;
        private final InteractionHand hand;
        private final ToolActionTarget target;
        private final ToolActionTimeline timeline;

        private ActiveAction(
                PhasedToolAction action,
                Item item,
                ResourceKey<Level> dimension,
                InteractionHand hand,
                ToolActionTarget target,
                ToolActionDurations durations
        ) {
            this.action = action;
            this.item = item;
            this.dimension = dimension;
            this.hand = hand;
            this.target = target;
            this.timeline = new ToolActionTimeline(durations);
        }

        private ToolActionState snapshot() {
            return new ToolActionState(
                    timeline.phase(),
                    timeline.elapsedTicks(),
                    timeline.duration(),
                    timeline.completedCycles(),
                    timeline.releaseRequested(),
                    hand,
                    target
            );
        }
    }
}
