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
    // Minecraft's Entity#equals and Entity#hashCode compare the entity id, not
    // object identity. In singleplayer (and a LAN host) the client LocalPlayer
    // and the integrated-server ServerPlayer share one id inside one JVM, so
    // they are equal keys. State is therefore partitioned by logical side; a
    // single shared map would have one action ticked and mutated from both the
    // client and server thread (double-speed timeline, raced fields).
    private static final Map<Player, ActiveAction> CLIENT_ACTIONS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, ActiveAction> SERVER_ACTIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ToolActionManager() {
    }

    private static Map<Player, ActiveAction> actions(Player player) {
        return player.level().isClientSide ? CLIENT_ACTIONS : SERVER_ACTIONS;
    }

    public static boolean start(
            Player player,
            InteractionHand hand,
            PhasedToolAction action,
            ToolActionTarget target
    ) {
        Map<Player, ActiveAction> actions = actions(player);
        synchronized (actions) {
            if (actions.containsKey(player)) {
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
            actions.put(player, active);
            notifyPhaseStarted(player, active);
            return true;
        }
    }

    public static void tick(Player player) {
        Map<Player, ActiveAction> actions = actions(player);
        synchronized (actions) {
            ActiveAction active = actions.get(player);
            if (active == null) {
                return;
            }

            if (player.isRemoved()
                    || player.isDeadOrDying()
                    || !player.level().dimension().equals(active.dimension)
                    || !hasActionItem(player, active)) {
                actions.remove(player);
                endServerSide(player, active);
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

        Map<Player, ActiveAction> actions = actions(player);
        synchronized (actions) {
            ActiveAction active = actions.get(player);
            if (active != null) {
                requestRelease(player, active);
            }
        }
    }

    public static boolean isActive(LivingEntity entity) {
        return entity instanceof Player player
                && actions(player).containsKey(player);
    }

    public static Optional<ToolActionState> getState(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return Optional.empty();
        }

        Map<Player, ActiveAction> actions = actions(player);
        synchronized (actions) {
            ActiveAction active = actions.get(player);
            return active == null
                    ? Optional.empty()
                    : Optional.of(active.snapshot());
        }
    }

    public static void cancel(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return;
        }

        Map<Player, ActiveAction> actions = actions(player);
        synchronized (actions) {
            ActiveAction removed = actions.remove(player);
            if (removed != null) {
                endServerSide(player, removed);
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
        // A pre-apex release transitions straight to RELEASE; broadcast it so
        // the immediate return is server-authoritative and synced to trackers.
        // A past-apex release only flags; the descend finishes and fires impact.
        if (active.timeline.requestRelease()) {
            notifyPhaseStarted(player, active);
        }
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
        actions(player).remove(player);
        endServerSide(player, active);
    }

    /**
     * Runs the terminal teardown for any reason the action ends (normal
     * release, cancel, item swap, dimension change, or death). The action has
     * already been removed from its map. Only the server owns gameplay effects
     * and the network broadcast; the client just stops ticking locally.
     */
    private static void endServerSide(Player player, ActiveAction active) {
        if (player.level().isClientSide) {
            return;
        }
        active.action.onServerActionFinished(context(player, active));
        if (!player.isRemoved()) {
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
